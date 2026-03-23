---
phase: 06-compilation-gate
plan: 01
subsystem: api
tags: [ballerina, workspace-manager, build-options, locking-mode]
requires:
  - phase: 05-workspace-project-correctness
    provides: project-root and workspace package routing used by project loading
provides:
  - contextual locking mode derivation during project load
  - experimental flag propagation without a global build-options field
  - single-load project initialization in BallerinaWorkspaceManager
affects: [06-02-PLAN.md, 06-03-PLAN.md, compilation-gate]
tech-stack:
  added: []
  patterns: [derive BuildOptions per load, keep experimental state as a boolean flag]
key-files:
  created: []
  modified:
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManagerProxy.java
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManagerProxyImpl.java
    - langserver-core/src/main/java/org/ballerinalang/langserver/BallerinaLanguageServer.java
key-decisions:
  - "Replaced the persisted BuildOptions field with a boolean experimental flag and rebuilt BuildOptions at load time."
  - "Derived PackageLockingMode from Dependencies.toml presence before loading to preserve a single load path."
patterns-established:
  - "Workspace initialization toggles experimental behavior through setExperimental(boolean) instead of prebuilt BuildOptions."
  - "Project loading computes BuildOptions inline with offline, experimental, and locking-mode values."
requirements-completed: [LOCK-01, LOCK-02, LOCK-03]
duration: 6min
completed: 2026-03-23
---

# Phase 06 Plan 01: Compilation Gate Summary

**Single-load project initialization with contextual locking mode and explicit experimental flag propagation**

## Performance

- **Duration:** 6 min
- **Started:** 2026-03-23T01:31:30Z
- **Completed:** 2026-03-23T01:37:11Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- Removed the global workspace-manager `BuildOptions` state and replaced it with an `experimental` flag that is set through the proxy and language server.
- Reworked `loadProjectResult()` to build `BuildOptions` per load with `offline`, `experimental`, and `PackageLockingMode`.
- Eliminated the old triple-load path and now load each project once after deriving locking mode from `Dependencies.toml`.

## Task Commits

Each task was committed atomically:

1. **Task 1: Replace setBuildOptions with setExperimental** - `fbe7c7f7ea` (feat)
2. **Task 2: Implement single-load project derivation in loadProjectResult** - `285dc931dc` (feat)

## Files Created/Modified
- `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java` - Removed persisted build options and derived locking mode/build options during project load.
- `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManagerProxy.java` - Added the proxy contract for experimental project loading.
- `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManagerProxyImpl.java` - Propagated the experimental flag to the managed workspace instances.
- `langserver-core/src/main/java/org/ballerinalang/langserver/BallerinaLanguageServer.java` - Switched LS initialization to `setExperimental(true)`.

## Decisions Made
- Rebuilt `BuildOptions` inside `loadProjectResult()` so locking mode is contextual and no longer sticky across unrelated project loads.
- Used `Dependencies.toml` existence as the pre-load signal for `SOFT` vs `MEDIUM` locking mode because the compiler API only exposes optimized-dependency state on an already loaded `Project`.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- A transient git `index.lock` collision occurred during the first task commit because this plan was running as a parallel executor. Retrying the commit after the stale lock cleared resolved it.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `waitAndGetPackageCompilation()` can now build on the new explicit locking-mode path for the recovery ladder in `06-02`.
- Remaining risk: optimized-dependency compilation cannot currently be detected before the initial load because the compiler API only reports it from an existing `Project`.

## Self-Check: PASSED

- Verified summary file creation on disk.
- Verified task commits `fbe7c7f7ea` and `285dc931dc` exist in git history.

---
*Phase: 06-compilation-gate*
*Completed: 2026-03-23*
