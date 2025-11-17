# bal-shell-service

## Module Overview

**Purpose**: Language server extension that provides interactive Ballerina Shell (REPL) functionality through the LSP protocol. This service enables notebook-style execution of Ballerina code snippets with persistent state, allowing IDEs to build interactive coding experiences like Jupyter notebooks for Ballerina.

**Module Name**: `io.ballerina.shell.service`

**Type**: LSP Extension Service

**Size**: 14 Java source files

## Key Responsibilities

- **Interactive Code Execution**: Execute Ballerina code snippets in a REPL environment
- **State Management**: Maintain execution context across multiple snippet evaluations
- **Variable Tracking**: Track and manage declared variables and their values
- **Declaration Management**: Handle module-level declarations (functions, types, etc.)
- **Console Output Capture**: Capture stdout/stderr from snippet execution
- **Shell File Export**: Export shell state to temporary Ballerina files
- **Session Reset**: Restart shell to clean state
- **Notebook Integration**: Support for Ballerina Notebook features in IDEs

## Architecture

### Entry Points

**BalShellService** (`BalShellService.java:97 lines`)
- LSP extension service implementation
- Implements `ExtendedLanguageServerService` SPI
- JSON-RPC segment: `balShell`
- Registers via ServiceLoader: `@JavaSPIService("org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService")`

**JSON-RPC Methods**:
```java
@JsonRequest
CompletableFuture<BalShellGetResultResponse> getResult(BalShellGetResultRequest)

@JsonRequest
CompletableFuture<ShellFileSourceResponse> getShellFileSource()

@JsonRequest
CompletableFuture<List<Map<String, String>>> getVariableValues()

@JsonRequest
CompletableFuture<Boolean> deleteDeclarations(DeleteRequest)

@JsonRequest
CompletableFuture<Boolean> restartNotebook()
```

### Core Components

#### 1. Shell Wrapper

**ShellWrapper** (`ShellWrapper.java:200+ lines`)
- Singleton wrapper around Ballerina Shell evaluator
- Manages shell lifecycle and state
- Coordinates snippet execution
- Handles console output redirection

**Key Responsibilities**:
- Initialize and maintain `Evaluator` instance
- Execute source snippets
- Capture console output
- Track available variables
- Generate shell file snapshots
- Handle shell restart

**Singleton Pattern**:
```java
public static ShellWrapper getInstance() {
    return InstanceHolder.instance;
}
```

#### 2. Evaluator Integration

**Ballerina Shell Evaluator**:
- Core REPL engine (from ballerina-shell module)
- Compiles and executes snippets
- Maintains execution context
- Tracks declarations and variables

**Execution Flow**:
1. Receive source snippet
2. Parse and compile snippet
3. Execute in shell context
4. Capture output and result
5. Update shell state
6. Return result to client

#### 3. Request/Response Models

**BalShellGetResultRequest** (`BalShellGetResultRequest.java`)
- Request to execute code snippet
- Fields:
  - `source`: Source code to execute

**BalShellGetResultResponse** (`BalShellGetResultResponse.java`)
- Response from snippet execution
- Fields:
  - `value`: Execution result (as object)
  - `output`: Console output (stdout/stderr)
  - `diagnostics`: Compilation/execution diagnostics
  - `metaInfo`: Metadata about execution

**ShellFileSourceResponse** (`ShellFileSourceResponse.java`)
- Response containing shell state as file
- Fields:
  - `uri`: Temporary file URI
  - `content`: File content

**DeleteRequest** (`DeleteRequest.java`)
- Request to delete declarations
- Fields:
  - `varToDelete`: Name of variable/declaration to remove

**MetaInfo** (`MetaInfo.java`)
- Execution metadata
- Contains timing, memory info, etc.

#### 4. Console Output Capture

**ConsoleOutCollector** (`ConsoleOutCollector.java`)
- Custom OutputStream that collects output
- Captures both stdout and stderr
- Provides collected output as string

