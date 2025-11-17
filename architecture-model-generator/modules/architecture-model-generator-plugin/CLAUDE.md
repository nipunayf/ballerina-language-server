# architecture-model-generator-plugin

## Module Overview

**Purpose**: Ballerina compiler plugin that generates architecture models during the compilation process. This plugin analyzes Ballerina source code at compile time to produce architectural model artifacts that can be used for documentation, visualization, and analysis.

**Module Name**: `io.ballerina.architecturemodelgenerator.plugin`

**Type**: Compiler plugin (runs during Ballerina compilation)

## Key Responsibilities

- **Compile-Time Analysis**: Analyze Ballerina source code during compilation
- **Model Generation**: Generate architecture models as build artifacts
- **Diagnostic Reporting**: Report issues found during model generation
- **Build Integration**: Integrate seamlessly into Ballerina build process
- **Artifact Publishing**: Output model artifacts for downstream tools

## Architecture

### Entry Points

**ModelGeneratorCompilerPlugin** (`ModelGeneratorCompilerPlugin.java:33 lines`)
- Main compiler plugin class
- Extends: `io.ballerina.projects.plugins.CompilerPlugin`
- Implements: `init(CompilerPluginContext)` method
- Registers: `ModelGeneratorCodeAnalyzer` for code analysis
- Lifecycle: Instantiated and initialized by Ballerina compiler during build

**SPI Registration**: Registered via `META-INF/services/io.ballerina.projects.plugins.CompilerPlugin`

### Core Components

#### 1. ModelGeneratorCodeAnalyzer

**File**: `ModelGeneratorCodeAnalyzer.java`

**Purpose**: Code analyzer that runs during compilation to extract architectural information

**Responsibilities**:
- Analyzes syntax trees during compilation
- Extracts service, entity, and function entry point information
- Generates architecture model artifacts
- Reports diagnostics for modeling issues

**Integration**:
- Registered with compiler plugin context
- Receives compilation events
- Accesses semantic model and syntax trees
- Can modify compilation output

#### 2. CompilationAnalysisTask

**File**: `CompilationAnalysisTask.java`

**Purpose**: Analysis task executed during compilation

**Responsibilities**:
- Performs actual model generation
- Accesses package compilation results
- Delegates to architecture-model-generator-core
- Generates output artifacts

**Pattern**: Task-based analysis in compiler plugin framework

### Diagnostic Support

**DiagnosticMessage** (`diagnostic/DiagnosticMessage.java`)
- Pre-defined diagnostic messages for plugin
- Severity levels: ERROR, WARNING, INFO
- Error codes for tracking issues

**PluginConstants** (`PluginConstants.java`)
- Constants used throughout plugin
- Error codes
- Configuration keys
- Artifact paths

## Key Classes

### ModelGeneratorCompilerPlugin

```java
public class ModelGeneratorCompilerPlugin extends CompilerPlugin {
    @Override
    public void init(CompilerPluginContext pluginContext) {
        pluginContext.addCodeAnalyzer(new ModelGeneratorCodeAnalyzer());
    }
}
```

**Methods**:
- `init(CompilerPluginContext)`: Registers code analyzer

### ModelGeneratorCodeAnalyzer

**Implements**: `io.ballerina.projects.plugins.CodeAnalyzer`

**Key Methods**:
- `init(CodeAnalysisContext)`: Initialize analyzer
- `perform(CodeActionContext)`: Perform analysis

**Pattern**: Analyzer pattern from Ballerina compiler plugin API

## Extension Points / APIs

### Compiler Plugin SPI

**Registration**: Via `META-INF/services/io.ballerina.projects.plugins.CompilerPlugin`

**Service File Content**:
```
io.ballerina.architecturemodelgenerator.plugin.ModelGeneratorCompilerPlugin
```

### Usage in Ballerina Projects

**Ballerina.toml**:
```toml
[build-options]
observabilityIncluded = true

[[tool.<plugin-name>]]
id = "architecturemodelgenerator"
```

The plugin automatically runs during `bal build` and `bal compile`.

## Dependencies

### Module Dependencies
- **architecture-model-generator-core**: Core model generation logic
- **ballerina-lang**: Language core
- **ballerina-parser**: Syntax tree API
- **ballerina-tools-api**: Compiler API
- **io.ballerina.projects**: Project and plugin API

### Compiler Plugin API
- Uses Ballerina's compiler plugin framework
- Integrates with compilation lifecycle
- Access to semantic model and syntax trees

## Common Patterns

### 1. Compiler Plugin Pattern
- Extends `CompilerPlugin` base class
- Registers analyzers and code modifiers
- Integrates into build lifecycle

### 2. Code Analyzer Pattern
- Implements `CodeAnalyzer` interface
- Receives compilation context
- Performs analysis and reports diagnostics

### 3. Task-based Execution
- Delegates work to analysis tasks
- Clean separation of concerns
- Reusable task implementations

