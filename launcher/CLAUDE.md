# launcher

## Module Overview

**Purpose**: Alternative launcher for Ballerina Language Server. Provides an entry point for starting the language server, potentially with different transport mechanisms or configurations compared to the stdio-launcher.

**Module Name**: `org.ballerinalang.langserver.launchers.stdio`

**Type**: Launcher module

## Key Responsibilities

- **Language Server Launch**: Start Ballerina Language Server
- **Stdio Transport**: Provide stdin/stdout communication
- **Server Initialization**: Initialize language server with client connection
- **LSP Protocol**: Enable LSP JSON-RPC communication

## Architecture

### Entry Point

**Main** (`Main.java`)

**Purpose**: Entry point for language server launcher

**Main Method**:
```java
public static void main(String[] args) throws InterruptedException, ExecutionException
```

**startServer Method**:
```java
public static void startServer(InputStream in, OutputStream out)
    throws InterruptedException, ExecutionException
```

**Workflow**:
1. Disable logging to avoid stdout interference
2. Create BallerinaLanguageServer instance
3. Create LSP4J Launcher with stdin/stdout
4. Connect server to client proxy
5. Start listening for LSP messages

### Core Components

#### Language Server Instance

Creates `BallerinaLanguageServer` from langserver-core

#### LSP4J Launcher

Uses Eclipse LSP4J library to:
- Create JSON-RPC connection over stdio
- Marshal/unmarshal LSP messages
- Handle request/response/notification routing

#### Client Proxy

**ExtendedLanguageClient**: Remote proxy for client

**Purpose**: Allows server to send notifications/requests to client

## Extension Points / APIs

### Programmatic Start

```java
import org.ballerinalang.langserver.launchers.stdio.Main;

// Start server programmatically
Main.startServer(System.in, System.out);
```

### API Doc Generation

```java
// Generate API specification
List<JsonObject> apiDocs = Main.generateApiDoc();
```

**Purpose**: Document all supported LSP methods

## Dependencies

### Module Dependencies
- **langserver-core**: BallerinaLanguageServer implementation
- **langserver-commons**: ExtendedLanguageClient interface
- **apispec-generator**: API specification generation

### External Libraries
- **org.eclipse.lsp4j**: LSP4J JSON-RPC framework
- **gson**: JSON serialization

## Common Patterns

### 1. Launcher Pattern
```java
Launcher<ExtendedLanguageClient> launcher = Launcher.createLauncher(
    server,
    ExtendedLanguageClient.class,
    inputStream,
    outputStream
);
ExtendedLanguageClient client = launcher.getRemoteProxy();
server.connect(client);
launcher.startListening().get();
```

### 2. Logging Suppression Pattern
```java
LogManager.getLogManager().reset();
Logger globalLogger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
globalLogger.setLevel(Level.OFF);
```

### 3. Future Blocking Pattern
```java
Future<?> startListening = launcher.startListening();
startListening.get();  // Block until server stops
```

## Usage Examples

### Start Server

```java
public static void main(String[] args) throws Exception {
    Main.startServer(System.in, System.out);
}
```

### Generate API Documentation

```java
List<JsonObject> apiSpec = Main.generateApiDoc();
for (JsonObject method : apiSpec) {
    System.out.println("Method: " + method.get("method"));
    System.out.println("Params: " + method.get("params"));
}
```

## File Locations

- **Source**: `launcher/src/main/java/`
  - `org/ballerinalang/langserver/launchers/stdio/`: Launcher implementation
- **Build**: `launcher/build.gradle`

## Important Notes for AI Assistants

1. **Stdio Transport**: Uses stdin/stdout for LSP communication
2. **LSP4J Framework**: Built on Eclipse LSP4J
3. **Blocking Operation**: Main method blocks until server stops
4. **Logging Disabled**: Prevents interference with LSP protocol
5. **Client Proxy**: Server can send messages to client
6. **API Doc Generation**: Can generate JSON-RPC API specification
7. **System Property**: Reads enableOutputStream property
8. **Production Use**: Used in actual IDE integrations

## Related Modules

- **langserver-core**: The actual language server implementation
- **langserver-cli**: CLI wrapper for this launcher
- **stdio-launcher**: Alternative/updated launcher implementation
- **langserver-commons**: Common interfaces and utilities
