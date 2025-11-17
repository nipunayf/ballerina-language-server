# langserver-commons

## Module Overview

**Purpose**: Foundational abstraction layer providing core APIs, interfaces, and Service Provider Interfaces (SPIs) for the Ballerina Language Server. This module defines contracts between components and enables plugin-based extensibility.

**Module Name**: `io.ballerina.language.server.commons`

**Type**: Library (no executable components)

## Key Responsibilities

- **API Definitions**: Core interfaces for all language server operations
- **SPI Framework**: Extensibility points for features (completion, code actions, commands, etc.)
- **Context Objects**: Rich operation contexts providing access to syntax trees, semantic models, workspace
- **Extension Framework**: Multi-language and multi-file format support
- **Workspace Abstractions**: Interfaces for project and document management
- **Client/Server Capabilities**: LSP capability negotiation

## Architecture

### Core Package (`org.ballerinalang.langserver.commons`)

#### Context Interfaces

**LanguageServerContext**
- Central key-value store for shared instances
- Methods: `<V> void put(Key<V>, V)`, `<V> V get(Key<V>)`
- Singleton holder pattern

**DocumentServiceContext**
- Base context for document operations
- Provides: workspace manager, language server context, file URI

**PositionedOperationContext** (extends DocumentServiceContext)
- Operations triggered at cursor positions
- Provides: cursor position, visible symbols, semantic model, syntax tree

**Specialized Contexts**:
- `CompletionContext` → `BallerinaCompletionContext`
- `CodeActionContext`
- `HoverContext`
- `SignatureContext`
- `RenameContext`
- `DefinitionContext`
- `ReferencesContext`
- And 15+ more operation-specific contexts

#### Extension Framework

**LanguageExtension&lt;I, O, C&gt;**
- Generic interface for language-specific feature extensions
- Type parameters: Input, Output, Context
- Methods:
  - `validate(String uri, C context)`: Pre-validation
  - `execute(I input, C context)`: Execute operation
  - `uriScheme()`: Supported URI scheme
  - `kind()`: Feature kind (COMPLETION, CODEACTION, DIAGNOSTIC, FORMAT)

**LanguageFeatureKind** (enum)
- COMPLETION, CODEACTION, DIAGNOSTIC, FORMAT

**BallerinaCompilerApi**
- Adapter pattern for version-agnostic compiler API
- Uses ServiceLoader to select implementation based on Ballerina version
- Provides: workspace detection, type resolution, diagnostics filtering
- Graceful fallback to default version

### SPI Packages - Extension Points

#### completion.spi

**BallerinaCompletionProvider&lt;T extends Node&gt;**
- Core completion provider interface
- Methods:
  - `List<Class<T>> getAttachmentPoints()`: Which syntax nodes trigger
  - `Precedence getPrecedence()`: Provider priority (HIGH, LOW, NORMAL)
  - `List<LSCompletionItem> getCompletions(BallerinaCompletionContext, T)`: Generate items
  - `void sort(BallerinaCompletionContext, T, List<LSCompletionItem>)`: Sort results
  - `boolean onPreValidation(BallerinaCompletionContext, T)`: Pre-filter

**LSCompletionItem**
- Wrapper for LSP `CompletionItem`
- Type classification: OBJECT_FIELD, RECORD_FIELD, SNIPPET, SYMBOL, TYPE, MODULE, KEYWORD, etc.
- Additional metadata for sorting and filtering

#### codeaction.spi

**LSCodeActionProvider** (base interface)
- Methods:
  - `List<CodeAction> getCodeActions(CodeActionContext)`: Generate actions
  - `boolean validate(CodeActionParams)`: Pre-validation
  - `int priority()`: Execution priority

**DiagnosticBasedCodeActionProvider** (extends LSCodeActionProvider)
- Triggered by specific diagnostics
- Method: `List<SupportedDiagnostic> supportedDiagnostics()`: Which diagnostics trigger

**RangeBasedCodeActionProvider** (extends LSCodeActionProvider)
- Triggered by cursor position/selection
- Method: `boolean isEnabled(CodeActionContext)`: Enablement check

