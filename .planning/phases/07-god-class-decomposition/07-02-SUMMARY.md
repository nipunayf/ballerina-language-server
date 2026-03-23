---
phase: 07-god-class-decomposition
plan: 02
subsystem: workspace
tags: [java, workspace, refactor, language-server]
requires:
  - phase: 07-god-class-decomposition
    provides: ProjectContext extraction and WorkspaceContext base contract
provides:
  - ProjectExecutor delegate with isolated process tracking
  - FileWatchHandler delegate with isolated watched-file routing
affects: [07-03, BallerinaWorkspaceManager, ProjectContext]
tech-stack:
  added: []
  patterns: [delegate-specific context interfaces, facade delegation, extracted process ownership]
key-files:
  created:
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/ProjectExecutor.java
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/ProjectExecutorContext.java
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/FileWatchHandler.java
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/FileWatchHandlerContext.java
  modified:
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/ProjectContext.java
key-decisions:
  - "Moved run-process ownership out of ProjectContext into ProjectExecutor keyed by project root."
  - "Kept Ballerina.toml watched-file upgrade and downgrade handling unchanged while routing it through FileWatchHandler."
  - "Verified with :langserver-core:compileJava because that is the real Gradle module in this repository."
requirements-completed: [DCMP-01, DCMP-02, DCMP-03]
duration: 17 min
completed: 2026-03-23
---

# Phase 7 Plan 02: Project executor and file watch delegate extraction Summary

**Project execution and watched-file routing were moved out of `BallerinaWorkspaceManager` into focused delegates without changing the existing workspace behavior.**

## Performance

- **Duration:** 17 min
- **Completed:** 2026-03-23T03:50:21Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments

- Extracted `ProjectExecutor` and `ProjectExecutorContext`, moving `run`, `stop`, and process lifecycle tracking out of `ProjectContext` and into a dedicated delegate owned by `BallerinaWorkspaceManager`.
- Extracted `FileWatchHandler` and `FileWatchHandlerContext`, moving watched-file dispatch, module-change detection, and reload routing out of `BallerinaWorkspaceManager`.
- Kept the existing file-watcher semantics for `.bal`, `Ballerina.toml`, and module directory changes while shrinking the facade methods down to delegation.

## Task Commits

1. **Task 1: Extract ProjectExecutor** - `249b8c9abf` (refactor)
2. **Task 2: Extract FileWatchHandler** - `1b6ea2cb5c` (refactor)

## Decisions Made

- Project run processes now live in `ProjectExecutor.processMap` instead of `ProjectContext`, so process cleanup no longer depends on mutable project state.
- The `FileWatchHandler` context exposes only the reload, lookup, and open-document access it needs; `BallerinaWorkspaceManager` remains the facade implementation for those contracts.
- `Ballerina.toml` creation still preserves the single-file-to-build-project upgrade path by using `getOrCreateProject(...)` through the new watcher context.

## Verification

- `./gradlew :langserver-core:compileJava`
  - Result: **PASS**
  - Note: Gradle still reports the existing `module-info.java` warning for missing `io.ballerina.datamapper`, but compilation succeeds.

## Deviations from Plan

None in product scope. Verification used `./gradlew :langserver-core:compileJava` as requested because the older `:language-server:language-server-core` path in the plan does not exist in this repository.

## Known Stubs

None.

## Self-Check: PASSED

- Found `.planning/phases/07-god-class-decomposition/07-02-SUMMARY.md`.
- Verified task commits `249b8c9abf` and `1b6ea2cb5c` exist in git history.
