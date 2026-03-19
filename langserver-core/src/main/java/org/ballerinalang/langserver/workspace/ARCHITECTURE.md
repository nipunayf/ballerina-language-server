---
project: Ballerina Language Server — Workspace Manager Redesign
version: 2.0
date: 2026-03-18
iterations: 14
phases-completed: 6
---

# Architecture: Ballerina Language Server — Workspace Manager Redesign

## Overview

The Ballerina Language Server (BLS) workspace manager is being redesigned from a monolithic `BallerinaWorkspaceManager` class into a modular, bounded-context architecture. The redesign addresses critical defects in concurrency safety, memory management, file system integrity, and compilation pipeline threading — while preserving the existing `WorkspaceManager` 18-method interface as a fixed facade (ADR-030). The system runs as a single long-lived JVM process communicating with IDE clients over LSP JSON-RPC via stdio.

## Architectural Drivers

8 primary drivers (ADR-036) dictate all structural decisions:

| # | Driver | Target | ADRs |
|---|--------|--------|------|
| 1 | **LSP Responsiveness via Async Pipeline** (QA-6, BC-5) | 200ms feature response; 100ms cancellation | ADR-007, ADR-018, ADR-042 |
| 2 | **Memory Minimization via Bounding** (QA-2, TC-1) | Max 1.5GB heap for 10 projects over 24h | ADR-013, ADR-019, ADR-041 |
| 3 | **Concurrency Safety & Deadlock Prevention** (QA-1, TC-2) | 50 concurrent requests, 0 deadlocks, 0 data corruption | ADR-009, ADR-006 |
| 4 | **File System Integrity via Change Delta Model** (QA-7, TC-6) | Zero split-brain incidents | ADR-005, ADR-047 |
| 5 | **Workspace Project Support** (QA-4, BC-3) | 5 interconnected packages in one workspace | ADR-019, ADR-024 |
| 6 | **Resolution-Level Interception** (QA-5, TC-3) | Cyclic dependency errors within 500ms | ADR-008, ADR-049 |
| 7 | **High Traceability & Crash Resilience** (QA-3, QA-8, BC-1) | 100% state events logged; 5s auto-recovery | ADR-015, ADR-014, ADR-037 |
| 8 | **Dependency Resolution Resilience** (QA-10, NFR-8, TC-8–10) | Recovery ladder within 500ms; zero re-entrant TOML events | ADR-049, ADR-036 |

## Domain Model

7 bounded contexts decomposed via DDD (Phase 3), mapped to packages (ADR-031):

| Bounded Context | Package | Responsibility | Key Aggregates |
|----------------|---------|---------------|----------------|
| **LSP Gateway** | `lspgateway` | Translate LSP JSON-RPC → domain commands; route responses | ClientSession, ProgressTracker |
| **Workspace Manager** | `workspacemanager` | Project registry, LRU eviction, change delta buffer, URI resolution, document lifecycle | Project, ProjectRegistry, SharedDependencyCache, ChangeBuffer, UriResolver |
| **Compiler Engine** | `compilerengine` | Resolution-first compilation, dual snapshots, health FSM, recovery ladder, cancellation | CompilationPipeline, ProjectSnapshot, ResolutionResult |
| **Execution Manager** | `executionmanager` | Child process lifecycle for bal run/debug | ExecutionProcess, ProcessRegistry |
| **Observability** | `observability` | Metrics, traces, health endpoints (cross-cutting) | MetricRegistry |
| **Resource Monitor** | `resourcemonitor` | Heap pressure detection with graduated levels and hysteresis | HeapPressureMonitor |

**Shared Kernel:** Event Bus (`eventbus` package) — `EventSyncPubSubHolder` with three-tier per-subscriber delivery (ADR-032).

**Key merges:** Document Store merged into Workspace Manager (ADR-046) — VFS is now a change delta buffer (ADR-047), not a content authority. Content ownership belongs to the compiler's `io.ballerina.projects.Document`.

Context map: [`architecture/domain-model/context-map.md`](architecture/domain-model/context-map.md)

## System Structure

### Architectural Pattern

Package-per-bounded-context within a single Gradle project (ADR-031). The `WorkspaceManager` interface is the fixed public facade (ADR-030) — all LSP handlers depend only on this interface. All URI schemes handled directly — no proxy routing (ADR-040).

### Communication Patterns

