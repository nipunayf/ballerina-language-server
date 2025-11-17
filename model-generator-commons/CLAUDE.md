# model-generator-commons

## Module Overview

**Purpose**: Shared infrastructure and utilities for various model generators (flow-model, service-model, architecture-model, etc.) in the Ballerina Language Server. Provides common functionality for function metadata extraction, package resolution, database access, and testing.

**Module Name**: `io.ballerina.model.generator.commons`

**Type**: Library module

## Key Responsibilities

- **Function/Connector Metadata Extraction**: Extract signatures, parameters, return types from Ballerina symbols
- **Package Resolution**: Pull and resolve Ballerina packages from Central
- **Database Access**: Query pre-indexed function/connector metadata from SQLite databases
- **Type System Utilities**: Type signature generation, import statements, default values
- **Testing Infrastructure**: Base test classes for language server extensions
- **AI Module Detection**: Identify AI-related types (ModelProvider, VectorStore, etc.)

## Architecture

### Core Components

#### 1. FunctionDataBuilder

**File**: `src/main/java/io/ballerina/modelgenerator/commons/FunctionDataBuilder.java`

**Purpose**: Factory class to build `FunctionData` instances from Ballerina function symbols

**Capabilities**:
- Analyzes function symbols from semantic model or database index
- Extracts: name, description, parameters, return type, package info
- Handles: functions, connectors, AI providers, resource methods, class initializers
- Generates import statements for dependent types
- Builds parameter metadata with type information

**Usage Pattern** (Builder):
```java
FunctionData data = new FunctionDataBuilder()
    .semanticModel(semanticModel)
    .functionSymbol(functionSymbol)
    .moduleInfo(moduleInfo)
    .parentSymbol(parentSymbol)      // For class methods
    .userModuleInfo(userModuleInfo)  // For import generation
    .project(project)
    .lsClientLogger(logger)
    .build();
```

**Parameters**:
- `semanticModel`: Semantic analysis results
- `functionSymbol`: Function to analyze
- `moduleInfo`: Module containing function
- `parentSymbol`: Parent class/connector (optional)
- `userModuleInfo`: User's module for import statement generation
- `project`: Current project
- `lsClientLogger`: Client logger for notifications

**Returns**: `FunctionData` with complete metadata

#### 2. DatabaseManager

**File**: `src/main/java/io/ballerina/modelgenerator/commons/DatabaseManager.java`

**Purpose**: Manages SQLite database (`central-index.sqlite`) for function/connector metadata

**Pattern**: Singleton

**Key Methods**:
- `getInstance()`: Get singleton instance
- `getPackage(org, packageName, version)`: Get package info
- `getFunctions(org, packageName, moduleName, version, limit, offset)`: Query functions
- `getFunction(org, packageName, moduleName, functionName, kind, version)`: Get specific function
- `getFunctionParameters(functionId)`: Get function parameters
- `getConnectorMethods(org, packageName, module, className, version)`: Get connector actions
- `searchFunctions(query, limit, offset, orgFilter)`: Search with pagination

**Database Schema** (from flow-model-index-generator):
- **Package**: package_id, package_name, module_name, org, version, keywords
- **Function**: function_id, kind, name, description, package_id, return_type, resource_path, etc.
- **Parameter**: parameter_id, name, description, type, kind, function_id, import_statements
- **ParameterMemberType**: member_id, type, kind, parameter_id, package_identifier
- **FunctionConnector**: function_id, connector_id (junction table)

#### 3. ServiceDatabaseManager

**File**: `src/main/java/io/ballerina/modelgenerator/commons/ServiceDatabaseManager.java`

**Purpose**: Manages SQLite database (`service-index.sqlite`) for service-specific metadata

**Pattern**: Singleton

**Key Methods**:
- `getInstance()`: Get singleton
- `getListeners(org, packageName, moduleName, version)`: Retrieve listener info
- `getServiceDeclarations(org, packageName, moduleName, version)`: Get service metadata
- `getServiceInitProperties(listenerKind)`: Get initialization properties
- `getServiceTypes(org, packageName, moduleName, version)`: Get service types
- `getServiceFunctions(serviceTypeId)`: Get functions for service type

#### 4. SearchDatabaseManager

