# json-to-record-converter

## Module Overview

**Purpose**: Core library for converting JSON strings/objects to Ballerina record type definitions. This module analyzes JSON structure and generates idiomatic Ballerina record types with proper field types, including support for nested records, arrays, unions, and optional fields.

**Module Name**: `io.ballerina.jsonmapper`

**Type**: Utility library (no executable components, no LSP extension)

**Size**: Primary API in `JsonToRecordMapper.java` (1500+ lines)

## Key Responsibilities

- **JSON to Record Conversion**: Parse JSON and generate Ballerina record type definitions
- **Type Inference**: Infer appropriate Ballerina types from JSON values
- **Nested Structure Handling**: Support deeply nested JSON objects and arrays
- **Union Type Generation**: Create union types for heterogeneous JSON arrays
- **Optional Field Detection**: Mark fields as optional when appropriate
- **Closed/Open Record Support**: Generate closed or open record types
- **Name Sanitization**: Handle invalid Ballerina identifiers in JSON keys
- **Multiple Sample Merging**: Merge multiple JSON samples to create comprehensive types

## Architecture

### Entry Points

**JsonToRecordMapper** (`JsonToRecordMapper.java`)
- Main API class for JSON to record conversion
- **Primary Method**: `convert(String, String, boolean, boolean, boolean, String, WorkspaceManager, boolean)`
- Parameters:
  - `jsonString`: Input JSON string
  - `recordName`: Name for generated record type
  - `isRecordTypeDesc`: Generate as type descriptor (vs type definition)
  - `isClosed`: Generate closed record type
  - `forceFormatRecordFields`: Apply forced formatting
  - `textFieldName`: Field name for text content in XML conversions
  - `workspaceManager`: Workspace manager for context
  - `useSingleQuotes`: Use single quotes for string literals
- Returns: `JsonToRecordResponse` with generated code or diagnostics

**JsonToRecordResponse** (`JsonToRecordResponse.java`)
- Response wrapper containing:
  - `codeBlock`: Generated Ballerina record code
  - `diagnostics`: List of diagnostic messages

### Core Components

#### 1. JSON Parsing and Analysis

**JSON Processing Flow**:
1. Parse JSON string using Gson `JsonParser`
2. Validate JSON syntax
3. Analyze JSON structure recursively
4. Identify field types and patterns
5. Generate type definitions bottom-up

**Supported JSON Types**:
- **Objects**: Mapped to Ballerina records
- **Arrays**: Mapped to Ballerina arrays or tuples
- **Primitives**: string, int, float, boolean, decimal
- **Null**: Represented as optional types
- **Mixed Arrays**: Generate union types

#### 2. Type Inference Engine

**Type Detection**:
- **Strings**: Always mapped to `string`
- **Numbers**:
  - Integers → `int`
  - Decimals → `decimal` or `float`
- **Booleans**: Mapped to `boolean`
- **Null values**: Make field optional
- **Objects**: Generate nested record types
- **Arrays**: Infer element types and generate arrays

**Union Type Creation**:
- Heterogeneous arrays → union of element types
- Null values → union with nil (`T?`)
- Mixed types → explicit union (`int|string`)

#### 3. Record Generation

**Record Type Construction**:
- Field analysis and type inference
- Nested record generation for objects
- Array type generation
- Optional field handling
- Import statement generation
- Formatting and code beautification

**Key Features**:
- **Closed vs Open Records**: Control with `isClosed` parameter
- **Readonly Detection**: Identify readonly fields
- **Rest Field Support**: Handle additional properties
- **Field Name Escaping**: Escape invalid identifiers
- **Type Deduplication**: Reuse equivalent types

#### 4. Diagnostic System

**DiagnosticMessage** (`diagnostic/DiagnosticMessage.java`)
- Enumeration of diagnostic codes
- Factory methods for common errors:
  - `invalidJSON()`: Invalid JSON syntax
  - `parserException()`: Parsing failures
  - `conversionException()`: Conversion errors

**DiagnosticUtils** (`diagnostic/DiagnosticUtils.java`)
- Diagnostic creation and formatting utilities
- Severity levels: ERROR, WARNING, INFO
- Position tracking for errors

#### 5. Converter Utilities

