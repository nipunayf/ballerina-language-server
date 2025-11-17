# sequence-model-generator-core

## Module Overview

**Purpose**: Core library for generating UML-style sequence diagram models from Ballerina code. Analyzes function call flows, interactions between participants (functions, workers, endpoints), and creates visual sequence diagram representations for documentation and comprehension.

**Module Name**: `io.ballerina.sequencemodelgenerator.core`

**Type**: Library module (no executable components)

## Key Responsibilities

- **Sequence Diagram Generation**: Convert Ballerina function code to UML sequence diagram models
- **Participant Analysis**: Identify and track participants (functions, workers, endpoints/clients)
- **Interaction Detection**: Detect method calls, remote calls, function calls between participants
- **Call Flow Tracking**: Track the sequence and flow of interactions in code
- **Visual Model Creation**: Generate structured data for rendering sequence diagrams

## Architecture

### Entry Points

**ModelGenerator** (`ModelGenerator.java:70 lines`)

**Main Entry Point**: Single static method for diagram generation

```java
public static Diagram getSequenceDiagramModel(Project project, LineRange lineRange,
                                               SemanticModel semanticModel)
```

**Workflow**:
1. Locate syntax node at specified line range (root participant)
2. Initialize ParticipantManager singleton
3. Analyze participant recursively to find interactions
4. Generate list of participants with their interactions
5. Return Diagram model

### Core Components

#### 1. ParticipantManager

**File**: `ParticipantManager.java`

**Pattern**: Singleton (per analysis session)

**Purpose**: Manages participant registry and tracks all participants in diagram

**Key Methods**:
- `initialize(semanticModel, project)`: Initialize new analysis session
- `getInstance()`: Get singleton instance
- `generateParticipant(semanticModel, node, moduleName)`: Analyze and register participant
- `getParticipants()`: Retrieve all discovered participants
- `getParticipantId(expression)`: Get or create participant ID for expression

**Responsibilities**:
- Maintain unique participant registry
- Prevent duplicate participants
- Assign unique IDs to participants
- Recursively discover new participants from interactions

#### 2. ParticipantAnalyzer

**File**: `ParticipantAnalyzer.java:87 lines`

**Pattern**: Visitor pattern for syntax tree traversal

**Purpose**: Analyzes syntax nodes to extract participant metadata

**Supported Node Types**:
- **FunctionDefinitionNode**: Functions become participants
- **ModuleVariableDeclarationNode**: Module-level endpoints (clients)
- **VariableDeclarationNode**: Local endpoints (clients)
- **CaptureBindingPatternNode**: Variable names extraction

**Output**: `Participant` object with:
- Unique ID (hash of location)
- Name (function/variable name)
- Kind (FUNCTION, WORKER, ENDPOINT)
- Module name
- Sequence nodes (interactions)
- Source location

#### 3. ParticipantBodyAnalyzer

**File**: `ParticipantBodyAnalyzer.java`

**Pattern**: Visitor pattern for statement analysis

**Purpose**: Analyzes function/worker body to extract interactions

**Detected Interaction Types**:
- **Remote Method Calls**: `client->methodName(args)`
- **Client Resource Access**: `client->/resource[arg]`
- **Function Calls**: `functionName(args)`
- **Worker Calls**: `value -> workerName`

**Control Flow Handling**:
- If/else statements
- While loops
- Return statements
- Variable declarations
- Assignment statements

**Builder Stack**: Maintains stack of SequenceNode.Builder for nested constructs

**Workflow**:
1. Visit statements in function body
2. Detect interactions (calls, sends)
3. Extract call parameters and target
4. Create Interaction nodes
5. Track control flow structures
6. Return list of SequenceNode objects

#### 4. Data Models

**Diagram** (`model/Diagram.java:35 lines`)

```java
public record Diagram(List<Participant> participants, LineRange location)
```

**Purpose**: Root model representing complete sequence diagram

**Fields**:
- `participants`: All participants with their interactions
- `location`: Source location of root participant

**Participant** (`model/Participant.java:50 lines`)

