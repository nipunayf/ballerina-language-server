# diagram-util

## Module Overview

**Purpose**: Utility library for generating visual diagram representations of Ballerina source code. This module converts Ballerina syntax trees into JSON format with semantic information for visualization purposes, and provides connector metadata extraction capabilities for generating connector documentation and API references.

**Module Name**: `io.ballerina.language-server-commons.extension`

**Type**: Utility library (no executable components)

**Size**: 46 Java source files

## Key Responsibilities

- **Syntax Tree to JSON Conversion**: Transform Ballerina syntax trees into structured JSON with type information
- **Semantic Model Integration**: Enrich JSON output with semantic information from the Ballerina compiler
- **Connector Metadata Generation**: Extract connector metadata from Ballerina client classes for documentation
- **Diagram Visualization Support**: Provide data structures for rendering visual diagrams of Ballerina code
- **Type Information Extraction**: Capture complex type information for visualization and analysis

## Architecture

### Entry Points

**DiagramUtil** (`DiagramUtil.java:123 lines`)
- Main utility class for diagram generation
- Method: `getSyntaxTreeJSON(Document, SemanticModel)`: Convert document syntax tree to JSON
- Method: `getSyntaxTreeJSON(NonTerminalNode, SemanticModel)`: Convert specific node to JSON
- Method: `getSyntaxTreeJSON(NonTerminalNode)`: Convert node to JSON without semantic model
- Method: `getClassDefinitionSyntaxJson(ClassDefinitionNode, SemanticModel)`: Get class-specific JSON
- Method: `getTypeDefinitionSyntaxJson(TypeDefinitionNode, SemanticModel)`: Get type definition JSON
- Returns: JsonElement containing enriched syntax tree representation

**ConnectorGenerator** (`connector/generator/ConnectorGenerator.java`)
- Connector metadata extraction from Ballerina projects
- Method: `getProjectConnectors(Project, boolean, String)`: Extract all connectors from project
- Parameters:
  - `project`: Ballerina Project to analyze
  - `detailed`: Include full metadata (functions, types, documentation)
  - `query`: Filter connector list by name
- Returns: List of Connector objects with complete metadata

### Core Components

#### 1. Syntax Tree Map Generator

**SyntaxTreeMapGenerator** (`SyntaxTreeMapGenerator.java:900+ lines`)
- NodeTransformer that converts syntax tree nodes to JSON
- Extends `NodeTransformer<JsonElement>`
- Maintains context of visible endpoints and variables during traversal
- Key capabilities:
  - Transforms all syntax node types to JSON
  - Enriches nodes with semantic type information
  - Tracks visible symbols (endpoints, variables) per scope
  - Captures diagnostics and source locations
  - Preserves minutiae (whitespace, comments)

**Key Fields**:
- `semanticModel`: Semantic model for type resolution
- `visibleEpsForEachBlock`: Visible endpoints per block scope
- `visibleEpsForModule`: Module-level visible endpoints
- `visibleEpsForClass`: Class-level visible endpoints

**Transformation Process**:
1. Visit syntax tree nodes using visitor pattern
2. Extract structural information from each node
3. Query semantic model for type information
4. Build JSON representation with enriched data
5. Maintain scope information for symbol visibility
6. Handle special nodes (services, classes, functions)

#### 2. Syntax Tree Diagnostics Utility

**SyntaxTreeDiagnosticsUtil** (`SyntaxTreeDiagnosticsUtil.java`)
- Extracts and formats diagnostics from syntax trees
- Converts compiler diagnostics to JSON format
- Provides diagnostic information for visualization

#### 3. Connector Model System

**ConnectorGenerator** (`connector/generator/ConnectorGenerator.java`)
- Analyzes Ballerina projects to find client classes
- Extracts connector functions, types, and documentation
- Uses docerina integration for documentation generation
- Filters public client classes from modules
- Processes display annotations for visualization

**Key Features**:
- Identifies public client classes
- Extracts init function parameters
- Collects remote and resource functions
- Generates connector display metadata
- Processes function documentation
- Extracts type information for parameters and returns

#### 4. Connector Models

**Location**: `connector/models/`

**Connector** (`connector/models/connector/Connector.java`)
- Top-level connector representation
- Fields:
  - `name`: Connector name
  - `displayName`: Display annotation name
  - `description`: Documentation string
  - `orgName`, `moduleName`, `version`: Package metadata
  - `functions`: List of connector functions
  - `types`: Map of reference types used
  - `icon`: Connector icon path
  - `iconUrl`: URL to connector icon

