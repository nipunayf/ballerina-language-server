# debug-adapter-runtime

## Module Overview

**Purpose**: Runtime utilities library for Ballerina debugger expression evaluation. This module provides helper methods that are class-loaded into the debuggee JVM to enable runtime operations like function invocations, method calls, remote actions, and XML operations during debug expression evaluation.

**Module Name**: `org.ballerinalang.debugadapter.runtime`

**Type**: Runtime utility library (loaded into debuggee JVM)

**Size**: 3 Java source files

## Key Responsibilities

- **Function Invocation**: Invoke Ballerina functions from debugger expressions
- **Method Execution**: Execute object methods in blocking manner
- **Remote Action Calls**: Support remote call actions during evaluation
- **XML Operations**: Enable XML step expressions (XPath-like operations)
- **Runtime Value Creation**: Create Ballerina runtime values (arrays, maps, errors, XML)
- **Module Initialization**: Initialize modules dynamically for evaluation context
- **Strand Management**: Create evaluation strands for async operations

## Architecture

### Entry Points

**DebuggerRuntime** (`DebuggerRuntime.java:400+ lines`)
- Main runtime utility class
- **Important**: This class is loaded into the debuggee JVM, not the debugger JVM
- Provides static utility methods for expression evaluation
- All methods marked `@SuppressWarnings("unused")` because they're invoked via reflection

**Key Methods**:
- `invokeObjectMethod()`: Invoke Ballerina object methods
- `invokeObjectMethodAsync()`: Invoke async object methods
- `invokeRemoteMethodSync()`: Synchronous remote method calls
- `invokeRemoteMethodAsync()`: Asynchronous remote method calls
- `createBArray()`: Create Ballerina arrays
- `createBMap()`: Create Ballerina maps
- `createBError()`: Create Ballerina errors
- `createBXml()`: Create XML values
- `getXmlItem()`: XPath-like XML navigation
- `initModule()`: Initialize Ballerina modules
- `invokeModuleStart()`: Start module execution

### Core Components

#### 1. Object Method Invocation

**Blocking Method Calls**:
```java
public static Object invokeObjectMethod(BObject bObject,
                                       String methodName,
                                       Object... args)
```
- Invokes object methods synchronously
- Blocks until method completes
- Returns method result
- Used for simple method evaluations

**Async Method Calls**:
```java
public static Object invokeObjectMethodAsync(BObject bObject,
                                            String methodName,
                                            Object... args)
```
- Invokes methods that may yield
- Creates evaluation strand
- Waits for completion
- Returns result when available

#### 2. Remote Action Support

**Synchronous Remote Calls**:
```java
public static Object invokeRemoteMethodSync(BObject bObject,
                                           String methodName,
                                           Object... args)
```
- Invokes remote actions synchronously
- Used for client method calls
- Blocks until remote call completes

**Asynchronous Remote Calls**:
```java
public static Object invokeRemoteMethodAsync(BObject bObject,
                                            String methodName,
                                            Object... args)
```
- Invokes remote actions asynchronously
- Creates evaluation strand
- Handles futures and waits

#### 3. Value Creation

**Array Creation**:
```java
public static Object createBArray(Type type, long size)
```
- Creates Ballerina array instances
- Supports all array types
- Initializes with default values

**Map Creation**:
```java
public static Object createBMap(Type type, BMapInitialValueEntry[] entries)
```
- Creates Ballerina map instances
- Populates with initial entries
- Supports all map types

**Error Creation**:
```java
public static BError createBError(Type type, BString message,
                                 BError cause, BMap<BString, Object> detail)
```
- Creates Ballerina error values
- Supports error types
- Handles message, cause, details

**XML Creation**:
```java
public static BXml createBXml(String xmlValue)
```
- Creates XML values from strings
- Parses XML content
- Returns BXml instance

#### 4. XML Navigation

**XML Step Operations**:
```java
public static BXml getXmlItem(BXml parent, String childName)
```
- Implements XML step expressions
- Supports:
  - Simple steps: `xml.element`
  - Wildcard: `xml.*`
  - Descendants: `xml.**`
  - Filters: `xml.<name1|name2>`
  - Indexed access: `xml[0]`

**XML Operation Types**:
- **All children**: `xml.\*` - Get all child elements
- **Descendants**: `xml.\*\*` - Get all descendants
- **Named step**: `xml.elementName` - Get named children
- **Name patterns**: `xml.<name1|name2>` - Get children matching pattern

