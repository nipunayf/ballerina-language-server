# debug-adapter-cli

## Module Overview

**Purpose**: Command-line interface for launching the Ballerina Debug Adapter. This module provides the `bal start-debugger-adapter` command that starts the debug server on a specified port for IDE integration.

**Module Name**: `org.ballerinalang.debugadapter.cmd`

**Type**: CLI command module

**Size**: 2 Java source files

## Key Responsibilities

- **CLI Command**: Provide `start-debugger-adapter` command for Ballerina CLI
- **Debug Server Launch**: Start debug adapter server on specified port
- **Argument Parsing**: Parse and validate command-line arguments
- **Error Handling**: Handle launch failures gracefully with helpful error messages

## Architecture

### Entry Points

**DebugAdapterStartCmd** (`DebugAdapterStartCmd.java:82 lines`)
- Main CLI command class
- Implements `BLauncherCmd` interface from Ballerina CLI framework
- Uses PicoCLI annotations for argument parsing
- Command name: `start-debugger-adapter`

**Command Syntax**:
```bash
bal start-debugger-adapter [--help|-h] <debugAdapterPort>
```

**Parameters**:
- `debugAdapterPort`: Port number for debug adapter server (required)
- `--help`, `-h`: Display help information

### Core Components

#### 1. Command Implementation

**BLauncherCmd Interface**:
```java
public class DebugAdapterStartCmd implements BLauncherCmd {
    void execute()
    String getName()
    void printLongDesc(StringBuilder out)
    void printUsage(StringBuilder out)
    void setParentCmdParser(CommandLine parentCmdParser)
}
```

**Command Execution Flow**:
1. Parse arguments via PicoCLI
2. Validate port number
3. Construct launcher arguments
4. Invoke `DebugAdapterLauncher.main()`
5. Handle any errors

#### 2. PicoCLI Integration

**Annotations**:
```java
@CommandLine.Command(
    name = "start-debugger-adapter",
    description = "start Ballerina Debug adapter"
)
```

**Argument Definitions**:
```java
@CommandLine.Parameters
private List<String> argList;  // Positional arguments

@CommandLine.Option(names = {"-h", "--help"}, hidden = true)
private boolean helpFlag;
```

#### 3. Launcher Integration

**DebugAdapterLauncher**:
- Actual debug server launcher (from debug-adapter-core)
- Started via `main()` method invocation
- Receives port number as argument

## Extension Points

This module implements Ballerina CLI extension:

### 1. BLauncherCmd SPI

**Implementation**: DebugAdapterStartCmd

**Registration**: Via Ballerina CLI tool infrastructure

**Purpose**: Add debug adapter start command to `bal` CLI

## Dependencies

### Module Dependencies
- **debug-adapter-core**: Contains DebugAdapterLauncher
- **ballerina-cli**: CLI framework (picocli integration)

### External Libraries
- **picocli**: Command-line argument parsing

## Common Patterns

### 1. Command Pattern
- Implements command interface
- Encapsulates action in execute() method

### 2. Facade Pattern
- Provides simple interface to complex debug adapter launch

### 3. Error Handling
- Validates input before execution
- Provides user-friendly error messages
- Uses LauncherUtils for exception creation

## Development Guidelines

### Using the Command

**From Command Line**:
```bash
# Start debug adapter on port 4711
bal start-debugger-adapter 4711
```

**From IDE**:
Most IDEs start the debug adapter automatically, but can manually start:
```bash
bal start-debugger-adapter 12345
```

**With Help Flag**:
```bash
bal start-debugger-adapter --help
```

### Implementation Details

```java
@Override
public void execute() {
    try {
        List<String> debugLauncherArgs = new ArrayList<>();

        // Extract port number from arguments
        if (argList != null && !argList.isEmpty()) {
            int debugServerPort = Integer.parseInt(argList.get(0));
            debugLauncherArgs.add(String.valueOf(debugServerPort));
        }

        // Launch the debug server
        DebugAdapterLauncher.main(debugLauncherArgs.toArray(new String[0]));

    } catch (NumberFormatException e) {
        // Invalid port number
        throw LauncherUtils.createLauncherException(
            "Failed to start debug adapter due to the invalid port " +
            "specified: '" + argList.get(0) + "'"
        );
    } catch (Throwable e) {
        // Other errors
        throw LauncherUtils.createLauncherException(
            "Failed to start debug adapter due to: " + e.getMessage()
        );
    }
}
```

