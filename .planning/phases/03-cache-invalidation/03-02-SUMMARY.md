---
phase: 03-cache-invalidation
plan: 02
subsystem: cache
tags: [guava-cache, lru, workspace-manager, characterization-tests]
requires:
  - phase: 02-concurrency-foundations
    provides: ProjectContext close lifecycle and read/write locking
  - phase: 03-cache-invalidation
    provides: targeted path-to-root cache invalidation
provides:
  - bounded Guava cache backing the project registry
  - workspace-aware cache weights and eviction metadata
  - characterization coverage for LRU eviction and pinning
affects: [03-cache-invalidation, 04-toml-consolidation, 05-workspace-project-correctness]
tech-stack:
  added: []
  patterns: [guava-cache-as-map backing store, workspace-aware cache weighting, temporary fixture cloning in tests]
key-files:
  created: [.planning/phases/03-cache-invalidation/03-02-SUMMARY.md]
  modified:
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java
    - langserver-core/src/test/java/org/ballerinalang/langserver/workspace/CharacterizationTest.java
key-decisions:
  - "Workspace relationship metadata is applied in every workspace load and reload path so cache weight and eviction semantics stay consistent."
  - "LRU eviction tests clone a real build-project fixture into temporary directories to exercise the true 8-slot cache limit without adding permanent repo fixtures."
patterns-established:
  - "Project registry changes should flow through the Guava cache while keeping the field type as Map for minimal call-site churn."
  - "Cache-behavior tests should prefer real temporary project fixtures over cache mocks or test-only accessors."
requirements-completed: [CACH-02]
duration: 6 min
completed: 2026-03-22
---

# Phase 03 Plan 02: LRU eviction for project registry Summary

**Guava-backed 8-slot project registry with workspace-aware eviction, open-document pinning, and real-fixture characterization tests**

## Performance

- **Duration:** 6 min
- **Started:** 2026-03-22T19:38:53Z
- **Completed:** 2026-03-22T19:45:17Z
- **Tasks:** 6
- **Files modified:** 2

## Accomplishments
- Replaced the unbounded `sourceRootToProject` backing store with a Guava cache using `maximumWeight(8)` and `expireAfterAccess(15, TimeUnit.MINUTES)`.
- Added workspace-child metadata, pinning checks, and workspace-root cascade eviction so cache cleanup matches workspace semantics.
- Added characterization coverage proving least-recently-used eviction and active-document pinning against real temporary build-project fixtures.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add workspace relationship fields to ProjectContext** - `b4a1fb0204` (feat)
2. **Task 2: Create Guava Cache with Weigher and RemovalListener** - `b8d6bf49ef` (feat)
3. **Task 3: Add helper methods for pinning and cascade eviction** - `e08d0bd57d` (refactor)
4. **Task 4: Update workspace project creation to set relationship fields** - `5fc151bd1d` (feat)
5. **Task 5: Add LRU eviction test** - `d459f2d1fe` (test)
6. **Task 6: Run full test suite to verify no regressions** - verification only, no code changes

## Files Created/Modified
- `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java` - bounded project cache, workspace metadata propagation, pinning, and cascade eviction
- `langserver-core/src/test/java/org/ballerinalang/langserver/workspace/CharacterizationTest.java` - LRU eviction and pinning characterization tests using temporary project copies
- `.planning/phases/03-cache-invalidation/03-02-SUMMARY.md` - execution record for this plan

## Decisions Made
- Applied workspace relationship metadata in all workspace load/reload paths, not just the explicit workspace cache-population loop, because the cache weigher and removal listener depend on that metadata being present everywhere.
- Used temporary copies of the existing `myproject` fixture for eviction tests because the repository does not contain nine distinct permanent build-project fixtures and the tests need to exercise the real cache limit.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- `gradlew test` emits a Docker daemon connectivity warning in this environment, but the targeted tests and full `CharacterizationTest` suite still completed successfully.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `CACH-02` is complete and verified, and the cache registry now has the workspace metadata needed for duplicate-load prevention in `03-03`.
- No blockers identified for the next cache-invalidation plan.

## Self-Check: PASSED

- Verified `.planning/phases/03-cache-invalidation/03-02-SUMMARY.md` exists.
- Verified task commits `b4a1fb0204`, `b8d6bf49ef`, `e08d0bd57d`, `5fc151bd1d`, and `d459f2d1fe` exist in git history.