**ResolvableCodeActionProvider** (extends LSCodeActionProvider)
- Lazy resolution support
- Method: `CodeAction resolve(CodeAction, CodeActionResolveContext)`: Resolve details

#### command.spi

**LSCommandExecutor**
- Methods:
  - `Object execute(ExecuteCommandContext)`: Command logic
  - `String getCommand()`: Command identifier

**CommandArgument**
- Type-safe command arguments
- JSON deserialization support
- Methods: `key()`, `valueAs(Class<T>)`, `valueAsObject()`, `value()` (JsonElement)

#### codelenses.spi

**LSCodeLensesProvider**
- Methods:
  - `List<CodeLens> getLenses(CodeLensesContext)`: Generate code lenses
  - `boolean isEnabled(CodeLensesContext)`: Enablement check

#### eventsync.spi

**EventSubscriber**
- Observer pattern for language server events
- Methods:
  - `EventKind eventKind()`: Which events to subscribe to
  - `void onEvent(EventContext)`: Event handler
  - `String getName()`: Subscriber identifier

**EventKind** (enum)
- PROJECT_UPDATE, PULL_MODULE, COMPILE_FAILED, WORKSPACE_LOAD_FAILED, etc.

#### service.spi

**ExtendedLanguageServerService**
- Plugin interface for custom JSON-RPC services
- Methods:
  - `Class<?> getRemoteInterface()`: Remote API contract
  - `void init(LanguageServer, WorkspaceManagerProxy, LanguageServerContext)`: Initialization
  - `Map<String, JsonRpcMethod> supportedMethods()`: Custom RPC methods (usually empty)
  - `void shutdown()`, `void exit(int)`: Lifecycle

### Workspace Management (`workspace`)

**WorkspaceManager**
- Central API for project/document/module management
- **Project Operations**:
  - `loadProject(Path)`: Load Ballerina project
  - `Project project(Path)`: Get project for path
  - `loadedProjects()`: All loaded projects
  - `clearProjects()`: Clear cache
- **Document Operations**:
  - `didOpen(Path, DidOpenTextDocumentParams)`: Document opened
  - `didChange(Path, DidChangeTextDocumentParams)`: Document changed
  - `didClose(Path)`: Document closed
  - `didSave(Path)`: Document saved
- **Compilation & Analysis**:
  - `syntaxTree(Path)`: Get syntax tree
  - `semanticModel(Path)`: Get semantic model
  - `compile(Path)`: Compile project
  - `run(Path, String[])`: Run project
- **Module Operations**:
  - `module(Path)`: Get module for file
  - `moduleComponents(ModuleId)`: Module contents

**WorkspaceDocumentManager**
- In-memory document management
- File locking for thread safety
- LSP text document synchronization

