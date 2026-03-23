---
phase: 06-compilation-gate
plan: 02
subsystem: api
tags: [ballerina, workspace-manager, compilation, recovery]
requires:
  - phase: 06-compilation-gate
    provides: contextual locking mode derivation and single-load project initialization
provides:
  - synchronous compilation recovery inside BallerinaWorkspaceManager
  - online then soft retry path for missing-module compiler failures
  - removal of the obsolete compilation-recovery event subscriber
affects: [06-03-PLAN.md, compilation-gate]
tech-stack:
  added: []
  patterns: [recover compilation under the workspace write lock, clear crash state only after successful retry]
key-files:
  created: []
  modified:
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java
    - langserver-core/src/main/resources/META-INF/services/org.ballerinalang.langserver.commons.eventsync.spi.EventSubscriber
  deleted:
    - langserver-core/src/main/java/org/ballerinalang/langserver/eventsync/subscribers/ResolveCompilationErrorsSubscriber.java
key-decisions:
  - "Handled missing-module compilation recovery directly in waitAndGetPackageCompilation() so subsequent reads observe the recovered project immediately."
  - "Removed the event subscriber because retry policy now belongs to the synchronous compilation path rather than async event handling."
patterns-established:
  - "Module-loading compiler failures retry with offline disabled first, then with SOFT locking mode before marking compilation as crashed."
  - "Non-recoverable compiler failures short-circuit to compilationCrashed without retry."
requirements-completed: [RECV-01, RECV-02, RECV-03]
duration: 8min
completed: 2026-03-23
---

# Phase 06 Plan 02: Compilation Gate Summary

**Workspace-managed compilation recovery with inline retries and subscriber removal**

## Performance

- **Duration:** 8 min
- **Started:** 2026-03-23T01:44:45Z
- **Completed:** 2026-03-23T01:52:52Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- Added a recovery ladder in `waitAndGetPackageCompilation()` that retries missing-module compiler failures with an online reload first and a SOFT-locking reload second.
- Kept the recovery sequence inside the workspace write lock so project replacement, crash flags, and cache invalidation stay synchronized.
- Removed `ResolveCompilationErrorsSubscriber` and its SPI registration because recovery now happens directly in the workspace manager.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add recovery ladder to waitAndGetPackageCompilation** - `1a1c539f2c` (feat)
2. **Task 2: Delete ResolveCompilationErrorsSubscriber** - `1df80e13d1` (fix)
3. **Follow-up: Clean up workspace manager checkstyle** - `0bc22e346d` (fix)

## Files Created/Modified
- `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java` - Added inline compilation retry handling, reload helpers, and crash-state updates for recoverable vs non-recoverable compiler failures.
- `langserver-core/src/main/resources/META-INF/services/org.ballerinalang.langserver.commons.eventsync.spi.EventSubscriber` - Removed the deleted subscriber registration.
- `langserver-core/src/main/java/org/ballerinalang/langserver/eventsync/subscribers/ResolveCompilationErrorsSubscriber.java` - Deleted now that recovery is handled synchronously in the workspace manager.

## Decisions Made
- Treated only `"failed to load the module"` compiler failures as recoverable; other compiler failures still trip `compilationCrashed` immediately.
- Retried with `offline=false` before SOFT locking mode so an online dependency refresh gets the first chance to repair the compilation.

## Deviations from Plan

- Verification used the repo's Gradle compile entrypoint instead of the stale Maven command in the plan.

## Issues Encountered

None beyond a follow-up checkstyle cleanup after the main recovery changes landed.

## User Setup Required

None.

## Next Phase Readiness

- Wave 3 can now add focused tests for locking-mode derivation and the recovery ladder using the committed recovery helpers.
- Remaining risk: compiler thread-safety under concurrent reads is still empirical and should be covered by phase testing rather than assumed from API docs.

## Self-Check: PASSED

- Verified summary file creation on disk.
- Verified commits `1a1c539f2c`, `1df80e13d1`, and `0bc22e346d` exist in git history.
- Verified `./gradlew :langserver-core:compileJava` succeeds after the recovery changes.

---
*Phase: 06-compilation-gate*
*Completed: 2026-03-23*
