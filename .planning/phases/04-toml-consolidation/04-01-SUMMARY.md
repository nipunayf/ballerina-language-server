---
phase: 04-toml-consolidation
plan: 01
subsystem: workspace-management
tags: [toml, strategy-pattern, handler, registry]

requires:
  - phase: 02-concurrency-foundations
    provides: ProjectContext.withWriteLock pattern for thread-safe project mutations
  - phase: 03-cache-invalidation
    provides: projectRegistry access and cache invalidation hooks

provides:
  - TomlHandler interface for strategy pattern implementation
  - TomlHandlerContext interface for narrow BWM access
  - AbstractTomlHandler base class with template method pattern
  - 6 concrete TOML handlers (Ballerina, Workspace Ballerina, Dependencies, Cloud, CompilerPlugin, BalTool)
  - TomlHandlerRegistry for dispatch table with workspace bifurcation

affects:
  - 04-02 (TOML integration with BWM)
  - 04-03 (TOML handler tests)

tech-stack:
  added: []
  patterns:
    - Strategy pattern for TOML handling
    - Template method pattern in AbstractTomlHandler
    - Registry pattern for handler dispatch

key-files:
  created:
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/toml/TomlHandler.java
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/toml/TomlHandlerContext.java
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/toml/AbstractTomlHandler.java
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/toml/BallerinaTomlHandler.java
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/toml/WorkspaceBallerinaTomlHandler.java
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/toml/DependenciesTomlHandler.java
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/toml/CloudTomlHandler.java
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/toml/CompilerPluginTomlHandler.java
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/toml/BalToolTomlHandler.java
    - langserver-core/src/main/java/org/ballerinalang/langserver/workspace/toml/TomlHandlerRegistry.java
  modified: []

key-decisions:
  - "Split GenericTomlHandler into 3 explicit handlers (Cloud, CompilerPlugin, BalTool) for clarity"
  - "Config-only handlers (Cloud, CompilerPlugin, BalTool) have affectsDependencyGraph() = false"
  - "Workspace bifurcation handled in TomlHandlerRegistry.lookup() using isWorkspaceProjectRoot()"
  - "AbstractTomlHandler enforces write-lock acquisition in updateContent()"

patterns-established:
  - "Strategy Pattern: TomlHandler interface with fileName(), affectsDependencyGraph(), handleWatchedChange(), updateContent()"
  - "Template Method: AbstractTomlHandler with onCreated/onChanged/onDeleted hooks"
  - "Registry Pattern: TomlHandlerRegistry with Map<String, TomlHandler> dispatch table"
  - "Narrow Interface: TomlHandlerContext exposes only required BWM operations"

requirements-completed:
  - TOML-01

duration: 25min
completed: 2026-03-23
---

# Phase 04 Plan 01: TOML Handler Hierarchy Summary

**Strategy pattern implementation for TOML handling with 10 files in `org.ballerinalang.langserver.workspace.toml` package**

## Performance

- **Duration:** 25 min
- **Started:** 2026-03-23T01:30:00Z
- **Completed:** 2026-03-23T01:55:00Z
- **Tasks:** 3
- **Files created:** 10

## Accomplishments

- Created `TomlHandler` interface defining the strategy contract with 4 methods
- Created `TomlHandlerContext` interface for narrow BWM access (5 methods)
- Created `AbstractTomlHandler` base class with template method pattern
- Created 6 concrete handlers implementing the strategy pattern:
  - `BallerinaTomlHandler` for package-level Ballerina.toml
  - `WorkspaceBallerinaTomlHandler` for workspace root with compiler API integration
  - `DependenciesTomlHandler` for Dependencies.toml
  - `CloudTomlHandler` for config-only Cloud.toml
  - `CompilerPluginTomlHandler` for config-only Compiler-plugin.toml
  - `BalToolTomlHandler` for config-only BalTool.toml
- Created `TomlHandlerRegistry` with dispatch table and workspace bifurcation

