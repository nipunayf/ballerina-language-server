# json-to-record-converter (LSP Extension)

## Module Overview

**Purpose**: Language server extension that wraps the json-to-record-converter utility library, providing JSON-to-Ballerina-record conversion as an LSP service. This allows IDEs to offer "Paste JSON as Record" functionality and JSON Schema to record conversion.

**Module Name**: `io.ballerina.converters`

**Type**: LSP Extension Service (wrapper around utility library)

**Size**: 10 Java source files

**Note**: This module wraps `/misc/json-to-record-converter` and exposes it via LSP.

## Key Responsibilities

- **LSP Integration**: Expose JSON-to-record conversion via JSON-RPC
- **JSON String Conversion**: Convert JSON instances to records
- **JSON Schema Conversion**: Convert JSON Schema to Ballerina records
- **Workspace Integration**: Use workspace manager for context
- **Capability Management**: Advertise JSON conversion support
- **Error Handling**: Translate conversion errors to LSP diagnostics

## Architecture

### Entry Points

**JsonToRecordConverterService** (`JsonToRecordConverterService.java`)
- LSP extension service implementation
- Implements `ExtendedLanguageServerService` SPI
- JSON-RPC segment: `jsonToRecord`
- Registers via ServiceLoader: `@JavaSPIService("org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService")`

**JSON-RPC Method**:
```java
@JsonRequest
CompletableFuture<JsonToRecordResponse> convert(JsonToRecordRequest)
```

### Core Components

#### 1. Service Layer

**JsonToRecordConverterService**:
- Wraps `JsonToRecordMapper` from utility library
- Adds workspace context
- Handles LSP-specific concerns
- Returns CompletableFuture for async operation

#### 2. Request/Response Models

**JsonToRecordRequest** (inferred from service)
- Request to convert JSON
- Fields:
  - `jsonString`: JSON string or schema
  - `recordName`: Desired record name
  - `isRecordTypeDesc`: Generate type descriptor vs definition
  - `isClosed`: Generate closed record
  - `forceFormatRecordFields`: Apply forced formatting
  - `filePathUri`: Context file URI
  - `isNullAsOptional`: Treat null as optional fields

**JsonToRecordResponse** (from utility library)
- Response with generated code
- Fields:
  - `codeBlock`: Generated Ballerina code
  - `diagnostics`: Conversion diagnostics

#### 3. Schema Conversion

**JsonToRecordConverter** (utility wrapper)
- Handles JSON Schema conversion
- Delegates to schema-specific converter
- Supports various JSON Schema versions

**SchemaGenerator** (`util/SchemaGenerator.java`)
- JSON Schema processing
- Schema validation
- Schema to record mapping

#### 4. Utility Classes

**ConverterUtils** (`util/ConverterUtils.java`)
- Conversion helper methods
- Type mapping utilities
- Name sanitization

**ErrorMessages** (`util/ErrorMessages.java`)
- Error message templates
- Diagnostic message generation

**Constants** (`util/Constants.java`)
- Service-wide constants
- Capability names
- Configuration keys

#### 5. Exception Handling

**JsonToRecordConverterException** (`exception/JsonToRecordConverterException.java`)
- Custom exception for conversion errors
- Wraps underlying errors
- Provides context for LSP diagnostics

#### 6. Capability Management

**JsonToRecordConverterClientCapabilities** (`JsonToRecordConverterClientCapabilities.java`)
- Client capability flags

**JsonToRecordConverterClientCapabilitySetter** (inferred)
- Registers client capabilities

**JsonToRecordConverterServerCapabilities** (`JsonToRecordConverterServerCapabilities.java`)
- Server capability flags

**JsonToRecordConverterServerCapabilitySetter** (`JsonToRecordConverterServerCapabilitySetter.java`)
- Registers server capabilities

## Extension Points / SPIs

### 1. ExtendedLanguageServerService SPI

**Implementation**: JsonToRecordConverterService

**Registration**: META-INF/services/org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService

**Annotation**: `@JsonSegment("jsonToRecord")`

### 2. Capability Registration

Registers JSON to record conversion capabilities

## Dependencies

### Module Dependencies
- **misc/json-to-record-converter**: Core conversion library
- **langserver-commons**: LSP service interfaces
- **formatter-core**: Code formatting

### External Libraries
- **gson**: JSON processing
- **commons-lang3**: Utilities

## Common Patterns

### 1. Wrapper Pattern
- Wraps utility library for LSP
- Adds LSP-specific functionality
- Delegates core work to library

