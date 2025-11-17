# flow-model-generator-core

## Module Overview

**Purpose**: Core library for generating visual flow/workflow models of Ballerina code. Powers low-code/no-code experiences by providing node-based representation of Ballerina functions, enabling visual programming, AI-assisted development, and data flow analysis.

**Module Name**: `io.ballerina.flowmodelgenerator.core`

**Type**: Library module (no executable components)

## Key Responsibilities

- **Flow Model Generation**: Convert Ballerina function code to visual node-based models
- **Available Nodes Discovery**: Provide searchable catalog of functions, connectors, AI components
- **Node Template Generation**: Generate code templates for adding new nodes to flows
- **Source Code Generation**: Convert visual flow models back to Ballerina source code
- **Data Mapping**: Support visual data transformation between types
- **Expression Editor**: Provide intelligent expression editing with completion, diagnostics, semantic tokens
- **AI Integration**: Special support for AI/ML components (agents, vector stores, model providers)
- **Error Handling**: Generate error handling code patterns
- **OpenAPI Client Generation**: Generate Ballerina clients from OpenAPI specs

## Architecture

### Entry Points

**ModelGenerator** (`ModelGenerator.java:500+ lines`)
- Main entry point for flow model generation
- Method: `getFlowNode(LineRange, FlowNodeKind)`: Extract flow node at position
- Analyzes function body statements
- Converts syntax tree to flow model
- Returns: JSON representation of flow nodes

**AvailableNodesGenerator** (`AvailableNodesGenerator.java:300+ lines`)
- Generates catalog of available nodes for insertion
- Categories: Functions, Connectors, AI Components, Control Flow
- Searchable by keywords and filters
- Returns hierarchical category structure

**NodeTemplateGenerator** (`NodeTemplateGenerator.java:400+ lines`)
- Generates code templates for new nodes
- Method: `getNodeTemplate(Codedata, LinePosition)`: Create template for node type
- Provides placeholder values
- Handles imports and dependencies
- Returns code snippet with cursor positions

**SourceGenerator** (`SourceGenerator.java:600+ lines`)
- Converts flow model JSON back to Ballerina code
- Method: `getSourceCode(JsonObject, TextDocument)`: Generate code from flow
- Validates model structure
- Generates syntactically correct code
- Handles formatting and indentation

### Core Components

#### 1. Flow Model Generation

**ModelGenerator** (`ModelGenerator.java`)

**Workflow**:
1. Receive line range and node kind
2. Find enclosing syntax node
3. Analyze node type (function call, if/else, assignment, etc.)
4. Extract metadata (parameters, types, return values)
5. Build JSON flow node representation
6. Return model with properties and metadata

**Supported Node Types**:
- **Function Call**: Remote calls, client actions, utility functions
- **Variable Assignment**: Variable declarations and assignments
- **Control Flow**: If/else, while, foreach, match
- **Data Operations**: JSON/XML transformations, type conversions
- **Error Handling**: Check expressions, on-fail clauses
- **AI Components**: Agent calls, vector store operations, embeddings

**Flow Node Structure**:
```json
{
  "kind": "FUNCTION_CALL",
  "codedata": {
    "node": "function_call_node_id",
    "org": "ballerina",
    "module": "http",
    "symbol": "Client.get"
  },
  "properties": {
    "path": "/api/users",
    "return": "json"
  },
  "metadata": {
    "label": "HTTP GET",
    "description": "Fetch users from API"
  }
}
```

#### 2. Available Nodes System

**AvailableNodesGenerator** (`AvailableNodesGenerator.java`)

**Categories**:
- **Control Flow**: If, while, foreach, match, transaction
- **Functions**: Module functions, utility functions
- **Connectors**: HTTP, SQL, gRPC, Kafka clients
- **AI Components**: Agents, model providers, vector stores, embeddings
- **Data Operations**: Type conversions, JSON/XML operations
- **Error Handling**: Check, panic, trap

**Search Commands** (`search/` package):
- `FunctionSearchCommand`: Search general functions
- `ConnectorSearchCommand`: Search connector clients
- `TypeSearchCommand`: Search type definitions
- `ModelProviderSearchCommand`: Search AI model providers
- `VectorStoreSearchCommand`: Search vector stores
- `AgentSearchCommand`: Search AI agents
- `EmbeddingProviderSearchCommand`: Search embedding providers
- `DataLoaderSearchCommand`, `ChunkerSearchCommand`, etc.