**File**: `src/main/java/io/ballerina/modelgenerator/commons/SearchDatabaseManager.java`

**Purpose**: Full-text search across functions, connectors, types using SQLite FTS5

**Pattern**: Singleton

**Key Methods**:
- `getInstance()`: Get singleton
- `searchFunctions(query, packageFilter, limit, offset)`: Search functions
- `searchConnectors(query, packageFilter, limit, offset)`: Search connectors
- `searchTypes(query, packageFilter, limit, offset)`: Search types
- Sanitizes queries for security (SQL injection prevention)
- BM25 ranking for relevance scoring

#### 5. CommonUtils

**File**: `src/main/java/io/ballerina/modelgenerator/commons/CommonUtils.java` (1000+ lines)

**Purpose**: Comprehensive utility class with type, import, and module helpers

**Key Methods**:

**Type Signatures**:
- `getTypeSignature(semanticModel, typeSymbol, ignoreError, moduleInfo)`: Convert type to string
- `getRawType(type)`: Extract raw type from complex types
- `getTypeNameFromSymbol(typeSymbol, moduleInfo, includeModulePrefix)`: Get type name

**Import Statements**:
- `getImportStatements(typeSymbol, moduleInfo)`: Generate import statement for type
- `getImportModulePrefix(typeSymbol, moduleInfo)`: Get module prefix for qualified name

**Module/Package Utilities**:
- `getModuleInfo(symbol)`: Extract ModuleInfo from symbol
- `getPackageIdentifier(org, packageName)`: Format package identifier
- `getModulePrefix(packageName)`: Get default module prefix

**AI Module Detection**:
- `isAiModelProvider(typeSymbol)`: Check if type is AI model provider
- `isAiVectorStore(typeSymbol)`: Check for vector store
- `isAiEmbeddingProvider(typeSymbol)`: Check for embedding provider
- `isAiDataLoader(typeSymbol)`, `isAiChunker(typeSymbol)`, etc.

**LSP Conversions**:
- `toRange(lineRange)`: LineRange to LSP Range
- `toPosition(linePosition)`: LinePosition to LSP Position

**Diagnostic Creation**:
- `getDiagnostics(compilation)`: Create LSP diagnostics from Ballerina diagnostics

**Type Checking**:
- `isSubtypeOf(typeSymbol, otherTypeSymbol)`: Subtype relationship check

#### 6. PackageUtil

**File**: `src/main/java/io/ballerina/modelgenerator/commons/PackageUtil.java`

**Purpose**: Package resolution and semantic model retrieval from Ballerina Central

**Key Methods**:
- `getSemanticModel(moduleInfo)`: Get semantic model for module (pulls if needed)
- `getModulePackage(project, org, packageName, version)`: Resolve package
- `isModuleUnresolved(org, packageName, version)`: Check if module needs pulling
- `pullPackages(project, packages, logger)`: Pull multiple packages from Central
- Thread-safe compilation access with locks
- Creates temporary sample projects for resolution

**Pattern**: Static utility methods with internal locking

#### 7. DefaultValueGeneratorUtil

**File**: `src/main/java/io/ballerina/modelgenerator/commons/DefaultValueGeneratorUtil.java`

**Purpose**: Generates default values for all Ballerina types

**Handles**:
- **Primitives**: int → 0, string → "", boolean → false, float → 0.0, decimal → 0.0d
- **Collections**: array → [], tuple → [val1, val2], map → {}, table → table []
- **Structured**: record → {field: value}, object → object {}
- **Special**: error → error("error"), nil → (), xml → xml ``
- **Complex**: union (first type), intersection (all constraints), stream, future

**Method**: `getDefaultValueForType(typeSymbol, semanticModel, moduleInfo, visited)`
- Recursive processing for nested types
- Cycle detection via visited set

### Data Models

#### FunctionData

**File**: `src/main/java/io/ballerina/modelgenerator/commons/FunctionData.java`

**Fields**:
- `id`: Function identifier (org:package:module:functionName)
- `name`: Function name
- `description`: Documentation
- `returnType`: Return type signature
- `org`, `packageName`, `module`, `version`: Package information
- `parameters`: Map<String, ParameterData>
- `resourcePath`: Resource path (for resource methods)
- `functionKind`: Kind (FUNCTION, CONNECTOR, LISTENER, AI_CLASS, etc.)
- `isErrorReturn`: Whether function can return error
- `inferredReturnType`: Inferred return type from body
- `importStatements`: Required imports