**Output Redirection**:
```java
PrintStream originalOut = System.out;
ConsoleOutCollector collector = new ConsoleOutCollector();
System.setOut(new PrintStream(collector));
// Execute code
String output = collector.getOutput();
System.setOut(originalOut);
```

#### 5. Type Utilities

**TypeUtils** (`util/TypeUtils.java`)
- Type conversion utilities
- JSON serialization of Ballerina values
- Handles complex types (records, arrays, maps)

**Constants** (`util/Constants.java`)
- Service-wide constants
- Capability names
- Configuration keys

#### 6. Capability Management

**BalShellServiceClientCapabilities** (`BalShellServiceClientCapabilities.java`)
- Client capability flags

**BalShellServiceClientCapabilitySetter** (`BalShellServiceClientCapabilitySetter.java`)
- Registers client capabilities
- Implements `BallerinaClientCapabilitySetter` SPI

**BalShellServiceServerCapabilitySetter** (`BalShellServiceServerCapabilitySetter.java`)
- Registers server capabilities
- Implements `BallerinaServerCapabilitySetter` SPI

**Constants** (`Constants.java`)
- Capability name: `"balShell"`

## Extension Points / SPIs

### 1. ExtendedLanguageServerService SPI

**Implementation**: BalShellService

**Registration**: META-INF/services/org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService

**Annotation**: `@JsonSegment("balShell")`

**Methods**: See JSON-RPC Methods above

### 2. Capability Registration

**Client Capability Setter**:
- Registers balShell client capabilities
- Enables feature detection

**Server Capability Setter**:
- Advertises balShell support
- Notifies clients of availability

## Dependencies

### Module Dependencies
- **ballerina-shell**: Core shell/REPL implementation
- **langserver-commons**: LSP service interfaces
- **ballerina-projects**: Project API

### External Libraries
- **jackson-databind**: JSON serialization
- **ballerina-shell**: REPL engine

## Common Patterns

### 1. Singleton Pattern
- ShellWrapper is singleton
- One shell instance per language server

### 2. Service Provider Interface
- Implements LSP extension SPI
- Loaded via ServiceLoader

### 3. Async/CompletableFuture
- All methods return CompletableFuture
- Non-blocking operation
- Async execution in background

### 4. Output Redirection
- Temporarily redirects System.out/err
- Captures console output
- Restores original streams

### 5. State Management
- Persistent shell state across requests
- Variables and declarations retained
- Restart to clear state

## Development Guidelines

### Using Bal Shell Service from IDE

**Execute Snippet**:
```typescript
// Request from IDE (TypeScript/JavaScript)
const request = {
  source: 'int x = 10 + 5;'
};

const response = await client.sendRequest(
  'balShell/getResult',
  request
);

console.log(response.value);     // 15
console.log(response.output);    // Console output
console.log(response.diagnostics); // Errors/warnings
```

**Get Variables**:
```typescript
const variables = await client.sendRequest(
  'balShell/getVariableValues'
);

// Returns: [
//   { name: "x", type: "int", value: "15" },
//   ...
// ]
```

**Delete Declaration**:
```typescript
await client.sendRequest(
  'balShell/deleteDeclarations',
  { varToDelete: "x" }
);
```

**Restart Notebook**:
```typescript
await client.sendRequest(
  'balShell/restartNotebook'
);
// Shell reset to initial state
```

**Get Shell File**:
```typescript
const fileResponse = await client.sendRequest(
  'balShell/getShellFileSource'
);

console.log(fileResponse.uri);     // file:///tmp/temp-xxx.bal
console.log(fileResponse.content); // Full shell state as Bal file
```

### Execution Examples

**Sequential Execution**:
```ballerina
// Snippet 1
int x = 10;
// Response: value = 10

// Snippet 2
int y = x + 5;
// Response: value = 15 (x is available)

// Snippet 3
io:println(y);
// Response: value = (), output = "15\n"
```

