# stdio-launcher

## Module Overview

**Purpose**: Standard I/O launcher for Ballerina Language Server. The primary production launcher that starts the language server with stdin/stdout transport for LSP communication. Used by all major IDE integrations (VS Code, IntelliJ, etc.).

**Module Name**: `org.ballerinalang.langserver.launchers.stdio`

**Type**: Launcher module (main class)

## Key Responsibilities

- **Language Server Launch**: Start Ballerina Language Server process
- **Stdio Transport**: Establish stdin/stdout LSP communication channel
- **Server Initialization**: Initialize server with client connection
- **LSP Protocol Handling**: Enable JSON-RPC over stdio
- **API Documentation**: Generate LSP method specifications

## Architecture

### Entry Point

**Main** (`Main.java:78 lines`)

**Main Method**:
```java
public static void main(String[] args) throws InterruptedException, ExecutionException {
    LogManager.getLogManager().reset();
    Logger globalLogger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    globalLogger.setLevel(Level.OFF);
    startServer(System.in, System.out);
}
```

**Purpose**: CLI entry point for launching language server

### Core Methods

#### startServer

```java
public static void startServer(InputStream in, OutputStream out)
        throws InterruptedException, ExecutionException
```

**Workflow**:
1. Disable stdout output for Ballerina Central calls
   ```java
   System.getProperty("enableOutputStream", "false");
   ```

2. Create language server instance
   ```java
   BallerinaLanguageServer server = new BallerinaLanguageServer();
   ```

3. Create LSP4J launcher with stdin/stdout
   ```java
   Launcher<ExtendedLanguageClient> launcher = Launcher.createLauncher(
       server,
       ExtendedLanguageClient.class,
       in,
       out
   );
   ```

4. Get remote client proxy
   ```java
   ExtendedLanguageClient client = launcher.getRemoteProxy();
   ```

5. Connect server to client
   ```java
   server.connect(client);
   ```

6. Start listening (blocks until shutdown)
   ```java
   Future<?> startListening = launcher.startListening();
   startListening.get();
   ```

#### generateApiDoc

```java
public static List<JsonObject> generateApiDoc()
```

**Purpose**: Generate API specification for all supported JSON-RPC methods

**Workflow**:
1. Disable stdout output
2. Create BallerinaLanguageServer instance
3. Get supported methods map
4. Generate specification for each method
5. Return list of method specifications

**Output**: List of JsonObject describing:
- Method name
- Parameter types
- Return types
- Documentation

### Core Components

#### LSP4J Integration

Uses Eclipse LSP4J framework:
- **Launcher**: Creates JSON-RPC connection
- **MessageReader/Writer**: Handle stdin/stdout
- **JsonRpcMethod**: Method routing
- **RemoteProxy**: Client interface proxy

#### Extended Language Client

**ExtendedLanguageClient** interface:
- Extends standard LSP LanguageClient
- Adds Ballerina-specific capabilities
- Enables custom notifications/requests

#### API Spec Generator

**ApiSpecGenerator** (`langserver.apispec.ApiSpecGenerator`)

**Purpose**: Generate JSON specifications for RPC methods

**Usage**: Documentation and tooling

## Extension Points / APIs

### Programmatic Launch

```java
import org.ballerinalang.langserver.launchers.stdio.Main;

// Start with custom streams
InputStream in = // custom input
OutputStream out = // custom output
Main.startServer(in, out);
```

### API Documentation

```java
// Generate API specification
List<JsonObject> spec = Main.generateApiDoc();

// Process specifications
for (JsonObject methodSpec : spec) {
    String method = methodSpec.get("method").getAsString();
    JsonArray params = methodSpec.getAsJsonArray("params");
    // Document or validate
}
```

## Dependencies

### Module Dependencies
- **langserver-core**: BallerinaLanguageServer implementation
- **langserver-commons**: ExtendedLanguageClient and utilities
- **apispec-generator**: API specification generation

### External Libraries
- **org.eclipse.lsp4j**: LSP4J JSON-RPC framework
- **gson**: JSON processing

## Common Patterns

