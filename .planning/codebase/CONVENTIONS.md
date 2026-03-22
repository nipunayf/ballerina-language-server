# Coding Conventions

**Analysis Date:** 2026-03-22

## Naming Patterns

**Files:**
- `PascalCase.java`: Standard Java naming convention. `BallerinaWorkspaceManager.java`, `WorkspaceManager.java`.

**Functions:**
- `camelCase()`: standard Java method naming. Examples: `projectRoot(Path filePath)`, `loadProject(Path filePath)`, `computeProjectRoot(Path filePath)`.

**Variables:**
- `camelCase`: standard Java variable naming. Examples: `pathToSourceRootCache`, `sourceRootToProject`, `projectLockMap`.
- `UPPER_SNAKE_CASE`: for constants. Examples: `JAVA_COMMAND`, `USER_DIR`, `BALLERINA_TOML`.

**Types:**
- `PascalCase`: for classes and interfaces. `BallerinaWorkspaceManager`, `WorkspaceManager`, `ProjectContext`.

## Code Style

**Formatting:**
- Standard WSO2/Ballerina Java style: 4-space indentation, braces on the same line.

**Linting:**
- Not explicitly detected in file headers, but follows common Java patterns.

## Import Organization

**Order:**
1. Standard Java imports (`java.*`)
2. Third-party library imports (`com.google.*`, `org.eclipse.lsp4j.*`)
3. Project-specific Ballerina imports (`io.ballerina.*`, `org.ballerinalang.*`)
4. Static imports at the end.

**Path Aliases:**
- Standard Java package structure used; no path aliases detected.

## Error Handling

**Patterns:**
- Extensive use of `Optional<T>` for return values that might be missing: `Optional<Project> project(Path filePath)`, `Optional<Document> document(Path filePath)`.
- Explicit checked exceptions for critical failures: `WorkspaceDocumentException`, `EventSyncException`, `ProjectException`.
- Internal try-catch blocks often log errors via `LSClientLogger` before re-throwing or returning empty.
- `try-finally` blocks used for resource management and lock releasing (e.g., `projectLock.unlock()`).

## Logging

**Framework:** `LSClientLogger` (custom wrapper around standard logging, likely SLF4J or similar).

**Patterns:**
- Logger instances are typically initialized per class: `this.clientLogger = LSClientLogger.getInstance(serverContext)`.
- Use of `clientLogger.logError(...)` and `clientLogger.logTrace(...)`.

## Comments

**When to Comment:**
- Javadoc on public methods and classes is mandatory.
- `TODO` comments used for planned refactoring or identified issues.

**JSDoc/TSDoc:**
- Standard JavaDoc (`/** ... */`) used for all public APIs in `BallerinaWorkspaceManager.java`.

## Function Design

**Size:** Methods vary from small getters/utilties to complex logic (e.g., `computeProjectRoot` is ~150 lines).

**Parameters:** Prefer passing `Path` objects for file locations. Often includes `CancelChecker` for long-running operations.

**Return Values:** Heavy usage of `Optional` to avoid `null` returns.

## Module Design

**Exports:** Standard Java visibility modifiers. `BallerinaWorkspaceManager` implements the `WorkspaceManager` interface.

**Barrel Files:** Not applicable in Java; uses standard package organization.

---

*Convention analysis: 2026-03-22*
