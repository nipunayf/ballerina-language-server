# T-053 Context Summary: ChangeBuffer API & Layer Semantics

## Executive Summary

Task T-053 requires implementing comprehensive test coverage for the **ChangeBuffer** — a per-project delta buffer that accumulates pending document and file changes organized by overlay layers (EDITOR, AI, EXPR). The ChangeBuffer replaces the old VFS content authority model with a change-delta architecture that works seamlessly with the compiler's pull-based modify API.

---

## 1. What is ChangeBuffer and How Does It Work?

### Purpose
The ChangeBuffer (ADR-047) is an **aggregate** (Domain-Driven Design) that accumulates pending `TextDocumentContentChangeEvent` changes that have not yet been processed by the ChangeApplier. It is the **single source of pending changes** for a project, designed to:
- Eliminate dual content storage (old VFS + compiler Document)
- Support change batching and optimal clustering before compiler modification
- Provide a unified model for source files, TOML files, and AI/expression overlay content

### Core Design Principles
1. **Stores deltas only** — NOT resolved content. Content authority remains in `io.ballerina.projects.Document`
2. **Per-URI, per-layer isolation** — changes in different layers never interleave
3. **lsp4j native types** — stores `TextDocumentContentChangeEvent` directly without transformation
4. **Open/closed via overlay existence** — EDITOR layer presence ≡ document is open
5. **Thread-safe with concurrent append/drain** — lock-free reads where possible

### Storage Model
```
ChangeBuffer:
├── layeredChanges: Map<DocumentUri, Map<ChangeLayer, ConcurrentLinkedQueue<BufferedChange>>>
├── closedDocChanges: Map<DocumentUri, FileEvent>          (watcher events for closed docs)
└── deferredWatcherEvents: Map<DocumentUri, FileEvent>     (watcher events for open docs)
```

### Key Invariants
1. **Layer isolation** — changes in one layer never interleave with another
2. **Version monotonic per layer** — `latestVersion` strictly increases within each layer
3. **Overlay = authority** — EDITOR overlay existence determines routing of file watcher events
4. **Buffer independence** — ChangeBuffer can exist and queue changes while Project is loading
5. **Drain atomicity** — `drain()` returns all pending changes and clears queue atomically

---

## 2. The Overlay Layers (EDITOR, AI, EXPR)

### Layer Semantics

| Layer | Created By | Compiled Against | URI Scheme | Open State | Lifespan |
|-------|-----------|------------------|-----------|-----------|----------|
| **EDITOR** | LSP `didOpen` | Base project | `file://` | Determines open/closed | User keeps file open |
| **AI** | AI session start | `project.duplicate()` fork | `ai://` | Always "open" in buffer | AI session active |
| **EXPR** | Expression evaluation start | `project.duplicate()` fork | `expr://` | Always "open" in buffer | Expression eval active |

### Key Properties

**EDITOR Layer**
- Represents user edits in the editor
- Tracks `file://` scheme URIs
- Presence/absence determines whether document is "open" in LSP sense
- Watcher events routed to `deferredWatcherEvents` when EDITOR exists
- Drained and applied to base project by ChangeApplier

**AI Layer**
- Represents AI-generated content changes
- Tracks `ai://` scheme URIs
- Applied to a duplicated project fork (`project.duplicate()`)
- Allows AI sessions to compile without affecting base project
- Each AI session has its own independent layer (future extensibility)

**EXPR Layer**
- Represents expression evaluation results
- Tracks `expr://` scheme URIs
- Applied to a duplicated project fork (`project.duplicate()`)
- Similar isolation as AI layer

### Priority Ordering
When draining all layers for a URI, the order is **EDITOR first, then AI, then EXPR**:
```java
for (ChangeLayer layer : ChangeLayer.values()) {  // Enum ordinal order
    all.addAll(drain(uri, layer));
}
```
This ensures EDITOR changes are processed first (highest priority), followed by AI, then EXPR.

---

## 3. Append and Drain Operations

### Append Operation
```java
public void append(DocumentUri uri, BufferedChange change)
```

**Flow:**
1. `ComputeIfAbsent` on URI → creates layer map if needed
2. `ComputeIfAbsent` on layer within map → creates queue if needed
3. Append `BufferedChange` (LSP event + layer + version) to queue
4. Thread-safe: multiple threads can append concurrently

