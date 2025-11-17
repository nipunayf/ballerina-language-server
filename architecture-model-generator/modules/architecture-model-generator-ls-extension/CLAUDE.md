# architecture-model-generator-ls-extension

## Module Overview

**Purpose**: Language Server Protocol (LSP) extension that exposes architecture model generation capabilities to IDEs and clients. Provides JSON-RPC endpoints for generating architecture models, design models, persist ER models, and project migration tools integration.

**Module Name**: `io.ballerina.architecturemodelgenerator.extension`

**Type**: LSP Extension (ExtendedLanguageServerService)

## Key Responsibilities

- **Architecture Model Generation**: Generate solution architecture models on-demand
- **Design Model Generation**: Generate design/workflow models for visualization
- **Persist ER Model Generation**: Generate entity-relationship diagrams for persist models
- **Project Service**: Provide project migration tools (Mule, TIBCO)
- **Artifact Publishing**: Publish generated models as LSP diagnostics/artifacts
- **Capability Negotiation**: Negotiate and advertise extension capabilities to clients

## Architecture

### Entry Points - LSP Services

This module provides **FOUR** separate LSP extension services:

#### 1. ArchitectureModelGeneratorService

**File**: `architecture/ArchitectureModelGeneratorService.java`

**JSON-RPC Segment**: `@JsonSegment("projectDesignService")`

**Endpoints**:
- `getProjectComponentModels(ArchitectureModelRequest)`: Generate architecture models for documents

**Purpose**: Generate complete architecture models including services, entities, and dependencies

#### 2. DesignModelGeneratorService

**File**: `designmodelgenerator/extension/DesignModelGeneratorService.java`

**JSON-RPC Segment**: `@JsonSegment("designService")`

**Endpoints**:
- `getDesignModel(GetDesignModelRequest)`: Generate design/workflow model
- `getArtifacts(ArtifactsRequest)`: Get generated artifacts

**Purpose**: Generate design models for workflow visualization in IDEs

#### 3. PersistERModelGeneratorService

**File**: `persist/PersistERModelGeneratorService.java`

**JSON-RPC Segment**: `@JsonSegment("persistService")`

**Endpoints**:
- `getPersistERModel(PersistERModelRequest)`: Generate ER diagram model

**Purpose**: Generate entity-relationship models from Ballerina persist definitions

#### 4. ProjectService

**File**: `projectservice/extension/ProjectService.java`

**JSON-RPC Segment**: `@JsonSegment("projectService")`

**Endpoints**:
- `importMule(ImportMuleRequest)`: Import Mule project
- `importTibco(ImportTibcoRequest)`: Import TIBCO project
- `getMigrationTools()`: Get available migration tools

**Purpose**: Provide project migration capabilities from other platforms

### Core Components

#### Architecture Model Service

**ArchitectureModelGeneratorService** (`architecture/ArchitectureModelGeneratorService.java:100+ lines`)

**Key Methods**:
```java
@JsonRequest
CompletableFuture<ArchitectureModelResponse> getProjectComponentModels(ArchitectureModelRequest request)
```

**Workflow**:
1. Receives document URIs from client
2. Loads projects from workspace manager
3. Delegates to `ArchitectureModelBuilder` from core module
4. Serializes models to JSON
5. Returns response with component models and diagnostics

**Response Structure**:
- `componentModels`: Map of package ID to architecture model JSON
- `diagnostics`: List of diagnostic messages

#### Design Model Service

**DesignModelGeneratorService** (`designmodelgenerator/extension/DesignModelGeneratorService.java:200+ lines`)

**Key Methods**:
```java
@JsonRequest
CompletableFuture<GetDesignModelResponse> getDesignModel(GetDesignModelRequest request)

@JsonRequest
CompletableFuture<ArtifactResponse> getArtifacts(ArtifactsRequest request)
```

**Workflow**:
1. Receives file path from client
2. Loads project from workspace
3. Generates design model using `DesignModelGenerator`
4. Returns model with services, listeners, connections

**Artifact Publishing**:
- Subscribes to workspace events via `PublishArtifactsSubscriber`
- Publishes artifacts when project is saved/compiled
- Sends artifacts to client for visualization

#### Persist ER Model Service

**PersistERModelGeneratorService** (`persist/PersistERModelGeneratorService.java`)

**Key Methods**:
```java
@JsonRequest
CompletableFuture<PersistERModelResponse> getPersistERModel(PersistERModelRequest request)
```

**Workflow**:
1. Receives file path
2. Analyzes persist entity definitions
3. Generates ER model with entities, attributes, relationships
4. Returns model for ER diagram visualization

#### Project Migration Service

**ProjectService** (`projectservice/extension/ProjectService.java`)

**Key Methods**:
```java
@JsonRequest
CompletableFuture<ImportMuleResponse> importMule(ImportMuleRequest request)

@JsonRequest
CompletableFuture<ImportTibcoResponse> importTibco(ImportTibcoRequest request)

@JsonRequest
CompletableFuture<MigrationToolListResponse> getMigrationTools()
```