| Interaction | Pattern |
|-------------|---------|
| LSP handler → bounded context | Synchronous Java method call via facade; all URI schemes handled directly (ADR-040) |
| Bounded context → bounded context | Async domain events via event bus |
| Snapshot reads (latency-sensitive) | Lock-free `AtomicReference` read from `StableSnapshot` (ADR-042) |
| Snapshot reads (correctness-critical) | Blocking `CompletableFuture` await on `InProgressSnapshot` (ADR-042) |
| Document change ingestion | LSP `didChange` → WM `ChangeBuffer.enqueue()` → ChangeApplier drains → `modify()` → WM-E4 (ADR-047) |
| URI resolution | Lock-free `AtomicRef<TrieNode>` read from UriResolver trie (ADR-048) |
| Heap pressure | RM polls `MemoryPoolMXBean` → publishes `RM-E1` to event bus → WM and CE subscribe (ADR-041) |
| Resolution-first pipeline | CE: `pkg.getResolution()` → `pkg.getCompilation()` + `pkg.getBuildToolResolution()` via `BallerinaCompilerApi` (ADR-049) |
| Recovery ladder | CE-internal: qualifying resolution error → stateless retry at escalated modes (ADR-049) |

**No RPC, no HTTP, no message broker** — single JVM process.

### C4 Diagrams

- System Context: [`architecture/diagrams/c4-context.c4`](architecture/diagrams/c4-context.c4)
- Containers: [`architecture/diagrams/c4-container.c4`](architecture/diagrams/c4-container.c4)
- Components: [`architecture/diagrams/c4-component.c4`](architecture/diagrams/c4-component.c4)

### Non-C4 Diagrams

- Sequence — User Types to Snapshot: [`architecture/diagrams/seq-user-types-to-snapshot.mermaid`](architecture/diagrams/seq-user-types-to-snapshot.mermaid)
- State Machine — Project Health FSM: [`architecture/diagrams/state-project-health.mermaid`](architecture/diagrams/state-project-health.mermaid)
- State Machine — Heap Pressure Levels: [`architecture/diagrams/state-pressure-level.mermaid`](architecture/diagrams/state-pressure-level.mermaid)
- Flowchart — Resolution-First Pipeline: [`architecture/diagrams/flow-resolution-first-pipeline.mermaid`](architecture/diagrams/flow-resolution-first-pipeline.mermaid)

### Folder Structure

```
src/main/java/[base]/workspace/
├── WorkspaceManager.java                   ← fixed interface (ADR-030)
│
├── lspgateway/
│   ├── WorkspaceManagerFacadeImpl.java     ← implements WorkspaceManager; delegates all methods; all URI schemes handled directly (ADR-040)
│   ├── ClientSession.java
│   └── ProgressTracker.java
│
├── workspacemanager/
│   ├── ProjectService.java                 ← internal service interface (project lifecycle + document operations)
│   ├── Project.java, ProjectRegistry.java
│   ├── SharedDependencyCache.java
│   ├── ChangeBuffer.java                   ← per-URI per-layer change delta buffer (ADR-047)
│   ├── UriResolver.java                    ← immutable persistent trie for URI resolution (ADR-048)
│   ├── ChangeApplier.java                  ← stateless service: drains buffer, clusters by module, calls modify() (ADR-047)
│   ├── LockingMode.java                    ← enum: SOFT, MEDIUM, HARD, LOCKED
│   └── [value objects: SourceRoot, DocumentUri, ContentVersion, LayerId,
│        HeapEstimate, OpenDocumentCount, EvictionReason, DuplicatedProjectMap, ...]
│
├── compilerengine/
│   ├── CompilationService.java             ← internal service interface
│   ├── CompilationPipeline.java
│   ├── CompileTask.java                    ← minimal surface: cancellation flag, version stamp, debounce timer, compilation future (ADR-043)
│   ├── StableSnapshot.java                 ← AtomicReference to last successful ProjectSnapshot (ADR-042)
│   ├── InProgressSnapshot.java             ← CompletableFuture of current compilation (ADR-042)
│   ├── ProjectSnapshot.java, ResolutionResult.java
│   ├── RecoveryLadder.java                 ← stateless strategy: escalates resolution modes LOCKED→SOFT (ADR-049)
│   └── [value objects: CancellationToken, ProjectHealthState, FailureClass,
│        RetryCount, DebounceTimer, ModuleSignatureHash, SnapshotView, ...]
│
├── executionmanager/
│   ├── ExecutionService.java               ← internal service interface
│   ├── ExecutionProcess.java, ProcessRegistry.java
│   └── [value objects: ProcessId, ProcessState, GracePeriod, ExecutionMode]
│
├── observability/
│   ├── MetricRegistry.java, TelemetryEmitter.java
│   └── WorkspaceTraceLogger.java
│
├── resourcemonitor/
│   ├── HeapPressureMonitor.java            ← polls MemoryPoolMXBean; publishes RM-E1 (ADR-041)
│   └── [value objects: PressureLevel, PressureThreshold, PressureDirection,
│        HysteresisMargin, PollInterval]
│
└── eventbus/
    ├── EventSyncPubSubHolder.java
    ├── EventKind.java
    └── DomainEvent.java
```