#### ParameterData

**File**: `src/main/java/io/ballerina/modelgenerator/commons/ParameterData.java`

**Fields**:
- `name`: Parameter name
- `description`: Parameter documentation
- `type`: Type signature
- `kind`: REQUIRED, DEFAULTABLE, REST, INCLUDED_RECORD
- `placeholder`: Placeholder value for code generation
- `defaultValue`: Default value (if defaultable)
- `label`: Display label
- `optional`: Whether optional
- `importStatements`: Required imports for type
- `typeMember`: Type member information
- `packageIdentifier`: Package for type

#### ModuleInfo

**File**: `src/main/java/io/ballerina/modelgenerator/commons/ModuleInfo.java`

**Record Class**:
```java
public record ModuleInfo(String org, String packageName, String moduleName, String version) {
    // Factory methods
    static ModuleInfo from(Symbol symbol)
    static ModuleInfo from(PackageDescriptor descriptor, String moduleName)
    static ModuleInfo from(ModuleSymbol moduleSymbol)
}
```

#### ServiceDeclaration

**File**: `src/main/java/io/ballerina/modelgenerator/commons/ServiceDeclaration.java`

**Fields**:
- `displayName`: Service display name
- `serviceTypeDescriptors`: Service type information
- `resourcePaths`: Available resource paths
- `listenerKind`: Kind of listener (HTTP, GRAPHQL, etc.)

### Testing Infrastructure

#### AbstractLSTest

**File**: `src/main/java/io/ballerina/modelgenerator/commons/AbstractLSTest.java`

**Purpose**: Base class for language server extension tests

**Features**:
- TestNG integration with data providers
- JSON comparison utilities
- LSP notification handling
- Config-based testing framework
- File path utilities for test resources

**Usage**:
```java
public class MyTest extends AbstractLSTest {
    @Test(dataProvider = "data-provider")
    public void testFeature(JsonObject config) {
        // Test implementation
    }

    @DataProvider(name = "data-provider")
    public Object[][] dataProvider() {
        return loadTestConfigs("test-configs");
    }
}
```

## Common Usage Patterns

### Pattern 1: Index-First, Semantic-Second

**Rationale**: Database index is fast, semantic analysis is slow

```java
// Try database index first
Optional<FunctionData> indexed = DatabaseManager.getInstance()
    .getFunction(org, packageName, module, functionName, kind, version);

if (indexed.isPresent()) {
    return indexed.get();
}

// Fall back to semantic analysis
SemanticModel model = PackageUtil.getSemanticModel(moduleInfo);
return new FunctionDataBuilder()
    .semanticModel(model)
    .functionSymbol(symbol)
    .build();
```

### Pattern 2: Package Resolution with User Notification

```java
if (PackageUtil.isModuleUnresolved(org, name, version)) {
    lsClientLogger.notify(MessageType.Info, "Pulling module " + org + "/" + name);

    Optional<Package> pkg = PackageUtil.getModulePackage(project, org, name, version);

    if (pkg.isEmpty()) {
        lsClientLogger.notify(MessageType.Error, "Failed to pull module");
        return Optional.empty();
    }

    lsClientLogger.notify(MessageType.Info, "Module pulled successfully");
}
```

### Pattern 3: Type Signature with Imports

```java
// Get type signature (simplified form)
String typeSignature = CommonUtils.getTypeSignature(
    semanticModel,
    typeSymbol,
    false,  // don't ignore errors
    moduleInfo
);

// Generate required import statements
Optional<String> imports = CommonUtils.getImportStatements(typeSymbol, moduleInfo);

// Use in code generation
if (imports.isPresent()) {
    code.append(imports.get()).append("\n");
}
code.append("function foo() returns ").append(typeSignature).append(" { ... }");
```

### Pattern 4: Parameter Extraction with Documentation