**ConverterUtils** (`util/ConverterUtils.java`)
- Helper methods for conversion:
  - `escapeIdentifier()`: Escape invalid Ballerina identifiers
  - `getPrimitiveTypeName()`: Get Ballerina type name from JSON value
  - `extractTypeDescriptorNodes()`: Extract type nodes from syntax tree
  - `sortTypeDescriptorNodes()`: Sort union type members
  - `getAndUpdateFieldNames()`: Handle field naming conflicts
  - `extractArrayTypeDescNode()`: Extract array element types
  - `extractUnionTypeDescNode()`: Create union types

**ListOperationUtils** (`util/ListOperationUtils.java`)
- Set operations on lists:
  - `intersection()`: Find common elements
  - `difference()`: Find differing elements
  - Used for merging multiple JSON samples

## Key Algorithms

### 1. JSON Structure Analysis

```
Algorithm: analyzeJSON(JsonElement)
  If JsonObject:
    For each field:
      Infer field type recursively
      Check if field appears in all samples
      Mark as optional if not always present
    Generate record type definition

  If JsonArray:
    Analyze all elements
    If homogeneous: array type
    If heterogeneous: union array type
    Generate array type descriptor

  If JsonPrimitive:
    Return primitive Ballerina type
```

### 2. Type Merging

When multiple JSON samples provided:
```
Algorithm: mergeTypes(List<JsonElement>)
  Parse all samples
  Extract field lists from each
  Find common fields (intersection)
  Find optional fields (difference)
  Merge field types (union if different)
  Generate unified record type
```

### 3. Name Generation

```
Algorithm: generateRecordName(JsonElement, context)
  If array element: parentName + "Item"
  If nested object: parentName + fieldName
  If root: use provided recordName
  Ensure uniqueness with counter suffix
```

## Extension Points / APIs

This is a utility library with direct API calls (no SPI):

### Main API

```java
import io.ballerina.jsonmapper.JsonToRecordMapper;
import io.ballerina.jsonmapper.JsonToRecordResponse;

// Convert JSON to record
String json = "{\"name\": \"John\", \"age\": 30}";
JsonToRecordResponse response = JsonToRecordMapper.convert(
    json,              // JSON string
    "Person",          // Record name
    false,             // isRecordTypeDesc
    false,             // isClosed
    true,              // forceFormatRecordFields
    "@value",          // textFieldName
    workspaceManager,  // workspace context
    false              // useSingleQuotes
);

if (!response.getDiagnostics().isEmpty()) {
    // Handle errors
} else {
    String ballerinaCode = response.getCodeBlock();
    // Use generated code
}
```

### Deprecated APIs

Several deprecated overloads exist for backward compatibility:
- `convert(String, String, boolean, boolean, boolean)` - Use main method instead
- `convert(String, String, boolean, boolean, boolean, String)` - Use main method instead

## Dependencies

### Module Dependencies
- **ballerina-parser**: Syntax tree manipulation
- **formatter-core**: Code formatting
- **langserver-commons**: Workspace manager interface

### External Libraries
- **gson**: JSON parsing and manipulation
- **commons-lang3**: String utilities

## Common Patterns

### 1. Builder Pattern
- Uses NodeFactory for syntax tree construction
- Incremental building of record definitions

### 2. Visitor Pattern (Implicit)
- Recursive traversal of JSON structure
- Type-specific handling for each JSON type

### 3. Template Method
- Common conversion flow
- Type-specific hooks for different JSON types

### 4. Error Handling
- Returns diagnostics instead of throwing exceptions
- Graceful degradation on parse errors
- Detailed error messages with context

### 5. Formatter Integration
- Always formats generated code
- Uses Ballerina formatter for idiomatic output
- Configurable formatting options

## Development Guidelines

### Converting JSON to Records

**Basic Usage**:
```java
String json = """
{
  "name": "Alice",
  "age": 25,
  "email": "alice@example.com"
}
""";

JsonToRecordResponse response = JsonToRecordMapper.convert(
    json, "User", false, false, true, "@value", null, false
);

System.out.println(response.getCodeBlock());
// Output:
// type User record {
//     string name;
//     int age;
//     string email;
// };
```