### 1. Stdio Launcher Pattern
```java
Launcher<ClientInterface> launcher = Launcher.createLauncher(
    serverImplementation,
    ClientInterface.class,
    inputStream,
    outputStream
);
ClientInterface client = launcher.getRemoteProxy();
serverImplementation.connect(client);
launcher.startListening().get();  // Block until shutdown
```

### 2. Logging Suppression Pattern
```java
// Disable all logging
LogManager.getLogManager().reset();
Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).setLevel(Level.OFF);
```

**Reason**: Logging to stdout would corrupt LSP messages

### 3. System Property Configuration
```java
// Disable Ballerina Central stdout
System.getProperty("enableOutputStream", "false");
```

**Reason**: Prevent Ballerina package manager from writing to stdout

## Usage Examples

### IDE Integration (VS Code)

**package.json**:
```json
{
    "activationEvents": ["onLanguage:ballerina"],
    "contributes": {
        "configuration": {
            "ballerina.home": {
                "type": "string",
                "description": "Ballerina installation directory"
            }
        }
    }
}
```

**extension.ts**:
```typescript
import { LanguageClient, ServerOptions, TransportKind } from 'vscode-languageclient/node';

const ballerinaHome = vscode.workspace.getConfiguration('ballerina').get('home');
const serverCommand = path.join(ballerinaHome, 'bin', 'ballerina');

const serverOptions: ServerOptions = {
    command: serverCommand,
    args: ['start-language-server'],
    transport: TransportKind.stdio
};

const client = new LanguageClient(
    'ballerina',
    'Ballerina Language Server',
    serverOptions,
    clientOptions
);

await client.start();
```

### Direct Launch

```bash
# From Ballerina distribution
cd $BALLERINA_HOME
java -jar LS_LAUNCHER_JAR

# Via Ballerina CLI
ballerina start-language-server
```

### Programmatic Launch

```java
public class MyLauncher {
    public static void main(String[] args) throws Exception {
        // Launch with stdin/stdout
        Main.startServer(System.in, System.out);
    }
}
```

## File Locations

- **Source**: `launchers/stdio-launcher/src/main/java/`
  - `org/ballerinalang/langserver/launchers/stdio/`: Launcher implementation
- **Build**: `launchers/stdio-launcher/build.gradle`

## Important Notes for AI Assistants

1. **Primary Launcher**: This is the main production launcher
2. **Stdio Transport**: Uses stdin/stdout exclusively
3. **Blocking Operation**: main() blocks until server shutdown
4. **Logging Disabled**: All logging suppressed to protect stdio
5. **LSP4J Framework**: Built on Eclipse LSP4J
6. **Client Proxy**: Server can send requests to client
7. **System Properties**: Uses system properties for configuration
8. **API Documentation**: Can generate method specifications
9. **Production Use**: Used in all major IDE integrations
10. **Clean Shutdown**: Properly handles shutdown signals

## Development Guidelines

### Testing Launcher

```java
@Test
public void testLauncherStartup() throws Exception {
    PipedInputStream clientInput = new PipedInputStream();
    PipedOutputStream serverOutput = new PipedOutputStream(clientInput);

    PipedInputStream serverInput = new PipedInputStream();
    PipedOutputStream clientOutput = new PipedOutputStream(serverInput);

    // Start server in separate thread
    new Thread(() -> {
        try {
            Main.startServer(serverInput, serverOutput);
        } catch (Exception e) {
            fail(e);
        }
    }).start();

    // Send LSP initialize request
    // Verify response
}
```

### Adding Custom Configuration

```java
// Read environment variable or system property
String customConfig = System.getProperty("ballerina.ls.config");
if (customConfig != null) {
    // Apply configuration
}
```

## Performance Considerations

- **Minimal Overhead**: Direct stdin/stdout, no network overhead
- **Efficient Serialization**: LSP4J uses efficient JSON-RPC
- **No Logging Overhead**: Logging disabled for performance
- **Single Process**: Server runs in same process, no IPC overhead

## Related Modules

- **langserver-core**: The actual language server implementation
- **langserver-cli**: CLI command wrapping this launcher
- **langserver-commons**: Common interfaces and types
- **launcher**: Alternative launcher implementation