**WorkspaceManagerProxy**
- Routing layer for URI scheme-specific workspace managers
- Supports multiple workspace implementations (file://, bala://, expr://)

### TOML Support (`toml`)

**Purpose**: Completion and validation for TOML files (Ballerina.toml, Dependencies.toml, etc.)

**Key Classes**:

**TomlSchemaVisitor**
- Visits TOML schema to generate completions
- Methods:
  - `visit(TomlTable)`, `visit(TomlTableArray)`, `visit(TomlKeyValuePair)`, etc.
  - Returns completion items for each schema element

**TomlNode Types**:
- `TomlTable`: Table in schema
- `TomlTableArray`: Array of tables
- `TomlKeyValuePair`: Key-value pair
- `TomlArray`: Array value
- Each has: `displayName()`, `description()`, `kind()`, `defaultValue()`

**TomlCompletionExtension** (interface)
- Extension point for custom TOML completion
- Method: `List<TomlCompletionItem> getCompletions(TomlCompletionContext)`

**Utilities**:
- `TomlSyntaxTreeUtil`: Syntax tree manipulation for TOML
- `TomlCommonUtil`: Common TOML utilities

### Client Communication (`client`)

**ExtendedLanguageClient** (extends LanguageClient)
- Extended LSP client with custom notifications
- Methods:
  - `traceLogs(MessageParams)`: Trace logging
  - `showTextDocument(ShowDocumentParams)`: Navigate to document
  - `publishArtifacts(PublishDiagnosticsParams)`: Design model artifacts
  - `logTrace(TraceValue)`: Trace state
  - `logMessage(MessageParams)`: Log callback
  - `showMessage(MessageParams)`: Show callback
  - `publishDiagnostics(PublishDiagnosticsParams)`: Diagnostics callback

### Capability Management (`registration`)

**BallerinaServerCapabilitySetter&lt;T&gt;**
- Customize server capabilities
- Methods:
  - `build(ServerCapabilities, T, LanguageServerContext)`: Build capabilities
  - `attachmentPoints()`: Which capabilities to modify

**BallerinaClientCapabilitySetter**
- Process client capabilities
- Methods:
  - `build(ClientCapabilities, ClientCapabilities, LanguageServerContext)`: Build
  - `priority()`: Processing priority

**Capability Models**:
- `BallerinaServerCapability` (enum): Server capability types
- `BallerinaClientCapability` (enum): Client capability types

### Capability (`capability`)

**LSClientCapabilities**
- Aggregated client capabilities
- Experimental capabilities support
- Methods to check feature support

**ExperimentalClientCapabilities**
- Experimental features (show text document, introspection, etc.)

**InitializationOptions**
- Server initialization configuration

## Key Interfaces Summary

### Context Hierarchy
```
DocumentServiceContext
├─ PositionedOperationContext
│  ├─ CompletionContext → BallerinaCompletionContext
│  ├─ CodeActionContext
│  ├─ HoverContext
│  ├─ SignatureContext
│  ├─ DefinitionContext
│  └─ ReferencesContext
└─ Other operation contexts
```

### SPI Registry (META-INF/services)

Extensions register via:
- `io.ballerina.langserver.commons.completion.spi.BallerinaCompletionProvider`
- `io.ballerina.langserver.commons.codeaction.spi.LSCodeActionProvider`
- `io.ballerina.langserver.commons.command.spi.LSCommandExecutor`
- `io.ballerina.langserver.commons.codelenses.spi.LSCodeLensesProvider`
- `io.ballerina.langserver.commons.eventsync.spi.EventSubscriber`
- `io.ballerina.langserver.commons.service.spi.ExtendedLanguageServerService`
- `io.ballerina.langserver.commons.registration.BallerinaClientCapabilitySetter`
- `io.ballerina.langserver.commons.registration.BallerinaServerCapabilitySetter`
- `io.ballerina.langserver.commons.BallerinaCompilerApi`
- `io.ballerina.langserver.commons.toml.spi.TomlCompletionExtension`

## Dependencies

### Core Dependencies
- **ballerina-lang**, **ballerina-parser**, **ballerina-tools-api**: Ballerina compiler APIs
- **org.eclipse.lsp4j**: LSP protocol implementation
- **toml-parser**: TOML file support
- **java-semver**: Semantic versioning for compiler API adapter

## Usage by Other Modules

### All Dependent Modules
- **langserver-core**: Main implementation
- **Model generators**: flow-model, architecture-model, service-model, sequence-model, graphql-model
- **Service extensions**: openapi-service, xsd-service, wsdl-service, edi-service, test-manager-service
- **Utilities**: bal-shell-service, launcher

### Usage Patterns

**1. Implementing Completion Provider**:
```java
public class MyCompletionProvider implements BallerinaCompletionProvider<FunctionNode> {
    @Override
    public List<Class<FunctionNode>> getAttachmentPoints() {
        return List.of(FunctionNode.class);
    }

    @Override
    public Precedence getPrecedence() {
        return Precedence.HIGH;
    }

    @Override
    public List<LSCompletionItem> getCompletions(BallerinaCompletionContext ctx, FunctionNode node) {
        // Generate completions using context
        return completions;
    }
}
```

**2. Implementing Code Action Provider**:
```java
public class MyCodeAction implements DiagnosticBasedCodeActionProvider {
    @Override
    public List<SupportedDiagnostic> supportedDiagnostics() {
        return List.of(new SupportedDiagnostic("BC2066", "Missing return statement"));
    }

    @Override
    public List<CodeAction> getCodeActions(CodeActionContext ctx) {
        // Generate code actions
        return actions;
    }
}
```

**3. Accessing Context Data**:
```java
// In any provider
public void useContext(BallerinaCompletionContext ctx) {
    // Get syntax tree
    SyntaxTree syntaxTree = ctx.currentSyntaxTree().orElse(null);

    // Get semantic model
    SemanticModel semanticModel = ctx.currentSemanticModel().orElse(null);

    // Get visible symbols at cursor
    List<Symbol> visibleSymbols = ctx.visibleSymbols(ctx.getCursorPosition());

    // Get workspace manager
    WorkspaceManager workspace = ctx.workspace();

    // Get language server context
    LanguageServerContext serverCtx = ctx.languageServerContext();
}
```

## Design Patterns

### 1. Service Provider Interface (SPI)
- All extension points use Java ServiceLoader
- Pluggable architecture

### 2. Context Object Pattern
- Rich contexts passed through operation chains
- Lazy-loaded expensive computations
- Immutable operation metadata

### 3. Adapter Pattern
- `BallerinaCompilerApi` abstracts version differences
- Selects best implementation at runtime

### 4. Proxy Pattern
- `WorkspaceManagerProxy` routes to URI-specific managers

### 5. Visitor Pattern
- `TomlSchemaVisitor` for schema-driven completion

### 6. Type Safety
- Generic types throughout: `BallerinaCompletionProvider<T extends Node>`
- Type-safe command arguments

### 7. Priority/Precedence
- Providers have priority for execution ordering
- Enables predictable behavior

### 8. Validation Phases
- Pre-validation before expensive operations
- Filters providers early

### 9. Cancellation Support
- Operations accept `CancelChecker`
- Responsive to user cancellation

### 10. Thread Safety
- Workspace operations include file locking
- Thread-safe context access

## Key Utilities

**CommandArgument**
- Type-safe access to command parameters
- JSON deserialization with Gson

**LSCompletionItem**
- Enhanced completion item with type classification
- Supports custom data for extensions

**TomlSyntaxTreeUtil**
- TOML syntax tree navigation and manipulation

## Development Guidelines

### Implementing a New SPI

1. **Create Interface** in appropriate `.spi` package
2. **Define Contract** with clear javadoc
3. **Add Service File** in `META-INF/services/`
4. **Document** in this CLAUDE.md
5. **Provide Base Class** (optional) for common functionality

### Using Contexts

1. **Never cache contexts**: They are operation-scoped
2. **Access data lazily**: Contexts provide expensive operations on-demand
3. **Check optionals**: Most context methods return `Optional<T>`
4. **Respect cancellation**: Check `CancelChecker` in loops

### Versioning Strategy

- **BallerinaCompilerApi** handles version differences
- Implement versioned adapter for new Ballerina releases
- Maintain backward compatibility in interfaces

## Testing

- No tests in this module (interface definitions only)
- Tests in implementing modules (langserver-core, etc.)

## File Locations

- **Source**: `langserver-commons/src/main/java/org/ballerinalang/langserver/commons/`
- **Resources**: `langserver-commons/src/main/resources/`
- **Build**: `langserver-commons/build.gradle`

## Important Notes for AI Assistants

1. **This is a Contract Module**: No implementation, only interfaces and SPIs
2. **All Extensions Use This**: Every language server extension implements these interfaces
3. **Context Provides Everything**: Always use context objects, never access workspace directly
4. **SPI Pattern is Central**: ServiceLoader is the extension mechanism
5. **Type Safety Matters**: Use generic types correctly for compilation
6. **Version Tolerance**: BallerinaCompilerApi enables supporting multiple compiler versions
7. **Lazy Loading**: Contexts defer expensive operations until needed
8. **Immutability**: Contexts are immutable, don't try to modify them
9. **Optional Everywhere**: Most context methods return Optional, handle appropriately
10. **Thread Safety**: Workspace manager handles locking, trust it

## Related Modules

- **langserver-core**: Main implementation of these interfaces
- **All model generators**: Use SPIs for extensibility
- **All service extensions**: Implement ExtendedLanguageServerService
- **launcher**: Uses workspace manager interfaces