**Function** (`connector/models/connector/Function.java`)
- Connector function metadata
- Fields:
  - `name`: Function name
  - `qualifiers`: Function qualifiers (remote, resource, etc.)
  - `parameters`: Function parameters
  - `returnType`: Return type
  - `documentation`: Function documentation
  - `displayAnnotation`: Display metadata

**ModuleInfo** (`connector/models/connector/ModuleInfo.java`)
- Module metadata for connectors
- Contains package organization, name, version

**Reference Types** (`connector/models/connector/reftypes/`)
- Type hierarchy for complex types:
  - `RefType`: Base type interface
  - `RefRecordType`: Record type definitions
  - `RefUnionType`: Union types
  - `RefArrayType`: Array types
  - `RefMapType`: Map types
  - `RefTupleType`: Tuple types
  - `RefEnumType`: Enum types
  - `RefConstType`: Constant types

**Type System** (`connector/models/connector/types/`)
- Specialized type representations:
  - `PathParamType`: Path parameter types for resources
  - Various primitive and complex type models

**BalaFile** (`connector/models/BalaFile.java`)
- Represents a .bala (Ballerina archive) file
- Contains connector list from archive

**Error** (`connector/models/Error.java`)
- Error model for connector generation failures

#### 5. Generator Utilities

**GeneratorUtils** (`connector/generator/GeneratorUtils.java`)
- Utility methods for connector generation
- Helper functions for:
  - Extracting documentation from markdown
  - Processing annotations
  - Type conversions
  - Name formatting

## Key Utilities

### DiagramUtil Methods

```java
// Get JSON for entire document
JsonElement getSyntaxTreeJSON(Document srcFile, SemanticModel semanticModel)

// Get JSON for specific node with semantic model
JsonElement getSyntaxTreeJSON(NonTerminalNode node, SemanticModel semanticModel)

// Get JSON for node without semantic model
JsonElement getSyntaxTreeJSON(NonTerminalNode node)

// Get JSON for class definition
JsonElement getClassDefinitionSyntaxJson(ClassDefinitionNode classDefinitionNode,
                                        SemanticModel semanticModel)

// Get JSON for type definition
JsonElement getTypeDefinitionSyntaxJson(TypeDefinitionNode typeDefinitionNode,
                                       SemanticModel semanticModel)
```

### ConnectorGenerator Methods

```java
// Extract all connectors from project
List<Connector> getProjectConnectors(Project project, boolean detailed, String query)
```

## Extension Points / APIs

This module provides library APIs (no SPI extension points):

### 1. Diagram JSON Generation API

```java
// Generate diagram JSON for a document
SemanticModel semanticModel = compilation.getSemanticModel(moduleId);
Document document = module.document(documentId);
JsonElement diagramJson = DiagramUtil.getSyntaxTreeJSON(document, semanticModel);
```

### 2. Connector Metadata Extraction API

```java
// Extract connectors from project
Project project = ...;
List<Connector> connectors = ConnectorGenerator.getProjectConnectors(
    project,
    true,    // detailed - include full metadata
    null     // query - no filtering
);

// Access connector metadata
for (Connector connector : connectors) {
    String name = connector.getName();
    List<Function> functions = connector.getFunctions();
    Map<String, Type> types = connector.getTypes();
}
```

## Dependencies

### Module Dependencies
- **ballerina-lang**: Language core for symbols and types
- **ballerina-parser**: Syntax tree API
- **ballerina-tools-api**: Compiler and project API
- **central-client**: Ballerina Central integration
- **docerina**: Documentation generation
- **formatter-core**: Code formatting support
- **toml-parser**: TOML parsing

### External Libraries
- **gson**: JSON serialization and deserialization
- **commons-lang3**: Apache Commons utilities

## Common Patterns

### 1. Visitor Pattern
- SyntaxTreeMapGenerator extends NodeTransformer
- Visits each node type with dedicated transform methods
- Returns JsonElement for each node

### 2. Semantic Enrichment
- Always uses semantic model when available
- Queries symbol information for type details
- Enriches JSON with semantic data

### 3. Scope Tracking
- Maintains visibility lists per scope level
- Tracks endpoints and variables visible at each level
- Provides context for diagram rendering

### 4. Null Safety
- Graceful handling of null semantic models
- Try-catch blocks return empty JsonObject on errors
- Never crashes on malformed input

### 5. Reflection-based Traversal
- Uses reflection to handle different node types
- Dynamically invokes transform methods
- Handles unknown node types gracefully

## Development Guidelines

### Using DiagramUtil for Visualization

1. **Obtain semantic model and document**
2. **Call getSyntaxTreeJSON with both**
3. **Parse returned JsonElement**
4. **Extract visualization data from JSON structure**

