# graphql-model-generator-ls-extension

## Module Overview

**Purpose**: Language Server Protocol (LSP) extension that exposes GraphQL schema model generation to IDEs. Provides JSON-RPC API for generating visual GraphQL schema models from Ballerina GraphQL service code, enabling schema visualization and documentation tools.

**Module Name**: `io.ballerina.graphqlmodelgenerator.extension`

**Type**: LSP Extension (ExtendedLanguageServerService)

## Key Responsibilities

- **GraphQL Model API**: Expose GraphQL schema model generation via LSP
- **Schema Visualization**: Enable visual GraphQL schema designers
- **Request/Response Handling**: Process client requests and return schema models
- **Project Integration**: Load projects and semantic models for schema analysis
- **JSON Serialization**: Convert schema models to JSON for client consumption
- **Error Handling**: Graceful error handling with diagnostics

## Architecture

### Entry Points - LSP Service

**GraphqlModelGeneratorService** (`GraphqlModelGeneratorService.java`)

**JSON-RPC Segment**: `@JsonSegment("graphqlDesignService")`

**Primary Service**: Single endpoint for GraphQL schema model generation

### Core JSON-RPC Endpoint

**getGraphqlModel**
```java
@JsonRequest
CompletableFuture<GraphqlDesignServiceResponse> getGraphqlModel(
    GraphqlDesignServiceRequest request)
```

**Request Parameters**:
- `filePath`: Absolute path to Ballerina file containing GraphQL service
- `lineRange`: LineRange of service declaration

**Response**:
- `graphqlModel`: JSON representation of GraphQL schema
- Contains types, queries, mutations, subscriptions

**Workflow**:
1. Extract file path from request
2. Load or retrieve project from workspace
3. Get semantic model for file
4. Delegate to core ModelGenerator
5. Serialize GraphqlModel to JSON
6. Return response with schema model

### Core Components

#### Request/Response Models

**GraphqlDesignServiceRequest** (`GraphqlDesignServiceRequest.java`)
```java
public class GraphqlDesignServiceRequest {
    private String filePath;
    private LineRange lineRange;
    // getters/setters
}
```

**GraphqlDesignServiceResponse** (`GraphqlDesignServiceResponse.java`)
```java
public class GraphqlDesignServiceResponse {
    private JsonElement graphqlModel;
    private List<Diagnostic> diagnostics;
    // getters/setters
}
```

#### Capability Management

**GraphqlModelGeneratorClientCapabilities** (`GraphqlModelGeneratorClientCapabilities.java`)
- Client-side capability declarations

**GraphqlModelGeneratorServerCapabilities** (`GraphqlModelGeneratorServerCapabilities.java`)
- Server-side capability declarations

**GraphqlModelGeneratorClientCapabilitySetter** (`GraphqlModelGeneratorClientCapabilitySetter.java`)
- Sets client capabilities during initialization

**GraphqlModelGeneratorServerCapabilitySetter** (`GraphqlModelGeneratorServerCapabilitySetter.java`)
- Sets server capabilities during initialization

#### Constants

**GraphqlModelGeneratorConstants** (`GraphqlModelGeneratorConstants.java`)
- Shared constants for service

## Extension Points / APIs

### LSP Service SPI

**Registration**:
```java
@JavaSPIService("org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService")
@JsonSegment("graphqlDesignService")
public class GraphqlModelGeneratorService implements ExtendedLanguageServerService
```

**Service File**: `META-INF/services/org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService`

### Client Integration

**From VS Code Extension**:
```typescript
// Request GraphQL schema model
const response = await client.sendRequest(
    'graphqlDesignService/getGraphqlModel',
    {
        filePath: document.uri.fsPath,
        lineRange: {
            fileName: document.fileName,
            startLine: { line: 5, offset: 0 },
            endLine: { line: 50, offset: 1 }
        }
    }
);

// Access schema model
const schema = response.graphqlModel;
const service = schema.service;
const components = schema.components;

// Render GraphQL schema diagram
renderGraphQLSchema(service, components);
```

## Dependencies

### Module Dependencies
- **graphql-model-generator-core**: Core schema model generation logic
- **langserver-commons**: LSP extension interfaces
- **ballerina-tools-api**: Project and semantic model API
- **gson**: JSON serialization

### External Libraries
- **org.eclipse.lsp4j**: LSP protocol types

## Common Patterns

### 1. LSP Extension Pattern
- Implements ExtendedLanguageServerService
- Uses @JsonSegment for namespace
- Uses @JsonRequest for endpoints

### 2. Async Response Pattern
- All endpoints return CompletableFuture
- Non-blocking request processing

### 3. Workspace Integration Pattern
- Access via WorkspaceManager
- Load projects on demand

### 4. Error Handling Pattern
- Try-catch around core operations
- Convert exceptions to response diagnostics
- Return partial results when possible

## Usage Examples

### Server-Side Implementation

```java
@JsonRequest
public CompletableFuture<GraphqlDesignServiceResponse> getGraphqlModel(
        GraphqlDesignServiceRequest request) {
    return CompletableFuture.supplyAsync(() -> {
        GraphqlDesignServiceResponse response = new GraphqlDesignServiceResponse();
        try {
            Path filePath = Path.of(request.getFilePath());
            Project project = getCurrentProject(filePath);
            SemanticModel semanticModel = workspaceManager
                .semanticModel(filePath)
                .orElseThrow(() -> new GraphqlModelGenerationException("Semantic model not found"));

            ModelGenerator modelGenerator = new ModelGenerator();
            GraphqlModel generatedModel = modelGenerator.getGraphqlModel(
                project,
                request.getLineRange(),
                semanticModel
            );

            Gson gson = new GsonBuilder().create();
            JsonElement modelJson = gson.toJsonTree(generatedModel);
            response.setGraphqlModel(modelJson);

        } catch (GraphqlModelGenerationException e) {
            response.addDiagnostic(createDiagnostic(e));
        } catch (Exception e) {
            response.addDiagnostic(createDiagnostic("Unexpected error", e));
        }
        return response;
    });
}
```

## File Locations

- **Source**: `graphql-model-generator/modules/graphql-model-generator-ls-extension/src/main/java/`
  - `io/ballerina/graphqlmodelgenerator/extension/`: Service implementation
- **Resources**: `graphql-model-generator/modules/graphql-model-generator-ls-extension/src/main/resources/`
  - `META-INF/services/`: SPI registration
- **Build**: `graphql-model-generator/modules/graphql-model-generator-ls-extension/build.gradle`

## Important Notes for AI Assistants

1. **Single Endpoint**: Only one main endpoint for schema model generation
2. **GraphQL-Specific**: Designed specifically for GraphQL services
3. **Schema Validation**: Relies on Ballerina GraphQL compiler validation
4. **JSON Serialization**: Uses Gson for automatic model serialization
5. **Visual Tools**: Returns data for visual schema designers
6. **Error Propagation**: Exceptions converted to diagnostics
7. **SPI Registration**: Auto-discovered via Java SPI mechanism
8. **Workspace Integration**: Uses WorkspaceManager for project access

## Related Modules

- **graphql-model-generator-core**: Core generation logic (heavily used)
- **langserver-core**: Language server hosting this extension
- **ballerina-graphql**: GraphQL service library
- **VS Code Extension**: Primary client consuming this service
