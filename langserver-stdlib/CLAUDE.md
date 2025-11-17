# langserver-stdlib

## Module Overview

**Purpose**: Minimal standard library stub implementations for Ballerina Language Server. Provides lightweight Java interop stub methods that enable language server semantic analysis without requiring full standard library runtime. Used during compilation for type checking and code intelligence.

**Module Name**: `org.ballerinalang.langserver.stdlib`

**Type**: Library module (Java interop stubs)

## Key Responsibilities

- **Stub Implementations**: Provide minimal method stubs for standard library functions
- **Type Checking Support**: Enable semantic analysis without runtime dependencies
- **Compilation Support**: Allow compilation for code intelligence without execution
- **Lightweight Interop**: Minimal Java interop layer for language server performance

## Architecture

### Core Components

#### Client Action Stubs

**ClientAction** (`ClientAction.java:57 lines`)

**Purpose**: Stub implementations for HTTP client actions

**Methods**:

```java
public static Object get(BString path, BTypedesc targetType)
public static Object forward(BString path, BObject request, BTypedesc targetType)
public static Object delete(BTypedesc targetType)
public static BStream responses(BTypedesc targetType)
public static Object postResource(BArray path, Object message, Object headers,
                                  Object mediaType, BTypedesc targetType)
```

**Pattern**: Return placeholder objects

**Purpose**:
- Enable HTTP client code to compile
- Provide type information for semantic analysis
- No actual HTTP calls made (stubs only)
- Used during language server analysis only

**Usage Context**:
- Language server loads this during compilation
- Enables completion, diagnostics, hover for HTTP client code
- Not used for actual program execution

## Extension Points / APIs

### Java Interop Pattern

**Ballerina Declaration**:
```ballerina
// In ballerina/http module
public isolated client class Client {
    public isolated remote function get(@untainted string path,
                                       typedesc<anydata> targetType = <>)
        returns targetType|ClientError = @java:Method {
        'class: "org.ballerinalang.langserver.stdlib.ClientAction"
    } external;
}
```

**Stub Implementation**:
```java
public static Object get(BString path, BTypedesc targetType) {
    return new Object();  // Placeholder return
}
```

**Effect**: Allows language server to analyze HTTP client code without runtime

## Dependencies

### Module Dependencies
- **ballerina-runtime**: Ballerina runtime API types (BString, BObject, etc.)

### External Libraries
- None (minimal dependencies)

## Common Patterns

### 1. Stub Return Pattern
```java
public static Object methodName(params...) {
    return new Object();  // Placeholder
}
```
- Returns generic Object
- Satisfies compilation
- No actual implementation

### 2. Stream Stub Pattern
```java
public static BStream responses(BTypedesc targetType) {
    return ValueCreator.createStreamValue(
        TypeCreator.createStreamType(targetType.getDescribingType())
    );
}
```
- Creates empty stream
- Correct type signature
- No data

### 3. Minimal Implementation Pattern
- Only what's needed for compilation
- No business logic
- No side effects
- Placeholder returns

## Usage Context

### Language Server Compilation

When language server compiles code:
1. User writes HTTP client code
2. Language server compiles for semantic analysis
3. HTTP module references Java interop methods
4. langserver-stdlib provides stub implementations
5. Compilation succeeds
6. Language server provides completions, diagnostics, hover

### NOT Used For

- Actual program execution
- Runtime behavior
- Test execution
- Production deployments

## File Locations

- **Source**: `langserver-stdlib/src/main/java/`
  - `org/ballerinalang/langserver/stdlib/`: Stub implementations
- **Build**: `langserver-stdlib/build.gradle`

## Important Notes for AI Assistants

1. **Stub Only**: This is NOT actual standard library implementation
2. **Language Server Specific**: Only used during language server compilation
3. **No Runtime Behavior**: Methods don't perform actual operations
4. **Type Information**: Provides type signatures for analysis
5. **Performance**: Keeps language server lightweight
6. **Compilation Support**: Enables semantic analysis without full runtime
7. **Placeholder Returns**: All methods return generic objects
8. **No Side Effects**: No actual I/O, network calls, or state changes
9. **Minimal Dependencies**: Deliberately minimal to reduce overhead
10. **Not Executable**: Code using these stubs won't work at runtime

## Development Guidelines

### Adding New Stub

When standard library adds new interop method:

1. **Identify Interop Method**: Check standard library for @java:Method

2. **Create Stub**:
   ```java
   public static Object newMethod(BString param1, BTypedesc targetType) {
       return new Object();
   }
   ```

3. **Match Signature**: Ensure signature matches Ballerina declaration exactly

4. **Minimal Implementation**: Return placeholder, no logic

5. **Update Build**: Ensure langserver-stdlib in classpath

### Why Stubs Are Needed

**Problem**: Language server needs to compile code for analysis, but:
- Standard library has external Java methods
- Java methods may have heavy dependencies
- Runtime dependencies slow down language server
- Some methods require actual runtime environment

**Solution**: Provide lightweight stubs:
- Satisfy compilation requirements
- Provide type information
- No runtime overhead
- Fast semantic analysis

## Performance Considerations

- **Minimal Footprint**: Very small JAR size
- **Fast Loading**: Quick to load into language server
- **No Heavy Dependencies**: Avoids unnecessary libraries
- **Compilation Speed**: Doesn't slow down semantic analysis

## Related Modules

- **langserver-core**: Language server using these stubs
- **ballerina-lang**: Compiler using stubs during analysis
- **ballerina-runtime**: Provides BString, BObject, etc. types
