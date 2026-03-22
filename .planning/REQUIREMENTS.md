# Requirements: BallerinaWorkspaceManager Gradual Refactor

**Defined:** 2026-03-22
**Core Value:** Every change must preserve existing behavior — no regressions, no user-visible differences.

## v1 Requirements

Requirements for the initial refactor. Each maps to roadmap phases.

### Testing

- [x] **TEST-01**: Characterization tests cover document lifecycle (open, change, close) for single-file and build projects
- [x] **TEST-02**: Characterization tests cover file system events (create/delete .bal files, create/delete TOML files, module operations)
- [ ] **TEST-03**: Characterization tests cover project loading and project root resolution for all project kinds
- [ ] **TEST-04**: Characterization tests cover workspace project hierarchy (workspace → packages → modules → documents)

### Concurrency

- [x] **CONC-01**: `openedDocuments` uses a thread-safe collection (`ConcurrentHashMap.newKeySet()`) instead of bare `HashSet`
- [x] **CONC-02**: `sourceRootToProject` uses a thread-safe collection (`ConcurrentHashMap`) instead of `HashMap` subclass
- [x] **CONC-03**: `compilationCrashed` and `projectCrashed` flags are `volatile` for cross-thread visibility
- [x] **CONC-04**: `didClose` for single-file projects evicts the lock entry from `projectLockMap` to prevent lock-object leaks
- [x] **CONC-05**: `ProjectContext` uses `ReentrantReadWriteLock` — reads (syntaxTree, semanticModel, document) acquire read lock; writes (didChange, reloadProject) acquire write lock

### Cache

- [ ] **CACH-01**: Path-to-source-root cache uses targeted invalidation (`cache.invalidate(key)`) instead of scorched-earth `cache.clear()`
- [ ] **CACH-02**: `sourceRootToProject` registry has bounded capacity with LRU eviction; evicted entries call `project.clearCaches()`
- [ ] **CACH-03**: Startup project loading uses `computeIfAbsent` pattern to prevent duplicate loads for the same path

### Workspace Projects

- [ ] **WKSP-01**: Clean hierarchical model: `WorkspaceProject` contains N `Project` instances, each `Project` has 1 `Package`, each `Package` has M `Module`s, each `Module` has K `Document`s
- [ ] **WKSP-02**: Opening a file in a workspace project correctly resolves to the right package within the workspace hierarchy
- [ ] **WKSP-03**: File events in one package of a workspace project do not trigger reloads in other packages

### Locking Mode

- [ ] **LOCK-01**: Replace boolean `sticky` flag with 4-level locking mode enum (SOFT, MEDIUM, HARD, LOCKED)
- [ ] **LOCK-02**: Default locking mode is SOFT for fresh projects (no Dependencies.toml) and MEDIUM for existing projects (with Dependencies.toml)
- [ ] **LOCK-03**: Locking mode translates to appropriate `BuildOptions` configuration before passing to the compiler

### Compilation Recovery

- [ ] **RECV-01**: When compilation fails with a BIR error (e.g., `BAD_SAD_FROM_COMPILER`), automatically retry with SOFT locking mode
- [ ] **RECV-02**: Recovery attempt reloads the project with SOFT locking mode before retrying compilation
- [ ] **RECV-03**: If recovery also fails, mark `compilationCrashed` and do not retry until next source change

### TOML Consolidation

- [ ] **TOML-01**: Consolidate the 6 near-identical TOML handler methods into a single parameterized method with a dispatch table
- [ ] **TOML-02**: Distinguish dependency-graph TOML changes from configuration-only changes; skip full reload for config-only changes

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Compilation Pipeline

- **COMP-01**: Async background compilation — `getCompilation()` runs on background executor, semantic queries return latest completed result
- **COMP-02**: Proper `CancelChecker` propagation through `waitAndGetPackageCompilation` — cancelled requests abort early
- **COMP-03**: Snapshot-based query model — writers CAS a new snapshot; readers observe without locking

### Event Handling

- **EVNT-01**: Bulk file event debouncing — coalesce events within a time window per project root
- **EVNT-02**: Active cancellation of zombie compilations via `Future.cancel(true)`

## Out of Scope

| Feature | Reason |
|---------|--------|
| Full event-driven architecture (event bus, bounded contexts) | Too much scope for gradual refactor |
| New WorkspaceManagerFacadeImpl / proxy class | Edit in place constraint |
| Virtual filesystem (VFS) abstraction | Requires changing every file-handling call site |
| Compiler API modifications | External dependency, out of our control |
| Performance benchmarking harness | No measurable targets, just no regressions |
| Reactive pipeline (Rx/Flow) | Unnecessary complexity, CompletableFuture sufficient |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| TEST-01 | Phase 1 | ✓ Complete (01-01) |
| TEST-02 | Phase 1 | ✓ Complete (01-02) |
| TEST-03 | Phase 1 | Pending |
| TEST-04 | Phase 1 | Pending |
| CONC-01 | Phase 2 | Complete |
| CONC-02 | Phase 2 | Complete |
| CONC-03 | Phase 2 | Complete |
| CONC-04 | Phase 2 | Complete |
| CONC-05 | Phase 2 | Complete |
| CACH-01 | Phase 3 | Pending |
| CACH-02 | Phase 3 | Pending |
| CACH-03 | Phase 3 | Pending |
| TOML-01 | Phase 4 | Pending |
| TOML-02 | Phase 4 | Pending |
| WKSP-01 | Phase 5 | Pending |
| WKSP-02 | Phase 5 | Pending |
| WKSP-03 | Phase 5 | Pending |
| LOCK-01 | Phase 6 | Pending |
| LOCK-02 | Phase 6 | Pending |
| LOCK-03 | Phase 6 | Pending |
| RECV-01 | Phase 6 | Pending |
| RECV-02 | Phase 6 | Pending |
| RECV-03 | Phase 6 | Pending |

**Coverage:**
- v1 requirements: 23 total
- Mapped to phases: 23
- Unmapped: 0

---
*Requirements defined: 2026-03-22*
*Last updated: 2026-03-22 after 01-02-PLAN.md — TEST-02 marked complete*
