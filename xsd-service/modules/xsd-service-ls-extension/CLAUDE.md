# xsd-service-ls-extension

## Module Overview

**Purpose**: Language Server Protocol (LSP) extension for generating Ballerina record types from XML Schema Definition (XSD) files. Provides JSON-RPC API for XSD to Ballerina type conversion, enabling XML schema integration in Ballerina projects.

**Module Name**: `io.ballerina.xsd.extension`

**Type**: LSP Extension (ExtendedLanguageServerService)

## Key Responsibilities

- **XSD to Ballerina**: Convert XML Schema definitions to Ballerina record types
- **Type Generation**: Generate type-safe Ballerina types from XSD complex types
- **Schema Parsing**: Parse and validate XSD files
- **Code Generation**: Generate Ballerina source code for XSD types

## Architecture

### Entry Points - LSP Service

**XSDConverterService** (`XSDConverterService.java`)

**JSON-RPC Segment**: Configured via @JsonSegment annotation

### Core JSON-RPC Endpoint

**convertXSDToBallerina** (likely endpoint name)
```java
@JsonRequest
CompletableFuture<XSDConverterResponse> convertXSDToBallerina(XSDConverterRequest request)
```

**Request Parameters**:
- `xsdFilePath`: Path to XSD file
- `outputOptions`: Generation options

**Response**:
- `ballerinaTypes`: Generated Ballerina record types
- `diagnostics`: Conversion diagnostics

### Core Components

#### XSD Type Generator

**XSDTypeGenerator** (`XSDTypeGenerator.java`)

**Purpose**: Generates Ballerina types from XSD definitions

**Workflow**:
1. Parse XSD file
2. Extract complex types and elements
3. Map XSD types to Ballerina types
4. Generate record definitions
5. Handle nested types
6. Return Ballerina source code

#### Request/Response Models

**XSDConverterRequest** (`XSDConverterRequest.java`)
```java
public class XSDConverterRequest {
    private String xsdFilePath;
    private Map<String, Object> options;
    // getters/setters
}
```

**XSDConverterResponse** (`XSDConverterResponse.java`)
```java
public class XSDConverterResponse {
    private String ballerinaTypes;
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
// Convert XSD to Ballerina
const response = await client.sendRequest(
    'xsdConverter/convert',
    {
        xsdFilePath: xsdFile.fsPath
    }
);

// Insert generated types into document
editor.insertSnippet(new vscode.SnippetString(response.ballerinaTypes));
```

## Dependencies

### Module Dependencies
- **langserver-commons**: LSP extension interfaces
- **ballerina-tools-api**: Type system API
- XSD parsing library

### External Libraries
- **org.eclipse.lsp4j**: LSP protocol types
- XML processing libraries

## File Locations

- **Source**: `xsd-service/modules/xsd-service-ls-extension/src/main/java/`
  - `io/ballerina/xsd/extension/`: Service implementation
- **Build**: `xsd-service/modules/xsd-service-ls-extension/build.gradle`

## Important Notes for AI Assistants

1. **XSD Conversion**: Converts XML schemas to Ballerina types
2. **Type Mapping**: Maps XSD types to Ballerina record types
3. **Schema Support**: Supports XSD complex types, elements, attributes
4. **Code Generation**: Generates compilable Ballerina code
5. **XML Integration**: Enables XML processing with type safety

## Related Modules

- **langserver-core**: Language server hosting this extension
- **VS Code Extension**: Client for XSD import functionality