### Adding Custom Options

To add new command-line options:

1. **Add PicoCLI annotation**:
```java
@CommandLine.Option(names = {"-v", "--verbose"})
private boolean verbose;
```

2. **Use in execute()**:
```java
if (verbose) {
    // Enable verbose logging
}
```

3. **Pass to launcher**:
```java
if (verbose) {
    debugLauncherArgs.add("--verbose");
}
```

## Usage Examples

### Example 1: Basic Usage

```bash
# Start debug adapter on default port
bal start-debugger-adapter 4711
```

**What happens**:
1. Command parses "4711" as port number
2. Validates it's a valid integer
3. Calls `DebugAdapterLauncher.main(new String[]{"4711"})`
4. Debug server starts and listens on port 4711
5. IDEs can connect to localhost:4711

### Example 2: Invalid Port

```bash
bal start-debugger-adapter abc
```

**Output**:
```
error: Failed to start debug adapter due to the invalid port specified: 'abc'
```

### Example 3: IDE Integration

VSCode launch.json:
```json
{
  "type": "ballerina",
  "request": "launch",
  "name": "Debug Ballerina",
  "debugServer": 4711
}
```

VSCode process:
1. Start debug adapter: `bal start-debugger-adapter 4711`
2. Wait for adapter to be ready
3. Connect to localhost:4711
4. Send DAP initialize request
5. Begin debugging session

## File Locations

- **Source**: `misc/debug-adapter/modules/debug-adapter-cli/src/main/java/org/ballerinalang/debugadapter/cmd/`
  - `DebugAdapterStartCmd.java`: Main command implementation
- **Build**: `misc/debug-adapter/modules/debug-adapter-cli/build.gradle`

## Important Notes for AI Assistants

1. **Thin Wrapper**: This is a very thin CLI wrapper around DebugAdapterLauncher
2. **Single Purpose**: Only purpose is to start the debug adapter server
3. **Port Validation**: Validates port is a valid integer, but not port range
4. **No Help Implementation**: printLongDesc and printUsage are empty (help handled elsewhere)
5. **Error Translation**: Converts low-level exceptions to user-friendly messages
6. **Blocking Call**: execute() blocks until debug adapter terminates
7. **IDE Usage**: Typically invoked by IDEs automatically, not by end users
8. **Single Port**: Only supports one port argument (debug adapter port)
9. **No Configuration File**: All configuration via command-line arguments
10. **Launcher Delegation**: All actual work delegated to DebugAdapterLauncher

## Related Modules

- **debug-adapter-core**: Contains the actual debug server implementation
- **launcher**: Main Ballerina launcher that may invoke debugger
- **langserver-cli**: Similar CLI module for language server

## Command Registration

This command is registered with the Ballerina CLI tool infrastructure and becomes available as:

```bash
bal start-debugger-adapter
```

When Ballerina is installed, this command is automatically available alongside other `bal` commands like `bal build`, `bal run`, etc.

## Typical Invocation Flow

```
IDE (VSCode)
    ↓
Start Debug Session
    ↓
Execute: bal start-debugger-adapter 4711
    ↓
DebugAdapterStartCmd.execute()
    ↓
DebugAdapterLauncher.main(["4711"])
    ↓
JBallerinaDebugServer created
    ↓
Server listens on port 4711
    ↓
IDE connects via socket
    ↓
DAP communication begins
```

## Error Scenarios

| Scenario | Error Message |
|----------|--------------|
| No arguments | ArrayIndexOutOfBoundsException (caught) |
| Invalid port | "Failed to start debug adapter due to the invalid port specified: 'xxx'" |
| Port in use | "Failed to start debug adapter due to: Address already in use" |
| Permission denied | "Failed to start debug adapter due to: Permission denied" |

## Performance Considerations

- **Startup Time**: Minimal overhead, just argument parsing
- **Memory**: Very small footprint, delegates to debug adapter
- **Blocking**: Blocks terminal until debug session ends
- **Port Binding**: Fails fast if port unavailable
