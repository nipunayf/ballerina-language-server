# openapi-service-ls-extension

## Module Overview

**Purpose**: Language Server Protocol (LSP) extension for converting between Ballerina services and OpenAPI specifications. Provides JSON-RPC API for bidirectional OpenAPI integration: generating OpenAPI specs from Ballerina services and generating Ballerina services from OpenAPI specs.

**Module Name**: `io.ballerina.openapi.extension`

**Type**: LSP Extension (ExtendedLanguageServerService)

## Key Responsibilities

- **Ballerina to OpenAPI**: Generate OpenAPI specifications from Ballerina HTTP services
- **OpenAPI to Ballerina**: Generate Ballerina service code from OpenAPI specifications
- **Spec Validation**: Validate OpenAPI specifications
- **Diagnostic Reporting**: Report mapping errors and warnings
- **Service Analysis**: Analyze Ballerina service declarations for API export

## Architecture

### Entry Points - LSP Service

**OpenAPIConverterService** (`OpenAPIConverterService.java`)

**JSON-RPC Segment**: `@JsonSegment("openAPILSExtension")`

### Core JSON-RPC Endpoints

**generateOpenAPIFile** (Deprecated)
```java
@JsonRequest
@Deprecated
CompletableFuture<OpenAPIConverterResponse> generateOpenAPIFile(
    OpenAPIConverterRequest request)
```
- Legacy endpoint
- Returns OpenAPI YAML string
- Deprecated in favor of generateOpenAPI

**generateOpenAPI**
```java
@JsonRequest
CompletableFuture<OpenAPIConverterResponse> generateOpenAPI(
    OpenAPIConverterRequest request)
```
- Generate OpenAPI specifications from Ballerina services
- Returns list of OASResult objects with diagnostics
- Supports multiple services per file

**Request Parameters**:
- `documentFilePath`: URI to Ballerina file
- `serviceName`: Optional service name filter
- `needJson`: Return JSON format instead of YAML

**Response**:
- `spec`: OpenAPI specification (YAML or JSON)
- `serviceName`: Service name
- `diagnostics`: Mapping diagnostics
- `file`: Output file path

### Core Components

#### OpenAPI Mapper Integration

Uses `io.ballerina.openapi.service.mapper.ServiceToOpenAPIMapper` for conversion

**Workflow**:
1. Parse Ballerina service syntax tree
2. Extract service metadata (base path, resources, etc.)
3. Map Ballerina types to OpenAPI schemas
4. Generate paths from resource methods
5. Create OpenAPI specification object
6. Serialize to YAML or JSON
7. Return with diagnostics

#### Request/Response Models

**OpenAPIConverterRequest** (`OpenAPIConverterRequest.java`)
```java
public class OpenAPIConverterRequest {
    private String documentFilePath;  // File URI
    private String serviceName;       // Optional filter
    private boolean needJson;         // JSON format flag
    // getters/setters
}
```

**OpenAPIConverterResponse** (`OpenAPIConverterResponse.java`)
```java
public class OpenAPIConverterResponse {
    private String spec;              // OpenAPI YAML/JSON
    private String serviceName;
    private List<Diagnostic> diagnostics;
    private String file;              // Output file path
    // getters/setters
}
```

#### Capability Management

**OpenAPIClientCapabilities** (`OpenAPIClientCapabilities.java`)
**OpenAPIServerCapabilities** (`OpenAPIServerCapabilities.java`)
**OpenAPIClientCapabilitySetter** (`OpenAPIClientCapabilitySetter.java`)
**OpenAPIServerCapabilitySetter** (`OpenAPIServerCapabilitySetter.java`)

#### Constants

**OpenAPIServiceConstants** (`OpenAPIServiceConstants.java`)
- Shared constants for OpenAPI operations

## Extension Points / APIs

### LSP Service SPI

**Registration**:
```java
@JavaSPIService("org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService")
@JsonSegment("openAPILSExtension")
public class OpenAPIConverterService implements ExtendedLanguageServerService
```

### Client Integration

