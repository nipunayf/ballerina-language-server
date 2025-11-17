# sequence-model-generator-ls-extension

## Module Overview

**Purpose**: Language Server Protocol (LSP) extension that exposes sequence diagram generation capabilities to IDEs. Provides JSON-RPC API for generating UML-style sequence diagrams from Ballerina code, enabling visual documentation and code comprehension tools.

**Module Name**: `io.ballerina.sequencemodelgenerator.ls.extension`

**Type**: LSP Extension (ExtendedLanguageServerService)

## Key Responsibilities

- **Sequence Diagram API**: Expose sequence diagram generation via LSP
- **Request/Response Handling**: Process client requests and return diagram models
- **Project Integration**: Load projects and semantic models for analysis
- **JSON Serialization**: Convert diagram models to JSON for client consumption
- **Error Handling**: Graceful error handling with diagnostics
- **Workspace Integration**: Access workspace documents and projects

## Architecture

### Entry Points - LSP Service

**SequenceModelGeneratorService** (`SequenceModelGeneratorService.java:92 lines`)

**JSON-RPC Segment**: `@JsonSegment("sequenceModelGeneratorService")`

**Primary Service**: Single endpoint for sequence diagram generation

### Core JSON-RPC Endpoint

**getSequenceDiagramModel**

```java
@JsonRequest
CompletableFuture<SequenceDiagramServiceResponse> getSequenceDiagramModel(
    SequenceDiagramServiceRequest request)
```

**Request Parameters**:
- `filePath`: Absolute path to Ballerina file
- `lineRange`: LineRange of function/participant to analyze

**Response**:
- `sequenceDiagram`: JSON representation of Diagram model
- Contains participants and their interactions

**Workflow**:
1. Extract file path from request
2. Load or retrieve project from workspace
3. Get semantic model for file
4. Delegate to core ModelGenerator
5. Serialize Diagram to JSON
6. Return response

### Core Components

#### Request/Response Models

**SequenceDiagramServiceRequest** (`SequenceDiagramServiceRequest.java`)

```java
public class SequenceDiagramServiceRequest {
    private String filePath;
    private LineRange lineRange;
    // getters/setters
}
```

**SequenceDiagramServiceResponse** (`SequenceDiagramServiceResponse.java`)

```java
public class SequenceDiagramServiceResponse {
    private JsonElement sequenceDiagram;
    // getters/setters
}
```

#### Diagnostics

**ModelDiagnostic** (`ModelDiagnostic.java`)

**Purpose**: Represents errors/warnings during model generation

**Fields**:
- `message`: Error message
- `severity`: Error, warning, info
- `range`: Location in source

#### Capability Management

**SequenceDiagramClientCapabilities** (`SequenceDiagramClientCapabilities.java`)

**Purpose**: Client-side capability declarations

**SequenceDiagramServerCapabilities** (`SequenceDiagramServerCapabilities.java`)

**Purpose**: Server-side capability declarations

**SequenceDiagramClientCapabilitySetter** (`SequenceDiagramClientCapabilitySetter.java`)

**Purpose**: Sets client capabilities during initialization

**SequenceDiagramServerCapabilitySetter** (`SequenceDiagramServerCapabilitySetter.java`)

**Purpose**: Sets server capabilities during initialization

**Pattern**: LSP capability negotiation protocol

#### Constants

**SequenceDiagramConstants** (`SequenceDiagramConstants.java`)

**Purpose**: Shared constants for service

## Extension Points / APIs

### LSP Service SPI

**Registration**:

```java
@JavaSPIService("org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService")
@JsonSegment("sequenceModelGeneratorService")
public class SequenceModelGeneratorService implements ExtendedLanguageServerService
```

**Service File**: `META-INF/services/org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService`

**Content**: `io.ballerina.sequencemodelgenerator.ls.extension.SequenceModelGeneratorService`

### Client Integration

**From VS Code Extension**:

