# langserver-core

## Module Overview

**Purpose**: Core implementation of the Ballerina Language Server Protocol (LSP) server. This module provides full IDE integration capabilities for the Ballerina programming language, including code completion, diagnostics, refactoring, navigation, and more.

**Module Name**: `io.ballerina.language.server.core`

**Size**: 507 Java source files (3.5MB of code)

## Key Responsibilities

- **LSP Compliance**: Complete implementation of Language Server Protocol for Ballerina
- **IDE Features**: Code completion, hover, go-to-definition, find references, rename, formatting, code actions
- **Workspace Management**: Project and document lifecycle management with compilation integration
- **Extensibility**: Plugin-based architecture using Java ServiceLoader for adding custom capabilities
- **Real-time Analysis**: Immediate feedback using Ballerina compiler's semantic model and syntax tree

## Architecture

### Entry Points

**BallerinaLanguageServer** (`BallerinaLanguageServer.java:475`)
- Main server class implementing `LanguageServer` and `ExtendedLanguageServer`
- Lifecycle: `initialize()`, `initialized()`, `shutdown()`, `exit()`
- Capability registration (static and dynamic)
- Service delegation to `BallerinaTextDocumentService` and `BallerinaWorkspaceService`

**BallerinaTextDocumentService** (`BallerinaTextDocumentService.java:716`)
- All text document operations: completion, hover, definition, references, codeAction, formatting, rename, documentSymbol, codeLens, semanticTokens, inlayHint
- Document lifecycle: `didOpen()`, `didChange()`, `didClose()`, `didSave()`

**BallerinaWorkspaceService** (`BallerinaWorkspaceService.java:145`)
- Workspace operations: `executeCommand()`, `didChangeConfiguration()`, `didChangeWatchedFiles()`

### Core Components

#### Workspace Management

**BallerinaWorkspaceManager** (`BallerinaWorkspaceManager.java:1796`)
- **Central workspace orchestrator**
- Project loading, compilation, and caching
- Document synchronization
- Semantic model and syntax tree caching
- Thread-safe with file locking

**BallerinaWorkspaceManagerProxy**
- Proxy pattern for workspace access
- Enables testing with mock workspaces

#### Completion System

**Location**: `/completions/`

**Provider Infrastructure**:
- `AbstractCompletionProvider<T>`: Base class with utilities for module/type/expression completions
- 114+ context-specific providers in `/providers/context/`
- Each syntax node type has dedicated provider (e.g., `FunctionDefinitionNodeContext`, `ServiceDeclarationNodeContext`)

**Builders**: `/completions/builder/`
- `FunctionCompletionItemBuilder`, `TypeCompletionItemBuilder`, etc.
- Ensures consistent completion item creation

**Pattern**:
1. Providers register attachment points (which syntax nodes trigger them)
2. Providers define precedence for ordering
3. Pre-validation filters providers
4. Completion generation with context awareness
5. Sorting by assignability and relevance

#### Code Actions System

**Location**: `/codeaction/`

**50+ Code Action Providers**:

*Refactoring*:
- `ExtractToFunctionCodeAction`, `ExtractToConstantCodeAction`, `ExtractToLocalVarCodeAction`

*Quick Fixes*:
- `AddCheckCodeAction`, `CreateFunctionCodeAction`, `ImplementMethodCodeAction`

*Import Management*: `/codeaction/providers/imports/`

*Documentation*: `/codeaction/providers/docs/`

*Variable Creation*: `/codeaction/providers/createvar/`

*Type Conversion*: `/codeaction/providers/changetype/`

#### Context System

**Location**: `/contexts/`

**24+ Context Implementations**:
- Unified context objects for each operation type
- Provides: syntax tree, semantic model, workspace, cursor position, client capabilities
- Examples: `CompletionContextImpl`, `CodeActionContextImpl`, `HoverContextImpl`

**ContextBuilder**: Factory for creating operation contexts

#### Extension Framework

**Location**: `/extensions/`