**From VS Code Extension**:
```typescript
// Generate OpenAPI from Ballerina service
const response = await client.sendRequest(
    'openAPILSExtension/generateOpenAPI',
    {
        documentFilePath: document.uri.toString(),
        serviceName: 'MyService',
        needJson: false  // Get YAML format
    }
);

// Display OpenAPI spec
const openApiSpec = response.spec;
showOpenAPIPreview(openApiSpec);

// Show diagnostics
if (response.diagnostics && response.diagnostics.length > 0) {
    showDiagnostics(response.diagnostics);
}
```

**Export OpenAPI Command**:
```typescript
vscode.commands.registerCommand('ballerina.exportOpenAPI', async () => {
    const editor = vscode.window.activeTextEditor;
    if (!editor) return;

    const response = await languageClient.sendRequest(
        'openAPILSExtension/generateOpenAPI',
        {
            documentFilePath: editor.document.uri.toString(),
            needJson: false
        }
    );

    // Save to file
    const openApiFile = path.join(workspace.rootPath, 'openapi.yaml');
    fs.writeFileSync(openApiFile, response.spec);
    vscode.window.showInformationMessage(`OpenAPI spec exported to ${openApiFile}`);
});
```

## Dependencies

### Module Dependencies
- **langserver-commons**: LSP extension interfaces
- **ballerina-tools-api**: Project and compilation API
- **openapi-service-mapper**: Ballerina to OpenAPI mapping
- **openapi-to-ballerina**: OpenAPI to Ballerina generation (if used)

### External Libraries
- **org.eclipse.lsp4j**: LSP protocol types
- **gson**: JSON serialization

## Common Patterns

### 1. LSP Extension Pattern
- Implements ExtendedLanguageServerService
- @JsonSegment for namespace
- @JsonRequest for endpoints

### 2. Async Response Pattern
- CompletableFuture for all endpoints
- Non-blocking processing

### 3. Diagnostic Collection Pattern
- Collect mapping diagnostics
- Return with response
- Client displays errors/warnings

## Usage Examples

### Generate OpenAPI from Service

```java
@JsonRequest
public CompletableFuture<OpenAPIConverterResponse> generateOpenAPI(
        OpenAPIConverterRequest request) {
    return CompletableFuture.supplyAsync(() -> {
        OpenAPIConverterResponse response = new OpenAPIConverterResponse();
        try {
            Path filePath = getPathFromURI(request.getDocumentFilePath())
                .orElseThrow();

            Project project = workspaceManager.loadProject(filePath);
            Module module = project.currentPackage().getDefaultModule();
            Document document = module.document(docId);

            // Generate OpenAPI
            ServiceToOpenAPIMapper mapper = new ServiceToOpenAPIMapper();
            OASResult oasResult = mapper.generateOAS3Definition(
                document.syntaxTree(),
                semanticModel,
                serviceName
            );

            // Serialize
            String spec = request.isNeedJson()
                ? oasResult.getJson()
                : oasResult.getYaml();

            response.setSpec(spec);
            response.setServiceName(serviceName);
            response.setDiagnostics(oasResult.getDiagnostics());

        } catch (Exception e) {
            response.addDiagnostic(createDiagnostic(e));
        }
        return response;
    });
}
```

## File Locations

- **Source**: `openapi-service/modules/openapi-service-ls-extension/src/main/java/`
  - `io/ballerina/openapi/extension/`: Service implementation
- **Resources**: `openapi-service/modules/openapi-service-ls-extension/src/main/resources/`
  - `META-INF/services/`: SPI registration
- **Build**: `openapi-service/modules/openapi-service-ls-extension/build.gradle`

## Important Notes for AI Assistants

1. **Bidirectional**: Supports both Ballerina→OpenAPI and OpenAPI→Ballerina
2. **HTTP Services**: Works with Ballerina HTTP services
3. **Multiple Formats**: Supports YAML and JSON output
4. **Diagnostic Reporting**: Returns mapping diagnostics with spec
5. **Service Filtering**: Can generate spec for specific service by name
6. **Deprecated API**: Old generateOpenAPIFile endpoint deprecated
7. **Mapper Library**: Uses separate openapi-service-mapper library
8. **URI Handling**: Accepts file URIs, converts to paths

## Related Modules

- **openapi-service-mapper**: Core Ballerina→OpenAPI mapping logic
- **service-model-generator-ls-extension**: Uses this for OpenAPI service generation
- **langserver-core**: Language server hosting this extension
- **VS Code Extension**: Primary client for OpenAPI export/import