**DefaultViewHolder** (`search/DefaultViewHolder.java`)
- Maintains default/recommended nodes
- Categorizes by use case
- Provides quick access to common operations

**RelevanceCalculator** (`search/RelevanceCalculator.java`)
- Calculates relevance scores for search results
- Considers keyword matches, popularity, context
- Ranks results for presentation

#### 3. Node Template System

**NodeTemplateGenerator** (`NodeTemplateGenerator.java`)

**Template Types**:
- **Function Call**: `functionName(param1, param2)`
- **Variable**: `Type varName = value;`
- **If Statement**: `if condition { ... }`
- **Foreach Loop**: `foreach item in collection { ... }`
- **Connector Init**: `http:Client client = check new (url);`
- **AI Components**: `ai:Agent agent = check new (config);`

**Placeholder Generation**:
- Uses `FunctionDataBuilder` for metadata
- Generates default values via `DefaultValueGeneratorUtil`
- Includes required imports
- Marks cursor positions

#### 4. Source Code Generation

**SourceGenerator** (`SourceGenerator.java`)

**Workflow**:
1. Parse flow model JSON
2. Validate node structure
3. Generate statements for each node
4. Handle data flow between nodes
5. Add necessary imports
6. Format code with proper indentation
7. Return TextEdit for LSP application

**Supported Transformations**:
- Flow nodes → Statement nodes
- Data mapping → Assignment expressions
- Control flow → If/while/foreach statements
- Error handling → Check expressions

#### 5. Data Mapping System

**DataMapManager** (`DataMapManager.java`)

**Purpose**: Visual data transformation between types

**Features**:
- Field-to-field mapping
- Type conversions
- Nested structure mapping
- Custom transformation functions

**Workflow**:
1. Analyze source and target types
2. Generate mapping configuration
3. Provide visual mapping interface
4. Generate transformation code

**Query Conversion**: Supports converting to query expressions for complex mappings

#### 6. Expression Editor

**Semantic Tokens** (`expressioneditor/semantictokens/SemanticTokenVisitor.java`)
- Provides syntax highlighting for expressions
- Categorizes tokens (keyword, variable, function, etc.)
- Supports LSP semantic token protocol

**Diagnostics** (`expressioneditor/diagnostics/`)
- Real-time error checking
- Type validation
- Undefined symbol detection

**Completion** (`expressioneditor/completion/`)
- Context-aware suggestions
- Symbol filtering by type
- Function signature help

**Type Information** (`TypesManager.java`)
- Extract type information for expressions
- Provide type hints
- Support type inference

#### 7. AI Component Support

**AI Search Commands** (in `search/` package):
- `AgentSearchCommand`: Find AI agents
- `ModelProviderSearchCommand`: Find LLM providers
- `VectorStoreSearchCommand`: Find vector databases
- `EmbeddingProviderSearchCommand`: Find embedding models
- `DataLoaderSearchCommand`: Find data loaders
- `ChunkerSearchCommand`: Find text chunkers
- `MemoryManagerSearchCommand`: Find memory managers
- `MemoryStoreSearchCommand`: Find memory stores
- `AgentToolSearchCommand`: Find agent tools
- `KnowledgeBaseSearchCommand`: Find knowledge bases

**AI Module Detection**:
- Uses `CommonUtils.isAi*()` methods
- Identifies AI types from ballerina/ai package
- Provides specialized templates for AI components

#### 8. Error Handling Generation

**ErrorHandlerGenerator** (`ErrorHandlerGenerator.java`)

**Patterns**:
- **Check expression**: `Type result = check riskyCall();`
- **On-fail clause**: `on fail error e { ... }`
- **Do-on-fail**: `do { ... } on fail error e { ... }`
- **Trap expression**: `Type|error result = trap riskyCall();`

**Generation**:
- Analyzes error-returning calls
- Suggests appropriate patterns
- Generates complete error handling code

#### 9. OpenAPI Client Generation

**OpenAPIClientGenerator** (`OpenAPIClientGenerator.java`)

**Purpose**: Generate Ballerina HTTP clients from OpenAPI specs

**Workflow**:
1. Parse OpenAPI specification
2. Generate client type definitions
3. Generate method stubs
4. Create initialization code
5. Add to project