## Task Commits

1. **Task 1: Create TomlHandler interface + TomlHandlerContext + AbstractTomlHandler** - `f2ceea3` (feat)
2. **Task 2: Create concrete handler classes** - `6d802a1` (feat)
3. **Task 3: Create TomlHandlerRegistry** - `f6c8eb6` (feat)

## Files Created

### Core Infrastructure (3 files)
- `TomlHandler.java` - Strategy interface with fileName(), affectsDependencyGraph(), handleWatchedChange(), updateContent()
- `TomlHandlerContext.java` - Narrow BWM access: reloadProject(), projectRegistry(), openedDocuments(), logError(), registerWorkspaceChildren()
- `AbstractTomlHandler.java` - Template method base with onCreated/onChanged/onDeleted hooks and write-lock enforcement

### Concrete Handlers (6 files)
- `BallerinaTomlHandler.java` - Package Ballerina.toml (affectsDependencyGraph = true)
- `WorkspaceBallerinaTomlHandler.java` - Workspace root Ballerina.toml with compilerApi.updateWorkspaceToml()
- `DependenciesTomlHandler.java` - Dependencies.toml (affectsDependencyGraph = true)
- `CloudTomlHandler.java` - Cloud.toml (config-only, affectsDependencyGraph = false)
- `CompilerPluginTomlHandler.java` - Compiler-plugin.toml (config-only)
- `BalToolTomlHandler.java` - BalTool.toml (config-only)

### Registry (1 file)
- `TomlHandlerRegistry.java` - Dispatch table with lookup(Path) and workspace bifurcation

## Decisions Made

1. **Split GenericTomlHandler into 3 explicit handlers** — Original plan had 1 GenericTomlHandler using lambdas for Cloud/CompilerPlugin/BalTool. Created 3 explicit handlers instead for clarity and type safety.

2. **Config-only optimization** — Cloud.toml, CompilerPlugin.toml, and BalTool.toml handlers return `affectsDependencyGraph() = false`, enabling future optimization to skip expensive project reloads.

3. **Workspace bifurcation in registry** — `TomlHandlerRegistry.lookup()` checks `isWorkspaceProjectRoot()` for Ballerina.toml files and returns the appropriate handler (package vs workspace).

## Deviations from Plan

### Architectural Change

**[Rule 4 - Architectural] Split GenericTomlHandler into explicit handlers**
- **Found during:** Task 3
- **Issue:** GenericTomlHandler with complex nested lambdas was difficult to read and had type compatibility issues
- **Fix:** Created CloudTomlHandler, CompilerPluginTomlHandler, and BalToolTomlHandler as separate classes
- **Impact:** 10 files instead of planned 8, but code is clearer and type-safe
- **Files modified:** Removed GenericTomlHandler.java, added 3 explicit handlers
- **Verification:** All files compile successfully

## Issues Encountered

None - all files compiled successfully on first attempt after deviation fix.

## Next Phase Readiness

- All handler infrastructure is ready for integration with BallerinaWorkspaceManager
- Ready for Phase 04-02: TOML handler integration and BWM refactoring
- Ready for Phase 04-03: TOML handler tests

## Self-Check: PASSED

- [x] All 10 files exist in `org.ballerinalang.langserver.workspace.toml` package
- [x] `./gradlew :langserver-core:compileJava` succeeds
- [x] TomlHandler interface has 4 methods
- [x] TomlHandlerContext interface has 5 methods
- [x] AbstractTomlHandler enforces write-lock in updateContent()
- [x] CloudTomlHandler.affectsDependencyGraph() returns false (config-only)
- [x] WorkspaceBallerinaTomlHandler uses compilerApi.updateWorkspaceToml()
- [x] TomlHandlerRegistry.lookup() contains isWorkspaceProjectRoot() check

---
*Phase: 04-toml-consolidation*
*Completed: 2026-03-23*
