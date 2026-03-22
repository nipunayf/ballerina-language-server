---
phase: 03-cache-invalidation
plan: 01
subsystem: infra
tags: [java, cache, invalidation, guava, langserver, characterization]
requires:
  - phase: 02-concurrency-foundations
    provides: Thread-safe project registry mutations and deterministic ProjectContext lifecycle cleanup
provides:
  - Targeted `pathToSourceRootCache` eviction scoped to the mutated source root
  - Cache invalidation coverage proving unrelated project roots survive mutations
  - Explicit invalidation wiring across project put/remove and upgrade paths
affects: [03-cache-invalidation, 04-toml-consolidation, workspace-manager, cache-correctness]
tech-stack:
  added: []
  patterns: [prefix-scoped cache eviction via invalidateCacheFor(Path root), characterization tests for cross-project cache survival]
key-files:
  created: []
  modified:
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java
    - langserver-core/src/test/java/org/ballerinalang/langserver/workspace/CharacterizationTest.java
key-decisions:
  - "Centralized path-cache invalidation in `invalidateCacheFor(Path root)` so every mutation site uses the same prefix-based eviction rule."
  - "Verified cache survival through a real project-upgrade flow instead of mocks, ensuring unrelated project entries remain cached across watched-file invalidation."
patterns-established:
  - "Project mutations invalidate only paths under the affected root after the sourceRootToProject mutation completes."
  - "Cache-correctness regressions are pinned with characterization tests that observe the real `pathToSourceRootCache` behavior."
requirements-completed: [CACH-01]
duration: 2 min
completed: 2026-03-23
---

# Phase 3 Plan 1: Targeted cache invalidation Summary

**Targeted source-root cache eviction replaced scorched-earth clears and preserved unrelated project cache entries across project upgrades**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-23T00:58:42+05:30
- **Completed:** 2026-03-23T01:00:43+05:30
- **Tasks:** 3
- **Files modified:** 2

## Accomplishments

- Added `invalidateCacheFor(Path root)` and routed cache invalidation through a shared prefix-match helper instead of global `pathToSourceRootCache.clear()` calls.
- Replaced the existing source-root cache clears across project creation, replacement, removal, and workspace upgrade paths with targeted invalidation tied to the affected root.
- Added characterization coverage proving one project's invalidation does not evict another project's cached root during a real `Ballerina.toml` upgrade flow.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add targeted cache invalidation helper method** - `6cb8b837c4` (feat)
2. **Task 2: Replace all cache.clear() calls with targeted invalidation** - `eafe96076a` (fix)
3. **Task 3: Add cache entry survival test** - `cebd65017a` (test)

## Files Created/Modified

- `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java` - Added the targeted invalidation helper and rewired all source-root cache eviction call sites to invalidate only the mutated root.
- `langserver-core/src/test/java/org/ballerinalang/langserver/workspace/CharacterizationTest.java` - Added `testCacheEntrySurvival()` to validate unrelated project cache entries survive a watched-file-driven upgrade.

## Decisions Made

- Kept invalidation as a prefix scan over the existing Guava-backed map instead of changing cache configuration, matching the phase context decision to solve correctness before tuning.
- Exercised the cache-survival invariant with a real workspace-manager upgrade path so the test covers file-watch invalidation and path-cache behavior together.

## Deviations from Plan

None - plan executed as intended.

## Issues Encountered

- The executor's verification-only metadata commit failed with `index.lock` permission errors after the task commits were already created, so the summary and roadmap metadata were finished in the parent session.
- `./gradlew :langserver-core:test --tests org.ballerinalang.langserver.workspace.CharacterizationTest` passed, but Gradle still emitted the repo's existing Docker-daemon warning during the run.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 3 now has the targeted invalidation primitive and regression coverage needed for bounded project-cache eviction in `03-02`.
- The remaining cache work can build on root-scoped invalidation instead of clearing the entire path cache on every mutation.

## Self-Check

PASSED

- FOUND: `.planning/phases/03-cache-invalidation/03-01-SUMMARY.md`
- FOUND: `6cb8b837c4`
- FOUND: `eafe96076a`
- FOUND: `cebd65017a`

---
*Phase: 03-cache-invalidation*
*Completed: 2026-03-23*
