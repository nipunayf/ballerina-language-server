# Roadmap: BallerinaWorkspaceManager Gradual Refactor

## Overview

A disciplined, incremental in-place refactor of `BallerinaWorkspaceManager` — the 1900-line god class at the heart of the Ballerina Language Server. The refactor proceeds in six phases ordered by dependency: characterization tests first, then concurrency foundations, then cache correctness, then TOML consolidation, then workspace project hierarchy, and finally the compilation gate (ReadWriteLock + locking mode + recovery). Each phase is independently shippable and must leave all existing tests passing.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Characterization Tests** - Pin behavioral invariants as a safety net before any production code changes (Planned)
- [x] **Phase 2: Concurrency Foundations** - Eliminate data corruption from non-thread-safe collections and dual-lock TOCTOU races (completed 2026-03-22)
- [ ] **Phase 3: Cache Invalidation** - Replace scorched-earth cache eviction with targeted invalidation and bounded project cache
- [ ] **Phase 4: TOML Consolidation** - Collapse 6 near-identical TOML handler methods and distinguish dependency-graph from config-only changes
- [ ] **Phase 5: Workspace Project Correctness** - Establish clean hierarchical model for workspace projects and fix cross-package reload isolation
- [ ] **Phase 6: Compilation Gate** - Replace coarse lock with ReadWriteLock, add locking mode enum, and add compilation recovery
- [ ] **Phase 7: God Class Decomposition** - Decompose the monolithic BallerinaWorkspaceManager into focused, cohesive classes

## Phase Details

### Phase 1: Characterization Tests
**Goal**: A test suite that pins behavioral invariants for all major LSP entry points so every subsequent code change has an automated regression safety net
**Depends on**: Nothing (first phase)
**Requirements**: TEST-01, TEST-02, TEST-03, TEST-04
**Success Criteria** (what must be TRUE):
  1. Tests exist that open, change, and close a document in both single-file and build project contexts and assert the resulting document/package state is correct
  2. Tests exist for file system events — creating and deleting `.bal` files, creating and deleting `Ballerina.toml`, and creating/deleting modules — and assert the workspace state transitions correctly after each
  3. Tests exist that load projects of each kind (single-file, build, bala) and assert the correct project root is resolved for files within each
  4. Tests exist that open a workspace project and assert that the workspace → package → module → document hierarchy is reachable and internally consistent
**Plans**: 3 plans
  - [x] 01-01-PLAN.md — Document lifecycle tests (TEST-01) ✓
  - [x] 01-02-PLAN.md — File system event tests (TEST-02) ✓
  - [x] 01-03-PLAN.md — Project loading and hierarchy tests (TEST-03, TEST-04) ✓

### Phase 2: Concurrency Foundations
**Goal**: All shared mutable state in `BallerinaWorkspaceManager` is accessed through thread-safe collections or under a unified per-project lock, eliminating data corruption under concurrent LSP requests
**Depends on**: Phase 1
**Requirements**: CONC-01, CONC-02, CONC-03, CONC-04, CONC-05
**Success Criteria** (what must be TRUE):
  1. `openedDocuments` is a `ConcurrentHashMap.newKeySet()` — concurrent `didOpen` and `didClose` calls from separate threads cannot corrupt the open-document set
  2. `sourceRootToProject` is a `ConcurrentHashMap` — concurrent project creation and lookup cannot produce duplicate project entries or lost updates
  3. `compilationCrashed` and `projectCrashed` on `ProjectContext` are `volatile` — a crash written by one thread is visible to all reader threads without synchronization
  4. Closing a single-file project removes the corresponding entry from `projectLockMap` — reopening the same file does not reuse a stale lock object
  5. `ProjectContext` exposes read and write locks — syntax tree and semantic model queries hold the read lock (N readers run concurrently); `didChange` and project reload hold the write lock (exclusive)
**Plans**: 3 plans
  - [x] 02-01-PLAN.md — Thread-safe collections and volatile flags (CONC-01, CONC-02, CONC-03)
  - [x] 02-02-PLAN.md — ReentrantReadWriteLock in ProjectContext and lockAndGet migration (CONC-05)
  - [x] 02-03-PLAN.md — Close lifecycle, shutdown hook cleanup, and concurrency tests (CONC-04)

### Phase 3: Cache Invalidation
**Goal**: The path-to-source-root cache uses targeted eviction and the project registry is bounded with LRU eviction, preventing both ABA cache races and unbounded memory growth
**Depends on**: Phase 2
**Requirements**: CACH-01, CACH-02, CACH-03
**Success Criteria** (what must be TRUE):
  1. A project mutation (add or remove file) evicts only the affected paths from `pathToSourceRootCache` — unrelated paths continue to resolve to their correct roots without recomputation
  2. `sourceRootToProject` has a bounded capacity; when a project is evicted, `project.clearCaches()` is called — a workspace with many projects does not accumulate ~90 MB per project indefinitely
  3. Concurrent startup events (didOpen + file watcher) for the same project root do not load the project twice — the second caller receives the same `ProjectContext` via `computeIfAbsent`