## Decisions

| ADR | Title | Status | Phase |
|-----|-------|--------|-------|
| ADR-001 | Architectural Constraints | Superseded by ADR-003 | 1 |
| ADR-002 | Primary Architectural Drivers | Superseded by ADR-004 | 2 |
| ADR-003 | Comprehensive Architectural Constraints | Accepted | 1 |
| ADR-004 | Comprehensive Primary Drivers (Top 7) | Superseded by ADR-036 | 2 |
| ADR-005 | Mandatory Virtual File System (VFS) | Accepted (§1/§3 superseded by ADR-047) | 1 |
| ADR-006 | Immutable State Snapshots | Accepted | 1 |
| ADR-007 | Asynchronous Compilation Pipeline | Accepted (§3 superseded by ADR-042) | 1 |
| ADR-008 | Granular Incremental Processing | Accepted | 1 |
| ADR-009 | Thread-Safe State Management | Accepted | 1 |
| ADR-010 | Robust File Watcher Event Processing | Accepted | 1 |
| ADR-011 | Unified Configuration Management Model | Accepted | 1 |
| ADR-012 | Unified Virtual Document Sandbox | Accepted (§3 superseded by ADR-047) | 1 |
| ADR-013 | Bounded Memory Eviction Policy | Accepted (SS3 reshaped by ADR-041) | 1 |
| ADR-014 | Structured Error Handling & Recovery | Accepted | 1 |
| ADR-015 | Observability, Testability & Telemetry | Accepted | 1 |
| ADR-016 | Isolated Execution Process Management | Accepted | 1 |
| ADR-017 | Phase 2 Gate — Architectural Drivers | Accepted | 2 |
| ADR-018 | Cooperative Cancellation Model | Accepted | 2 |
| ADR-019 | Multi-Project Workspace Management | Accepted | 2 |
| ADR-020 | Progressive Startup & Initialization Strategy | Accepted | 2 |
| ADR-021 | Dependency Resolution & Package Manager | Accepted | 2 |
| ADR-022 | Extensibility & Plugin Architecture | Accepted | 2 |
| ADR-023 | LSP API Surface & Protocol Extension Contract | Accepted | 2 |
| ADR-024 | Dynamic Project Kind Transitioning | Accepted | 2 |
| ADR-025 | Topology-Aware File Event Routing | Accepted (reshaped by ADR-048) | 2 |
| ADR-026 | Cache Coherency Strategy | Accepted | 2 |
| ADR-027 | Granular TOML Reactivity | Superseded by ADR-051 | 2 |
| ADR-028 | Structured and Timing Logging | Accepted | 2 |
| ADR-029 | Generic LSP Handler Registry | Accepted | 2 |
| ADR-030 | WorkspaceManager as Fixed Facade over BCs | Accepted (superseded by ADR-040 for routing) | 3 |
| ADR-031 | Package-per-Bounded-Context Decomposition | Accepted | 4 |
| ADR-032 | Event Bus Backpressure Architecture | Accepted | 3 |
| ADR-033 | Compilation Recovery Loop Prevention | Accepted | 3 |
| ADR-034 | DocumentUri Scheme Preservation Invariant | Superseded by ADR-040 | 3 |
| ADR-035 | Dependency Locking Mode Selection Model | Superseded by ADR-049 | 1 |
| ADR-036 | Primary Drivers — 8 Drivers (Iteration 4) | Accepted | 2 |
| ADR-037 | Async Pipeline Structured Events | Accepted | 5 |
| ADR-038 | Compilation Progress Notification ($/progress) | Accepted | 5 |
| ADR-039 | Phase 6 Gate — Code-Gen Readiness | Accepted | 6 |
| ADR-040 | Unified Workspace Manager with VFS Overlays and Project Duplication | Accepted (§2 superseded by ADR-047; merge by ADR-046) | 3 |
| ADR-041 | Resource Monitor — Graduated Heap Pressure | Accepted | 3 |
| ADR-042 | Dual Snapshot Access Pattern | Accepted | 3 |
| ADR-043 | CompileTask Minimal Surface | Accepted | 3 |
| ADR-044 | Three Diagnostic Sources Before Execution | Accepted | 3 |
| ADR-045 | BallerinaCompilerApi Adapter Boundary | Accepted | 3 |
| ADR-046 | Merge Workspace Manager and Document Store | Accepted | 3 |
| ADR-047 | VFS as Change Delta Buffer with Overlay Layers | Accepted | 3 |
| ADR-048 | URI Resolver — Trie-Cache Resolution Layer | Accepted | 3 |
| ADR-049 | Resolution-First Pipeline with CE-Owned Recovery Ladder | Accepted | 1 |
| ADR-050 | Phase 1 Iteration 4 Gate — ADR-049 Validation | Accepted | 1 |
| ADR-051 | TOML Files as Document Changes | Accepted | 3 |
| ADR-052 | Phase 3 Iteration 3 Gate — Domain Model Validation | Accepted | 3 |