```typescript
// Request sequence diagram
const response = await client.sendRequest(
    'sequenceModelGeneratorService/getSequenceDiagramModel',
    {
        filePath: document.uri.fsPath,
        lineRange: {
            fileName: document.fileName,
            startLine: { line: 10, offset: 0 },
            endLine: { line: 20, offset: 1 }
        }
    }
);

// Access diagram data
const diagram = response.sequenceDiagram;
const participants = diagram.participants;

// Render sequence diagram
renderSequenceDiagram(participants);
```

**Typical UI Flow**:
1. User selects a function in editor
2. Clicks "Show Sequence Diagram" action
3. Extension sends LSP request with function location
4. Server returns diagram model
5. Extension renders UML sequence diagram

## Dependencies

### Module Dependencies
- **sequence-model-generator-core**: Core diagram generation logic
- **langserver-commons**: LSP extension interfaces and workspace access
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
- Background computation

### 3. Workspace Integration Pattern
- Access via WorkspaceManager
- Load projects on demand
- Cache loaded projects

### 4. Error Handling Pattern
- Try-catch around core operations
- Convert exceptions to RuntimeException
- Client handles error responses

### 5. JSON Serialization Pattern
- Use Gson for model serialization
- JsonElement for flexible response structure
- Automatic serialization of record types

## Development Guidelines

### Adding Additional Endpoints

To add more sequence diagram related operations:

1. **Define Request/Response**:
   ```java
   public class NewSequenceRequest {
       private String filePath;
       private String operation;
       // getters/setters
   }

   public class NewSequenceResponse {
       private JsonElement result;
       // getters/setters
   }
   ```

2. **Add Endpoint**:
   ```java
   @JsonRequest
   public CompletableFuture<NewSequenceResponse> newOperation(NewSequenceRequest request) {
       return CompletableFuture.supplyAsync(() -> {
           NewSequenceResponse response = new NewSequenceResponse();
           try {
               Path filePath = Path.of(request.getFilePath());
               Project project = getCurrentProject(filePath);
               // Perform operation
               response.setResult(result);
           } catch (Exception e) {
               throw new RuntimeException(e);
           }
           return response;
       });
   }
   ```

3. **Update Client**:
   ```typescript
   const result = await client.sendRequest(
       'sequenceModelGeneratorService/newOperation',
       { filePath: uri, operation: 'op' }
   );
   ```

### Error Handling Best Practices

```java
try {
    Project project = getCurrentProject(filePath);
    SemanticModel semanticModel = workspaceManager
        .semanticModel(filePath)
        .orElseThrow(() -> new RuntimeException("Semantic model not found"));

    Diagram diagram = ModelGenerator.getSequenceDiagramModel(
        project, request.getLineRange(), semanticModel
    );

    response.setSequenceDiagram(gson.toJsonTree(diagram));

} catch (WorkspaceDocumentException e) {
    throw new RuntimeException("Document not found: " + filePath, e);
} catch (EventSyncException e) {
    throw new RuntimeException("Workspace sync failed", e);
} catch (Exception e) {
    throw new RuntimeException("Diagram generation failed", e);
}
```

## Usage Examples

### Server-Side Implementation

```java
@JsonRequest
public CompletableFuture<SequenceDiagramServiceResponse> getSequenceDiagramModel(
        SequenceDiagramServiceRequest request) {
    return CompletableFuture.supplyAsync(() -> {
        SequenceDiagramServiceResponse response = new SequenceDiagramServiceResponse();
        Path filePath = Path.of(request.getFilePath());

        try {
            // Get or load project
            Project project = getCurrentProject(filePath);

            // Get semantic model
            SemanticModel semanticModel = this.workspaceManager
                .semanticModel(filePath)
                .orElseThrow();

            // Generate diagram
            Diagram sequenceModel = ModelGenerator.getSequenceDiagramModel(
                project,
                request.getLineRange(),
                semanticModel
            );

            // Serialize to JSON
            Gson gson = new GsonBuilder().create();
            JsonElement sequenceModelJson = gson.toJsonTree(sequenceModel);
            response.setSequenceDiagram(sequenceModelJson);

        } catch (WorkspaceDocumentException | EventSyncException e) {
            throw new RuntimeException(e);
        }

        return response;
    });
}
```

