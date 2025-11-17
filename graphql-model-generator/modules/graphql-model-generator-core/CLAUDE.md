# graphql-model-generator-core

## Module Overview

**Purpose**: Core library for generating visual GraphQL schema models from Ballerina GraphQL service code. Analyzes GraphQL services to extract types, queries, mutations, subscriptions, and their relationships, creating structured models for visual schema designers and documentation tools.

**Module Name**: `io.ballerina.graphqlmodelgenerator.core`

**Type**: Library module (no executable components)

## Key Responsibilities

- **GraphQL Schema Model Generation**: Convert Ballerina GraphQL services to visual schema models
- **Type Analysis**: Extract GraphQL types (Object, Interface, Union, Enum, etc.)
- **Service Analysis**: Analyze service classes and resource methods
- **Resolver Detection**: Identify query, mutation, and subscription resolvers
- **Field Hierarchy**: Build nested object type hierarchies
- **Introspection Support**: Include GraphQL introspection types
- **Schema Validation**: Leverage Ballerina GraphQL compiler for schema validation

## Architecture

### Entry Points

**ModelGenerator** (`ModelGenerator.java`)

**Main Entry Point**:
```java
public GraphqlModel getGraphqlModel(Project project, LineRange position,
                                     SemanticModel semanticModel)
    throws GraphqlModelGenerationException
```

**Parameters**:
- `project`: Ballerina project containing GraphQL service
- `position`: LineRange of service declaration
- `semanticModel`: Semantic model for type analysis

**Returns**: GraphqlModel containing complete schema representation

**Workflow**:
1. Locate service declaration at position
2. Extract GraphQL Schema object via Ballerina GraphQL compiler
3. Validate schema is not empty
4. Delegate to ServiceModelGenerator for model construction
5. Return GraphqlModel with all types and interactions

### Core Components

#### 1. Service Model Generator

**ServiceModelGenerator** (`ServiceModelGenerator.java`)

**Purpose**: Generates GraphQL service model from schema object

**Workflow**:
1. Create root Service component
2. Process schema types (Query, Mutation, Subscription, custom types)
3. Build type hierarchy
4. Generate component models for each type
5. Link interactions between types

**Output**: Service component with nested type components

#### 2. Interacted Component Model Generator

**InteractedComponentModelGenerator** (`InteractedComponentModelGenerator.java`)

**Purpose**: Generates models for interacted/related types

**Handles**:
- Object types referenced by resolvers
- Union type members
- Interface implementations
- Nested object types

**Pattern**: Recursively processes type references

#### 3. Data Models

**GraphqlModel** (`model/GraphqlModel.java`)

**Root Model**: Contains complete schema

**Fields**:
- `service`: Service component (root)
- `components`: Map of all type components
- `interactions`: Inter-type relationships

**Service** (`model/Service.java`)

**Purpose**: Represents GraphQL service (root Query/Mutation/Subscription)

**Fields**:
- `name`: Service name
- `functions`: List of root resolvers (query/mutation/subscription)
- `fields`: Service configuration fields
- `location`: Source location

**ServiceClassComponent** (`model/ServiceClassComponent.java`)

**Purpose**: Represents GraphQL object type

**Fields**:
- `name`: Type name
- `kind`: OBJECT, INTERFACE, UNION, ENUM
- `fields`: Object fields (for object/interface)
- `functions`: Resolver methods
- `interfaces`: Implemented interfaces
- `location`: Source location

**RecordComponent** (`model/RecordComponent.java`)

**Purpose**: Represents GraphQL input type (Ballerina record)

**Fields**:
- `name`: Input type name
- `fields`: Input fields
- `location`: Source location

**RecordField** (`model/RecordField.java`)

**Purpose**: Field in record/input type

**Fields**:
- `name`: Field name
- `type`: Field type
- `optional`: Whether field is optional
- `defaultValue`: Default value if any

**EnumComponent** (`model/EnumComponent.java`)

**Purpose**: GraphQL enum type

**Fields**:
- `name`: Enum name
- `members`: Enum values

**EnumField** (`model/EnumField.java`)

**Purpose**: Enum member

**UnionComponent** (`model/UnionComponent.java`)

**Purpose**: GraphQL union type

**Fields**:
- `name`: Union name
- `memberTypes`: List of member type names

**InterfaceComponent** (`model/InterfaceComponent.java`)

**Purpose**: GraphQL interface type

**ResourceFunction** (`model/ResourceFunction.java`)

**Purpose**: GraphQL field resolver (resource method)

**Fields**:
- `name`: Field name
- `kind`: QUERY, MUTATION, SUBSCRIPTION, FIELD
- `returnType`: Return type
- `parameters`: Input parameters
- `location`: Source location

**RemoteFunction** (`model/RemoteFunction.java`)

**Purpose**: GraphQL remote resolver

**Param** (`model/Param.java`)

**Purpose**: Function parameter

**HierarchicalResourceComponent** (`model/HierarchicalResourceComponent.java`)

**Purpose**: Hierarchical resource path resolver

**Interaction** (`model/Interaction.java`)

**Purpose**: Interaction between types

**Fields**:
- `source`: Source type
- `target`: Target type
- `kind`: FIELD_REFERENCE, IMPLEMENTS, UNION_MEMBER

**DefaultIntrospectionType** (`model/DefaultIntrospectionType.java`)

**Purpose**: GraphQL introspection types (__Schema, __Type, etc.)

### Core Utilities

**ModelGenerationUtils** (`utils/ModelGenerationUtils.java`)