#### 5. Module Management

**Module Initialization**:
```java
public static void initModule(Object metaData, String orgName,
                             String moduleName, String version,
                             String className)
```
- Dynamically initializes Ballerina modules
- Loads module classes
- Executes `$moduleInit` method
- Sets up module runtime state

**Module Start**:
```java
public static void invokeModuleStart(String orgName, String moduleName,
                                    String version, String className)
```
- Invokes `$moduleStart` method
- Starts module-level initialization
- Required for some evaluations

#### 6. Strand Management

**Evaluation Strand Creation**:
- Creates dedicated strand for evaluation
- Named "evaluator-strand"
- Isolated from program strands
- Enables async operations during debugging

**Scheduler Integration**:
- Uses BalRuntime scheduler
- Creates evaluation runtime
- Manages strand lifecycle
- Waits for completion

### Variable Utilities

**VariableUtils** (`VariableUtils.java`)
- Utility methods for variable operations
- Complementary to DebuggerRuntime
- Variable value extraction and manipulation

## How It Works

### Class Loading Strategy

1. **Debugger JVM** (debug-adapter-core):
   - Detects need for runtime operation
   - Loads DebuggerRuntime class into debuggee JVM via JDI
   - Invokes methods via reflection

2. **Debuggee JVM** (running Ballerina program):
   - DebuggerRuntime class loaded into classpath
   - Methods invoked within program context
   - Has access to program state and runtime

### Invocation Flow

```
Expression: person.getName()

1. Debug adapter parses expression
2. Identifies object method call
3. Loads DebuggerRuntime into debuggee JVM
4. Invokes DebuggerRuntime.invokeObjectMethod()
   via JDI reflection
5. Method executes in debuggee context
6. Result returned to debugger
7. Result displayed in IDE
```

## Extension Points / APIs

This module provides runtime utilities (no SPIs):

### Runtime Utility API

Methods are invoked via reflection from debug-adapter-core:

```java
// Invoked via JDI reflection
Class<?> runtimeClass = debuggeeClassLoader.loadClass(
    "org.ballerinalang.debugadapter.runtime.DebuggerRuntime"
);

Method method = runtimeClass.getMethod("invokeObjectMethod", ...);
Object result = method.invoke(null, bObject, methodName, args);
```

## Dependencies

### Module Dependencies
- **ballerina-runtime**: Core runtime API
- **ballerina-lang**: Language internals

### External Libraries
- None (minimal dependencies to reduce classpath conflicts)

## Common Patterns

### 1. Static Utility Pattern
- All methods are static
- No instance state
- Pure utility class

### 2. Reflection-Based Invocation
- Methods called via reflection from debugger
- Enables cross-JVM invocation
- Loose coupling between debugger and runtime

### 3. Strand Isolation
- Evaluation strands separate from program execution
- Prevents interference with program state
- Enables safe expression evaluation

### 4. Blocking Operations
- Most methods block until completion
- Simplifies debugger implementation
- Works with DAP synchronous model

### 5. Dynamic Module Loading
- Loads modules on-demand
- Minimal initialization overhead
- Only loads what's needed for evaluation

## Development Guidelines

### Adding New Runtime Operations

1. **Add static method to DebuggerRuntime**:
```java
@SuppressWarnings("unused")
public static Object myNewOperation(Type param) {
    // Implementation
    return result;
}
```

2. **Invoke from debug-adapter-core**:
```java
// In DebugExpressionEvaluator
Class<?> runtimeClass = loadRuntimeClass(debuggee);
Method method = runtimeClass.getMethod("myNewOperation", Type.class);
Object result = method.invoke(null, param);
```

### Handling Async Operations

```java
public static Object invokeAsyncOperation(BObject obj, String name, Object... args) {
    Scheduler scheduler = new Scheduler(false);
    ClassloaderRuntime runtime = new ClassloaderRuntime(scheduler);

    Future future = scheduler.schedule(
        new Object[]{obj, name, args},
        myFunction,
        EVALUATOR_STRAND_NAME
    );

    scheduler.start();
    Object result = future.getResult();
    runtime.stop();

    return result;
}
```

### XML Navigation Implementation