**Workflow**:
1. Receives source project path and target path
2. Invokes migration tools from core module
3. Returns migration results with generated Ballerina code

### Event Subscribers

**PublishArtifactsSubscriber** (`designmodelgenerator/extension/PublishArtifactsSubscriber.java`)

**Purpose**: Publishes design model artifacts to client on project changes

**Implementation**: Implements `EventSubscriber` SPI

**Event Type**: `EventKind.PROJECT_UPDATE`, `EventKind.COMPILE_SUCCESS`

**Workflow**:
1. Subscribes to project compilation events
2. Generates design model when project changes
3. Publishes via LSP `publishArtifacts` notification

**Registration**: `META-INF/services/org.ballerinalang.langserver.commons.eventsync.spi.EventSubscriber`

### Capability Management

Each service has capability setter classes:

**Architecture Model**:
- `ArchitectureModelGeneratorClientCapabilities`
- `ArchitectureModelGeneratorClientCapabilitySetter`
- `ArchitectureModelGeneratorServerCapabilities`
- `ArchitectureModelGeneratorServerCapabilitySetter`

**Design Model**:
- `DesignModelGeneratorClientCapabilities`
- `DesignModelGeneratorClientCapabilitiesSetter`
- `DesignModelGeneratorServerCapabilities`
- `DesignModelGeneratorServerCapabilitiesSetter`

**Persist ER Model**:
- `PersistERModelGeneratorClientCapabilities`
- `PersistERModelGeneratorClientCapabilitySetter`
- `PersistERModelGeneratorServerCapabilities`
- `PersistERModelGeneratorServerCapabilitySetter`

**Project Service**:
- `ProjectServiceClientCapabilities`
- `ProjectServiceClientCapabilitiesSetter`
- `ProjectServiceServerCapabilities`
- `ProjectServiceServerCapabilitiesSetter`

**Purpose**: Negotiate capabilities during LSP initialization

## Request/Response Models

### Architecture Model

**ArchitectureModelRequest**:
- `documentUris`: List of document URIs to analyze

**ArchitectureModelResponse**:
- `componentModels`: Map<String, JsonObject> of models
- `diagnostics`: List of diagnostics

### Design Model

**GetDesignModelRequest**:
- `filePath`: File path to analyze

**GetDesignModelResponse**:
- `designModel`: Design model JSON
- `diagnostics`: List of diagnostics

**ArtifactsRequest**:
- `filePath`: File path

**ArtifactResponse**:
- `artifacts`: List of artifact objects
- `diagnostics`: List of diagnostics

### Persist ER Model

**PersistERModelRequest**:
- `filePath`: File path to persist definitions

**PersistERModelResponse**:
- `erModel`: ER model JSON
- `diagnostics`: List of diagnostics

### Project Migration

**ImportMuleRequest**:
- `sourcePath`: Mule project path
- `targetPath`: Output path

**ImportMuleResponse**:
- `success`: Boolean
- `message`: Status message
- `generatedFiles`: List of generated file paths

**ImportTibcoRequest**:
- `sourcePath`: TIBCO project path
- `targetPath`: Output path

**ImportTibcoResponse**:
- Similar to ImportMuleResponse

**MigrationToolListResponse**:
- `tools`: List of available migration tools

## Extension Points / APIs

### LSP Service SPI

All services implement `ExtendedLanguageServerService`:

```java
@JavaSPIService("org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService")
@JsonSegment("serviceName")
public class MyService implements ExtendedLanguageServerService {
    @JsonRequest
    public CompletableFuture<Response> myMethod(Request request) {
        // Implementation
    }
}
```

**Registration**: `META-INF/services/org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService`

### Client Integration

Clients call these services via JSON-RPC:

```javascript
// Architecture model
const response = await client.sendRequest('projectDesignService/getProjectComponentModels', {
    documentUris: ['file:///path/to/file.bal']
});

// Design model
const designModel = await client.sendRequest('designService/getDesignModel', {
    filePath: 'file:///path/to/file.bal'
});

// Persist ER model
const erModel = await client.sendRequest('persistService/getPersistERModel', {
    filePath: 'file:///path/to/persist.bal'
});

// Mule import
const result = await client.sendRequest('projectService/importMule', {
    sourcePath: '/mule/project',
    targetPath: '/ballerina/output'
});
```

## Dependencies

### Module Dependencies
- **architecture-model-generator-core**: Core model generation logic
- **langserver-commons**: LSP extension interfaces
- **ballerina-tools-api**: Project and workspace API
- **org.eclipse.lsp4j**: LSP protocol types

### External Libraries
- **gson**: JSON serialization

## Common Patterns

### 1. LSP Extension Pattern
- Implements `ExtendedLanguageServerService`
- Uses `@JsonSegment` for namespace
- Uses `@JsonRequest` for endpoints

### 2. Async Response Pattern
- All endpoints return `CompletableFuture<Response>`
- Enables non-blocking processing
- Background model generation

### 3. Workspace Integration Pattern
- Accesses workspace via `WorkspaceManager`
- Loads projects for analysis
- Retrieves semantic models