### 2. Service Provider Interface
- Implements LSP extension SPI
- Loaded dynamically

### 3. Async Operations
- Returns CompletableFuture
- Non-blocking conversion

### 4. Error Translation
- Catches library exceptions
- Converts to LSP diagnostics

## Development Guidelines

### Using JSON to Record Service from IDE

**Convert JSON String**:
```typescript
const request = {
  jsonString: '{"name": "Alice", "age": 30}',
  recordName: "Person",
  isRecordTypeDesc: false,
  isClosed: false,
  forceFormatRecordFields: true,
  filePathUri: "file:///path/to/file.bal",
  isNullAsOptional: true
};

const response = await client.sendRequest(
  'jsonToRecord/convert',
  request
);

console.log(response.codeBlock);
// type Person record {
//     string name;
//     int age;
// };
```

**Convert JSON Schema**:
```typescript
const schemaRequest = {
  jsonString: JSON.stringify({
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
      "name": {"type": "string"},
      "age": {"type": "integer"}
    }
  }),
  recordName: "Person",
  isRecordTypeDesc: false,
  isClosed: true,
  forceFormatRecordFields: true,
  filePathUri: "file:///path/to/file.bal",
  isNullAsOptional: true
};

const response = await client.sendRequest(
  'jsonToRecord/convert',
  schemaRequest
);
```

## Usage Examples

### Example 1: Paste JSON as Record (IDE Feature)

User copies JSON:
```json
{
  "id": 123,
  "title": "Sample",
  "tags": ["a", "b", "c"]
}
```

IDE sends request:
```typescript
await client.sendRequest('jsonToRecord/convert', {
  jsonString: copiedJson,
  recordName: "MyRecord",
  // ... other params
});
```

Result inserted:
```ballerina
type MyRecord record {
    int id;
    string title;
    string[] tags;
};
```

### Example 2: Generate from API Response

```typescript
// Fetch API schema
const apiSchema = await fetch('/api/schema');

// Convert to record
const response = await client.sendRequest(
  'jsonToRecord/convert',
  {
    jsonString: JSON.stringify(apiSchema),
    recordName: "ApiResponse",
    // ...
  }
);

// Insert into editor
editor.insertText(response.codeBlock);
```

## File Locations

- **Source**: `misc/ls-extensions/modules/json-to-record-converter/src/main/java/io/ballerina/converters/`
  - `JsonToRecordConverterService.java`: Main service
  - `exception/`: Exception classes
  - `util/`: Utility classes
  - Capability management classes
- **Build**: `misc/ls-extensions/modules/json-to-record-converter/build.gradle`
- **SPI Registration**: `src/main/resources/META-INF/services/`

## Important Notes for AI Assistants

1. **Wrapper Service**: This wraps the standalone json-to-record-converter library
2. **Dual Support**: Handles both JSON instances and JSON Schema
3. **IDE Integration**: Primary use case is "Paste JSON as Record" IDE feature
4. **Workspace Context**: Uses workspace manager for file context
5. **Async Operation**: All conversions async via CompletableFuture
6. **Error Handling**: Translates library errors to LSP diagnostics
7. **Formatting**: Always formats generated code
8. **Schema Detection**: Auto-detects JSON Schema via $schema property
9. **Related to XML Converter**: Similar service exists for XML
10. **Stateless**: Each conversion is independent

## Related Modules

- **misc/json-to-record-converter**: Core conversion library (wrapped by this)
- **misc/xml-to-record-converter**: XML conversion service
- **langserver-core**: Loads this extension service

## RPC Methods

| Method | Purpose | Request | Response |
|--------|---------|---------|----------|
| `jsonToRecord/convert` | Convert JSON/Schema to record | `{jsonString, recordName, ...}` | `{codeBlock, diagnostics}` |

## Comparison with Core Library

| Aspect | Core Library (`misc/json-to-record-converter`) | LSP Service (this module) |
|--------|----------------------------------------------|---------------------------|
| Type | Utility library | LSP extension service |
| Usage | Direct API calls | JSON-RPC via LSP |
| Context | No workspace context | Has workspace manager |
| Async | Synchronous | Asynchronous (CompletableFuture) |
| Integration | Can be used anywhere | IDE integration only |

## Performance Considerations

- **Delegation Overhead**: Minimal, just wraps library call
- **Async Execution**: Prevents blocking LSP server
- **Workspace Access**: May need workspace compilation
- **Formatting**: Adds latency to response
- **Large JSON**: Performance depends on JSON size and complexity
