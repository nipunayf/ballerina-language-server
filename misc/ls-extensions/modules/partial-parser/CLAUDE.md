# partial-parser

## Module Overview

**Purpose**: Language server extension that provides partial parsing capabilities for Ballerina code snippets. This service allows parsing and formatting incomplete code fragments (expressions, statements) without requiring a complete, valid Ballerina program.

**Module Name**: `io.ballerina.parsers`

**Type**: LSP Extension Service

**Size**: 10 Java source files

## Key Responsibilities

- **Partial Expression Parsing**: Parse individual expressions without full program context
- **Statement Parsing**: Parse block-level statements independently
- **Syntax Tree Generation**: Generate syntax trees for code fragments
- **Code Formatting**: Format partial code snippets
- **Modification Tracking**: Track syntax tree modifications
- **Diagram Generation**: Generate diagrams for partial syntax trees

## Architecture

### Entry Points

**PartialParserService** (`PartialParserService.java`)
- LSP extension service implementation
- Implements `ExtendedLanguageServerService` SPI
- JSON-RPC segment: `partialParser`
- Registers via ServiceLoader: `@JavaSPIService("org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService")`

**JSON-RPC Method**:
```java
@JsonRequest
CompletableFuture<STResponse> getSTForSample(PartialSTRequest)
```

### Core Components

#### 1. Parsing Engine

**NodeParser Integration**:
- Uses Ballerina's `NodeParser` for parsing fragments
- Supports two parsing modes:
  - `BLOCK_LEVEL_STATEMENT`: Parse statements
  - `EXPRESSION`: Parse expressions

**Parsing Flow**:
1. Receive code snippet and kind
2. Wrap snippet in minimal context if needed
3. Parse using NodeParser
4. Extract relevant syntax tree
5. Format if requested
6. Generate diagram JSON if requested
7. Return syntax tree + metadata

#### 2. Request/Response Models

**PartialSTRequest** (`PartialSTRequest.java`)
- Request for partial parsing
- Fields:
  - `source`: Code snippet to parse
  - `kind`: Parsing kind (EXPRESSION or BLOCK_LEVEL_STATEMENT)
  - `needFormatting`: Whether to format result
  - `needDiagram`: Whether to generate diagram

**STResponse** (`STResponse.java`)
- Response with parsed syntax tree
- Fields:
  - `syntaxTree`: Syntax tree as JSON
  - `formattedSource`: Formatted code (if requested)
  - `diagram`: Diagram JSON (if requested)
  - `diagnostics`: Parsing diagnostics

**STModification** (`STModification.java`)
- Tracks syntax tree modifications
- Used for tracking parsing changes

#### 3. Code Wrapping

**Context Wrapping**:
For expressions and statements, the service wraps code in minimal context:

**Expression wrapping**:
```ballerina
function wrapper() {
    _ = <expression>;
}
```

**Statement wrapping**:
```ballerina
function wrapper() {
    <statement>
}
```

This allows parsing fragments as valid Ballerina code.

#### 4. Formatting Integration

Uses Ballerina formatter (`org.ballerinalang.formatter.core.Formatter`) to format parsed code.

#### 5. Diagram Integration

Uses `DiagramUtil` from diagram-util module to generate syntax tree diagrams.

#### 6. Capability Management

**PartialParserClientCapabilities** (`PartialParserClientCapabilities.java`)
- Client capability flags

**PartialParserClientCapabilitySetter** (`PartialParserClientCapabilitySetter.java`)
- Registers client capabilities

**PartialParserServerCapabilities** (`PartialParserServerCapabilities.java`)
- Server capability flags

**PartialParserServerCapabilitySetter** (`PartialParserServerCapabilitySetter.java`)
- Registers server capabilities

**Constants** (`Constants.java`)
- Capability name: `"partialParser"`
- Configuration constants

## Extension Points / SPIs

### 1. ExtendedLanguageServerService SPI

**Implementation**: PartialParserService

**Registration**: META-INF/services/org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService

**Annotation**: `@JsonSegment("partialParser")`

### 2. Capability Registration

Registers partial parser capabilities with LSP client/server

## Dependencies

### Module Dependencies
- **diagram-util**: Syntax tree to diagram conversion
- **ballerina-parser**: NodeParser for parsing
- **formatter-core**: Code formatting
- **langserver-commons**: LSP service interfaces

