# Architecture

**Analysis Date:** 2024-03-22

## Pattern Overview

**Overall:** Layered Architecture with Event-Driven Synchronization

**Key Characteristics:**
- **Centralized Workspace Management:** `BallerinaWorkspaceManager` acts as the single source of truth for all projects and documents in the workspace.
- **Project-Centric Concurrency:** Locking is managed at the project level (`ProjectContext`) to ensure thread-safe operations on the Ballerina compiler's `Project` instances.
- **Hybrid Synchronization:** Uses both direct LSP notifications (`didOpen`, `didChange`) and file system watches (`didChangeWatched`) to keep the internal project state consistent with the disk.

## Layers

**Language Server Protocol (LSP) Interface:**
- Purpose: Handles incoming JSON-RPC requests from the client.
- Location: `langserver-core/src/main/java/org/ballerinalang/langserver/`
- Contains: `BallerinaLanguageServer.java`, `BallerinaTextDocumentService.java`, `BallerinaWorkspaceService.java`
- Depends on: `langserver-commons`, `langserver-core:org.ballerinalang.langserver.workspace`
- Used by: LSP Client (e.g., VS Code)

**Workspace Management Layer:**
- Purpose: Manages the lifecycle of Ballerina projects, mapping file paths to project instances and handling updates.
- Location: `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/`
- Contains: `BallerinaWorkspaceManager.java`, `BallerinaWorkspaceManagerProxy.java`
- Depends on: `io.ballerina.projects` (Compiler API), `langserver-commons`
- Used by: LSP Services (`BallerinaTextDocumentService`, etc.)

**Compiler API Layer:**
- Purpose: Provides the core logic for parsing, compiling, and analyzing Ballerina code.
- Location: External library (`io.ballerina.projects`, `io.ballerina.compiler.api`)
- Contains: `Project`, `Module`, `Package`, `SemanticModel`, `SyntaxTree`
- Depends on: N/A (Standard library)
- Used by: `BallerinaWorkspaceManager`

## Data Flow

**Document Update Flow:**

1. **LSP Event:** Client sends `textDocument/didChange`.
2. **Service Dispatch:** `BallerinaTextDocumentService` receives the event and calls `workspaceManager.didChange()`.
3. **Workspace Locking:** `BallerinaWorkspaceManager` identifies the project root, acquires the `ProjectContext` lock.
4. **Compiler Update:** The internal `Project` instance is updated via the Compiler API (e.g., `document.modify().withContent(content).apply()`).
5. **State Invalidation:** Internal caches (syntax trees, semantic models) are invalidated or marked for refresh.

**Semantic Query Flow:**

1. **LSP Query:** Client sends `textDocument/hover`.
2. **Context Creation:** `BallerinaTextDocumentService` creates a context and calls `workspaceManager.semanticModel(path)`.
3. **Wait for Compilation:** `BallerinaWorkspaceManager` ensures the project is compiled (calling `waitAndGetPackageCompilation`).
4. **Semantic Model Retrieval:** Returns the `SemanticModel` from the compiler for the specific module.
5. **Analysis:** The hover provider uses the `SemanticModel` to look up symbols and returns documentation.

**State Management:**
- **In-Memory Cache:** `pathToSourceRootCache` and `sourceRootToProject` in `BallerinaWorkspaceManager` maintain the mapping of file system paths to `ProjectContext`.
- **ProjectContext:** Encapsulates the `Project` instance, a `ReentrantLock`, and status flags (`compilationCrashed`, `projectCrashed`).

## Key Abstractions

**WorkspaceManager:**
- Purpose: Interface for managing the workspace state.
- Examples: `langserver-commons/src/main/java/org/ballerinalang/langserver/commons/workspace/WorkspaceManager.java`
- Pattern: Strategy / Manager

**ProjectContext:**
- Purpose: Represents the runtime state and synchronization primitive for a single Ballerina project.
- Examples: Inner class in `BallerinaWorkspaceManager.java`
- Pattern: Value Object with synchronization

**LSContextOperation:**
- Purpose: Enum representing various language server operations for logging and telemetry.
- Examples: `langserver-core/src/main/java/org/ballerinalang/langserver/LSContextOperation.java`
- Pattern: Command / Enumeration

## Entry Points

**LSP Server:**
- Location: `langserver-core/src/main/java/org/ballerinalang/langserver/BallerinaLanguageServer.java`
- Triggers: Client connection
- Responsibilities: Initializes services, sets up capabilities.

**Workspace Manager:**
- Location: `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java`
- Triggers: Called by `BallerinaTextDocumentService` and `BallerinaWorkspaceService`.
- Responsibilities: Loading projects, handling file events, providing syntax/semantic models.

## Error Handling

**Strategy:** Fail-soft with user notification via LSP logs/messages.

**Patterns:**
- **Crash Tracking:** `ProjectContext` tracks if a project or compilation has crashed to prevent redundant failing attempts and to trigger reloads.
- **Diagnostic Collection:** Captures compiler diagnostics and publishes them back to the client via `publishDiagnostics`.

## Cross-Cutting Concerns

**Logging:** Centralized via `LSClientLogger`, which logs to the client's output window.
**Validation:** Syntax validation is handled by the compiler's syntax tree; semantic validation via `SemanticModel`.
**Authentication:** N/A (Handles local files primarily).

---

*Architecture analysis: 2024-03-22*