#### 10. Copilot Integration

**CopilotContextGenerator** (`CopilotContextGenerator.java`)

**Purpose**: Provide context for AI code suggestions

**Features**:
- Extract visible symbols
- Provide type information
- Include function signatures
- Context for current position

**SuggestedComponentService** (`SuggestedComponentService.java`)

**Purpose**: AI-suggested component recommendations

**Workflow**:
- Analyze current code context
- Suggest relevant components
- Rank by relevance
- Provide usage examples

#### 11. Helper Services

**LocalIndexCentral** (`LocalIndexCentral.java`)
- Local database access for function/connector metadata
- Fast lookups without network calls
- Delegates to `DatabaseManager` from commons

**VisibleVariableTypesGenerator** (`VisibleVariableTypesGenerator.java`)
- Extracts variables in scope
- Provides type information
- Supports data mapping source selection

**DeleteNodeHandler** (`DeleteNodeHandler.java`)
- Handles node deletion from flows
- Updates dependent nodes
- Maintains data flow integrity

**EnclosedNodeFinder** (`EnclosedNodeFinder.java`)
- Finds nodes within a range
- Supports multi-node operations
- Used for bulk edits

## Key Data Models

**AvailableNode** (`model/AvailableNode.java`)
- Represents a node available for insertion
- Fields: `id`, `label`, `description`, `kind`, `icon`, `metadata`

**Category** (`model/Category.java`)
- Hierarchical category for nodes
- Fields: `name`, `label`, `items`, `subcategories`

**Codedata** (`model/Codedata.java`)
- References to source code elements
- Fields: `node`, `org`, `module`, `symbol`, `kind`

**Item** (`model/Item.java`)
- Represents an item in category
- Can be a node or subcategory

**Metadata** (`model/Metadata.java`)
- Additional node metadata
- Fields: `label`, `description`, `icon`, `tags`

**NodeKind** (`model/NodeKind.java`)
- Enum of supported node types
- VALUES: FUNCTION_CALL, IF, WHILE, FOREACH, ASSIGNMENT, MATCH, etc.

## Extension Points / APIs

### Main APIs

**Generate Flow Model**:
```java
ModelGenerator generator = new ModelGenerator(semanticModel, document, pkg);
JsonObject flowNode = generator.getFlowNode(lineRange, FlowNodeKind.FUNCTION_CALL);
```

**Get Available Nodes**:
```java
AvailableNodesGenerator generator = new AvailableNodesGenerator(semanticModel, document, pkg);
Category rootCategory = generator.generate(branch, offset);
```

**Generate Node Template**:
```java
NodeTemplateGenerator generator = new NodeTemplateGenerator(semanticModel, document, project);
String template = generator.getNodeTemplate(codedata, position);
```

**Generate Source Code**:
```java
SourceGenerator generator = new SourceGenerator(semanticModel, document, project);
List<TextEdit> edits = generator.getSourceCode(flowModelJson, textDocument);
```

## Dependencies

### Module Dependencies
- **model-generator-commons**: Shared utilities, database access, function metadata
- **langserver-commons**: LSP interfaces and contexts
- **ballerina-lang**: Language core
- **ballerina-parser**: Syntax tree API
- **ballerina-tools-api**: Compiler and project API

### External Libraries
- **gson**: JSON serialization
- **guava**: Caching and collections

## Common Patterns

### 1. Generator Pattern
- Specialized generators for each concern
- Common interface: analyze input, produce output
- Composable and testable

### 2. Search Command Pattern
- Each search type has dedicated command class
- Implements `SearchCommand` interface
- Encapsulates search logic and ranking

### 3. Visitor Pattern
- Syntax tree traversal for analysis
- Semantic token generation
- Flow node extraction

### 4. Template Method Pattern
- Base generators define workflow
- Subclasses customize specifics
- Code reuse and consistency

### 5. Builder Pattern
- Complex models built incrementally
- Node builders for each kind
- Category builders for hierarchies

### 6. Facade Pattern
- High-level APIs hide complexity
- Single entry points for features
- Simplified integration

## Development Guidelines

### Adding a New Node Kind

1. **Add to NodeKind enum**
   ```java
   public enum NodeKind {
       EXISTING_KIND,
       NEW_KIND  // Add here
   }
   ```