```java
// Handle different XML step types
String[] parts = childName.split(XML_STEP_SEPARATOR);

if (childName.equals(XML_ALL_CHILDREN_STEP)) {
    // Get all children
    return GetElements.getElements((BXmlSequence) parent);
}

if (childName.startsWith(XML_DESCENDANT_STEP_PREFIX)) {
    // Get descendants
    return SelectDescendants.selectDescendants((BXmlSequence) parent, ...);
}

// Normal named step
return GetFilteredChildrenFlat.getFilteredChildrenFlat(...);
```

## Usage Examples

### Example 1: Evaluate Method Call

In debugger:
```
Watch expression: person.getName()
```

Runtime execution:
```java
// Debugger invokes via reflection:
DebuggerRuntime.invokeObjectMethod(
    personObject,       // BObject
    "getName",         // method name
    new Object[0]      // no args
)
// Returns: "John Doe"
```

### Example 2: Evaluate Remote Call

In debugger:
```
Watch expression: httpClient->get("/users")
```

Runtime execution:
```java
// Debugger invokes:
DebuggerRuntime.invokeRemoteMethodAsync(
    httpClientObject,  // BObject
    "get",            // method name
    new Object[]{"/users"}  // args
)
// Returns: HTTP response
```

### Example 3: Create Array

In debugger:
```
Watch expression: new int[5]
```

Runtime execution:
```java
// Debugger invokes:
Type intArrayType = TypeCreator.createArrayType(PredefinedTypes.TYPE_INT);
DebuggerRuntime.createBArray(intArrayType, 5)
// Returns: [0, 0, 0, 0, 0]
```

### Example 4: XML Navigation

In debugger:
```
Watch expression: xmlDoc.person.name
```

Runtime execution:
```java
// Step 1: Get <person> elements
BXml persons = DebuggerRuntime.getXmlItem(xmlDoc, "person");

// Step 2: Get <name> elements from <person>
BXml names = DebuggerRuntime.getXmlItem(persons, "name");
```

## File Locations

- **Source**: `misc/debug-adapter/modules/debug-adapter-runtime/src/main/java/org/ballerinalang/debugadapter/runtime/`
  - `DebuggerRuntime.java`: Main runtime utilities
  - `VariableUtils.java`: Variable utilities
- **Build**: `misc/debug-adapter/modules/debug-adapter-runtime/build.gradle`

## Important Notes for AI Assistants

1. **Dual JVM Architecture**: This code runs in the DEBUGGEE JVM, not the debugger JVM
2. **Reflection Invocation**: All methods called via reflection from debug-adapter-core
3. **No Direct Instantiation**: Never instantiate DebuggerRuntime, all methods static
4. **Classpath Isolation**: Loaded into debuggee's classpath at runtime
5. **Strand Safety**: Creates isolated evaluation strands
6. **Blocking Nature**: Methods block, suitable for synchronous debugging
7. **Module Init**: Must initialize modules before evaluating module-level items
8. **XML Lib Integration**: Uses langlib.internal for XML operations
9. **Error Handling**: Methods may throw - debugger must catch and handle
10. **Version Compatibility**: Must match Ballerina runtime version

## Related Modules

- **debug-adapter-core**: Primary consumer, invokes these utilities
- **ballerina-runtime**: Provides runtime APIs used by this module

## Invocation Mechanism

### From Debug Adapter Core

```java
// 1. Load runtime class into debuggee JVM
ClassLoader debuggeeClassloader = getDebuggeeClassloader();
Class<?> runtimeClass = Class.forName(
    "org.ballerinalang.debugadapter.runtime.DebuggerRuntime",
    true,
    debuggeeClassloader
);

// 2. Get method
Method invokeMethod = runtimeClass.getMethod(
    "invokeObjectMethod",
    BObject.class,
    String.class,
    Object[].class
);

// 3. Invoke in debuggee context
Object result = invokeMethod.invoke(
    null,              // static method
    bObject,           // object instance
    "methodName",      // method to call
    new Object[]{...}  // arguments
);

// 4. Convert result back to debugger representation
```

## Performance Considerations

- **Reflection Overhead**: Invocation via reflection adds latency
- **Strand Creation**: Creating evaluation strands has overhead
- **Module Initialization**: Lazy init modules on first use
- **XML Operations**: Can be expensive for large XML trees
- **Blocking Operations**: Hold debugger while executing
