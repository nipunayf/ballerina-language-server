---
phase: 03-cache-invalidation
plan: 03
subsystem: cache
tags: [guava-cache, atomic-loader, concurrency, workspace-manager, characterization-tests]
requires:
  - phase: 03-cache-invalidation
    provides: bounded Guava project registry with workspace-aware eviction metadata
provides:
  - atomic project creation through `projectCache.get(...)`
  - deduplicated load paths for `loadProject`, project-pair creation, and watched TOML creation
  - concurrent characterization coverage for same-root project loads
affects: [03-cache-invalidation, 04-toml-consolidation, 05-workspace-project-correctness]
tech-stack:
  added: []
  patterns: [guava atomic cache loader for project creation, same-root concurrent load characterization with CyclicBarrier]
key-files:
  created: []
  modified:
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java
    - langserver-core/src/test/java/org/ballerinalang/langserver/workspace/CharacterizationTest.java
key-decisions:
  - "Unified project creation behind `getOrCreateProject(...)` so the cache itself enforces single-loader semantics instead of scattered `put` call sites."
  - "Kept crash-recovery behavior in `createOrGetProjectPair(...)` by explicitly evicting crashed entries before delegating back to the atomic loader."
patterns-established:
  - "New project creation paths should route through the Guava cache loader rather than writing directly to `sourceRootToProject`."
  - "Concurrency regressions for project creation should be pinned with same-root load races against the real workspace manager."
requirements-completed: [CACH-03]
duration: 6 min
completed: 2026-03-23
---

# Phase 3 Plan 3: Duplicate load prevention Summary

**Atomic Guava cache loading now deduplicates same-root project creation across direct loads, crash recovery, and watched TOML startup paths**

## Performance

- **Duration:** 6 min
- **Started:** 2026-03-23T01:20:15+05:30
- **Completed:** 2026-03-23T01:26:26+05:30
- **Tasks:** 6
- **Files modified:** 2

## Accomplishments

- Added `getOrCreateProject(...)` to centralize project creation behind `projectCache.get(root, loader)` and reuse the Guava cache's atomic single-loader behavior.
- Converted `createOrGetProjectPair(...)`, watched TOML-triggered creation flows, and `loadProject(...)` to use the atomic loader instead of direct map writes.
- Added concurrent characterization coverage proving same-root project creation deduplicates correctly, then re-ran the full `CharacterizationTest` suite on the final tree.

## Task Commits

Each task was committed atomically:

1. **Task 1: Create atomic project loader helper method** - `d90c540750` (feat)
2. **Task 2: Convert createOrGetProjectPair to use atomic loader** - `163b31d207` (feat)
3. **Task 3: Convert didChangeWatched TOML handlers to use atomic loader** - `f07f8bce17` (feat)
4. **Task 4: Convert loadProject to use atomic loader** - `7764c65ddc` (feat)
5. **Task 5: Add concurrent load characterization** - `84fab43307` (test)
6. **Task 6: Run characterization verification** - `5fdd8505e5` (chore)

## Files Created/Modified

- `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java` - Added the shared atomic project loader and rewired all remaining creation paths to use it.
- `langserver-core/src/test/java/org/ballerinalang/langserver/workspace/CharacterizationTest.java` - Added concurrency coverage for same-root project creation and retry behavior.

## Decisions Made

- Preserved the existing crash-recovery branch by removing crashed entries before calling back into the shared loader, so retries stay explicit while duplicate loads disappear.
- Treated watched TOML creation as a real project-creation path and routed it through the same loader rather than keeping a special-case direct insertion path.

## Deviations from Plan

None - plan executed as intended.

## Issues Encountered

- The executor runtime stopped before writing the summary/docs metadata, so the parent session completed the summary and final roadmap state after the task commits were already present.
- `./gradlew :langserver-core:test --tests org.ballerinalang.langserver.workspace.CharacterizationTest` passed, but Gradle still emitted the repo's existing Docker-daemon warning in this environment.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 3 now has targeted invalidation, bounded project-cache eviction, and atomic same-root loading in place, which closes the cache-correctness phase.
- Phase 4 can build on a stable project-registry lifecycle instead of compensating for duplicate loads or full-cache invalidations.

## Self-Check

PASSED

- FOUND: `d90c540750`
- FOUND: `163b31d207`
- FOUND: `f07f8bce17`
- FOUND: `7764c65ddc`
- FOUND: `84fab43307`
- FOUND: `5fdd8505e5`

---
*Phase: 03-cache-invalidation*
*Completed: 2026-03-23*
