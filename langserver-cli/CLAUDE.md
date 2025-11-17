# langserver-cli

## Module Overview

**Purpose**: Command-line interface for starting the Ballerina Language Server. Provides CLI commands integrated into the Ballerina distribution for launching the language server from command line or IDE integration scripts.

**Module Name**: `org.ballerinalang.langserver.cmd`

**Type**: CLI module (Ballerina launcher command)

## Key Responsibilities

- **CLI Command**: Provide `start-language-server` command for Ballerina CLI
- **Language Server Launcher**: Start language server process
- **Logging Configuration**: Configure logging for language server
- **Integration Point**: Enable IDE integration via command-line invocation

## Architecture

### Entry Points

#### Start Language Server Command

**LangServerStartCmd** (`LangServerStartCmd.java:80 lines`)

**Command**: `ballerina start-language-server`

**Implementation**: PicoCLI command

```java
@CommandLine.Command(
    name = "start-language-server",
    description = "start Ballerina language server"
)
public class LangServerStartCmd implements BLauncherCmd
```

**Options**:
- `-h, --help`: Show help

**Execution**:
```java
@Override
public void execute() {
    // Set ballerina.home system property
    System.setProperty("ballerina.home", BALLERINA_HOME);

    // Disable logging
    LogManager.getLogManager().reset();
    Logger globalLogger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    globalLogger.setLevel(Level.OFF);

    // Start server via stdio
    Main.startServer(System.in, System.out);
}
```

**Workflow**:
1. Parse command-line arguments
2. Set Ballerina home directory
3. Disable logging (to avoid interfering with LSP stdio)
4. Delegate to stdio launcher Main class
5. Start language server on stdin/stdout

#### Spec Command

**LangServerSpecCmd** (`LangServerSpecCmd.java`)

**Purpose**: Generate language server API specification (likely)

**Usage**: Document supported LSP methods

### Core Components

#### Command Registration

Registered as Ballerina launcher command via:
- **Service Provider Interface**: `io.ballerina.cli.BLauncherCmd`
- **Service File**: `META-INF/services/io.ballerina.cli.BLauncherCmd`

#### Integration with Launcher

Delegates to: `org.ballerinalang.langserver.launchers.stdio.Main.startServer()`

## Extension Points / APIs

### Command Line Interface

**Usage**:
```bash
# Start language server
ballerina start-language-server

# Show help
ballerina start-language-server --help
```

**Integration in IDEs**:
```json
{
    "command": "ballerina",
    "args": ["start-language-server"],
    "transport": "stdio"
}
```

### PicoCLI Command

**Interface**: `io.ballerina.cli.BLauncherCmd`

**Methods**:
- `execute()`: Execute command
- `getName()`: Return command name
- `printLongDesc(StringBuilder)`: Print long description
- `printUsage(StringBuilder)`: Print usage
- `setParentCmdParser(CommandLine)`: Set parent parser

## Dependencies

### Module Dependencies
- **ballerina-cli**: CLI framework
- **stdio-launcher**: Language server stdio launcher
- **picocli**: CLI parsing library

### External Libraries
- **java.util.logging**: Logging framework

## Common Patterns

### 1. Launcher Command Pattern
```java
@CommandLine.Command(name = "command-name")
public class MyCmd implements BLauncherCmd {
    @Override
    public void execute() {
        // Command logic
    }
}
```

### 2. Logging Suppression Pattern
```java
LogManager.getLogManager().reset();
Logger globalLogger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
globalLogger.setLevel(Level.OFF);
```
- Prevents log output to stdout
- Avoids interfering with LSP stdio communication

### 3. System Property Pattern
```java
System.setProperty("ballerina.home", BALLERINA_HOME);
```
- Set runtime configuration
- Enable language server to locate Ballerina installation

## Usage Examples

### From Command Line

```bash
# Start language server for IDE integration
ballerina start-language-server
```

### From VS Code Extension

**package.json**:
```json
{
    "contributes": {
        "configuration": {
            "ballerina.langServerPath": {
                "type": "string",
                "default": "ballerina"
            }
        }
    }
}
```

**extension.ts**:
```typescript
const serverOptions: ServerOptions = {
    command: 'ballerina',
    args: ['start-language-server'],
    transport: TransportKind.stdio
};

const client = new LanguageClient(
    'ballerina',
    'Ballerina Language Server',
    serverOptions,
    clientOptions
);

client.start();
```

### From IntelliJ Plugin

```kotlin
val commandLine = GeneralCommandLine(
    "ballerina",
    "start-language-server"
)
val process = commandLine.createProcess()
// Connect to process stdin/stdout
```

## File Locations

- **Source**: `langserver-cli/src/main/java/`
  - `org/ballerinalang/langserver/cmd/`: Command implementations
- **Resources**: `langserver-cli/src/main/resources/`
  - `META-INF/services/`: Service provider registration
- **Build**: `langserver-cli/build.gradle`

## Important Notes for AI Assistants

1. **CLI Integration**: Integrates language server into Ballerina CLI
2. **Stdio Transport**: Uses stdin/stdout for LSP communication
3. **Logging Disabled**: Explicitly disables logging to avoid stdio interference
4. **IDE Integration Point**: Primary way IDEs start language server
5. **Ballerina Home**: Sets ballerina.home system property
6. **Launcher Delegation**: Delegates to stdio-launcher module
7. **PicoCLI Framework**: Uses PicoCLI for command parsing
8. **SPI Registration**: Auto-discovered by Ballerina launcher
9. **No Direct Implementation**: Actual server in stdio-launcher module
10. **Production Use**: Used in production IDE integrations

## Development Guidelines

### Adding New CLI Option

1. **Add Option Field**:
   ```java
   @CommandLine.Option(names = {"-p", "--port"}, description = "Server port")
   private int port = 9229;
   ```

2. **Use in execute()**:
   ```java
   @Override
   public void execute() {
       // Use this.port
       Main.startServer(System.in, System.out, port);
   }
   ```

3. **Update Help**:
   ```java
   @Override
   public void printUsage(StringBuilder out) {
       out.append("Usage: ballerina start-language-server [options]\n");
       out.append("  -p, --port <port>   Server port\n");
   }
   ```

## Related Modules

- **stdio-launcher**: Actual language server implementation
- **launcher**: Alternative launcher module
- **langserver-core**: Language server core logic
- **ballerina-cli**: Ballerina CLI framework