**Thread Safety:**
- Uses `ConcurrentHashMap` for outer and inner maps
- Uses `ConcurrentLinkedQueue` for the actual queue
- No explicit locking needed for append operations

### Drain Operation (Single Layer)
```java
public List<BufferedChange> drain(DocumentUri uri, ChangeLayer layer)
```

**Flow:**
1. Get layer map for URI
2. Use `compute()` on the layer key to atomically:
   - Extract all pending changes into a `List<BufferedChange>`
   - Replace the queue with an empty `ConcurrentLinkedQueue`
3. Return unmodifiable list of drained changes (or empty list if none)

**Atomicity:**
- `compute()` is atomic — no partial drains possible
- After drain, queue still exists (key remains) → document still "open" if EDITOR layer
- Layer map removed only after `clear()` is called or EDITOR layer is explicitly removed

### Drain Operation (All Layers)
```java
public List<BufferedChange> drain(DocumentUri uri)
```

**Flow:**
1. Iterate through all `ChangeLayer` enum values in order
2. Drain each layer individually
3. Accumulate results in priority order (EDITOR first)
4. Return combined unmodifiable list

**Ordering Guarantee:**
- EDITOR changes appear before AI changes, which appear before EXPR changes
- Within a layer, insertion order is preserved

### hasChanges() Query
```java
public boolean hasChanges(DocumentUri uri)
```

- Checks if ANY layer has pending changes for the URI
- Returns `true` if at least one queue is non-empty
- Used by ChangeApplier to avoid unnecessary processing

---

## 4. Layer Priority and Ordering

### Ordering Semantics
1. **Enum ordinal order** — ChangeLayer is an enum with ordinal values:
   - EDITOR = 0
   - AI = 1
   - EXPR = 2

2. **Drain order** — `for (ChangeLayer layer : ChangeLayer.values())` iterates in enum ordinal order

3. **ChangeApplier applies in priority order**:
   ```
   1. All EDITOR changes for all URIs
   2. Then all AI changes for all URIs
   3. Then all EXPR changes for all URIs
   ```

### Why Priority Matters
- **EDITOR layer** represents the most authoritative user content
- **AI layer** should not override user edits (applied to independent fork)
- **EXPR layer** is transient expression evaluation context
- Within each layer, changes are applied in **insertion order** (FIFO)

---

## 5. Concurrent Behavior Expected

### Thread-Safe Operations
1. **Concurrent Append** — Multiple LSP handlers can append changes simultaneously
   ```
   Thread 1: didChange(uri1) → append to EDITOR layer
   Thread 2: didChange(uri2) → append to EDITOR layer
   Thread 3: AI session → append to AI layer
   ```
   All succeed without collision or data loss.

2. **Concurrent Append + Drain** — Handler appends while ChangeApplier drains
   ```
   Thread 1: didChange → append to queue
   Thread 2: ChangeApplier → compute() atomically replaces queue
   ```
   No data loss; newly appended changes may appear after drain if they arrive during the atomic operation.

### Open Detection with Concurrent Access
```java
private boolean isOpen(DocumentUri uri)
```

Open state is determined by **key existence**, not queue emptiness:
- After `drain(uri, EDITOR)`, the EDITOR layer key still exists → document still "open"
- After `clear(uri)` or explicit layer removal, the key is gone → document is "closed"
- File watcher events routed based on this check

### Deferred Watcher Events
```java
public void routeWatcherEvent(DocumentUri uri, FileEvent event)
```

- Calls `isOpen(uri)` to decide routing
- Open: event → `deferredWatcherEvents` (tracked but action deferred)
- Closed: event → `closedDocChanges` (processed immediately)
- Later, `drainDeferredWatcherEvents()` returns all deferred events when EDITOR layer is removed

### Drain Atomicity
- `compute()` callback is executed atomically
- No partial drains — if queue is non-empty, caller gets ALL pending changes
- Queue replacement with empty queue is atomic with change extraction

---

## 6. TOML Files Relationship (ADR-051)

### TOML Changes Flow Through ChangeBuffer
Per ADR-051, **TOML files are treated as document changes**, not special configuration:

| TOML File | Change Type | Handling |
|-----------|------------|----------|
| `Ballerina.toml` | Created/Deleted | File watcher → project kind transition (ADR-024) |
| `Ballerina.toml` | Modified | Accumulated in ChangeBuffer (no compilation triggered) |
| `Dependencies.toml` | Any | Accumulated in ChangeBuffer (no reaction for now) |
| `Cloud.toml` | Any | Accumulated in ChangeBuffer |
| `CompilerPlugin.toml` | Any | Accumulated in ChangeBuffer |
| `BalTool.toml` | Any | Accumulated in ChangeBuffer |

### Key Points
1. **No tiered reactivity classification** — ADR-027 tier system is superseded
2. **Same ChangeBuffer path** — TOML files use the same `append()` and `drain()` as source files
3. **URI is the identifier** — TOML files have URIs like `file:///workspace/Ballerina.toml`
4. **ChangeApplier routes at apply time** — Uses filename to decide which modify API to call:
   - `ballerinaToml.modify().withContent(content).apply()`
   - `dependenciesToml.modify().withContent(content).apply()`
   - etc.

### Self-Write Suppression (TC-10)
- Mechanism retained: path-keyed write tokens suppress file watcher re-triggering
- When `Dependencies.toml` is written by `ResolutionRecovered` event, token prevents re-enqueue
- ChangeBuffer still queues the change, but doesn't cause cascade

---

## 7. BufferedChange and ChangeLayer Types

### BufferedChange Record
```java
public record BufferedChange(
    TextDocumentContentChangeEvent change,
    ChangeLayer layer,
    ContentVersion version
) { ... }
```

**Components:**
- `change`: The actual LSP event (full replacement or incremental edit)
- `layer`: Which overlay (EDITOR, AI, EXPR)
- `version`: Content version associated with the change

### ChangeLayer Enum
```java
public enum ChangeLayer {
    EDITOR,    // User edits via editor (file://)
    AI,        // AI-generated edits (ai://)
    EXPR       // Expression evaluation (expr://)
}
```

**Extensible:** New layers can be added without structural changes to ChangeBuffer.

---

## 8. API Summary for Testing

### Core Methods

| Method | Signature | Purpose | Thread-Safe |
|--------|-----------|---------|-------------|
| `append()` | `void append(DocumentUri uri, BufferedChange change)` | Queue a change to a URI/layer | Yes |
| `drain(uri, layer)` | `List<BufferedChange> drain(DocumentUri uri, ChangeLayer layer)` | Drain single layer atomically | Yes (atomic) |
| `drain(uri)` | `List<BufferedChange> drain(DocumentUri uri)` | Drain all layers in priority order | Yes (per-layer atomic) |
| `clear(uri)` | `void clear(DocumentUri uri)` | Remove all layers for URI | Yes |
| `hasChanges(uri)` | `boolean hasChanges(DocumentUri uri)` | Check if any pending changes | Yes |
| `routeWatcherEvent()` | `void routeWatcherEvent(DocumentUri uri, FileEvent event)` | Route watcher event based on open state | Yes |
| `drainClosedDocChanges()` | `Map<DocumentUri, FileEvent> drainClosedDocChanges()` | Atomic drain of closed-doc watcher events | Yes (atomic) |
| `drainDeferredWatcherEvents()` | `Map<DocumentUri, FileEvent> drainDeferredWatcherEvents()` | Atomic drain of deferred watcher events | Yes (atomic) |

### Open State Detection (Private)
```java
private boolean isOpen(DocumentUri uri)
```
- Returns `true` if EDITOR layer key exists for URI
- Used internally by `routeWatcherEvent()`
- Key point: queue emptiness doesn't matter; key presence is the signal

---

## 9. Expected Behaviors to Test

### Layer Isolation
- Appends to one layer don't affect other layers' queues
- Draining EDITOR doesn't clear AI layer
- Changes from different layers preserve their layer identity

### Version Monotonicity
- Within a layer, versions strictly increase
- Test: append changes with v=1, v=2, v=3; drain; verify order

### Open/Closed State
- EDITOR layer exists → document open
- No EDITOR layer → document closed
- Watcher events routed accordingly

### Drain Atomicity
- Multiple threads appending while draining → no data loss
- Drain returns all pending changes in insertion order
- After drain, queue is empty but key remains (if not cleared)