### 4. Error Handling Pattern
- Catches exceptions during generation
- Converts to diagnostic messages
- Returns partial results with diagnostics

### 5. Capability Negotiation Pattern
- Client and server capability classes
- Capability setters for initialization
- Feature detection

### 6. Event-Driven Pattern
- Subscribers listen for workspace events
- Automatic artifact generation
- Push notifications to client

## Development Guidelines

### Creating an LSP Extension

1. **Implement Service Interface**
   ```java
   @JavaSPIService("org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService")
   @JsonSegment("myService")
   public class MyService implements ExtendedLanguageServerService {
       private WorkspaceManager workspaceManager;

       @Override
       public void init(LanguageServer langServer, WorkspaceManager wsManager) {
           this.workspaceManager = wsManager;
       }

       @Override
       public Class<?> getRemoteInterface() {
           return null;
       }
   }
   ```

2. **Add JSON-RPC Endpoint**
   ```java
   @JsonRequest
   public CompletableFuture<MyResponse> myOperation(MyRequest request) {
       return CompletableFuture.supplyAsync(() -> {
           // Implementation
           return new MyResponse();
       });
   }
   ```

3. **Register via SPI**
   - Add to `META-INF/services/org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService`

4. **Define Capabilities**
   - Create client/server capability classes
   - Implement capability setters

### Working with Workspace

```java
Path filePath = Path.of(request.getFilePath());
Project project = workspaceManager.loadProject(filePath);
Optional<SemanticModel> semanticModel = workspaceManager.semanticModel(filePath);
Optional<Document> document = workspaceManager.document(filePath);
```

### Error Handling

```java
try {
    ArchitectureModel model = generator.generate();
    response.setModel(model);
} catch (Exception e) {
    DiagnosticMessage message = DiagnosticMessage.failedToGenerate(e.getMessage());
    response.addDiagnostic(message);
}
```

## Usage Examples

### From VS Code Extension

```typescript
// Get architecture model
const architectureResponse = await languageClient.sendRequest(
    'projectDesignService/getProjectComponentModels',
    { documentUris: [documentUri] }
);

// Render architecture diagram
renderArchitectureDiagram(architectureResponse.componentModels);

// Get design model
const designResponse = await languageClient.sendRequest(
    'designService/getDesignModel',
    { filePath: documentUri }
);

// Render flow diagram
renderFlowDiagram(designResponse.designModel);
```

### From IntelliJ Plugin

```java
// Call via LSP4J
CompletableFuture<ArchitectureModelResponse> future = languageServer
    .getLanguageClient()
    .request("projectDesignService/getProjectComponentModels", request);

ArchitectureModelResponse response = future.get();
// Process response
```

## File Locations

- **Source**: `architecture-model-generator/modules/architecture-model-generator-ls-extension/src/main/java/`
  - `io/ballerina/architecturemodelgenerator/extension/`: Architecture model service
  - `io/ballerina/designmodelgenerator/extension/`: Design model service
  - `io/ballerina/projectservice/extension/`: Project migration service
- **Resources**: `architecture-model-generator/modules/architecture-model-generator-ls-extension/src/main/resources/`
  - `META-INF/services/`: SPI registration files
- **Tests**: `architecture-model-generator/modules/architecture-model-generator-ls-extension/src/test/java/`
- **Build**: `architecture-model-generator/modules/architecture-model-generator-ls-extension/build.gradle`

## Important Notes for AI Assistants

1. **Multiple Services**: This module contains FOUR separate LSP services in one module
2. **JSON-RPC Segmentation**: Each service has its own namespace via `@JsonSegment`
3. **Async Processing**: All endpoints return `CompletableFuture` for non-blocking
4. **Workspace Access**: Services access workspace via injected `WorkspaceManager`
5. **Error Handling**: Errors converted to diagnostics, not thrown
6. **Artifact Publishing**: Design model service publishes artifacts via events
7. **Capability Negotiation**: Each service has separate capability classes
8. **Client Integration**: Called from IDE extensions via JSON-RPC
9. **Project Loading**: Always load project before generating models
10. **Diagnostic Collection**: Collect all diagnostics, return with response

## Testing

### Unit Tests

Located in `src/test/java/`:
- `ArchitectureModelGeneratorServiceTests.java`
- `DesignModelGeneratorTest.java`
- `PersistERModelGeneratorTests.java`
- `GetMigrationToolsTest.java`
- `ImportMuleTest.java`
- `ImportTibcoTest.java`

### Testing Pattern

```java
@Test
public void testArchitectureModelGeneration() {
    ArchitectureModelRequest request = new ArchitectureModelRequest();
    request.setDocumentUris(List.of("file:///test.bal"));

    ArchitectureModelResponse response = service
        .getProjectComponentModels(request)
        .get();

    assertNotNull(response.getComponentModels());
}
```

## Related Modules

- **architecture-model-generator-core**: Core generation logic (used by all services)
- **architecture-model-generator-plugin**: Compiler plugin for build-time generation
- **langserver-core**: Language server hosting these extensions
- **langserver-commons**: Extension framework and interfaces
- **VS Code Extension**: Client consuming these services