```java
public record Participant(String id, String name, ParticipantKind kind,
                          String moduleName, List<SequenceNode> nodes,
                          LineRange location)
```

**ParticipantKind**: FUNCTION, WORKER, ENDPOINT

**SequenceNode** (`model/SequenceNode.java`)

**Base Class**: Abstract base for all sequence diagram nodes

**Builder Pattern**: Provides Builder for constructing nodes

**Fields**:
- `kind`: NodeKind (INTERACTION, EXPRESSION, etc.)
- `label`: Display label
- `properties`: Map of node properties
- `location`: Source location

**Interaction** (`model/Interaction.java:94 lines`)

**Extends**: SequenceNode

**Purpose**: Represents interaction/call between participants

**InteractionType**:
- `ENDPOINT_CALL`: Remote call to client endpoint
- `FUNCTION_CALL`: Call to another function
- `METHOD_CALL`: Method call on object
- `WORKER_CALL`: Message send to worker

**Properties**:
- `params`: List of parameter values
- `name`: Method/function name
- `expr`: Expression being called
- `value`: Return value binding
- `resourcePath`: Resource path (for resource calls)
- `targetId`: ID of target participant

**Expression** (`model/Expression.java`)

**Purpose**: Represents expressions in interactions (parameters, return values)

**Factory Methods**: Create expressions from syntax nodes

#### 5. Utilities

**CommonUtil** (`CommonUtil.java`)

**Purpose**: Shared utility methods

**Key Methods**:
- `getFilePath(project, fileName, moduleId)`: Resolve file path
- `getSyntaxTree(project, filePath)`: Get syntax tree
- `getNode(syntaxTree, textRange)`: Find node at range
- `getModuleName(symbol)`: Extract module name

**Constants** (`Constants.java`)

**Purpose**: Shared constants

- `DEFAULT_MODULE`: Default module name

## Extension Points / APIs

### Main API

**Generate Sequence Diagram**:

```java
import io.ballerina.sequencemodelgenerator.core.ModelGenerator;
import io.ballerina.sequencemodelgenerator.core.model.Diagram;
import io.ballerina.tools.text.LineRange;

// Generate diagram for function at line range
Diagram diagram = ModelGenerator.getSequenceDiagramModel(
    project,
    lineRange,    // Function location
    semanticModel
);

// Access participants
List<Participant> participants = diagram.participants();

// Access interactions for each participant
for (Participant p : participants) {
    for (SequenceNode node : p.nodes()) {
        if (node instanceof Interaction interaction) {
            String target = interaction.targetId();
            String method = interaction.properties().get("name");
            // Render interaction
        }
    }
}
```

## Dependencies

### Module Dependencies
- **ballerina-lang**: Language core and semantic model
- **ballerina-parser**: Syntax tree API
- **ballerina-tools-api**: Compiler and project API

### External Libraries
- None (pure Ballerina compiler API usage)

## Common Patterns

### 1. Visitor Pattern
- ParticipantAnalyzer and ParticipantBodyAnalyzer extend NodeVisitor
- Override visit methods for specific node types
- Traverse syntax tree systematically

### 2. Singleton Pattern
- ParticipantManager uses singleton per analysis session
- Ensures single participant registry
- Must call initialize() before getInstance()

### 3. Builder Pattern
- SequenceNode.Builder and Interaction.Builder
- Fluent API for constructing complex nodes
- Incremental property setting

### 4. Record Pattern
- Data models use Java records for immutability
- Clear, concise data structures
- Automatic equals/hashCode/toString

### 5. Recursive Analysis
- ParticipantManager recursively discovers participants
- When interaction detected, target becomes new participant
- Prevents infinite recursion via participant registry

## Development Guidelines

### Adding Support for New Interaction Type

1. **Identify Syntax Node**: Determine which SyntaxKind represents the interaction