2. **Create Builder**
   ```java
   public class NewKindBuilder extends NodeBuilder {
       @Override
       public JsonObject build() {
           // Build node representation
       }
   }
   ```

3. **Update ModelGenerator**
   ```java
   case NEW_KIND:
       return new NewKindBuilder().build(node, semanticModel);
   ```

4. **Add Template**
   ```java
   // In NodeTemplateGenerator
   case NEW_KIND:
       return generateNewKindTemplate(codedata);
   ```

5. **Update SourceGenerator**
   ```java
   // Handle NEW_KIND in source generation
   ```

### Adding a Search Command

1. **Implement SearchCommand**
   ```java
   public class MySearchCommand implements SearchCommand {
       @Override
       public List<SearchResult> search(String query, SearchContext ctx) {
           // Search logic
       }
   }
   ```

2. **Register in AvailableNodesGenerator**
   ```java
   List<SearchCommand> commands = List.of(
       new ExistingCommand(),
       new MySearchCommand()
   );
   ```

3. **Add to Category**
   ```java
   Category category = new Category.Builder("My Category")
       .addSearchCommand(new MySearchCommand())
       .build();
   ```

## Usage Examples

### Generate Flow Model

```java
// Setup
SemanticModel semanticModel = compilation.getSemanticModel(moduleId);
Document document = module.document(documentId);
Package pkg = project.currentPackage();

// Generate model
ModelGenerator generator = new ModelGenerator(semanticModel, document, pkg);
LineRange range = LineRange.from("file.bal", 10, 0, 10, 30);
JsonObject flowNode = generator.getFlowNode(range, FlowNodeKind.FUNCTION_CALL);

// Use model
System.out.println(flowNode.get("kind"));
System.out.println(flowNode.get("properties"));
```

### Search Available Nodes

```java
AvailableNodesGenerator generator = new AvailableNodesGenerator(
    semanticModel, document, pkg
);

// Get all available nodes
Category rootCategory = generator.generate(Branch.MAIN, offset);

// Search for HTTP connectors
SearchCommand httpSearch = new ConnectorSearchCommand();
List<AvailableNode> httpNodes = httpSearch.search("http", context);
```

### Generate Code Template

```java
NodeTemplateGenerator generator = new NodeTemplateGenerator(
    semanticModel, document, project
);

Codedata codedata = new Codedata.Builder()
    .org("ballerina")
    .module("http")
    .symbol("Client.get")
    .build();

LinePosition position = LinePosition.from(10, 4);
String template = generator.getNodeTemplate(codedata, position);

// Returns: "json result = check httpClient->get(\"/api/users\");"
```

## File Locations

- **Source**: `flow-model-generator/modules/flow-model-generator-core/src/main/java/`
  - `io/ballerina/flowmodelgenerator/core/`: Core generators
  - `io/ballerina/flowmodelgenerator/core/search/`: Search commands
  - `io/ballerina/flowmodelgenerator/core/model/`: Data models
  - `io/ballerina/flowmodelgenerator/core/expressioneditor/`: Expression editor support
- **Build**: `flow-model-generator/modules/flow-model-generator-core/build.gradle`

## Important Notes for AI Assistants

1. **Visual Programming**: This module enables visual/low-code experiences
2. **Bidirectional**: Supports both code→model and model→code transformations
3. **AI First**: Special support for AI/ML components and patterns
4. **Search-Driven**: Heavy use of search for discoverability
5. **Template-Based**: Code generation via templates with placeholders
6. **Type-Aware**: Uses semantic model for type information
7. **Database-Backed**: Uses local database for fast metadata lookups
8. **Expression Editor**: Full language server features for expression editing
9. **Error Handling**: Generates idiomatic error handling patterns
10. **Context-Aware**: Suggestions and templates based on current context

## Performance Considerations

- **Database Caching**: Function metadata cached in SQLite
- **Lazy Loading**: Semantic models loaded on demand
- **Incremental Analysis**: Only analyze changed regions
- **Search Indexing**: Pre-indexed for fast search
- **Template Caching**: Common templates cached

## Related Modules

- **flow-model-generator-ls-extension**: LSP integration for IDE support
- **flow-model-index-generator**: Builds the search database
- **flow-model-central-client**: Fetches metadata from Ballerina Central
- **model-generator-commons**: Shared utilities and database access
- **langserver-core**: Language server using flow models
