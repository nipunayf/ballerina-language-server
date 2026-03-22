# Codebase Concerns

**Analysis Date:** 2026-03-22

## Tech Debt

**Workspace Management Refactoring:**
- Issue: The `BallerinaWorkspaceManager` is currently undergoing refactoring as evidenced by the project name `wm-refactor`. There is a TODO in the code suggesting that project context locks should be combined with the project context itself to handle initial compilations correctly.
- Files: `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java`
- Impact: Potential race conditions during the first compilation before the project context is fully established.
- Fix approach: Implement a unified locking mechanism that covers the entire lifecycle of a project from initial load to subsequent updates.

**Redundant Project Loading:**
- Issue: In `createProject`, the code loads the project multiple times (up to 3 times in some paths) to handle sticky build options and optimized dependency compilation workarounds.
- Files: `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java` (lines 1545-1563)
- Impact: Performance degradation during project startup and configuration changes.
- Fix approach: Consolidate build options into a single load call if the underlying `BallerinaCompilerApi` allows, or optimize the project detection to determine required options before loading.

**Mixed Workspace and Package Logic:**
- Issue: `BallerinaWorkspaceManager` handles both individual package projects and the newer "workspace projects" (multi-package). The logic for distinguishing between them is interspersed throughout the file, leading to complex conditional blocks.
- Files: `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java`
- Impact: Increased maintainability cost and risk of regressions when adding support for new project structures.
- Fix approach: Abstract the project handling into a strategy pattern where `WorkspaceProject` and `PackageProject` have distinct management logic.

## Known Bugs

**Shutdown Hook Potential Leak:**
- Issue: The shutdown hook uses a `WeakReference` to `sourceRootToProject` but then iterates over its values. If the map is partially collected or if new projects are added during shutdown, it might lead to inconsistent states.
- Files: `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java` (lines 161-171)
- Impact: Resources (processes) might not be correctly cleaned up on JVM exit in some edge cases.
- Fix approach: Use a more robust lifecycle management system for background processes instead of a shutdown hook on a weak map.

## Security Considerations

**Command Injection in Run Command:**
- Issue: The `run` command constructs execution commands using `java.command` from the context and other parameters. While these usually come from trusted LS configurations, if user-provided paths or arguments are not properly sanitized, it could lead to command injection.
- Files: `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java` (lines 634-704)
- Current mitigation: Uses `ProcessBuilder` which is generally safer than raw string execution, and most parameters come from internal project structures.
- Recommendations: Ensure `javaCmd` and `programArgs` in `RunContext` are strictly validated against allowed patterns.

## Performance Bottlenecks

**Frequent Cache Clearing:**
- Issue: `SourceRootToProjectMap.put` and `remove` call `cache.clear()` which wipes the entire `pathToSourceRootCache`.
- Files: `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java` (lines 1825, 1833, 1841)
- Cause: This is a "best effort" to keep the two maps in sync, but it's very aggressive.
- Improvement path: Implement a more granular cache invalidation strategy that only removes the affected paths instead of clearing the whole cache.

**Blocking Project Loads:**
- Issue: `loadProject` uses a ReentrantLock and performs synchronous project creation, which involves I/O and heavy compiler operations.
- Files: `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java` (lines 234-251)
- Cause: Thread safety requirements for the project map.
- Improvement path: Move project creation to an asynchronous background task and use `CompletableFuture` to avoid blocking the main LS threads.

## Fragile Areas

**Watched File Change Handling:**
- Issue: The `didChangeWatched` logic is extremely complex, manually calculating parent paths to identify project roots and handling "downgrades" (BUILD -> SINGLE_FILE) and "upgrades".
- Files: `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java` (lines 505-626, 849-922)
- Why fragile: Relies on string-based path comparisons and assumptions about the Ballerina project structure (e.g., `modules`, `tests`, `generated` folders). Any change in the Ballerina project layout could break this.
- Safe modification: Rely more on `ProjectPaths` utility and `BallerinaCompilerApi` for all path-to-project mappings.

## Scaling Limits

**Memory Usage for Project Contexts:**
- Current capacity: Up to 1000 entries in `pathToSourceRootCache`.
- Limit: Large workspaces with hundreds of projects or deeply nested structures may hit memory limits because each `ProjectContext` holds a full `Project` instance which includes syntax trees and semantic models.
- Scaling path: Implement a more aggressive eviction policy for `Project` instances in `sourceRootToProject` using a proper LRU cache instead of a plain `HashMap`.

## Missing Critical Features

**Workspace Ballerina.toml Creation:**
- Problem: The code explicitly states that creating a workspace Ballerina.toml is "not yet supported".
- Files: `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java` (line 1397)
- Blocks: Users cannot use the Language Server to initialize new multi-package workspaces.

## Test Coverage Gaps

**Edge Case Project Upgrades/Downgrades:**
- What's not tested: The transitions between `SINGLE_FILE_PROJECT` and `BUILD_PROJECT` when a `Ballerina.toml` is added or removed at runtime.
- Files: `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java` (lines 886-904, 1216-1231)
- Risk: These transitions are complex and involve removing/adding keys to maps; errors here lead to "Project not found" or "Project already exists" exceptions.
- Priority: High

---

*Concerns audit: 2026-03-22*
