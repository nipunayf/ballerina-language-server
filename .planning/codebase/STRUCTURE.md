# Codebase Structure

**Analysis Date:** 2024-03-22

## Directory Layout

```
ballerina-language-server/
├── langserver-core/          # Main Language Server implementation
│   ├── src/main/java/        # Source code for LS providers, workspace management
│   └── src/test/java/        # Unit and Integration tests for core
├── langserver-commons/       # Shared interfaces and abstractions (SPI)
│   └── src/main/java/        # WorkspaceManager interface, context definitions
├── langserver-cli/           # CLI wrapper for the language server
├── launchers/                # Scripts and launchers for starting the LS (Stdio)
├── docs/                     # Documentation (User Guide, images)
├── misc/                     # Supporting utilities and LS extensions
│   ├── debug-adapter/        # Ballerina Debug Adapter implementation
│   ├── diagram-util/         # Utilities for diagram generation
│   └── ls-extensions/        # Additional LS capabilities
├── architecture-model-generator/ # Service and Architecture modeling logic
├── flow-model-generator/     # Logic for flow diagram modeling
└── persist-service/          # Persistance modeling and logic
```

## Directory Purposes

**langserver-core:**
- Purpose: The heartbeat of the language server.
- Contains: All core LSP handlers (Completion, Diagnostics, Hover, Definition) and the `BallerinaWorkspaceManager`.
- Key files: `org.ballerinalang.langserver.workspace.BallerinaWorkspaceManager.java`, `org.ballerinalang.langserver.BallerinaLanguageServer.java`

**langserver-commons:**
- Purpose: Defines the interfaces used to decouple core logic from external integrations and extensions.
- Contains: Context objects (`DocumentServiceContext`, `CompletionContext`) and service provider interfaces (SPIs).
- Key files: `org.ballerinalang.langserver.commons.workspace.WorkspaceManager.java`, `org.ballerinalang.langserver.commons.LanguageServerContext.java`

**launchers:**
- Purpose: Provides entry points for the server to communicate via standard I/O.
- Contains: Main classes for bootstrapping the server process.

**misc/ls-extensions:**
- Purpose: Extends the core LS with non-standard capabilities required for the Ballerina ecosystem (e.g., visual modeling).

## Key File Locations

**Entry Points:**
- `langserver-core/src/main/java/org/ballerinalang/langserver/BallerinaLanguageServer.java`: Main server initialization class.
- `launchers/stdio-launcher/src/main/java/org/ballerinalang/langserver/launchers/stdio/Main.java`: Stdio entry point.

**Configuration:**
- `langserver-core/src/main/java/org/ballerinalang/langserver/config/LSClientConfigHolder.java`: Management of user-defined LS configuration.

**Core Logic:**
- `langserver-core/src/main/java/org/ballerinalang/langserver/workspace/BallerinaWorkspaceManager.java`: The central manager for projects and documents.

**Testing:**
- `langserver-core/src/test/java/`: Location of test suites for the core server logic.

## Naming Conventions

**Files:**
- PascalCase for Java classes: `BallerinaWorkspaceManager.java`
- Suffix for Providers: `CompletionProvider.java`, `CodeActionProvider.java`

**Directories:**
- Package-based structure following Java conventions: `org/ballerinalang/langserver/workspace/`

## Where to Add New Code

**New LSP Feature:**
- Primary code: `langserver-core/src/main/java/org/ballerinalang/langserver/[feature]/`
- Interface (if needed): `langserver-commons/src/main/java/org/ballerinalang/langserver/commons/`
- Tests: `langserver-core/src/test/java/`

**New Workspace-level Capability:**
- Implementation: Modify `BallerinaWorkspaceManager.java` or add a new manager in the same package.

**New Utility:**
- Shared helpers: `langserver-core/src/main/java/org/ballerinalang/langserver/common/utils/PathUtil.java` or `CommonUtil.java`

## Special Directories

**misc/:**
- Purpose: Contains various standalone modules that integrate with the LS but aren't strictly part of the core server.
- Generated: No
- Committed: Yes

---

*Structure analysis: 2024-03-22*