```java
Map<String, ParameterData> parameters = new LinkedHashMap<>();

// Get parameter documentation from function
Map<String, String> paramDocs = functionSymbol.documentation()
    .map(Documentation::parameterMap)
    .orElse(Map.of());

// Extract parameters from function type
functionTypeSymbol.params().ifPresent(paramList ->
    paramList.forEach(param -> {
        ParameterData paramData = new ParameterData(
            param.getName().orElse(""),
            paramDocs.get(param.getName().orElse("")),
            CommonUtils.getTypeSignature(semanticModel, param.typeDescriptor(), false, moduleInfo),
            determineKind(param),
            // ... other fields
        );
        parameters.put(paramData.name(), paramData);
    })
);
```

### Pattern 5: AI Module Detection

```java
// Check if a type is an AI component
if (CommonUtils.isAiModelProvider(typeSymbol)) {
    // Handle as model provider
} else if (CommonUtils.isAiVectorStore(typeSymbol)) {
    // Handle as vector store
} else if (CommonUtils.isAiAgent(typeSymbol)) {
    // Handle as agent
}
```

## Database Singleton Access

All database managers are singletons:

```java
DatabaseManager db = DatabaseManager.getInstance();
ServiceDatabaseManager serviceDb = ServiceDatabaseManager.getInstance();
SearchDatabaseManager searchDb = SearchDatabaseManager.getInstance();
```

## Dependencies

### Core Dependencies
- **ballerina-lang**: Language core for type system
- **ballerina-tools-api**: Compiler API for semantic analysis
- **org.eclipse.lsp4j**: LSP protocol types
- **sqlite-jdbc**: SQLite database access
- **guava**: Caching and utilities
- **testng**: Testing framework

## Usage by Model Generators

### Service Model Generator
- Uses `ServiceDatabaseManager` for listener/service metadata
- Uses `CommonUtils` for type signatures and imports
- Uses `FunctionDataBuilder` for service method metadata

### Flow Model Generator
- Uses `PackageUtil` for AI module resolution
- Uses `CommonUtils` for AI module detection
- Uses `FunctionDataBuilder` for connector/AI class initializers
- Uses `DatabaseManager` for function search
- Uses `SearchDatabaseManager` for fast search

### Architecture Model Generator
- Uses `PackageUtil` for package resolution
- Uses `CommonUtils` for type analysis and imports
- Uses `FunctionDataBuilder` for function metadata

### GraphQL Model Generator
- Uses `DatabaseManager` for GraphQL connector metadata
- Uses `CommonUtils` for type operations

## File Locations

- **Source**: `model-generator-commons/src/main/java/io/ballerina/modelgenerator/commons/`
- **Build**: `model-generator-commons/build.gradle`

## Important Notes for AI Assistants

1. **Index-First Strategy**: Always try database lookup before semantic analysis
2. **Singleton Access**: Use `getInstance()` for all database managers
3. **Package Pulling**: Use `PackageUtil` with user notifications, not direct Ballerina APIs
4. **Import Generation**: Use `CommonUtils.getImportStatements()`, don't manually generate
5. **Type Signatures**: Use `CommonUtils.getTypeSignature()` for consistent formatting
6. **AI Detection**: Use provided utility methods, don't check types manually
7. **Thread Safety**: `PackageUtil` has internal locking, safe for concurrent use
8. **Cycle Detection**: `DefaultValueGeneratorUtil` handles recursive types
9. **Module Info**: Use factory methods on `ModuleInfo` for consistent creation
10. **Testing**: Extend `AbstractLSTest` for all language server extension tests

## Best Practices

1. **Always notify users** when pulling packages (network operation)
2. **Handle Optional properly** - all database queries return Optional
3. **Use builders** for complex objects (FunctionData, ParameterData)
4. **Leverage caching** - databases are in-memory caches
5. **Check version compatibility** when resolving packages
6. **Sanitize search queries** - use SearchDatabaseManager methods, not raw SQL
7. **Document parameters** - extract from function documentation
8. **Generate proper imports** - use CommonUtils, not string manipulation
9. **Handle errors gracefully** - return empty/default rather than crash
10. **Test with AbstractLSTest** - config-based testing for consistency

## Related Modules

- **flow-model-generator**: Heavy user of all utilities
- **architecture-model-generator**: Uses package resolution and type utilities
- **service-model-generator**: Uses service database and function extraction
- **flow-model-index-generator**: Creates the databases used by this module
- **graphql-model-generator**: Uses database access and type utilities