```java
// Example: Generate diagram JSON
PackageCompilation compilation = project.currentPackage()
    .getCompilation();
SemanticModel semanticModel = compilation.getSemanticModel(moduleId);
Document document = module.document(documentId);

JsonElement json = DiagramUtil.getSyntaxTreeJSON(document, semanticModel);

// JSON contains:
// - "kind": Node type
// - "source": Source code text
// - "position": Line/column information
// - "typeData": Semantic type information (when available)
// - "visibleEndpoints": Visible client/service variables
// - Child nodes with same structure
```

### Extracting Connector Metadata

```java
// Load project
Project project = ProjectLoader.loadProject(projectPath);

// Extract connectors
List<Connector> connectors = ConnectorGenerator.getProjectConnectors(
    project,
    true,  // Include detailed metadata
    null   // No query filter
);

// Process connectors
for (Connector connector : connectors) {
    System.out.println("Connector: " + connector.getName());

    // Access functions
    for (Function func : connector.getFunctions()) {
        System.out.println("  Function: " + func.getName());
        System.out.println("    Params: " + func.getParameters());
        System.out.println("    Return: " + func.getReturnType());
    }

    // Access types
    for (Map.Entry<String, Type> entry : connector.getTypes().entrySet()) {
        System.out.println("  Type: " + entry.getKey());
    }
}
```

### Adding New Connector Type Support

1. **Create new RefType implementation** in `connector/models/connector/reftypes/`
2. **Implement type-specific fields and methods**
3. **Update GeneratorUtils** to handle new type
4. **Add type conversion logic** in connector generator

## Usage Examples

### Example 1: Generate Syntax Tree JSON

```java
import org.ballerinalang.diagramutil.DiagramUtil;
import com.google.gson.JsonElement;

// From a document
JsonElement json = DiagramUtil.getSyntaxTreeJSON(document, semanticModel);

// From a specific node (e.g., a function)
FunctionDefinitionNode functionNode = ...;
JsonElement funcJson = DiagramUtil.getSyntaxTreeJSON(functionNode, semanticModel);

// Without semantic model (structural only)
JsonElement structuralJson = DiagramUtil.getSyntaxTreeJSON(node);
```

### Example 2: Extract Connector Information

```java
import org.ballerinalang.diagramutil.connector.generator.ConnectorGenerator;
import org.ballerinalang.diagramutil.connector.models.connector.Connector;

Project project = ...;

// Get all connectors with full details
List<Connector> allConnectors = ConnectorGenerator.getProjectConnectors(
    project, true, null
);

// Get filtered connectors (by name query)
List<Connector> filteredConnectors = ConnectorGenerator.getProjectConnectors(
    project, true, "http"
);

// Get minimal connector info (no detailed metadata)
List<Connector> minimalInfo = ConnectorGenerator.getProjectConnectors(
    project, false, null
);
```

## File Locations

- **Source**: `misc/diagram-util/src/main/java/org/ballerinalang/diagramutil/`
  - `DiagramUtil.java`: Main utility class
  - `SyntaxTreeMapGenerator.java`: Syntax tree to JSON transformer
  - `SyntaxTreeDiagnosticsUtil.java`: Diagnostics extraction
  - `connector/`: Connector metadata generation subsystem
- **Build**: `misc/diagram-util/build.gradle`

## Important Notes for AI Assistants

1. **Two Main Capabilities**: This module has TWO distinct features:
   - Syntax tree to JSON conversion for diagram visualization
   - Connector metadata extraction for documentation
2. **Semantic Model Optional**: Can work with or without semantic model, but richer with it
3. **JSON Output Structure**: Output JSON mirrors syntax tree structure with added semantic data
4. **Visitor Pattern**: Uses NodeTransformer pattern for tree traversal
5. **Error Resilience**: Never throws exceptions to caller, returns empty JsonObject on errors
6. **Scope Tracking**: Maintains three levels of visible endpoint tracking (block, module, class)
7. **Connector Detection**: Only extracts public client classes (must have `public` and `client` qualifiers)
8. **Documentation Integration**: Uses docerina for extracting and formatting documentation
9. **Type System**: Rich type model for representing complex Ballerina types in connectors
10. **Position Information**: Captures line ranges and positions for IDE navigation

## Related Modules

- **langserver-core**: Primary consumer for diagram generation
- **Architecture/flow/sequence model generators**: Use this for diagram data
- **docerina**: Documentation generation integration
- **central-client**: Ballerina Central connectivity information

## Exception Classes

**JSONGenerationException** (`JSONGenerationException.java`)
- Exception for JSON generation failures
- Extends RuntimeException
- Rarely thrown due to error handling strategy
