# debug-adapter-core

## Module Overview

**Purpose**: Core implementation of the Debug Adapter Protocol (DAP) for Ballerina. This module provides a complete debugging server that enables IDE debugging features for Ballerina programs, including breakpoints, step debugging, variable inspection, expression evaluation, and more.

**Module Name**: `org.ballerinalang.debugadapter`

**Type**: Debug Server Implementation (DAP protocol)

**Size**: 100+ Java source files across multiple packages

## Key Responsibilities

- **DAP Protocol Implementation**: Full Debug Adapter Protocol server for Ballerina
- **Breakpoint Management**: Set, validate, and manage source breakpoints
- **Step Debugging**: Step over, step in, step out, continue, pause operations
- **Variable Inspection**: Examine local variables, globals, and complex data structures
- **Expression Evaluation**: Evaluate Ballerina expressions in debug context
- **Call Stack Navigation**: Browse call stack frames and switch contexts
- **Thread Management**: Handle multiple execution threads and strands
- **JDI Integration**: Interface with Java Debug Interface for JVM-level debugging
- **Debug Completion**: Code completion in debug REPL/watch expressions

## Architecture

### Entry Points

**JBallerinaDebugServer** (`JBallerinaDebugServer.java:1500+ lines`)
- Main debug server implementation
- Implements `org.eclipse.lsp4j.debug.services.IDebugProtocolServer`
- Extends `BallerinaExtendedDebugServer` for Ballerina-specific features
- Lifecycle methods: `initialize()`, `launch()`, `attach()`, `disconnect()`, `terminate()`

**Key Methods**:
- `setBreakpoints()`: Set source breakpoints
- `continue_()`: Continue execution
- `next()`, `stepIn()`, `stepOut()`: Step debugging
- `pause()`: Pause execution
- `threads()`: Get active threads
- `stackTrace()`: Get call stack
- `scopes()`: Get variable scopes
- `variables()`: Get variable values
- `evaluate()`: Evaluate expressions
- `completions()`: Get code completions

**BallerinaExtendedDebugServer** (`BallerinaExtendedDebugServer.java`)
- Extended interface for Ballerina-specific debug features
- Adds custom capabilities beyond standard DAP

### Core Components

#### 1. Execution Management

**DebugExecutionManager** (`DebugExecutionManager.java`)
- Manages debug session lifecycle
- Coordinates program execution state
- Controls pause/resume operations

**ExecutionContext** (`ExecutionContext.java`)
- Holds debug session context
- Project information, source paths
- Client configuration (launch/attach)
- Virtual machine reference

**SuspendedContext** (`SuspendedContext.java`)
- Represents paused execution state
- Current thread reference
- Stack frame information
- Breakpoint hit details

#### 2. Breakpoint Processing

**BreakpointProcessor** (`BreakpointProcessor.java:700+ lines`)
- Manages all breakpoint operations
- Converts DAP breakpoints to JDI breakpoints
- Validates breakpoint locations
- Handles dynamic breakpoints (logpoints, conditional)

**BalBreakpoint** (`breakpoint/BalBreakpoint.java`)
- Ballerina breakpoint representation
- Source file, line number
- Condition, hit condition, log message
- Verified status

**Breakpoint Types**:
- Source breakpoints (line-based)
- Conditional breakpoints
- Logpoints (non-breaking breakpoints)
- Exception breakpoints

#### 3. Variable Inspection

**Variable System** (`variable/`)

**VariableFactory** (`variable/VariableFactory.java`)
- Creates BVariable instances from JDI values
- Factory pattern for different variable types

**BVariable Hierarchy**:
- `BVariable`: Base interface
- `BSimpleVariable`: Primitives (int, string, boolean, etc.)
- `BCompoundVariable`: Complex types (records, objects, arrays, maps)
  - `NamedCompoundVariable`: Records, objects with named fields
  - `IndexedCompoundVariable`: Arrays, tuples with indexed elements

**VariableUtils** (`variable/VariableUtils.java`)
- Variable manipulation utilities
- Type conversion helpers
- Variable tree construction

#### 4. Expression Evaluation

**DebugExpressionEvaluator** (`evaluation/DebugExpressionEvaluator.java`)
- Evaluates Ballerina expressions in debug context
- Parses expression syntax
- Resolves variables and symbols
- Invokes methods and functions
- Returns evaluation results

**EvaluationContext** (`EvaluationContext.java`)
- Context for expression evaluation
- Current frame, thread
- Visible symbols and variables
- Evaluation mode (watch, REPL, hover)

**Expression Types**:
- Variable references
- Field access (record.field)
- Function calls
- Method calls
- Remote actions
- Arithmetic expressions
- Logical expressions

#### 5. Completion Support

**CompletionGenerator** (`completion/CompletionGenerator.java`)
- Generates code completions for debug expressions
- Works with REPL and watch expressions
- Uses semantic model for context-aware suggestions