**Function Declaration**:
```ballerina
// Snippet 1: Define function
function greet(string name) returns string {
    return "Hello, " + name;
}
// Response: success

// Snippet 2: Call function
string greeting = greet("Alice");
// Response: value = "Hello, Alice"
```

**Error Handling**:
```ballerina
// Snippet with error
int x = "not a number";
// Response: diagnostics contains compilation error
// Shell state unchanged
```

## Usage Examples

### Example 1: Interactive Variable Exploration

```ballerina
// Step 1: Create record
type Person record {
    string name;
    int age;
};

// Step 2: Create instance
Person alice = {name: "Alice", age: 30};

// Step 3: Access field
string name = alice.name;
// Output: "Alice"

// Step 4: Get all variables
// getVariableValues() returns:
// [
//   {name: "alice", type: "Person", value: "{name: \"Alice\", age: 30}"},
//   {name: "name", type: "string", value: "\"Alice\""}
// ]
```

### Example 2: Module Declaration Management

```ballerina
// Declare function
function add(int a, int b) returns int {
    return a + b;
}

// Use function
int result = add(5, 3);
// result = 8

// Delete function
// deleteDeclarations({varToDelete: "add"})

// Try to use deleted function
int x = add(1, 2);
// Error: undefined function 'add'
```

### Example 3: Notebook-Style Development

```ballerina
// Import module
import ballerina/io;

// HTTP client example
http:Client githubClient = check new("https://api.github.com");

// Make request
json response = check githubClient->get("/users/ballerina-platform");

// Extract data
string login = check response.login;
io:println(login);
// Output: "ballerina-platform"

// Variable tracking shows:
// - githubClient (http:Client)
// - response (json)
// - login (string)
```

## File Locations

- **Source**: `misc/ls-extensions/modules/bal-shell-service/src/main/java/io/ballerina/shell/service/`
  - `BalShellService.java`: Main service
  - `ShellWrapper.java`: Shell wrapper
  - `BalShellGetResult*.java`: Request/response models
  - `ConsoleOutCollector.java`: Output capture
  - `util/`: Utilities
- **Build**: `misc/ls-extensions/modules/bal-shell-service/build.gradle`
- **SPI Registration**: `src/main/resources/META-INF/services/`

## Important Notes for AI Assistants

1. **Stateful Service**: Shell maintains state across requests (unlike stateless LSP methods)
2. **Singleton Shell**: One shell instance per language server process
3. **Output Capture**: Redirects System.out/err to capture console output
4. **Notebook Support**: Designed for Jupyter-style notebook integration
5. **Variable Persistence**: Variables and declarations persist until deleted or reset
6. **Compilation Context**: Uses ballerina-shell's compilation context
7. **Error Isolation**: Compilation errors don't crash shell, just return diagnostics
8. **Async Execution**: All methods async via CompletableFuture
9. **Temp File Generation**: Can export shell state to temporary .bal files
10. **No LSP Document Sync**: Independent of LSP document lifecycle

## Related Modules

- **ballerina-shell**: Core shell/REPL implementation (external dependency)
- **langserver-core**: Loads this extension service

## RPC Methods

| Method | Purpose | Request | Response |
|--------|---------|---------|----------|
| `balShell/getResult` | Execute snippet | `{source: string}` | `{value, output, diagnostics}` |
| `balShell/getShellFileSource` | Export shell state | none | `{uri, content}` |
| `balShell/getVariableValues` | List variables | none | `[{name, type, value}]` |
| `balShell/deleteDeclarations` | Delete declaration | `{varToDelete: string}` | `boolean` |
| `balShell/restartNotebook` | Reset shell | none | `boolean` |

## Performance Considerations

- **Shell Initialization**: First request slower due to shell setup
- **State Size**: Large number of variables increases memory usage
- **Compilation Overhead**: Each snippet compiled separately
- **Output Buffering**: Console output buffered in memory
- **Temp Files**: Temp files not automatically cleaned up