### Priority Ordering
- Drain all layers returns EDITOR changes first
- Within EDITOR, insertion order preserved
- Then AI changes, then EXPR changes

### File Watcher Routing
- Open doc watcher event → `deferredWatcherEvents`
- Closed doc watcher event → `closedDocChanges`
- Switching between states routes events correctly

### Deferred Event Handling
- EDITOR layer removed → can now drain deferred events
- Deferred events applied after EDITOR changes drained

### Concurrent Scenarios
- Multiple threads appending to different URIs concurrently
- ChangeApplier draining while handlers appending
- Race conditions between `isOpen()` check and watcher event routing

### TOML File Handling
- TOML files have URIs like regular files
- `append()` works for TOML URIs
- `drain()` returns TOML changes mixed with source changes
- Filename inspection determines which modify API to call

### Lifecycle
- ChangeBuffer created with project
- Changes queued independently of Project loading status
- Clear removes all layers and changes
- Eviction discards all pending changes

---

## 10. Key Integration Points

### ChangeApplier (Domain Service)
- Periodically drains ChangeBuffer (triggered by 150ms debounce timer)
- For EDITOR layer: applies to base project
- For AI/EXPR layers: applies to `project.duplicate()` forks
- Reads `document.textDocument().toString()` as base content
- Applies incremental edits to produce full content
- Calls `document.modify().withContent(fullContent).apply()`
- Publishes CE-E5 (CompilationCompleted) after modification

### UriResolver (ADR-048)
- Maps URI → Document in the project
- Called by ChangeApplier during apply phase
- Immutable persistent trie (lock-free reads)

### CompilationPipeline
- Waits for ChangeBuffer to be drained before compiling
- Uses dirty flag (hasChanges) to detect if compilation needed
- Debounce timer (150ms) prevents compile thrashing

---

## 11. Testing Strategy Outline

### Unit Tests (ChangeBuffer in isolation)
1. **Append & Drain**
   - Single-layer append and drain
   - Multi-layer append and drain in order
   - Empty buffer drain returns empty list

2. **Layer Isolation**
   - Changes to different layers don't interleave
   - Clear one layer doesn't affect others

3. **Open State Detection**
   - EDITOR present → isOpen true
   - EDITOR absent → isOpen false
   - Queue emptiness irrelevant

4. **Watcher Event Routing**
   - Open doc event → deferred
   - Closed doc event → closedDocChanges
   - Correct maps returned atomically

5. **Concurrent Append/Drain**
   - Multiple threads append without collision
   - Drain while appending → no data loss
   - hasChanges accurate under concurrent access

6. **Priority Ordering**
   - drain(uri) returns layers in enum order
   - Within layer, insertion order preserved

### Integration Tests (ChangeBuffer with ChangeApplier)
- Append changes, drain, verify compiler receives modifications
- Deferral of watcher events during EDITOR layer presence
- TOML files treated as document changes
- AI/EXPR layers applied to project forks

### Scenario Tests (from .feature file)
- Open file buffer precedence over disk
- Closed file reads from disk
- didClose removes EDITOR overlay
- Content version atomic with delta enqueue
- External file change doesn't override buffer
- TOML files flow through ChangeBuffer
- Post-hoc injection race eliminated
- DocumentUri scheme preserved

---

## Summary of Key APIs and Semantics

**ChangeBuffer** is a thread-safe, per-URI per-layer delta accumulator with these key properties:

1. **Stores deltas only** via `TextDocumentContentChangeEvent` (lsp4j native)
2. **Three layers** (EDITOR, AI, EXPR) with priority-ordered draining
3. **Open/closed via overlay existence** — EDITOR layer presence signals open state
4. **Atomic drain operations** with insertion-order preservation
5. **Concurrent append + drain** without data loss
6. **File watcher routing** based on open state (EDITOR layer)
7. **TOML files treated as document changes** (ADR-051)
8. **Independent of Project loading** — queues changes before project ready

**Test Coverage Should Verify:**
- Layer isolation and priority ordering
- Version monotonicity and insertion order
- Concurrent append/drain without data loss or collision
- Open/closed state detection and watcher event routing
- TOML file handling within same ChangeBuffer
- Integration with ChangeApplier's modify chain
- Deferred watcher event handling
- Lifecycle (create, drain, clear, evict)