**Plans**: 3 plans
  - [x] 03-01-PLAN.md — Targeted cache invalidation (CACH-01)
  - [x] 03-02-PLAN.md — LRU eviction for project registry (CACH-02)
  - [x] 03-03-PLAN.md — Duplicate load prevention (CACH-03)

### Phase 4: TOML Consolidation
**Goal**: The 6 near-identical TOML handler methods are collapsed into a single parameterized method, and dependency-graph TOML changes are distinguished from configuration-only changes so that config-only edits skip the expensive full project reload
**Depends on**: Phase 3
**Requirements**: TOML-01, TOML-02
**Success Criteria** (what must be TRUE):
  1. There is one `updateToml()` method with a dispatch table keyed by TOML file name — adding a new TOML file type requires changing only the dispatch table, not adding a new handler method
  2. Editing a TOML file in a way that only changes configuration (not dependency graph) does not trigger a project reload — the workspace state is unchanged and no compilation is re-queued
**Plans**: 2 plans
  - [ ] 04-01-PLAN.md — Handler hierarchy + registry (Strategy pattern infrastructure)
  - [ ] 04-02-PLAN.md — Wire into BWM + delete old methods + tests (TOML-01, TOML-02)

### Phase 5: Workspace Project Correctness
**Goal**: Workspace projects expose a clean hierarchical model and file events in one package do not trigger reloads in other packages within the same workspace
**Depends on**: Phase 4
**Requirements**: WKSP-01, WKSP-02, WKSP-03
**Success Criteria** (what must be TRUE):
  1. A workspace project can be traversed as `WorkspaceProject` → N `Project` instances → 1 `Package` per project → M `Module`s per package → K `Document`s per module — the hierarchy is navigable without NPE or inconsistency
  2. Opening a file inside a workspace project resolves to the `Package` that contains that file's source root — it does not resolve to a sibling package or the workspace root itself
  3. A file system event (create, delete, or change a `.bal` file) inside package A of a workspace project does not cause package B to reload — diagnostic events and compilation triggers are scoped to the affected package only
**Plans**: 3 plans
  - [ ] 05-01-PLAN.md — Workspace helpers + cascade eviction wiring (WKSP-01, WKSP-02, WKSP-03)
  - [ ] 05-02-PLAN.md — WorkspaceProjectTest class + hierarchy test (WKSP-01)
  - [ ] 05-03-PLAN.md — Path resolution + reload isolation tests (WKSP-02, WKSP-03)

### Phase 6: Compilation Gate
**Goal**: Reads (hover, completion, syntax tree) run concurrently under a read lock while writes compile under a write lock; locking mode is an explicit enum translating to `BuildOptions`; failed compilations retry with SOFT mode before being marked as crashed
**Depends on**: Phase 5
**Requirements**: LOCK-01, LOCK-02, LOCK-03, RECV-01, RECV-02, RECV-03
**Success Criteria** (what must be TRUE):
  1. `ProjectContext` uses a `ReentrantReadWriteLock` — multiple concurrent hover or completion requests are processed simultaneously without queuing behind each other or behind an in-progress compilation
  2. Locking mode is one of four explicit enum values (SOFT, MEDIUM, HARD, LOCKED) — there is no boolean `sticky` flag anywhere in the class
  3. A fresh project without `Dependencies.toml` defaults to SOFT mode; a project with an existing `Dependencies.toml` defaults to MEDIUM mode — the appropriate `BuildOptions` are passed to the compiler for each
  4. When compilation fails with a BIR error, the system automatically retries using SOFT locking mode with a freshly loaded project before surfacing the failure
  5. If the SOFT-mode recovery compilation also fails, `compilationCrashed` is set and no further retry occurs until the next source change
**Plans**: 3 plans
  - [x] 06-01-PLAN.md — Refactor locking mode derivation and single load
  - [x] 06-02-PLAN.md — Implement compilation recovery ladder and delete subscriber
  - [ ] 06-03-PLAN.md — Testing CompilationGate

### Phase 7: God Class Decomposition
**Goal**: Decompose the monolithic BallerinaWorkspaceManager into focused, cohesive classes
**Depends on**: Phase 6
**Requirements**: TBD
**Success Criteria** (what must be TRUE):
  - TBD
**Plans**: Not planned yet

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6 → 7

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Characterization Tests | 3/3 | Completed | 2026-03-22 |
| 2. Concurrency Foundations | 3/3 | Complete   | 2026-03-22 |
| 3. Cache Invalidation | 3/3 | Complete | 2026-03-23 |
| 4. TOML Consolidation | 0/2 | Planned | - |
| 5. Workspace Project Correctness | 0/3 | Planned | - |
| 6. Compilation Gate | 2/3 | Executing | - |
| 7. God Class Decomposition | 0/TBD | Not planned yet | - |
