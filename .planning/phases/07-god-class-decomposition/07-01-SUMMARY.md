---
phase: 07-god-class-decomposition
plan: 01
subsystem: api
tags: [java, workspace, refactor, language-server]
requires:
  - phase: 06-compilation-gate
    provides: ProjectContext read/write locking and synchronous compilation recovery
provides:
  - Top-level ProjectContext in the workspace package
  - WorkspaceContext base contract for delegate extraction
  - Typed exceptions for compilation crash and project load failures
affects: [07-02, 07-03, BallerinaWorkspaceManager, workspace.toml]
tech-stack:
  added: []
  patterns: [top-level mutable project state holder, package-private extraction context interface, typed workspace exceptions]
key-files:
  created:
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/ProjectContext.java
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/WorkspaceContext.java
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/CompilationCrashException.java
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/ProjectLoadException.java
  modified:
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/toml/TomlHandler.java
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/toml/TomlHandlerContext.java
key-decisions:
  - "Kept ProjectContext mutable and retained process methods in the extracted class for this plan."
  - "Used LSClientLogger as the WorkspaceContext logger contract to match the existing repository type surface."
  - "Verified compilation with :langserver-core:compileJava because the plan's :language-server:language-server-core path does not exist in this repository."
patterns-established:
  - "Top-level shared state objects can be extracted first, with import fallout cleaned up in adjacent packages."
  - "New extraction contracts should reuse existing repository types rather than introducing framework-level logger abstractions."
requirements-completed: [DCMP-04, DCMP-05]
duration: 42 min
completed: 2026-03-23
---

# Phase 7 Plan 01: Foundation extraction for ProjectContext and workspace base types Summary

**Top-level ProjectContext extraction with repo-native workspace context and typed crash/load exceptions for the Ballerina workspace decomposition.**

## Performance

- **Duration:** 42 min
- **Started:** 2026-03-23T02:50:29Z
- **Completed:** 2026-03-23T03:32:12Z
- **Tasks:** 2
- **Files modified:** 14

## Accomplishments

- Extracted `ProjectContext` from `BallerinaWorkspaceManager` into a top-level workspace class without changing its current mutable locking and process behavior.
- Updated TOML handler imports and workspace references so the extracted type compiles across package boundaries.
- Added `WorkspaceContext`, `CompilationCrashException`, and `ProjectLoadException` as the foundation for later delegate extraction and crash signaling.

## Task Commits

Each task was committed atomically:

1. **Task 1: Extract ProjectContext** - `da618cf8de` (refactor)
2. **Task 2: Create base classes** - `235340a271` (feat)

## Files Created/Modified

- `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/ProjectContext.java` - Top-level mutable project state holder extracted from BWM.
- `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java` - Removed the nested `ProjectContext` and switched remaining direct field access to accessors.
- `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/WorkspaceContext.java` - Package-private base context contract for future delegate extraction.
- `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/CompilationCrashException.java` - Runtime exception for unrecoverable compilation failures.
- `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/ProjectLoadException.java` - Checked exception for project loading failures.
- `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/toml/*.java` - Updated TOML handler imports to use the extracted `ProjectContext`.

## Decisions Made

- Kept `ProjectContext` mutable and retained its process field and methods in this plan because process ownership is scheduled for a later extraction step.
- Used `org.ballerinalang.langserver.LSClientLogger` as `WorkspaceContext.logger()` to preserve the existing logger contract in this repository.
- Treated the plan's Gradle verification path as stale and verified with `:langserver-core:compileJava`, which is the actual module present in this repository.

## Deviations from Plan

None in product scope. Verification used `./gradlew :langserver-core:compileJava` instead of the plan's `./gradlew :language-server:language-server-core:compileJava` because the latter project path does not exist in this repository.

## Issues Encountered

- Extracting a formerly nested type required import updates in the `workspace.toml` package before compilation would pass.
- The first `WorkspaceContext` draft used a non-existent `LanguageClientLogger` type and was corrected to `LSClientLogger`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- The shared state holder and base context/exception types are in place for delegate extraction in plans `07-02` and `07-03`.
- Process ownership still lives on `ProjectContext`, which matches the phase context and remains to be moved when `ProjectExecutor` is extracted.

## Self-Check: PASSED

- Found `.planning/phases/07-god-class-decomposition/07-01-SUMMARY.md`.
- Verified task commits `da618cf8de` and `235340a271` exist in git history.

---
*Phase: 07-god-class-decomposition*
*Completed: 2026-03-23*
