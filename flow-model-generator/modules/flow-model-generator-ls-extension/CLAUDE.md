# flow-model-generator-ls-extension

## Module Overview

**Purpose**: Language Server Protocol (LSP) extension that exposes flow model generation, visual programming, and AI-assisted development capabilities to IDEs. Provides comprehensive JSON-RPC API for building low-code/no-code experiences, visual flow designers, data mappers, and AI code assistants.

**Module Name**: `io.ballerina.flowmodelgenerator.extension`

**Type**: LSP Extension (ExtendedLanguageServerService)

## Key Responsibilities

- **Flow Model Generation**: Generate visual flow models from Ballerina functions
- **Available Nodes API**: Provide searchable catalog of functions, connectors, AI components
- **Node Template Generation**: Generate code templates for visual node insertion
- **Source Code Generation**: Convert visual flows back to Ballerina code
- **Data Mapping**: Visual data transformation and mapping interface
- **Expression Editor**: Complete language server for expression editing
- **AI Component Discovery**: Search and recommend AI/ML components
- **Config Variables Management**: Manage configurable variables across flows
- **Function Definition Support**: Extract and manage function signatures
- **OpenAPI Client Generation**: Generate clients from OpenAPI specifications

## Architecture

### Entry Points - LSP Service

**FlowModelGeneratorService** (`FlowModelGeneratorService.java:1000+ lines`)

**JSON-RPC Segment**: `@JsonSegment("flowDesignService")`

**Primary Service**: Single comprehensive service with 25+ endpoints for flow design

### Core JSON-RPC Endpoints

#### Flow Model Operations

**getFlowModel**
```java
@JsonRequest
CompletableFuture<FlowModelGeneratorResponse> getFlowModel(FlowModelGeneratorRequest request)
```
- Generate flow model from Ballerina function
- Returns visual representation of code

**getAvailableNodes**
```java
@JsonRequest
CompletableFuture<FlowModelAvailableNodesResponse> getAvailableNodes(
    FlowModelAvailableNodesRequest request)
```
- Get catalog of available nodes for insertion
- Hierarchical categories and search

**getNodeTemplate**
```java
@JsonRequest
CompletableFuture<FlowModelNodeTemplateResponse> getNodeTemplate(
    FlowModelNodeTemplateRequest request)
```
- Generate code template for node type
- Returns code with placeholders

**generateSource**
```java
@JsonRequest
CompletableFuture<FlowModelSourceGeneratorResponse> generateSource(
    FlowModelSourceGeneratorRequest request)
```
- Convert flow model JSON to Ballerina source code
- Returns TextEdit operations

**suggestFlowNodes**
```java
@JsonRequest
CompletableFuture<FlowModelSuggestedGenerationResponse> suggestFlowNodes(
    FlowModelSuggestedGenerationRequest request)
```
- AI-powered node suggestions
- Context-aware recommendations

#### Data Mapping Operations

**getDataMappingModel**
```java
@JsonRequest
CompletableFuture<DataMappingResponse> getDataMappingModel(DataMappingRequest request)
```
- Generate visual data mapping model
- Field-to-field mapping interface

**addDataMapping**
```java
@JsonRequest
CompletableFuture<DataMappingResponse> addDataMapping(DataMappingRequest request)
```
- Add new field mapping
- Returns updated model

**deleteDataMapping**
```java
@JsonRequest
CompletableFuture<DataMappingResponse> deleteDataMapping(DataMappingRequest request)
```
- Remove field mapping
- Updates dependent mappings

**visualizeDataMapping**
```java
@JsonRequest
CompletableFuture<DataMappingResponse> visualizeDataMapping(DataMappingRequest request)
```
- Get visual representation of mappings
- For UI rendering

**convertToQuery**
```java
@JsonRequest
CompletableFuture<DataMappingResponse> convertToQuery(DataMappingRequest request)
```
- Convert mappings to query expression
- Optimized code generation