**Completion Contexts** (`completion/context/`)
- Different contexts for completions:
  - `RemoteMethodCallActionNodeContext`
  - `AsyncSendActionNodeContext`
  - Generic expression contexts

**Completion Utilities**:
- `CompletionUtil`: Completion helpers
- `CommonUtil`: Shared utilities
- `SymbolUtil`: Symbol filtering
- `QNameReferenceUtil`: Qualified name resolution

#### 6. Configuration

**ClientConfigHolder** (`config/ClientConfigHolder.java`)
- Base configuration holder
- Common debug session settings

**ClientLaunchConfigHolder** (`config/ClientLaunchConfigHolder.java`)
- Launch mode configuration
- Script path, program arguments
- Working directory, environment variables

**ClientAttachConfigHolder** (`config/ClientAttachConfigHolder.java`)
- Attach mode configuration
- Debug port, host
- Attach to running process

**ClientConfigurationException** (`config/ClientConfigurationException.java`)
- Configuration validation errors

#### 7. JDI Integration

**JDI Proxies** (`jdi/`)
- Wrapper classes for JDI types:
  - `VirtualMachineProxyImpl`: JVM proxy
  - `ThreadReferenceProxyImpl`: Thread proxy
  - `StackFrameProxyImpl`: Stack frame proxy
  - `LocalVariableProxyImpl`: Local variable proxy

**JDIUtils** (`jdi/JDIUtils.java`)
- JDI utility methods
- Type conversions
- JDI request helpers

**JDIEventProcessor** (`JDIEventProcessor.java`)
- Processes JDI events
- Breakpoint events
- Step events
- Thread events
- VM events

#### 8. Program Runners

**Runner Classes** (`runner/`)

**BProgramRunner** (`runner/BProgramRunner.java`)
- Base runner interface
- Common launch logic

**BPackageRunner** (`runner/BPackageRunner.java`)
- Runs Ballerina packages in debug mode
- Builds and launches package projects

**BSingleFileRunner** (`runner/BSingleFileRunner.java`)
- Runs single Ballerina files
- Quick script debugging

#### 9. Stack Frame Management

**BallerinaStackFrame** (`BallerinaStackFrame.java`)
- Represents a Ballerina stack frame
- Maps JDI stack frame to DAP stack frame
- Source location information
- Frame ID management

#### 10. Debug Output

**DebugOutputLogger** (`DebugOutputLogger.java`)
- Logging to debug console
- stdout/stderr capture
- Output event generation

#### 11. Project Cache

**DebugProjectCache** (`DebugProjectCache.java`)
- Caches Ballerina project information
- Source file mappings
- Module metadata

## Extension Points

This module doesn't provide SPIs but implements DAP protocol:

### Debug Adapter Protocol

**Standard DAP Methods**:
- Initialize, launch, attach, disconnect, terminate
- Breakpoint operations
- Step operations (continue, next, stepIn, stepOut, pause)
- Stack trace, scopes, variables
- Evaluate, completions
- Thread management

**Custom Extensions**:
- Ballerina-specific capabilities
- Custom evaluation contexts
- Strand-aware debugging (Ballerina concurrency)

## Dependencies

### Module Dependencies
- **debug-adapter-runtime**: Runtime utilities for expression evaluation
- **ballerina-lang**: Language core
- **ballerina-parser**: Syntax tree parsing
- **ballerina-tools-api**: Compiler and project API
- **ballerina-runtime**: Runtime internals

### External Libraries
- **org.eclipse.lsp4j.debug**: Debug Adapter Protocol implementation
- **com.sun.jdi**: Java Debug Interface
- **gson**: JSON serialization

## Common Patterns

### 1. Proxy Pattern
- JDI proxies wrap JDI interfaces
- Adds caching and error handling
- Simplifies JDI API usage

### 2. Factory Pattern
- VariableFactory creates appropriate variable types
- CompletionItemBuilder creates completion items

### 3. Context Pattern
- ExecutionContext, EvaluationContext, SuspendedContext
- Encapsulate operation state

### 4. Visitor Pattern (Implicit)
- Expression evaluation traverses syntax trees
- Variable tree construction

### 5. Event-Driven
- JDI event processing
- DAP event notifications

### 6. State Machine
- Debug session state transitions
- Thread state management

## Development Guidelines

### Launching Debug Session

**From IDE (VS Code)**:
1. Configure launch.json
2. IDE sends `initialize` request
3. Server responds with capabilities
4. IDE sends `launch` or `attach` request
5. Server starts/attaches to Ballerina program
6. Server sends `initialized` event
7. IDE sends `setBreakpoints`, `configurationDone`
8. Debug session active

### Setting Breakpoints