All ADRs: [`architecture/adrs/`](architecture/adrs/)

## Constraints for Implementation

These are non-negotiable structural constraints derived from accepted ADRs:

- **WorkspaceManager interface is frozen** (ADR-030): All 18 methods and signatures preserved exactly. The implementation is a facade that delegates to bounded context services. No domain logic in the facade. Max 5 lines per facade method body.
- **No proxy routing** (ADR-040): All URI schemes (`file://`, `expr://`, `ai://`, `untitled:`) are handled directly by the unified workspace manager. No scheme-based router or multiple workspace manager instances.
- **No cross-package imports** (ADR-031): Bounded context packages must not import sibling packages' internal classes. Communication only via event bus or facade delegation.
- **ChangeBuffer holds deltas, not content** (ADR-047): The buffer accumulates `TextDocumentContentChangeEvent` deltas per-URI per-layer. Content authority belongs to the compiler's `io.ballerina.projects.Document`. No code path may bypass the compiler for resolved content.
- **Compilation never on request threads** (ADR-007): All compilation runs on dedicated `CompilationWorker` threads. Request threads read `StableSnapshot` via `AtomicReference` without locks (ADR-042).
- **Dual snapshot model** (ADR-042): `StableSnapshot` (sync, `AtomicReference`) for latency-sensitive features; `InProgressSnapshot` (async, `CompletableFuture`) for correctness-critical features.
- **Resolution-first pipeline** (ADR-049): CE runs `pkg.getResolution()` before compilation. Mutually exclusive diagnostic events: CE-E4 fires only on resolution errors (no compilation); CE-E5 fires only when resolution is clean.
- **Recovery ladder is stateless CE strategy** (ADR-049): Escalates modes `LOCKED→HARD→MEDIUM→SOFT` without mutating `CompilationOptions` or `BuildOptions`. Locking mode is Project configuration, not a separate controller.
- **LIFO cancellation with thread interrupt** (ADR-018): Superseding tasks cancel in-progress compilation via `isCancelled` flag + `Thread.interrupt()`. No zombie compilations.
- **Weighted LRU eviction** (ADR-013): Project registry is bounded. Heap pressure eviction via RM-E1 events at CRITICAL/EMERGENCY levels (ADR-041).
- **Three-tier event bus delivery** (ADR-032): CRITICAL (bounded queue + timeout), COALESCEABLE (last-write-wins), BEST_EFFORT (ring + head-drop). Per-subscriber isolation mandatory.
- **Single transient retry with circuit breaker** (ADR-033): Recovery loops prevented — max 1 automatic retry, then CIRCUIT_OPEN.
- **DocumentUri preserved at facade boundary** (ADR-040): Raw `java.nio.file.Path` never used at or above facade. `DocumentUri` type required.
- **Explicit locking mode enum** (ADR-035): `BuildOptions.setSticky(true)` must never be used. Four explicit modes: SOFT/MEDIUM/HARD/LOCKED.
- **Self-write suppression** (TC-10): Path-keyed write token mechanism retained for future `Dependencies.toml` persistence (deferred).
- **Content version atomicity** (ADR-005 Mandate 3): Version stamp and content bytes must update in a single atomic operation. No partial reads.
- **All state transitions emit structured events** (ADR-037): Every async pipeline state change publishes to the event bus for observability.
- **All compiler API access via BallerinaCompilerApi** (ADR-045): The pre-existing singleton adapter is the sole sanctioned boundary for `ballerina-tools-api` calls.
- **TOML files are document changes** (ADR-051): TOML files flow through ChangeBuffer like source files — no tiered reactivity classification.