**addCustomMapping**
```java
@JsonRequest
CompletableFuture<DataMappingResponse> addCustomMapping(DataMappingRequest request)
```
- Add custom transformation function
- User-defined mapping logic

#### Expression Editor Operations

**getExpressionEditorTypes**
```java
@JsonRequest
CompletableFuture<TypesResponse> getExpressionEditorTypes(TypesRequest request)
```
- Get type information for expressions
- Supports type hints and validation

**getExpressionEditorCompletion**
```java
@JsonRequest
CompletableFuture<CompletionResponse> getExpressionEditorCompletion(
    CompletionRequest request)
```
- Context-aware completion suggestions
- Symbol filtering by type

**getExpressionEditorDiagnostics**
```java
@JsonRequest
CompletableFuture<DiagnosticsResponse> getExpressionEditorDiagnostics(
    DiagnosticsRequest request)
```
- Real-time error checking
- Type validation

**getExpressionEditorSemanticTokens**
```java
@JsonRequest
CompletableFuture<SemanticTokensResponse> getExpressionEditorSemanticTokens(
    SemanticTokensRequest request)
```
- Syntax highlighting for expressions
- Token classification

**getExpressionEditorSignatureHelp**
```java
@JsonRequest
CompletableFuture<SignatureResponse> getExpressionEditorSignatureHelp(
    SignatureRequest request)
```
- Function signature hints
- Parameter information

#### Search Operations

**searchNodes**
```java
@JsonRequest
CompletableFuture<SearchNodesResponse> searchNodes(SearchNodesRequest request)
```
- Search all available nodes
- Keyword-based search with ranking

**searchFunctions**
```java
@JsonRequest
CompletableFuture<SearchResponse> searchFunctions(SearchRequest request)
```
- Search functions from database
- Full-text search with pagination

**searchConnectors**
```java
@JsonRequest
CompletableFuture<SearchResponse> searchConnectors(SearchRequest request)
```
- Search connector clients
- Filter by organization

**searchTypes**
```java
@JsonRequest
CompletableFuture<SearchResponse> searchTypes(SearchRequest request)
```
- Search type definitions
- Record, class, object types

#### Configuration Management

**getConfigVariables**
```java
@JsonRequest
CompletableFuture<ConfigVariablesResponse> getConfigVariables(
    ConfigVariablesRequest request)
```
- Extract configurable variables
- Returns variable definitions

**updateConfigVariables**
```java
@JsonRequest
CompletableFuture<ConfigVariablesResponse> updateConfigVariables(
    ConfigVariablesRequest request)
```
- Update variable configurations
- Applies changes to code

#### AI Component Operations

**getAvailableAgents**
```java
@JsonRequest
CompletableFuture<AgentsResponse> getAvailableAgents(AgentsRequest request)
```
- List available AI agents
- Filtered by capabilities

**getAvailableVectorStores**
```java
@JsonRequest
CompletableFuture<VectorStoresResponse> getAvailableVectorStores(
    VectorStoresRequest request)
```
- List vector store implementations
- For RAG applications

**getAvailableModelProviders**
```java
@JsonRequest
CompletableFuture<ModelProvidersResponse> getAvailableModelProviders(
    ModelProvidersRequest request)
```
- List LLM providers (OpenAI, Anthropic, etc.)
- With capabilities

**getKnowledgeBaseNodes**
```java
@JsonRequest
CompletableFuture<KnowledgeBaseResponse> getKnowledgeBaseNodes(
    KnowledgeBaseRequest request)
```
- Get knowledge base configuration nodes
- For building RAG pipelines

#### Function Operations

**getFunctionDefinition**
```java
@JsonRequest
CompletableFuture<FunctionDefinitionResponse> getFunctionDefinition(
    FunctionDefinitionRequest request)
```
- Extract function signature and metadata
- For function catalog

**getEnclosedFunctionDefinition**
```java
@JsonRequest
CompletableFuture<EnclosedFuncDefResponse> getEnclosedFunctionDefinition(
    EnclosedFuncDefRequest request)
```
- Get function containing a position
- For context detection