**Purpose**: Utility methods for model generation

**Key Methods**:
- `getServiceBasePath(serviceNode)`: Extract service base path
- `extractTypes(schema)`: Extract all types from schema
- `buildTypeHierarchy(types)`: Build type dependency graph

**CommonUtil** (`utils/CommonUtil.java`)

**Purpose**: Common utilities

**Key Methods**:
- `toRange(lineRange)`: Convert LineRange to LSP Range
- `findSTNode(range, syntaxTree)`: Find syntax node at range

### Exception Handling

**GraphqlModelGenerationException** (`exception/GraphqlModelGenerationException.java`)

**Purpose**: Custom exception for model generation errors

**Usage**:
- Schema validation failures
- Invalid node types
- Compilation errors

## Extension Points / APIs

### Main API

**Generate GraphQL Model**:

```java
import io.ballerina.graphqlmodelgenerator.core.ModelGenerator;
import io.ballerina.graphqlmodelgenerator.core.model.GraphqlModel;

// Generate model from service
ModelGenerator generator = new ModelGenerator();
GraphqlModel model = generator.getGraphqlModel(
    project,
    serviceLocation,
    semanticModel
);

// Access service
Service service = model.service();

// Access types
Map<String, Component> components = model.components();

// Iterate over resolvers
for (ResourceFunction resolver : service.functions()) {
    System.out.println(resolver.name() + ": " + resolver.returnType());
}
```

## Dependencies

### Module Dependencies
- **ballerina-lang**: Language core
- **ballerina-tools-api**: Compiler and project API
- **graphql-commons**: GraphQL schema utilities
- **graphql-compiler**: GraphQL service validation

### External Libraries
- **org.eclipse.lsp4j**: LSP types (Range, Position)

## Common Patterns

### 1. Schema Object Pattern
- Leverage Ballerina GraphQL compiler's Schema object
- Avoid re-implementing GraphQL validation
- Use validated schema as input

### 2. Component Hierarchy Pattern
- Service as root component
- Type components as children
- Interactions as edges in graph

### 3. Recursive Type Processing
- Handle nested object types
- Process interface implementations
- Resolve union members

### 4. Location Tracking Pattern
- Every component tracks source location
- Enables jump-to-definition
- Supports error reporting

## Development Guidelines

### Adding Support for New GraphQL Feature

1. **Check Schema Support**: Ensure Ballerina GraphQL compiler supports the feature

2. **Add Model Class** if needed:
   ```java
   public record NewComponent(String name, List<Field> fields, LineRange location)
       implements Component {
   }
   ```

3. **Update ServiceModelGenerator**:
   ```java
   // In type processing loop
   if (type instanceof NewGraphQLType newType) {
       NewComponent component = processNewType(newType);
       components.put(component.name(), component);
   }
   ```

4. **Add to GraphqlModel** if needed

## Usage Examples

### Generate Schema Model

```java
// Load project
Project project = BuildProject.load(Path.of("/path/to/graphql/service"));

// Get semantic model
SemanticModel semanticModel = project.currentPackage()
    .getCompilation()
    .getSemanticModel(moduleId);

// Locate service
LineRange serviceLocation = LineRange.from("service.bal", 10, 0, 50, 1);

// Generate model
ModelGenerator generator = new ModelGenerator();
GraphqlModel model = generator.getGraphqlModel(
    project,
    serviceLocation,
    semanticModel
);

// Serialize to JSON
Gson gson = new Gson();
String json = gson.toJson(model);
```

### Extract Type Information

```java
GraphqlModel model = generator.getGraphqlModel(...);

// Get all object types
List<ServiceClassComponent> objectTypes = model.components().values().stream()
    .filter(c -> c instanceof ServiceClassComponent)
    .map(c -> (ServiceClassComponent) c)
    .filter(c -> c.kind() == ComponentKind.OBJECT)
    .toList();

// Print type hierarchy
for (ServiceClassComponent type : objectTypes) {
    System.out.println("Type: " + type.name());
    System.out.println("  Interfaces: " + type.interfaces());
    System.out.println("  Fields:");
    for (ServiceClassField field : type.fields()) {
        System.out.println("    " + field.name() + ": " + field.type());
    }
}
```

## File Locations

- **Source**: `graphql-model-generator/modules/graphql-model-generator-core/src/main/java/`
  - `io/ballerina/graphqlmodelgenerator/core/`: Core generators
  - `io/ballerina/graphqlmodelgenerator/core/model/`: Data models
  - `io/ballerina/graphqlmodelgenerator/core/utils/`: Utilities
  - `io/ballerina/graphqlmodelgenerator/core/exception/`: Exceptions
- **Build**: `graphql-model-generator/modules/graphql-model-generator-core/build.gradle`

## Important Notes for AI Assistants

1. **Schema-Based**: Uses Ballerina GraphQL compiler's Schema object
2. **Validation Built-In**: Schema validation handled by compiler
3. **Type Hierarchy**: Properly models GraphQL type system
4. **Introspection Included**: Models include introspection types
5. **Immutable Models**: All models are records (immutable)
6. **Location Tracking**: Every component has source location
7. **Service-Centric**: Service is the root of the model
8. **Interaction Graph**: Models type relationships as interactions
9. **No Code Generation**: Only generates data models, not code
10. **JSON Serializable**: Models designed for JSON serialization

## Related Modules

- **graphql-model-generator-ls-extension**: LSP integration
- **ballerina-graphql**: GraphQL service library
- **graphql-commons**: GraphQL utilities