**LangExtensionDelegator** (`LangExtensionDelegator.java:300`)
- ServiceLoader pattern for language extensions
- Delegates to registered extensions: completion, code actions, formatting, diagnostics
- Supports custom URI schemes (bala://, expr://)

**Extension Services**: `/extensions/ballerina/`
- `/connector/`: Connector services
- `/document/`: Document services (AST, syntax tree)
- `/example/`: Example code services
- `/packages/`: Package management
- `/runner/`: Code execution
- `/symbol/`: Symbol services

#### Command Execution

**Location**: `/command/`

**LSCommandExecutorProvidersHolder**: Registry for command executors

**Executors**: `/command/executors/`
- Run, documentation, pull modules, etc.
- Each executor implements `LSCommandExecutor` SPI

#### Event System

**Location**: `/eventsync/`

**EventSyncPubSubHolder**: Pub/sub registry

**Pattern**:
- Publishers emit events (PROJECT_UPDATE, COMPILE_FAILED)
- Subscribers react to events
- Decouples event sources from handlers

#### Other Features

- **Definition**: `/definition/` - Go-to-definition
- **References**: `/references/` - Find references
- **Hover**: `/hover/` - Hover information
- **Signature**: `/signature/` - Signature help
- **Rename**: `/rename/` - Symbol renaming
- **Semantic Tokens**: `/semantictokens/` - Semantic highlighting
- **Inlay Hints**: `/inlayhint/` - Parameter/type hints
- **Folding Range**: `/foldingrange/` - Code folding
- **Document Symbol**: `/documentsymbol/` - Outline/structure
- **Code Lenses**: `/codelenses/` - Inline actions
- **Diagnostics**: `/diagnostic/` - Error reporting

## Extension Points (SPIs)

### 1. BallerinaCompletionProvider&lt;T&gt;

**File**: langserver-commons

**Purpose**: Register completion logic for specific syntax nodes

**Methods**:
- `List<Class<T>> getAttachmentPoints()`: Which nodes trigger this provider
- `Precedence getPrecedence()`: Provider priority
- `List<LSCompletionItem> getCompletions(BallerinaCompletionContext, T)`: Generate completions
- `void sort(BallerinaCompletionContext, T, List<LSCompletionItem>)`: Sort results

**Implementations**: 114+ in langserver-core

**Registration**: META-INF/services/io.ballerina.langserver.commons.completion.spi.BallerinaCompletionProvider

### 2. LSCodeActionProvider

**File**: langserver-commons

**Purpose**: Register quick fixes and refactorings

**Methods**:
- `List<CodeAction> getCodeActions(CodeActionContext)`: Generate code actions
- `boolean validate(CodeActionParams)`: Pre-validation

**Implementations**: 50+ in langserver-core

**Registration**: META-INF/services/io.ballerina.langserver.commons.codeaction.spi.LSCodeActionProvider

### 3. LSCommandExecutor

**File**: langserver-commons

**Purpose**: Execute custom commands

**Methods**:
- `Object execute(ExecuteCommandContext)`: Command logic
- `String getCommand()`: Command identifier

**Registration**: META-INF/services/io.ballerina.langserver.commons.command.spi.LSCommandExecutor

### 4. ExtendedLanguageServerService

**File**: langserver-commons

**Purpose**: Add custom LSP methods

**Methods**:
- `void init(LanguageServer, WorkspaceManagerProxy, LanguageServerContext)`: Initialization
- `Map<String, JsonRpcMethod> supportedMethods()`: Custom RPC methods
- `void shutdown()`, `void exit(int)`: Lifecycle

**Examples**: AST provider, example service, connector service

**Registration**: META-INF/services/io.ballerina.langserver.commons.service.spi.ExtendedLanguageServerService

### 5. EventSubscriber

**File**: langserver-commons

**Purpose**: React to workspace events

**Methods**:
- `EventKind eventKind()`: Which events to subscribe to
- `void onEvent(EventContext)`: Event handler
- `String getName()`: Subscriber identifier

**Registration**: META-INF/services/io.ballerina.langserver.commons.eventsync.spi.EventSubscriber

### 6. LSCodeLensesProvider

**File**: langserver-commons

**Purpose**: Provide code lenses (run, test, debug buttons)

**Registration**: META-INF/services/io.ballerina.langserver.commons.codelenses.spi.LSCodeLensesProvider

### 7. LanguageExtension

**File**: langserver-commons

**Purpose**: Multi-language/URI scheme support

**Examples**: TOML completion, Ballerina.toml validation

### 8. BallerinaClientCapabilitySetter

**File**: langserver-commons

**Purpose**: Customize client capability handling

**Registration**: META-INF/services/io.ballerina.langserver.commons.registration.BallerinaClientCapabilitySetter

### 9. BallerinaServerCapabilitySetter

**File**: langserver-commons

**Purpose**: Customize server capability advertising

**Registration**: META-INF/services/io.ballerina.langserver.commons.registration.BallerinaServerCapabilitySetter

### 10. BallerinaCompilerApi

**File**: langserver-commons

**Purpose**: Version-agnostic compiler API adapter

**Registration**: META-INF/services/io.ballerina.langserver.commons.BallerinaCompilerApi

## Dependencies

### Module Dependencies
- **langserver-commons**: All SPI interfaces and common contexts
- **ballerina-lang**: Language core
- **ballerina-parser**: Syntax tree API
- **ballerina-tools-api**: Compiler API
- **ballerina-runtime**: Runtime support
- **formatter-core**: Code formatting
- **diagram-util**: Visual diagram generation
- **central-client**: Ballerina Central integration
- **toml-parser**: TOML file support

### External Libraries
- **org.eclipse.lsp4j**: LSP protocol implementation
- **jackson-databind**, **jackson-dataformat-yaml**: JSON/YAML
- **guava**: Caching and utilities
- **commons-lang3**, **commons-io**: Apache utilities
- **slf4j**: Logging

## Common Patterns

### 1. Service Provider Interface (SPI)
- Extensive use of Java ServiceLoader
- 11+ extension points
- Pluggable architecture for easy feature addition

### 2. Context Pattern
- Rich context objects for each operation
- Single source of truth for operation state
- Lazy-loaded expensive computations

### 3. Builder Pattern
- Completion items, code actions, syntax nodes
- Ensures consistency

### 4. Provider Pattern
- Base classes define common behavior
- Concrete providers implement specific logic
- Attachment points and precedence

### 5. Proxy Pattern
- WorkspaceManagerProxy for abstraction
- Enables testing and multiple implementations

### 6. Pub/Sub Pattern
- Event-driven architecture
- Decoupled components

### 7. Visitor Pattern
- AST traversal for code generation
- Syntax tree walking

### 8. Extension Point Pattern
- Custom URI schemes
- Third-party extensions

### 9. Caching Strategy
- Guava caches for semantic models
- Lazy initialization
- CompletableFuture for async operations

### 10. Error Handling
- `UserErrorException` for user-facing errors
- Graceful degradation (return empty on error)
- `LSClientLogger` for error reporting

## Key Utilities

### CommonUtil (`/common/utils/CommonUtil.java:35KB`)
- Symbol manipulation
- Name generation/validation
- Type resolution
- Module operations

### SymbolUtil (`/common/utils/SymbolUtil.java`)
- Symbol filtering
- Visibility checks
- Symbol comparison

### FunctionGenerator (`/common/utils/FunctionGenerator.java`)
- Function code generation
- Parameter handling

### PathUtil (`/common/utils/PathUtil.java`)
- File path operations
- URI handling

## Development Guidelines

### Adding a New Completion Provider

1. Create class extending `AbstractCompletionProvider<NodeType>`
2. Implement `getAttachmentPoints()` - return node types
3. Implement `getPrecedence()` - set priority
4. Implement `getCompletions()` - generate items
5. Optionally override `sort()` for custom sorting
6. Register in `META-INF/services/io.ballerina.langserver.commons.completion.spi.BallerinaCompletionProvider`

### Adding a New Code Action

1. Create class implementing `LSCodeActionProvider`
2. Implement `validate()` for pre-filtering
3. Implement `getCodeActions()` - return code actions
4. Use `CodeActionUtil` for common operations
5. Register in `META-INF/services/io.ballerina.langserver.commons.codeaction.spi.LSCodeActionProvider`

### Adding a New Command

1. Create class implementing `LSCommandExecutor`
2. Implement `getCommand()` - return command identifier
3. Implement `execute()` - command logic
4. Register in `META-INF/services/io.ballerina.langserver.commons.command.spi.LSCommandExecutor`

### Adding a New Extension Service

1. Create class implementing `ExtendedLanguageServerService`
2. Define remote interface with `@JsonRequest`/`@JsonNotification` methods
3. Implement `init()` to receive server/workspace references
4. Implement `supportedMethods()` to register RPC methods
5. Register in `META-INF/services/io.ballerina.langserver.commons.service.spi.ExtendedLanguageServerService`

## Testing

- Base test class: `AbstractLSTest` (in model-generator-commons)
- Config-based testing with JSON comparisons
- TestNG integration
- Mock workspace managers for unit tests

## Performance Considerations

- **Caching**: Semantic models and syntax trees cached in workspace manager
- **Lazy Loading**: Expensive operations deferred until needed
- **Async Processing**: CompletableFuture for non-blocking operations
- **Incremental Compilation**: Only recompile changed documents
- **Provider Filtering**: Pre-validation reduces unnecessary work
- **Parallel Processing**: Independent operations run concurrently

## File Locations

- **Source**: `langserver-core/src/main/java/org/ballerinalang/langserver/`
- **Resources**: `langserver-core/src/main/resources/`
- **Tests**: `langserver-core/src/test/java/`
- **Build**: `langserver-core/build.gradle`

## Important Notes for AI Assistants

1. **Context is King**: Always access data through context objects, never directly
2. **Use Utilities**: Leverage `CommonUtil`, `SymbolUtil`, etc. rather than reimplementing
3. **Follow SPI Pattern**: Use ServiceLoader for extensibility
4. **Handle Errors Gracefully**: Return empty results on error, don't crash
5. **Respect Cancellation**: Check `CancelChecker` in long-running operations
6. **Thread Safety**: Workspace operations are thread-safe, use locking when needed
7. **LSP Spec Compliance**: Follow LSP specification for all protocol operations
8. **Semantic Model Access**: Use workspace manager, don't compile directly
9. **Position Calculations**: Use `PositionUtil` for cursor/range operations
10. **Import Statements**: Use `ImportUtil` for adding/removing imports

## Related Modules

- **langserver-commons**: Shared interfaces and SPIs
- **langserver-stdlib**: Standard library support
- **langserver-cli**: CLI launcher
- All model generators and service extensions depend on this module