**getVisibleVariableTypes**
```java
@JsonRequest
CompletableFuture<VisibleVariableTypesResponse> getVisibleVariableTypes(
    VisibleVariableTypesRequest request)
```
- Get variables in scope with types
- For data mapping source selection

#### Node Manipulation

**deleteFlowNode**
```java
@JsonRequest
CompletableFuture<FlowNodeDeleteResponse> deleteFlowNode(FlowNodeDeleteRequest request)
```
- Delete node from flow
- Updates dependent nodes

**deleteComponent**
```java
@JsonRequest
CompletableFuture<ComponentDeleteResponse> deleteComponent(
    ComponentDeleteRequest request)
```
- Delete entire component (service, function)
- Cascading deletion

#### Service Generation

**generateOpenAPIService**
```java
@JsonRequest
CompletableFuture<OpenApiServiceGenerationResponse> generateOpenAPIService(
    OpenAPIServiceGenerationRequest request)
```
- Generate Ballerina service from OpenAPI spec
- Complete service scaffolding

**generateOpenAPIClient**
```java
@JsonRequest
CompletableFuture<OpenApiClientGenerationResponse> generateOpenAPIClient(
    OpenAPIClientGenerationRequest request)
```
- Generate Ballerina client from OpenAPI spec
- Type-safe client generation

**getServiceFieldNodes**
```java
@JsonRequest
CompletableFuture<ServiceFieldNodesResponse> getServiceFieldNodes(
    ServiceFieldNodesRequest request)
```
- Get service configuration fields
- For service builder UI

#### Error Handling

**generateErrorHandler**
```java
@JsonRequest
CompletableFuture<ErrorHandlerResponse> generateErrorHandler(
    ErrorHandlerRequest request)
```
- Generate error handling code
- Check, do-on-fail, trap patterns

#### Copilot Integration

**getCopilotContext**
```java
@JsonRequest
CompletableFuture<CopilotContextResponse> getCopilotContext(
    CopilotContextRequest request)
```
- Provide context for AI code generation
- Visible symbols, types, signatures

**getSuggestedComponents**
```java
@JsonRequest
CompletableFuture<SuggestedComponentResponse> getSuggestedComponents(
    SuggestedComponentRequest request)
```
- AI-suggested components
- Ranked by relevance

#### Module Management

**importModule**
```java
@JsonRequest
CompletableFuture<ImportModuleResponse> importModule(ImportModuleRequest request)
```
- Add import statement
- Handles organization prefix

### Core Components

#### Diagnostics Debouncer

**DiagnosticsDebouncer** (`diagnostics/DiagnosticsDebouncer.java`)

**Purpose**: Debounces diagnostic requests to avoid overwhelming the client

**Pattern**: Debouncing with configurable delay

**Workflow**:
1. Receives diagnostic request
2. Cancels pending requests for same document
3. Schedules new request after delay
4. Sends diagnostics to client

#### File System Utilities

**FileSystemUtils** (`utils/FileSystemUtils.java`)

**Purpose**: File system operations for flow model generator

**Features**:
- Create flow model files
- Save generated code
- Manage temporary files
- Clean up artifacts

### Request/Response Models

All requests and responses are in `request/` and `response/` packages.

**Common Pattern**:
```java
// Request
public class MyRequest {
    private String filePath;
    private JsonObject data;
    // getters/setters
}

// Response
public class MyResponse {
    private boolean success;
    private JsonObject result;
    private List<Diagnostic> diagnostics;
    // getters/setters
}
```

## Extension Points / APIs

### LSP Service SPI

**Registration**:
```java
@JavaSPIService("org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService")
@JsonSegment("flowDesignService")
public class FlowModelGeneratorService implements ExtendedLanguageServerService
```

**Service File**: `META-INF/services/org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService`

### Client Integration