### External Libraries
- **gson**: JSON processing

## Common Patterns

### 1. Wrapper Pattern
- Wraps incomplete code in valid context
- Allows standard parser to work

### 2. Service Provider Interface
- Implements LSP extension SPI
- Loaded dynamically

### 3. Async Operations
- Returns CompletableFuture
- Non-blocking parsing

### 4. Error Handling
- Returns diagnostics for parse errors
- Graceful degradation

## Development Guidelines

### Using Partial Parser from IDE

**Parse Expression**:
```typescript
const request = {
  source: "10 + 20",
  kind: "EXPRESSION",
  needFormatting: false,
  needDiagram: false
};

const response = await client.sendRequest(
  'partialParser/getSTForSample',
  request
);

console.log(response.syntaxTree); // Syntax tree JSON
```

**Parse Statement with Formatting**:
```typescript
const request = {
  source: "int x=10;",
  kind: "BLOCK_LEVEL_STATEMENT",
  needFormatting: true,
  needDiagram: false
};

const response = await client.sendRequest(
  'partialParser/getSTForSample',
  request
);

console.log(response.formattedSource); // "int x = 10;"
```

**Parse with Diagram**:
```typescript
const request = {
  source: "x + y * z",
  kind: "EXPRESSION",
  needFormatting: false,
  needDiagram: true
};

const response = await client.sendRequest(
  'partialParser/getSTForSample',
  request
);

console.log(response.diagram); // Diagram JSON
```

## Usage Examples

### Example 1: Expression Parsing

Input:
```ballerina
10 + 20 * 30
```

Process:
1. Wrap: `function wrapper() { _ = 10 + 20 * 30; }`
2. Parse full statement
3. Extract expression node
4. Return syntax tree

### Example 2: Statement Parsing

Input:
```ballerina
if (x > 10) {
    io:println("Large");
}
```

Process:
1. Wrap: `function wrapper() { if (x > 10) { ... } }`
2. Parse function
3. Extract if statement node
4. Return syntax tree

### Example 3: With Formatting

Input (unformatted):
```ballerina
int x=10+20;
```

Output (formatted):
```ballerina
int x = 10 + 20;
```

## File Locations

- **Source**: `misc/ls-extensions/modules/partial-parser/src/main/java/io/ballerina/parsers/`
  - `PartialParserService.java`: Main service
  - `PartialSTRequest.java`: Request model
  - `STResponse.java`: Response model
  - `STModification.java`: Modification tracking
  - `PartialParser*.java`: Capability management
  - `Constants.java`: Constants
- **Build**: `misc/ls-extensions/modules/partial-parser/build.gradle`
- **SPI Registration**: `src/main/resources/META-INF/services/`

## Important Notes for AI Assistants

1. **Partial Code Support**: Designed for incomplete code fragments
2. **Two Modes**: Expression vs statement parsing
3. **Wrapping Strategy**: Wraps code in minimal valid context
4. **Formatter Integration**: Can format parsed code
5. **Diagram Support**: Can generate syntax tree diagrams
6. **Error Tolerance**: Handles parse errors gracefully
7. **Stateless**: Each parse is independent
8. **IDE Use Case**: For code playgrounds, REPLs, snippet editors
9. **No Semantic Model**: Syntax-only parsing, no type checking
10. **Fast Parsing**: Optimized for quick feedback

## Related Modules

- **diagram-util**: Used for diagram generation
- **formatter-core**: Used for code formatting
- **langserver-core**: Loads this extension service

## RPC Methods

| Method | Purpose | Request | Response |
|--------|---------|---------|----------|
| `partialParser/getSTForSample` | Parse snippet | `{source, kind, needFormatting, needDiagram}` | `{syntaxTree, formattedSource?, diagram?}` |

## Parsing Kinds

```typescript
enum Kind {
  BLOCK_LEVEL_STATEMENT,  // Statements (if, while, assignments, etc.)
  EXPRESSION              // Expressions (10 + 20, function calls, etc.)
}
```

## Performance Considerations

- **Lightweight Parsing**: Faster than full program parsing
- **No Compilation**: Syntax-only, no semantic analysis
- **Formatting Overhead**: Formatting adds latency
- **Diagram Generation**: Can be expensive for complex trees
- **No Caching**: Each request parsed independently