### 4. Diagnostic Reporting
- Uses compiler diagnostic API
- Categorized by severity
- Includes source locations

### 5. Artifact Generation
- Generates build artifacts
- Writes to target directory
- Consumed by downstream tools

## Development Guidelines

### Creating a Compiler Plugin

1. **Extend CompilerPlugin**
   ```java
   public class MyPlugin extends CompilerPlugin {
       @Override
       public void init(CompilerPluginContext ctx) {
           ctx.addCodeAnalyzer(new MyAnalyzer());
       }
   }
   ```

2. **Implement Code Analyzer**
   ```java
   public class MyAnalyzer extends CodeAnalyzer {
       @Override
       public void init(CodeAnalysisContext ctx) {
           ctx.addCompilationAnalysisTask(new MyTask());
       }
   }
   ```

3. **Register via SPI**
   - Create `META-INF/services/io.ballerina.projects.plugins.CompilerPlugin`
   - Add fully qualified class name

4. **Package as JAR**
   - Build and package plugin
   - Distribute via Maven Central or Ballerina Central

### Reporting Diagnostics

```java
// In analyzer or task
Diagnostic diagnostic = DiagnosticFactory.createDiagnostic(
    new DiagnosticInfo(
        "BCE001",
        "Failed to generate model",
        DiagnosticSeverity.ERROR
    ),
    location
);
ctx.reportDiagnostic(diagnostic);
```

### Accessing Compilation Results

```java
// In analysis task
Package currentPackage = ctx.currentPackage();
PackageCompilation compilation = ctx.compilation();
SemanticModel semanticModel = compilation.getSemanticModel(moduleId);

// Use core library for model generation
ArchitectureModelBuilder builder = new ArchitectureModelBuilder();
ArchitectureModel model = builder.constructComponentModel(currentPackage);
```

## Usage Examples

### Plugin Execution During Build

When a Ballerina project is compiled:

```bash
bal build
```

The plugin automatically:
1. Registers with the compiler
2. Receives compilation events
3. Analyzes the package
4. Generates architecture models
5. Writes artifacts to `target/` directory

### Generated Artifacts

Typical output in `target/`:
- `architecture-model.json`: Architecture model
- `design-model.json`: Design visualization model
- `diagnostics.json`: Analysis diagnostics

### Integration with Build Tools

The plugin integrates with:
- **Ballerina Build**: `bal build`
- **IDEs**: Via language server integration
- **CI/CD**: Automatic model generation in pipelines

## File Locations

- **Source**: `architecture-model-generator/modules/architecture-model-generator-plugin/src/main/java/`
  - `io/ballerina/architecturemodelgenerator/plugin/`: Plugin implementation
- **Resources**: `architecture-model-generator/modules/architecture-model-generator-plugin/src/main/resources/`
  - `META-INF/services/`: SPI registration files
- **Build**: `architecture-model-generator/modules/architecture-model-generator-plugin/build.gradle`

## Important Notes for AI Assistants

1. **Compiler Plugin Lifecycle**: Plugin runs during compilation, not at runtime
2. **Limited Scope**: Can only analyze, not modify source code (readonly)
3. **Semantic Model Access**: Full access to compiler's semantic model
4. **Build Output**: Artifacts written to `target/` directory
5. **Error Handling**: Must not crash compilation on errors
6. **Performance**: Should be fast to avoid slowing builds
7. **SPI Registration**: Must be properly registered via service file
8. **Dependencies**: Core module does the heavy lifting, plugin is just the integration layer
9. **Compilation Context**: Operates on entire package, not individual files
10. **Artifact Format**: JSON output for interoperability

## Testing

### Unit Testing

```java
@Test
public void testPluginInitialization() {
    ModelGeneratorCompilerPlugin plugin = new ModelGeneratorCompilerPlugin();
    CompilerPluginContext mockContext = mock(CompilerPluginContext.class);

    plugin.init(mockContext);

    verify(mockContext).addCodeAnalyzer(any(ModelGeneratorCodeAnalyzer.class));
}
```

### Integration Testing

- Use Ballerina test projects
- Run compilation with plugin enabled
- Verify generated artifacts
- Check diagnostic output

### Performance Testing

- Measure compilation time impact
- Ensure minimal overhead
- Profile memory usage

## Best Practices

1. **Fast Analysis**: Minimize compilation time impact
2. **Graceful Errors**: Don't crash on invalid code
3. **Clear Diagnostics**: Provide actionable error messages
4. **Minimal Dependencies**: Keep plugin lightweight
5. **Version Compatibility**: Support multiple Ballerina versions
6. **Documentation**: Document plugin usage and configuration
7. **Artifact Versioning**: Version output format for compatibility
8. **Incremental Analysis**: Support incremental builds when possible

## Related Modules

- **architecture-model-generator-core**: Core model generation logic (used by this plugin)
- **architecture-model-generator-ls-extension**: Language server integration
- **langserver-core**: Language server using plugin outputs
- **ballerina-lang**: Compiler providing plugin framework
