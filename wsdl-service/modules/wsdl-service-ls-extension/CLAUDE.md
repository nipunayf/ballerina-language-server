# wsdl-service-ls-extension

## Module Overview

**Purpose**: Language Server Protocol (LSP) extension for generating Ballerina types and clients from Web Services Description Language (WSDL) files. Provides JSON-RPC API for WSDL to Ballerina conversion, enabling SOAP web service integration in Ballerina projects.

**Module Name**: `io.ballerina.wsdl.extension`

**Type**: LSP Extension (ExtendedLanguageServerService)

## Key Responsibilities

- **WSDL to Ballerina**: Convert WSDL definitions to Ballerina client code
- **Type Generation**: Generate Ballerina types from WSDL schema types
- **Client Generation**: Generate SOAP client implementations
- **WSDL Parsing**: Parse and validate WSDL files
- **Code Generation**: Generate compilable Ballerina source code

## Architecture

### Entry Points - LSP Service

**WSDLConverterService** (`WSDLConverterService.java`)

**JSON-RPC Segment**: Configured via @JsonSegment annotation

### Core JSON-RPC Endpoint

**convertWSDLToBallerina** (likely endpoint name)
```java
@JsonRequest
CompletableFuture<WSDLConverterResponse> convertWSDLToBallerina(WSDLConverterRequest request)
```

**Request Parameters**:
- `wsdlFilePath`: Path to WSDL file
- `options`: Generation options (service name, port, etc.)

**Response**:
- `ballerinaCode`: Generated Ballerina client and types
- `diagnostics`: Conversion diagnostics

### Core Components

#### WSDL Type Generator

**WSDLTypeGenerator** (`WSDLTypeGenerator.java`)

**Purpose**: Generates Ballerina types and clients from WSDL

**Workflow**:
1. Parse WSDL file
2. Extract service definitions, bindings, messages
3. Map WSDL types to Ballerina types
4. Generate client class
5. Generate request/response records
6. Handle SOAP operations
7. Return Ballerina source code

**Type Mapping**:
- WSDL complex types → Ballerina records
- WSDL simple types → Ballerina primitives
- SOAP operations → Client methods

#### Request/Response Models

**WSDLConverterRequest** (`WSDLConverterRequest.java`)
```java
public class WSDLConverterRequest {
    private String wsdlFilePath;
    private String serviceName;
    private String portName;
    private Map<String, Object> options;
    // getters/setters
}
```

**WSDLConverterResponse** (`WSDLConverterResponse.java`)
```java
public class WSDLConverterResponse {
    private String ballerinaCode;
    private List<Diagnostic> diagnostics;
    // getters/setters
}
```

## Extension Points / APIs

### LSP Service SPI

**Registration**: Via META-INF/services/ExtendedLanguageServerService

### Client Integration

**From VS Code Extension**:
```typescript
// Convert WSDL to Ballerina
const response = await client.sendRequest(
    'wsdlConverter/convert',
    {
        wsdlFilePath: wsdlFile.fsPath,
        serviceName: 'MyService'
    }
);

// Create new Ballerina file with generated code
const uri = vscode.Uri.file(path.join(workspace.rootPath, 'soap_client.bal'));
await workspace.fs.writeFile(uri, Buffer.from(response.ballerinaCode));
```

## Dependencies

### Module Dependencies
- **langserver-commons**: LSP extension interfaces
- **ballerina-tools-api**: Type system API
- WSDL parsing library

### External Libraries
- **org.eclipse.lsp4j**: LSP protocol types
- SOAP/XML processing libraries

## File Locations

- **Source**: `wsdl-service/modules/wsdl-service-ls-extension/src/main/java/`
  - `io/ballerina/wsdl/extension/`: Service implementation
- **Build**: `wsdl-service/modules/wsdl-service-ls-extension/build.gradle`

## Important Notes for AI Assistants

1. **WSDL Conversion**: Converts WSDL to Ballerina SOAP clients
2. **Client Generation**: Generates complete client implementations
3. **Type Mapping**: Maps WSDL/XSD types to Ballerina types
4. **SOAP Support**: Enables SOAP web service integration
5. **Service Selection**: Can select specific service/port from WSDL

## Related Modules

- **langserver-core**: Language server hosting this extension
- **VS Code Extension**: Client for WSDL import functionality