**From VS Code Extension**:
```typescript
// Get flow model
const flowModel = await client.sendRequest('flowDesignService/getFlowModel', {
    filePath: documentUri,
    functionName: 'main'
});

// Render visual flow
renderFlowDiagram(flowModel.flowNodes);

// Get available nodes for insertion
const availableNodes = await client.sendRequest(
    'flowDesignService/getAvailableNodes',
    { filePath: documentUri, branch: 'MAIN' }
);

// Show node palette
showNodePalette(availableNodes.categories);

// Generate code from flow
const sourceEdits = await client.sendRequest('flowDesignService/generateSource', {
    filePath: documentUri,
    flowModel: flowJson
});

// Apply edits
await workspace.applyEdit({ changes: { [documentUri]: sourceEdits } });
```

**Data Mapping UI**:
```typescript
// Get data mapping model
const mapping = await client.sendRequest('flowDesignService/getDataMappingModel', {
    filePath: documentUri,
    linePosition: { line: 10, character: 4 }
});

// Render visual mapper
renderDataMapper(mapping.sourceType, mapping.targetType, mapping.mappings);

// Add field mapping
await client.sendRequest('flowDesignService/addDataMapping', {
    filePath: documentUri,
    sourcePath: 'user.name',
    targetPath: 'customer.fullName'
});
```

**Expression Editor**:
```typescript
// Get completion suggestions
const completions = await client.sendRequest(
    'flowDesignService/getExpressionEditorCompletion',
    {
        filePath: documentUri,
        expression: 'user.',
        position: { line: 0, character: 5 }
    }
);

// Show completion list
showCompletions(completions.items);

// Get diagnostics
const diagnostics = await client.sendRequest(
    'flowDesignService/getExpressionEditorDiagnostics',
    {
        filePath: documentUri,
        expression: 'user.invalidField'
    }
);

// Show errors
showDiagnostics(diagnostics.diagnostics);
```

## Dependencies

### Module Dependencies
- **flow-model-generator-core**: Core generation logic
- **langserver-commons**: LSP extension interfaces
- **ballerina-tools-api**: Project and workspace API
- **org.eclipse.lsp4j**: LSP protocol types

### External Libraries
- **gson**: JSON serialization

## Common Patterns

### 1. LSP Extension Pattern
- Single service with many endpoints
- `@JsonSegment` for namespace
- `@JsonRequest` for each operation

### 2. Async Response Pattern
- All endpoints return `CompletableFuture<Response>`
- Non-blocking processing
- Background computation

### 3. Workspace Integration Pattern
- Access via `WorkspaceManagerProxy`
- Load projects for analysis
- Retrieve semantic models

### 4. Error Handling Pattern
- Try-catch around core operations
- Convert exceptions to diagnostics
- Return partial results when possible

### 5. Request-Response Pattern
- Dedicated classes for each operation
- Type-safe parameter passing
- Structured error reporting

### 6. Debouncing Pattern
- Prevent excessive client updates
- Configurable delays
- Cancellation of pending requests

## Development Guidelines

### Adding a New Endpoint

1. **Define Request/Response**
   ```java
   // request/MyRequest.java
   public class MyRequest {
       private String filePath;
       private JsonObject params;
       // getters/setters
   }

   // response/MyResponse.java
   public class MyResponse {
       private JsonObject result;
       private List<Diagnostic> diagnostics;
       // getters/setters
   }
   ```

2. **Add Endpoint to Service**
   ```java
   @JsonRequest
   public CompletableFuture<MyResponse> myOperation(MyRequest request) {
       return CompletableFuture.supplyAsync(() -> {
           MyResponse response = new MyResponse();
           try {
               // Load project
               Path filePath = Path.of(request.getFilePath());
               Project project = workspaceManagerProxy.get().loadProject(filePath);

               // Delegate to core
               MyGenerator generator = new MyGenerator(project);
               JsonObject result = generator.generate();

               response.setResult(result);
           } catch (Exception e) {
               Diagnostic diag = createDiagnostic(e);
               response.addDiagnostic(diag);
           }
           return response;
       });
   }
   ```