## Verification

Run all architectural constraint scenarios as acceptance tests:

```
architecture/scenarios/*.feature
```

| Feature File | Covers |
|-------------|--------|
| `cross-context-boundary.feature` | Facade constraint, package isolation (ADR-030, ADR-031, ADR-040) |
| `async-compilation-pipeline.feature` | Background compilation, snapshot publication (ADR-007, ADR-042) |
| `vfs-buffer-precedence.feature` | Change delta buffer, content versioning (ADR-047, ADR-005) |
| `event-bus-backpressure.feature` | Three-tier delivery, recovery loops (ADR-032, ADR-033) |
| `cancellation-model.feature` | Cooperative cancellation, LIFO replacement (ADR-018) |
| `memory-eviction.feature` | Weighted LRU, heap pressure (ADR-013, ADR-041) |
| `thread-safety.feature` | Thread-safe data structures, lock granularity (ADR-009, ADR-006) |
| `locking-mode.feature` | Locking mode configuration, recovery ladder, self-write guard (ADR-049, ADR-051) |

**Instruction:** Every `.feature` file must pass as an acceptance test before the implementation is considered complete.

## Known Risks & Trade-offs

### Accepted Risks

| Risk | Status | Mitigation |
|------|--------|-----------|
| R-P4-1: Cold start 5s IWL target may need disk checkpointing | Deferred to implementation | Measure actual cold start; decide then |
| R-P5-3: Memory budget estimation accuracy | Accepted | Backstop: graduated heap pressure (ADR-041) |
| R-P5-5: Self-write suppression race | Accepted | TC-10 token mechanism + ADR-037 event monitoring |
| R-P5-7: Event storm FIFO degradation (8+ projects) | Accepted | Documented limitation; FIFO semantics under extreme load |

### Trade-off Points (from ATAM Phase 5)

| TP | Trade-off | Decision |
|----|-----------|----------|
| TP-1 | Snapshot staleness UX vs. compilation cost | ADR-038: $/progress notifications signal stale state |
| TP-2 | Per-subscriber isolation overhead vs. simplicity | ADR-032: Accept overhead for guaranteed non-blocking |
| TP-3 | Thread interrupt safety vs. cooperative checkpoints | ADR-018 Mandate 8: Interrupt is safe (compiler is side-effect-free) |
| TP-4 | Change delta buffer memory vs. correctness | ADR-047: Accept overhead — correctness is non-negotiable |
| TP-5 | Facade rigidity vs. evolution | ADR-030: Accept rigidity for v1; facade is a fixed constraint |

### Sensitivity Points

- **SP-1:** `CompilationWorker` thread pool sizing — directly impacts responsiveness under multi-project load
- **SP-2:** ChangeBuffer delta accumulation — scales with typing speed and number of open files
- **SP-3:** Circuit breaker threshold — too aggressive causes false positives

## Full Traceability

[`architecture/traceability-matrix.md`](architecture/traceability-matrix.md) — maps every ADR to its driver, evidence source, and current status.

## Maintenance

This is a **living document**. When architectural decisions change during implementation:

1. **Supersede the old ADR** — create a new ADR referencing the superseded one (status: Superseded).
2. **Update this file** — modify the Decisions table, Constraints section, and any affected diagrams or folder structure references.
3. **Update AGENTS.md** — ensure the DO NOT list and service boundaries reflect the new decision.
4. **Update the traceability matrix** — change the ADR Status column and add the new ADR row.
5. **Update or add Gherkin scenarios** — ensure the affected `.feature` file reflects the new constraint.

If this file falls out of sync with the ADRs, the ADRs are authoritative. When in doubt, read `architecture/adrs/` directly.
