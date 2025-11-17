# edi-service-ls-extension

## Module Overview

**Purpose**: Language Server Protocol (LSP) extension for generating Ballerina types from Electronic Data Interchange (EDI) schemas. Provides JSON-RPC API for EDI schema to Ballerina type conversion, enabling EDI message processing in Ballerina applications.

**Module Name**: `io.ballerina.edi.extension`

**Type**: LSP Extension (ExtendedLanguageServerService)

## Key Responsibilities

- **EDI to Ballerina**: Convert EDI schemas to Ballerina record types
- **Type Generation**: Generate type-safe Ballerina types from EDI segments
- **Schema Parsing**: Parse and validate EDI schema files
- **Code Generation**: Generate Ballerina source code for EDI messages
- **Segment Mapping**: Map EDI segments and elements to Ballerina fields

## Architecture

### Entry Points - LSP Service

**EDIConverterService** (`EDIConverterService.java`)

**JSON-RPC Segment**: Configured via @JsonSegment annotation

### Core JSON-RPC Endpoint

**convertEDIToBallerina** (likely endpoint name)
```java
@JsonRequest
CompletableFuture<EDIConverterResponse> convertEDIToBallerina(EDIConverterRequest request)
```

**Request Parameters**:
- `ediSchemaPath`: Path to EDI schema file
- `messageType`: EDI message type (e.g., X12, EDIFACT)
- `options`: Generation options

**Response**:
- `ballerinaTypes`: Generated Ballerina record types
- `diagnostics`: Conversion diagnostics

### Core Components

#### EDI Type Generator

**EDITypeGenerator** (`EDITypeGenerator.java`)

**Purpose**: Generates Ballerina types from EDI schemas

**Workflow**:
1. Parse EDI schema file
2. Extract message structure
3. Map EDI segments to Ballerina records
4. Map EDI elements to Ballerina fields
5. Handle nested structures
6. Generate record definitions
7. Return Ballerina source code

**Supported EDI Standards**:
- X12
- EDIFACT
- Custom EDI formats

#### Request/Response Models

**EDIConverterRequest** (`EDIConverterRequest.java`)
```java
public class EDIConverterRequest {
    private String ediSchemaPath;
    private String messageType;
    private Map<String, Object> options;
    // getters/setters
}
```

**EDIConverterResponse** (`EDIConverterResponse.java`)
```java
public class EDIConverterResponse {
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
// Convert EDI schema to Ballerina
const response = await client.sendRequest(
    'ediConverter/convert',
    {
        ediSchemaPath: schemaFile.fsPath,
        messageType: 'X12'
    }
);

// Insert generated types
editor.insertSnippet(new vscode.SnippetString(response.ballerinaTypes));
```

## Dependencies

### Module Dependencies
- **langserver-commons**: LSP extension interfaces
- **ballerina-tools-api**: Type system API
- EDI parsing library

### External Libraries
- **org.eclipse.lsp4j**: LSP protocol types
- EDI schema processing libraries

## File Locations

- **Source**: `edi-service/modules/edi-service-ls-extension/src/main/java/`
  - `io/ballerina/edi/extension/`: Service implementation
- **Build**: `edi-service/modules/edi-service-ls-extension/build.gradle`

## Important Notes for AI Assistants

1. **EDI Conversion**: Converts EDI schemas to Ballerina types
2. **Message Types**: Supports X12, EDIFACT, and custom formats
3. **Type Safety**: Generates type-safe Ballerina records
4. **Segment Mapping**: Maps EDI segments to structured types
5. **B2B Integration**: Enables enterprise B2B data exchange

## Related Modules

- **langserver-core**: Language server hosting this extension
- **VS Code Extension**: Client for EDI schema import