3. **Document Client Usage**
   ```typescript
   // In client documentation
   const result = await client.sendRequest('flowDesignService/myOperation', {
       filePath: documentUri,
       params: { ... }
   });
   ```

### Error Handling Best Practices

```java
try {
    // Core operation
    result = coreOperation();
    response.setResult(result);
} catch (WorkspaceDocumentException e) {
    // Document not found
    response.addDiagnostic(createDiagnostic("Document not found", e));
} catch (EventSyncException e) {
    // Workspace sync error
    response.addDiagnostic(createDiagnostic("Workspace sync failed", e));
} catch (Exception e) {
    // General error
    response.addDiagnostic(createDiagnostic("Operation failed", e));
    logger.error("Unexpected error", e);
}
```

## File Locations

- **Source**: `flow-model-generator/modules/flow-model-generator-ls-extension/src/main/java/`
  - `io/ballerina/flowmodelgenerator/extension/`: Service implementation
  - `io/ballerina/flowmodelgenerator/extension/request/`: Request models
  - `io/ballerina/flowmodelgenerator/extension/response/`: Response models
- **Resources**: `flow-model-generator/modules/flow-model-generator-ls-extension/src/main/resources/`
  - `META-INF/services/`: SPI registration
- **Tests**: `flow-model-generator/modules/flow-model-generator-ls-extension/src/test/java/`
- **Build**: `flow-model-generator/modules/flow-model-generator-ls-extension/build.gradle`

## Important Notes for AI Assistants

1. **Comprehensive Service**: 25+ endpoints in single service for flow design
2. **Visual Programming**: Enables complete low-code/no-code experience
3. **Bidirectional**: Supports code↔model transformations
4. **Data Mapping**: Full visual data transformation support
5. **Expression Editor**: Complete language server within a service
6. **AI Integration**: Deep AI/ML component support
7. **Search-Driven**: Multiple search endpoints for discoverability
8. **Async Processing**: All operations non-blocking
9. **Error Resilient**: Returns partial results with diagnostics
10. **Client-Driven**: Designed for IDE/visual tool integration

## Testing

### Test Coverage

Located in `src/test/java/io/ballerina/flowmodelgenerator/extension/`:
- `ModelGeneratorTest.java`: Flow model generation tests
- `NodeTemplateTest.java`: Template generation tests
- `DataMappingModelTest.java`: Data mapping tests
- `ExpressionEditorCompletionTest.java`: Expression completion tests
- `ExpressionEditorDiagnosticsTest.java`: Expression diagnostics tests
- `ExpressionEditorSemanticTokensTest.java`: Token generation tests
- `SearchNodesTest.java`: Node search tests
- `ConfigVariablesV2Test.java`: Config management tests
- `OpenApiClientGeneratorTest.java`: OpenAPI client gen tests
- 30+ test classes total

### Testing Pattern

```java
@Test
public void testFlowModelGeneration() throws Exception {
    FlowModelGeneratorRequest request = new FlowModelGeneratorRequest();
    request.setFilePath("test.bal");
    request.setFunctionName("main");

    FlowModelGeneratorResponse response = service
        .getFlowModel(request)
        .get();

    assertNotNull(response.getFlowModel());
    assertTrue(response.getDiagnostics().isEmpty());
}
```

## Performance Considerations

- **Database Caching**: Metadata cached in SQLite for fast access
- **Lazy Loading**: Semantic models loaded only when needed
- **Debouncing**: Prevents excessive updates to client
- **Async Processing**: Non-blocking operations
- **Incremental Analysis**: Only analyze changed regions

## Related Modules

- **flow-model-generator-core**: Core generation logic (heavily used)
- **flow-model-index-generator**: Builds search database
- **flow-model-central-client**: Fetches metadata from Central
- **model-generator-commons**: Shared utilities
- **langserver-core**: Language server hosting this extension
- **VS Code Extension**: Primary client consuming this service
