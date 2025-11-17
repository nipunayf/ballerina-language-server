# architecture-model-generator-core

## Module Overview

**Purpose**: Core library for generating architectural model representations of Ballerina projects. This module analyzes Ballerina source code to extract and model services, entities (persist models), function entry points, and their interactions for visualization and documentation purposes.

**Module Name**: `io.ballerina.architecturemodelgenerator.core`

**Type**: Library module (no executable components)

## Key Responsibilities

- **Service Model Generation**: Extract service declarations, resource functions, remote functions, and their dependencies
- **Entity Model Generation**: Generate entity-relationship models from Ballerina persist type definitions
- **Function Entry Point Analysis**: Identify and model main functions and their interactions
- **Dependency Tracking**: Analyze connections between components (client calls, database interactions, etc.)
- **Design Model Generation**: Generate architectural diagrams showing services, listeners, connections, and automations
- **Project Migration Support**: Tools for migrating Mule and TIBCO projects to Ballerina

## Architecture

### Entry Points

**ArchitectureModelBuilder** (`ArchitectureModelBuilder.java:100+ lines`)
- Main orchestrator for architecture model generation
- Method: `constructComponentModel(Package)`: Generate complete architecture model
- Delegates to specialized generators for services, entities, and function entry points
- Collects diagnostics and handles errors gracefully
- Returns: `ArchitectureModel` with services, entities, dependencies, diagnostics

**DesignModelGenerator** (`designmodelgenerator/core/DesignModelGenerator.java:200+ lines`)
- Generates design models for project visualization
- Analyzes main functions (automation entry points)
- Identifies services and their connections
- Tracks module-level client connections
- Returns: `DesignModel` with automation flows and service interactions

### Core Components

#### 1. Service Model Generator

**ServiceModelGenerator** (`generators/service/ServiceModelGenerator.java`)
- Extracts service declarations from modules
- Identifies resource functions (HTTP GET, POST, etc.)
- Tracks remote functions and actions
- Analyzes connections to external services
- Captures display annotations for visualization

**Visitors**:
- `ServiceDeclarationNodeVisitor`: Visits service declaration nodes
- `ServiceMemberFunctionNodeVisitor`: Extracts resource and remote functions
- `ActionNodeVisitor`: Processes action nodes (client calls)

**Output**: Map of `Service` objects with:
- Service ID, name, base path
- Resource functions with HTTP methods and paths
- Remote functions
- Connection dependencies
- Display annotations
- Source locations

#### 2. Entity Model Generator

**EntityModelGenerator** (`generators/entity/EntityModelGenerator.java`)
- Generates ER models from Ballerina persist entities
- Extracts entity attributes and their types
- Identifies associations (relationships) between entities
- Captures cardinality and relationship types

**Visitor**:
- `TypeDefinitionNodeVisitor`: Visits type definition nodes to extract entity metadata

**Output**: Map of `Entity` objects with:
- Entity name and ID
- Attributes (name, type, nullable)
- Associations (references to other entities)
- Display annotations
- Source locations

#### 3. Function Entry Point Generator

**FunctionEntryPointModelGenerator** (`generators/entrypoint/FunctionEntryPointModelGenerator.java`)
- Identifies main function entry points
- Extracts function parameters and return types
- Tracks interactions within the function body
- Captures function metadata

**Visitors**:
- `FunctionEntryPointVisitor`: Visits function definitions
- `FunctionEntryPointMemberNodeVisitor`: Analyzes function body

**Output**: `FunctionEntryPoint` with:
- Function name, parameters, return type
- Interactions (function calls, remote calls)
- Display annotations
- Source locations

#### 4. Design Model Components

**CodeAnalyzer** (`designmodelgenerator/core/CodeAnalyzer.java`)
- Visitor pattern for analyzing Ballerina syntax trees
- Identifies functions, services, connections, listeners
- Builds intermediate representation of code structure

**ConnectionFinder** (`designmodelgenerator/core/ConnectionFinder.java`)
- Tracks connections between components
- Identifies HTTP calls, database connections, client instantiations
- Maps data flow through the application

**IntermediateModel** (`designmodelgenerator/core/IntermediateModel.java`)
- Internal representation holding:
  - Function models with dependencies
  - Service models with listeners
  - Connection metadata
  - Listener configurations

#### 5. Project Migration Tools

**MuleImporter** (`projectservice/core/MuleImporter.java`)
- Imports Mule project configurations
- Extracts integration flows
- Maps Mule components to Ballerina equivalents

**TibcoImporter** (`projectservice/core/TibcoImporter.java`)
- Imports TIBCO BusinessWorks projects
- Parses TIBCO process definitions
- Converts to Ballerina architecture

