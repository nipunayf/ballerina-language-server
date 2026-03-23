---
phase: 06-compilation-gate
plan: 03
subsystem: testing
tags: [ballerina, workspace-manager, compilation-gate, tests]
requires:
  - phase: 06-compilation-gate
    provides: locking mode derivation and synchronous recovery ladder implementation
provides:
  - compilation gate regression coverage for locking mode derivation
  - compilation gate regression coverage for recovery ladder retries and crash states
affects: [06-01-PLAN.md, 06-02-PLAN.md, compilation-gate]
tech-stack:
  added: []
  patterns: [reflection-backed compiler API shim, mocked ProjectContext recovery flows]
key-files:
  created:
    - langserver-core/src/test/java/org/ballerinalang/langserver/workspace/CompilationGateTest.java
  modified:
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java
key-decisions:
  - "Used a fake BallerinaCompilerApi singleton in tests to capture BuildOptions and drive reload sequences without relying on compiler internals."
  - "Restored the optimized-dependency SOFT override in BallerinaWorkspaceManager by reloading once with SOFT locking when the loaded project reports optimized dependency compilation."
requirements-completed: [LOCK-01, RECV-01]
duration: 5min
completed: 2026-03-23
---

# Phase 06 Plan 03: Testing Compilation Gate Summary

**Compilation-gate regression tests for locking mode derivation and synchronous recovery retries**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-23T02:09:55Z
- **Completed:** 2026-03-23T02:14:16Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Created `CompilationGateTest` with seven focused tests covering SOFT vs MEDIUM lock selection, the optimized-dependency SOFT override, online recovery, SOFT fallback recovery, repeated-failure crash handling, and fatal compiler short-circuits.
- Verified the recovery ladder through `waitAndGetPackageCompilation()` by seeding `ProjectContext` instances and routing reloads through a fake compiler API that records `BuildOptions`.
- Fixed the missing optimized-dependency override so projects that report optimized dependency compilation are reloaded with `PackageLockingMode.SOFT`.

## Task Commits

1. **Task 1 RED: Locking mode derivation tests** - `e928559378` (test)
2. **Task 1 GREEN: Optimized dependency SOFT override** - `cc9925935f` (feat)
3. **Task 2 RED: Recovery ladder tests** - `2c1991fa21` (test)
4. **Task 2 GREEN: Recovery ladder coverage finalized** - `ea6c6b2e14` (feat)

## Files Created/Modified

- `langserver-core/src/test/java/org/ballerinalang/langserver/workspace/CompilationGateTest.java` - Added locking mode and recovery ladder regression coverage with compiler API capture helpers.
- `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java` - Reintroduced the optimized-dependency SOFT reload path via centralized per-load `BuildOptions`.

## Decisions Made

- Used a fake `BallerinaCompilerApi` in tests so locking mode assertions target the exact `BuildOptions.lockingMode()` values passed by the workspace manager.
- Kept recovery tests at the workspace-manager boundary by injecting mocked `Project` and `Package` instances into `ProjectContext` rather than mocking private methods.
- Reload optimized dependency projects with `SOFT` only when the initial load reports optimized dependency compilation and the chosen locking mode was not already `SOFT`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Restored the optimized-dependency SOFT override**
- **Found during:** Task 1
- **Issue:** `loadProjectResult()` derived `MEDIUM` from `Dependencies.toml` but never applied the required optimized-dependency override, so optimized projects stayed on the wrong locking mode.
- **Fix:** Added a single follow-up reload with `PackageLockingMode.SOFT` when the loaded project reports optimized dependency compilation.
- **Files modified:** `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java`
- **Verification:** `./gradlew :langserver-core:test --tests org.ballerinalang.langserver.workspace.CompilationGateTest`
- **Commit:** `cc9925935f`

**Total deviations:** 1 auto-fixed bug
**Impact:** Brought production behavior in line with the phase requirement that optimized dependency compilation overrides `Dependencies.toml`-driven MEDIUM locking.

## Authentication Gates

None.

## Issues Encountered

None.

## User Setup Required

None.

## Next Phase Readiness

- Phase 06 is now complete with implementation and regression coverage for locking-mode derivation and the synchronous recovery ladder.
- Ready for Phase 07 planning.

## Self-Check: PASSED

- Verified `06-03-SUMMARY.md` exists on disk.
- Verified commits `e928559378`, `cc9925935f`, `2c1991fa21`, and `ea6c6b2e14` exist in git history.