### Client-Side Usage

```typescript
import * as vscode from 'vscode';
import { LanguageClient } from 'vscode-languageclient/node';

// Generate sequence diagram command
vscode.commands.registerCommand('ballerina.showSequenceDiagram', async () => {
    const editor = vscode.window.activeTextEditor;
    if (!editor) return;

    const document = editor.document;
    const selection = editor.selection;

    // Prepare request
    const request = {
        filePath: document.uri.fsPath,
        lineRange: {
            fileName: document.fileName,
            startLine: {
                line: selection.start.line,
                offset: selection.start.character
            },
            endLine: {
                line: selection.end.line,
                offset: selection.end.character
            }
        }
    };

    // Send request
    const response = await languageClient.sendRequest(
        'sequenceModelGeneratorService/getSequenceDiagramModel',
        request
    );

    // Render diagram
    const panel = vscode.window.createWebviewPanel(
        'sequenceDiagram',
        'Sequence Diagram',
        vscode.ViewColumn.Two,
        {}
    );

    panel.webview.html = renderSequenceDiagram(response.sequenceDiagram);
});

function renderSequenceDiagram(diagram: any): string {
    // Generate HTML/SVG for sequence diagram
    let html = '<svg>...';

    diagram.participants.forEach(participant => {
        // Draw participant
        participant.nodes.forEach(node => {
            // Draw interactions
        });
    });

    return html + '</svg>';
}
```

## File Locations

- **Source**: `sequence-model-generator/modules/sequence-model-generator-ls-extension/src/main/java/`
  - `io/ballerina/sequencemodelgenerator/ls/extension/`: Service implementation
- **Resources**: `sequence-model-generator/modules/sequence-model-generator-ls-extension/src/main/resources/`
  - `META-INF/services/`: SPI registration
- **Tests**: `sequence-model-generator/modules/sequence-model-generator-ls-extension/src/test/java/`
- **Build**: `sequence-model-generator/modules/sequence-model-generator-ls-extension/build.gradle`

## Important Notes for AI Assistants

1. **Single Endpoint**: Only one main endpoint for diagram generation
2. **Workspace Integration**: Uses WorkspaceManager for project access
3. **Async Processing**: All operations non-blocking via CompletableFuture
4. **JSON Serialization**: Uses Gson for automatic model serialization
5. **No Direct Rendering**: Returns data model, client renders visuals
6. **Project Caching**: WorkspaceManager caches loaded projects
7. **Semantic Model Required**: Needs semantic analysis for accurate diagrams
8. **LineRange Input**: Client must provide exact function location
9. **Error Propagation**: Exceptions wrapped in RuntimeException
10. **SPI Registration**: Auto-discovered via Java SPI mechanism

## Performance Considerations

- **Async Execution**: Non-blocking CompletableFuture
- **Project Caching**: Workspace manager caches projects
- **On-Demand Loading**: Projects loaded only when needed
- **Lightweight Models**: Diagram models are simple data structures

## Testing

### Test Coverage

Located in `src/test/java/`:
- Service initialization tests
- Request/response serialization tests
- Error handling tests
- Integration tests with core generator

### Testing Pattern

```java
@Test
public void testSequenceDiagramGeneration() throws Exception {
    SequenceDiagramServiceRequest request = new SequenceDiagramServiceRequest();
    request.setFilePath("test.bal");
    request.setLineRange(lineRange);

    SequenceDiagramServiceResponse response = service
        .getSequenceDiagramModel(request)
        .get();

    assertNotNull(response.getSequenceDiagram());
    JsonObject diagram = response.getSequenceDiagram().getAsJsonObject();
    assertTrue(diagram.has("participants"));
}
```

## Related Modules

- **sequence-model-generator-core**: Core generation logic (heavily used)
- **langserver-core**: Language server hosting this extension
- **langserver-commons**: Common LSP utilities
- **VS Code Extension**: Primary client consuming this service