**MigrateToolInvokingUtil** (`projectservice/core/MigrateToolInvokingUtil.java`)
- Invokes migration tools via Ballerina tool infrastructure
- Executes bal tool commands for migration
- Captures migration results

**BalToolsUtil** (`projectservice/core/baltool/BalToolsUtil.java`)
- Utilities for working with Ballerina tools
- Tool manifest parsing
- Tool execution helpers

## Key Classes and Data Models

### Model Classes

**ArchitectureModel** (`core/ArchitectureModel.java`)
- Top-level model containing all architectural elements
- Fields:
  - `id`: Package identifier
  - `packageOrg`, `packageName`, `packageVersion`: Package metadata
  - `services`: Map of service models
  - `entities`: Map of entity models
  - `functionEntryPoint`: Main function entry point (if present)
  - `dependencies`: All connection dependencies
  - `diagnostics`: Compilation and analysis diagnostics
  - `hasDiagnosticErrors`: Error flag

**Service** (`model/service/Service.java`)
- Service declaration model
- Fields:
  - `id`, `elementId`, `name`, `basePath`, `filePath`
  - `resourceFunctions`: List of resource functions
  - `remoteFunctions`: List of remote functions
  - `connections`: Service-level connections
  - `displayAnnotation`: Visualization metadata
  - `sourceLocation`: Source code location

**ResourceFunction** (`model/service/ResourceFunction.java`)
- HTTP resource function model
- Fields:
  - `id`, `name`, `accessor` (GET, POST, etc.)
  - `path`: Resource path
  - `parameters`: Function parameters
  - `returnType`: Return type
  - `interactions`: Calls made within function

**Entity** (`model/entity/Entity.java`)
- Persist entity model
- Fields:
  - `elementId`, `id`, `name`
  - `attributes`: Entity fields
  - `associations`: Relationships to other entities
  - `displayAnnotation`, `sourceLocation`

**Attribute** (`model/entity/Attribute.java`)
- Entity attribute model
- Fields: `name`, `type`, `optional`, `defaultable`

**Association** (`model/entity/Association.java`)
- Entity relationship model
- Fields: `owner`, `entityName`, `associationType` (ONE_TO_ONE, ONE_TO_MANY, MANY_TO_MANY)

**FunctionEntryPoint** (`model/functionentrypoint/FunctionEntryPoint.java`)
- Main function model
- Fields:
  - `name`, `parameters`, `returnType`
  - `interactions`: Function calls and interactions
  - `displayAnnotation`, `sourceLocation`

**DesignModel** (`designmodelgenerator/core/model/DesignModel.java`)
- Design visualization model
- Fields:
  - `automation`: Main automation flow
  - `services`: List of services
  - `listeners`: List of listeners
  - `connections`: All connections

**Connection** (`model/service/Connection.java`)
- Connection/dependency model
- Fields:
  - `id`, `name`, `type`
  - `kind`: CONNECTION_KIND (HTTP_CLIENT, DB_CLIENT, etc.)

### Diagnostic Classes

**ArchitectureModelDiagnostic** (`diagnostics/ArchitectureModelDiagnostic.java`)
- Diagnostic message model
- Fields: `code`, `message`, `severity`, `location`, `file`

**DiagnosticMessage** (`diagnostics/DiagnosticMessage.java`)
- Pre-defined diagnostic messages
- Factory methods for common errors

**ArchitectureModelException** (`diagnostics/ArchitectureModelException.java`)
- Exception for architecture model generation failures

## Extension Points / APIs

This module provides the following APIs for external use:

### 1. ArchitectureModelBuilder API

```java
ArchitectureModelBuilder builder = new ArchitectureModelBuilder();
ArchitectureModel model = builder.constructComponentModel(ballerinaPackage);
```

### 2. DesignModelGenerator API

```java
DesignModelGenerator generator = new DesignModelGenerator(ballerinaPackage);
DesignModel designModel = generator.generate();
```

### 3. Migration Tool APIs

```java
// Mule migration
MuleImporter muleImporter = new MuleImporter();
ToolExecutionResult result = muleImporter.importProject(projectPath, outputPath);

// TIBCO migration
TibcoImporter tibcoImporter = new TibcoImporter();
ToolExecutionResult result = tibcoImporter.importProject(projectPath, outputPath);
```

## Dependencies

### Module Dependencies
- **model-generator-commons**: Shared utilities for model generation
- **langserver-commons**: Language server interfaces
- **ballerina-lang**: Language core for symbols and types
- **ballerina-parser**: Syntax tree API
- **ballerina-tools-api**: Compiler and project API
- **ballerina-runtime**: Runtime support
- **toml-parser**: TOML file parsing

### External Libraries
- **gson**: JSON serialization for model export

## Common Patterns

### 1. Visitor Pattern
- Extensive use for syntax tree traversal
- Each model generator has dedicated visitors
- Separates tree walking from model building

