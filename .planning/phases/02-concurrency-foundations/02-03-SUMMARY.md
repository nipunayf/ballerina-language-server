---
phase: 02-concurrency-foundations
plan: 03
subsystem: testing
tags: [java, concurrency, lifecycle, characterization, rwlock, gradle, langserver]
requires:
  - phase: 02-concurrency-foundations
    provides: Fair ProjectContext lock guards and atomic workspace project loading from 02-02
provides:
  - Deterministic ProjectContext close lifecycle for removals, replacements, and shutdown
  - Concurrency characterization coverage for project creation, crash visibility, reopen safety, and read/write coordination
  - Explicit cleanup of removed ProjectContexts so stale lock state cannot leak across reopen paths
affects: [03-cache-invalidation, workspace-manager, concurrency, characterization-tests]
tech-stack:
  added: []
  patterns: [deterministic context shutdown via close(), concurrency invariants pinned with synchronized CharacterizationTest coverage]
key-files:
  created: []
  modified:
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java
    - langserver-core/src/test/java/org/ballerinalang/langserver/workspace/CharacterizationTest.java
key-decisions:
  - "Closed removed and replaced ProjectContext instances after map mutation completes, avoiding lock-order issues between ConcurrentHashMap updates and the ProjectContext write lock."
  - "Used reflection only for the openedDocuments invariant because the production API does not expose that internal concurrent set."
patterns-established:
  - "ProjectContext removal or replacement always closes the old context and clears caches deterministically."
  - "Concurrency characterization tests coordinate threads with CyclicBarrier and CountDownLatch instead of relying on timing-only races."
requirements-completed: [CONC-04, CONC-01, CONC-02, CONC-03, CONC-05]
duration: 7 min
completed: 2026-03-23
---

# Phase 2 Plan 3: Close lifecycle and concurrency characterization Summary

**Deterministic ProjectContext shutdown and synchronized concurrency characterization coverage for workspace manager shared-state invariants**

## Performance

- **Duration:** 7 min
- **Started:** 2026-03-22T19:12:27Z
- **Completed:** 2026-03-22T19:19:03Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Added `ProjectContext.close()` and `isClosed()`, rewired shutdown cleanup, and closed removed or replaced contexts across single-file removal, downgrade, and crash-recovery paths.
- Added five synchronized concurrency characterization tests covering concurrent open/close, same-root project creation, crash-flag visibility, close/reopen freshness, and concurrent read/write behavior.
- Preserved the earlier Phase 2 locking model while pinning the lifecycle and race-prevention guarantees with real multi-threaded workspace-manager tests.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add close() lifecycle to ProjectContext and wire into removal sites and shutdown hook** - `07aff45526` (fix)
2. **Task 2: Add concurrency tests to CharacterizationTest** - `ee9ac5e59a` (test)

## Files Created/Modified

- `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java` - Added deterministic `ProjectContext.close()`, shutdown-hook cleanup, removal-site close handling, and replaced-context cleanup after `compute(...)`.
- `langserver-core/src/test/java/org/ballerinalang/langserver/workspace/CharacterizationTest.java` - Added synchronized concurrency characterization tests and helper utilities for concurrent fixture setup and verification.

## Decisions Made

- Closed replaced contexts only after the `ConcurrentHashMap.compute(...)` callback returns so map mutation never nests the ProjectContext write lock inside the map update.
- Kept the concurrency tests on the real workspace manager and compiler stack; only `openedDocuments` inspection uses reflection because there is no public accessor for that internal set.

## Deviations from Plan

None - plan executed as intended on top of the 02-02 lock migration.

## Issues Encountered

- `./gradlew :langserver-core:test --tests org.ballerinalang.langserver.workspace.CharacterizationTest` passed on the final tree.
- The broader `./gradlew :langserver-core:test` invocation remained live without a terminal completion line during this session, so full-suite verification is still inconclusive here.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 2 now has deterministic lifecycle cleanup and explicit concurrency regression coverage for the shared-state fixes introduced across plans 01-03.
- Phase 3 can build on a stable project-registry and lock model without carrying forward stale ProjectContext instances.

## Self-Check

PASSED

- FOUND: `.planning/phases/02-concurrency-foundations/02-03-SUMMARY.md`
- FOUND: `07aff45526`
- FOUND: `ee9ac5e59a`

---
*Phase: 02-concurrency-foundations*
*Completed: 2026-03-23*