2. **Add Visitor Method** in ParticipantBodyAnalyzer:
   ```java
   @Override
   public void visit(NewCallNode newCallNode) {
       String targetId = ParticipantManager.getInstance()
           .getParticipantId(newCallNode.target());

       nodeBuilder = new Interaction.Builder(semanticModel)
           .interactionType(Interaction.InteractionType.NEW_CALL_TYPE)
           .targetId(targetId)
           .location(newCallNode);

       // Extract properties
       nodeBuilder.property("name", newCallNode.callName());

       appendNode();
   }
   ```

3. **Add InteractionType** to Interaction.InteractionType enum:
   ```java
   public enum InteractionType {
       ENDPOINT_CALL,
       FUNCTION_CALL,
       NEW_CALL_TYPE  // Add here
   }
   ```

### Adding Support for New Participant Type

1. **Add ParticipantKind**:
   ```java
   public enum ParticipantKind {
       FUNCTION,
       WORKER,
       ENDPOINT,
       NEW_KIND  // Add here
   }
   ```

2. **Add Visitor Method** in ParticipantAnalyzer:
   ```java
   @Override
   public void visit(NewParticipantNode node) {
       name = extractName(node);
       kind = ParticipantKind.NEW_KIND;
       location = node.location().lineRange();

       // Optionally analyze body
       ParticipantBodyAnalyzer bodyAnalyzer = new ParticipantBodyAnalyzer(semanticModel);
       node.body().accept(bodyAnalyzer);
       sequenceNodes = bodyAnalyzer.getSequenceNodes();
   }
   ```

## Usage Examples

### Generate Diagram from Function

```java
// Setup
Project project = // ... load project
SemanticModel semanticModel = // ... get semantic model
LineRange functionLocation = LineRange.from("main.bal", 10, 0, 20, 1);

// Generate
Diagram diagram = ModelGenerator.getSequenceDiagramModel(
    project,
    functionLocation,
    semanticModel
);

// Render
System.out.println("Participants:");
for (Participant p : diagram.participants()) {
    System.out.println("  " + p.name() + " (" + p.kind() + ")");
}

System.out.println("\nInteractions:");
for (Participant p : diagram.participants()) {
    for (SequenceNode node : p.nodes()) {
        if (node instanceof Interaction i) {
            System.out.println("  " + p.name() + " -> " +
                             findParticipantById(i.targetId()).name());
        }
    }
}
```

### Serialize to JSON

```java
import com.google.gson.Gson;

Diagram diagram = ModelGenerator.getSequenceDiagramModel(...);
Gson gson = new Gson();
String json = gson.toJson(diagram);

// Send to client for rendering
```

## File Locations

- **Source**: `sequence-model-generator/modules/sequence-model-generator-core/src/main/java/`
  - `io/ballerina/sequencemodelgenerator/core/`: Core generators
  - `io/ballerina/sequencemodelgenerator/core/model/`: Data models
- **Build**: `sequence-model-generator/modules/sequence-model-generator-core/build.gradle`

## Important Notes for AI Assistants

1. **Singleton Lifecycle**: ParticipantManager MUST be initialized before use
2. **Immutable Models**: All model objects are records (immutable)
3. **Recursive Discovery**: Participants discovered transitively through interactions
4. **Visitor Pattern**: Extend NodeVisitor to add new node type support
5. **Hash-Based IDs**: Participant IDs generated from location hash
6. **Module Tracking**: Each participant tracks its module for qualified names
7. **No Direct Rendering**: This module generates data models, not visual diagrams
8. **Semantic Model Required**: Needs semantic analysis for accurate type information
9. **Project Context**: Requires Project for resolving file paths
10. **JSON Serialization**: Models designed for JSON serialization to client

## Performance Considerations

- **Single Pass**: Each function analyzed once
- **Participant Deduplication**: Registry prevents duplicate analysis
- **Lazy Evaluation**: Only analyzes referenced participants
- **Memory Efficient**: Models are lightweight data structures

## Related Modules

- **sequence-model-generator-ls-extension**: LSP integration for IDE support
- **langserver-core**: Language server hosting the extension
- **VS Code Extension**: Client consuming sequence diagram models