```java
// IDE sends SetBreakpointsArguments
SetBreakpointsArguments args = new SetBreakpointsArguments();
args.setSource(...);
args.setBreakpoints(...);

// Server processes
BreakpointProcessor processor = new BreakpointProcessor(...);
List<BalBreakpoint> balBreakpoints = processor.setBreakpoints(args);

// Convert to JDI breakpoints
// Return verified breakpoints to client
```

### Evaluating Expressions

```java
// In debug context (paused at breakpoint)
EvaluateArguments args = new EvaluateArguments();
args.setExpression("person.name");
args.setFrameId(frameId);
args.setContext("watch");

// Evaluate
DebugExpressionEvaluator evaluator = new DebugExpressionEvaluator(...);
BExpressionValue result = evaluator.evaluate(expression, context);

// Return result to client
EvaluateResponse response = new EvaluateResponse();
response.setResult(result.getString());
response.setType(result.getType());
response.setVariablesReference(result.getReference());
```

### Inspecting Variables

```java
// Get scopes for a frame
ScopesArguments args = new ScopesArguments();
args.setFrameId(frameId);

// Return local, global scopes
Scope[] scopes = {...};

// Get variables in scope
VariablesArguments varArgs = new VariablesArguments();
varArgs.setVariablesReference(scopeReference);

// Convert JDI variables to DAP variables
Variable[] variables = {...};
```

## Usage Examples

### Example 1: Launch Configuration (VS Code)

```json
{
  "type": "ballerina",
  "request": "launch",
  "name": "Debug Ballerina Program",
  "script": "${file}",
  "debugServer": 4711
}
```

### Example 2: Set Conditional Breakpoint

```java
SourceBreakpoint bp = new SourceBreakpoint();
bp.setLine(42);
bp.setCondition("count > 10");

SetBreakpointsArguments args = new SetBreakpointsArguments();
args.setBreakpoints(new SourceBreakpoint[]{bp});
```

### Example 3: Step Debugging Flow

```
User action: Step Over
→ IDE sends NextArguments
→ Server disables other threads
→ Server creates step request in JDI
→ Server resumes thread
→ JDI step event fires
→ Server suspends thread
→ Server sends StoppedEvent to IDE
→ IDE requests stack trace
→ Server returns current frame
→ IDE displays source location
```

## File Locations

- **Source**: `misc/debug-adapter/modules/debug-adapter-core/src/main/java/org/ballerinalang/debugadapter/`
  - `JBallerinaDebugServer.java`: Main server
  - `BreakpointProcessor.java`: Breakpoint management
  - `evaluation/`: Expression evaluation
  - `variable/`: Variable inspection
  - `completion/`: Debug completion
  - `config/`: Configuration
  - `jdi/`: JDI integration
  - `runner/`: Program execution
- **Build**: `misc/debug-adapter/modules/debug-adapter-core/build.gradle`

## Important Notes for AI Assistants

1. **DAP Implementation**: Implements Debug Adapter Protocol, not LSP
2. **JDI Dependency**: Relies heavily on Java Debug Interface for JVM debugging
3. **Two-Layer Architecture**: DAP layer (this module) + JDI layer
4. **Ballerina Strands**: Handles Ballerina concurrency model (strands != threads)
5. **Source Mapping**: Maps Ballerina source to generated Java bytecode
6. **Expression Evaluation**: Complex - requires parsing, symbol resolution, execution
7. **Breakpoint Validation**: Validates against actual executable lines
8. **Variable Representation**: Converts JDI values to Ballerina-friendly format
9. **Async Operations**: Most DAP methods return CompletableFuture
10. **Error Handling**: Graceful degradation - debug session shouldn't crash on errors

## Related Modules

- **debug-adapter-runtime**: Runtime utilities for evaluation
- **debug-adapter-cli**: CLI launcher for debug adapter
- **launcher**: Main launcher that may start debug mode

## Debug Adapter Protocol

**Version**: DAP 1.x

**Communication**: JSON-RPC over stdio or socket

**Request/Response Examples**:
```json
// Initialize request
{
  "command": "initialize",
  "arguments": {
    "clientID": "vscode",
    "adapterID": "ballerina"
  }
}

// Set breakpoints request
{
  "command": "setBreakpoints",
  "arguments": {
    "source": {"path": "/path/to/file.bal"},
    "breakpoints": [{"line": 42}]
  }
}

// Stopped event (breakpoint hit)
{
  "event": "stopped",
  "body": {
    "reason": "breakpoint",
    "threadId": 1
  }
}
```

## Performance Considerations

- **JDI Overhead**: JDI calls can be expensive, cache when possible
- **Variable Expansion**: Lazy-load variable children
- **Expression Evaluation**: Can be slow, implement timeouts
- **Breakpoint Validation**: Cache validated breakpoint locations
- **Step Operations**: Minimize JDI event processing overhead