### 2. Builder Pattern
- Models use builder pattern for construction
- Example: `DesignModel.DesignModelBuilder`

### 3. Generator Pattern
- Specialized generators for each model type
- Common interface for generation workflow
- Delegates to visitors for tree analysis

### 4. Intermediate Representation
- Two-phase analysis: parse to intermediate, then to final model
- Allows complex dependency resolution
- Separates concerns between analysis and representation

### 5. Error Handling
- Graceful degradation on errors
- Collects all diagnostics for reporting
- Continues generation even with partial failures

### 6. Package-Level Analysis
- Analyzes entire packages, not single files
- Cross-module dependency tracking
- Semantic model for type resolution

## Development Guidelines

### Adding a New Model Generator

1. **Create Generator Class** extending `ModelGenerator`
2. **Implement Visitors** for relevant syntax nodes
3. **Define Model Classes** for output
4. **Integrate into ArchitectureModelBuilder**
5. **Add Unit Tests** with sample Ballerina code

### Working with Syntax Trees

```java
// Get semantic model and syntax tree
PackageCompilation compilation = PackageUtil.getCompilation(pkg);
SemanticModel semanticModel = compilation.getSemanticModel(moduleId);
SyntaxTree syntaxTree = module.document(documentId).syntaxTree();

// Visit nodes
MyNodeVisitor visitor = new MyNodeVisitor(semanticModel);
visitor.visit(syntaxTree.rootNode());
```

### Extracting Function Metadata

```java
// Get function symbol from semantic model
Optional<Symbol> symbol = semanticModel.symbol(functionNode);
if (symbol.isPresent() && symbol.get() instanceof FunctionSymbol funcSymbol) {
    // Extract parameters, return type, documentation
    FunctionTypeSymbol funcType = funcSymbol.typeDescriptor();
    // ... extract metadata
}
```

## Usage Examples

### Generate Architecture Model

```java
Package ballerinaPackage = project.currentPackage();
ArchitectureModelBuilder builder = new ArchitectureModelBuilder();
ArchitectureModel model = builder.constructComponentModel(ballerinaPackage);

// Access services
Map<String, Service> services = model.getServices();
for (Service service : services.values()) {
    System.out.println("Service: " + service.getName());
    for (ResourceFunction rf : service.getResourceFunctions()) {
        System.out.println("  " + rf.getAccessor() + " " + rf.getPath());
    }
}

// Access entities
Map<String, Entity> entities = model.getEntities();
for (Entity entity : entities.values()) {
    System.out.println("Entity: " + entity.getName());
    for (Attribute attr : entity.getAttributes()) {
        System.out.println("  " + attr.getName() + ": " + attr.getType());
    }
}
```

### Generate Design Model

```java
Package ballerinaPackage = project.currentPackage();
DesignModelGenerator generator = new DesignModelGenerator(ballerinaPackage);
DesignModel designModel = generator.generate();

// Serialize to JSON
Gson gson = new GsonBuilder().serializeNulls().create();
JsonObject json = (JsonObject) gson.toJsonTree(designModel);
String jsonString = gson.toJson(json);
```

## File Locations

- **Source**: `architecture-model-generator/modules/architecture-model-generator-core/src/main/java/`
  - `io/ballerina/architecturemodelgenerator/core/`: Architecture model generation
  - `io/ballerina/designmodelgenerator/core/`: Design model generation
  - `io/ballerina/projectservice/core/`: Project migration tools
- **Build**: `architecture-model-generator/modules/architecture-model-generator-core/build.gradle`

## Important Notes for AI Assistants

1. **Two Model Types**: This module generates TWO different model types:
   - **ArchitectureModel**: Service + Entity + Function models for architecture documentation
   - **DesignModel**: Design diagram model for visual workflow representation
2. **Package-Level Analysis**: Always work with entire packages, not individual files
3. **Semantic Model Required**: Use semantic model for type resolution and symbol lookup
4. **Visitor Pattern**: Use visitors for syntax tree traversal, don't manually walk trees
5. **Error Collection**: Collect all diagnostics, don't fail fast
6. **Module Iteration**: Iterate through all modules in package for complete analysis
7. **Connection Tracking**: Connections are tracked at multiple levels (service, function, module)
8. **Display Annotations**: Capture `@display` annotations for visualization metadata
9. **Source Locations**: Always capture source locations for IDE navigation
10. **Persist Entities**: Entity models specifically target Ballerina persist types

## Related Modules

- **architecture-model-generator-plugin**: Compiler plugin for build-time model generation
- **architecture-model-generator-ls-extension**: Language server extension for IDE integration
- **model-generator-commons**: Shared utilities and data models
- **langserver-core**: Language server core consuming these models