**Nested Records**:
```java
String json = """
{
  "person": {
    "name": "Bob",
    "age": 30
  },
  "company": "TechCorp"
}
""";

JsonToRecordResponse response = JsonToRecordMapper.convert(
    json, "Employee", false, false, true, "@value", null, false
);

// Generates:
// type Person record {
//     string name;
//     int age;
// };
//
// type Employee record {
//     Person person;
//     string company;
// };
```

**Arrays and Union Types**:
```java
String json = """
{
  "tags": ["java", "ballerina", "LSP"],
  "scores": [95, 87.5, 92]
}
""";

// Generates array types and numeric unions
```

### Handling Invalid JSON

```java
String invalidJson = "{name: 'John'}";  // Missing quotes
JsonToRecordResponse response = JsonToRecordMapper.convert(
    invalidJson, "Person", false, false, true, "@value", null, false
);

if (!response.getDiagnostics().isEmpty()) {
    for (var diagnostic : response.getDiagnostics()) {
        System.err.println(diagnostic.getMessage());
    }
}
```

### Closed vs Open Records

```java
// Open record (allows additional fields)
JsonToRecordResponse open = JsonToRecordMapper.convert(
    json, "OpenRec", false, false, true, "@value", null, false
);
// type OpenRec record {
//     string name;
// };

// Closed record (no additional fields)
JsonToRecordResponse closed = JsonToRecordMapper.convert(
    json, "ClosedRec", false, true, true, "@value", null, false
);
// type ClosedRec record {|
//     string name;
// |};
```

## Usage Examples

### Example 1: REST API Response to Record

```java
String apiResponse = """
{
  "id": 123,
  "username": "johndoe",
  "profile": {
    "firstName": "John",
    "lastName": "Doe",
    "bio": "Software Engineer"
  },
  "posts": [
    {"id": 1, "title": "Hello World"},
    {"id": 2, "title": "Ballerina Rocks"}
  ]
}
""";

JsonToRecordResponse response = JsonToRecordMapper.convert(
    apiResponse, "UserData", false, false, true, "@value", null, false
);

// Generates nested Profile and Post records
```

### Example 2: Configuration File to Record

```java
String config = """
{
  "server": {
    "host": "localhost",
    "port": 8080
  },
  "database": {
    "url": "jdbc:mysql://localhost:3306/mydb",
    "username": "admin"
  }
}
""";

JsonToRecordResponse response = JsonToRecordMapper.convert(
    config, "AppConfig", false, true, true, "@value", null, false
);
```

## File Locations

- **Source**: `misc/json-to-record-converter/src/main/java/io/ballerina/jsonmapper/`
  - `JsonToRecordMapper.java`: Main API class
  - `JsonToRecordResponse.java`: Response model
  - `diagnostic/`: Diagnostic messages
  - `util/`: Utility classes
- **Build**: `misc/json-to-record-converter/build.gradle`

## Important Notes for AI Assistants

1. **Pure Utility Library**: This is NOT an LSP extension, just a reusable library
2. **JSON Schema Not Supported**: Works with JSON instances, not JSON Schema
3. **Type Inference**: Infers types from values, may need manual adjustment for edge cases
4. **Multiple Samples**: Can merge multiple JSON samples to create more accurate types
5. **Name Conflicts**: Automatically handles duplicate names with numeric suffixes
6. **Formatting**: Always formats output using Ballerina formatter
7. **Error Recovery**: Tries to continue on non-fatal errors, collects all diagnostics
8. **Optional Fields**: Fields missing in some samples become optional
9. **Null Handling**: Null values make fields optional (T?)
10. **Array Homogeneity**: Checks if array elements are same type vs mixed

## Related Modules

- **misc/xml-to-record-converter**: Similar converter for XML
- **misc/ls-extensions/modules/json-to-record-converter**: LSP service wrapper for this library
- **langserver-core**: May use this for code generation features

## Diagnostic Codes

- **INVALID_JSON**: Malformed JSON syntax
- **PARSER_EXCEPTION**: JSON parsing error
- **CONVERSION_EXCEPTION**: Conversion logic error
- **INVALID_IDENTIFIER**: Invalid Ballerina identifier in JSON key

## Performance Considerations

- **Large JSON**: Performance degrades with very large/deep JSON structures
- **Caching**: No built-in caching, caller should cache if needed
- **Formatting**: Formatting adds overhead, can be disabled if needed
- **Memory**: Builds entire syntax tree in memory before formatting
