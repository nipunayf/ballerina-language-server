# service-model-generator-ls-extension

## Module Overview

**Purpose**: Language Server Protocol (LSP) extension for visual service/trigger design in Ballerina. Provides comprehensive JSON-RPC API for building low-code service builders, enabling visual creation and modification of HTTP, GraphQL, Kafka, RabbitMQ, and other protocol services through IDE interfaces.

**Module Name**: `io.ballerina.servicemodelgenerator.extension`

**Type**: LSP Extension (ExtendedLanguageServerService)

## Key Responsibilities

- **Service Model Generation**: Generate visual models from Ballerina service code
- **Trigger Management**: Provide catalog of available service triggers (listeners)
- **Service Builder**: Visual service creation with form-based configuration
- **Function/Resource Builder**: Add and modify service resources and functions
- **Listener Management**: Discover, create, and configure listeners
- **Service Class Support**: Handle service classes and their fields
- **Source Code Generation**: Convert visual service models to Ballerina code
- **OpenAPI Integration**: Generate services from OpenAPI/GraphQL specifications
- **Protocol-Specific Builders**: Specialized builders for HTTP, Kafka, RabbitMQ, GraphQL, Solace, MCP, TCP, etc.

## Architecture

### Entry Points - LSP Service

**ServiceModelGeneratorService** (`ServiceModelGeneratorService.java:2000+ lines`)

**JSON-RPC Segment**: `@JsonSegment("serviceDesign")`

**Primary Service**: Comprehensive service with 30+ endpoints for service design

### Core JSON-RPC Endpoints

#### Trigger/Listener Discovery

**getTriggerList**
```java
@JsonRequest
CompletableFuture<TriggerListResponse> getTriggerList(TriggerListRequest request)
```
- Get list of available service types/triggers
- Returns: HTTP, GraphQL, Kafka, RabbitMQ, gRPC, Solace, WebSocket, etc.
- Loaded from `trigger_properties.json` resource

**getTrigger**
```java
@JsonRequest
CompletableFuture<TriggerResponse> getTrigger(TriggerRequest request)
```
- Get detailed trigger metadata for specific service type
- Returns configuration schema and defaults

#### Listener Operations

**getListenerModel**
```java
@JsonRequest
CompletableFuture<ListenerModelResponse> getListenerModel(ListenerModelRequest request)
```
- Extract listener model from source code
- Analyzes ListenerDeclarationNode

**getListenerModelFromSource**
```java
@JsonRequest
CompletableFuture<ListenerFromSourceResponse> getListenerModelFromSource(
    ListenerSourceRequest request)
```
- Generate listener source code from visual model

**updateListener**
```java
@JsonRequest
CompletableFuture<CommonSourceResponse> updateListener(ListenerModifierRequest request)
```
- Modify existing listener configuration
- Returns TextEdit operations

**discoverListeners**
```java
@JsonRequest
CompletableFuture<ListenerDiscoveryResponse> discoverListeners(
    ListenerDiscoveryRequest request)
```
- Scan file for existing listeners
- Returns list of available listeners

**addOrGetDefaultListener**
```java
@JsonRequest
CompletableFuture<AddOrGetDefaultListenerResponse> addOrGetDefaultListener(TriggerRequest request)
```
- Add default listener if none exists
- Returns existing or newly created listener

#### Service Operations

**getServiceModel**
```java
@JsonRequest
CompletableFuture<ServiceModelResponse> getServiceModel(ServiceModelRequest request)
```
- Extract service model from source code
- Analyzes ServiceDeclarationNode

**getServiceModelFromSource**
```java
@JsonRequest
CompletableFuture<ServiceFromSourceResponse> getServiceModelFromSource(
    ServiceSourceRequest request)
```
- Generate service source code from visual model
- Protocol-specific generation (HTTP, GraphQL, etc.)

**updateService**
```java
@JsonRequest
CompletableFuture<CommonSourceResponse> updateService(ServiceModifierRequest request)
```
- Modify existing service configuration
- Update service annotations and properties

**getServiceInitModel**
```java
@JsonRequest
CompletableFuture<ServiceInitModelResponse> getServiceInitModel(TriggerRequest request)
```
- Get initialization model for new service
- Returns default values and schema

**addServiceModelFromSource**
```java
@JsonRequest
CompletableFuture<CommonSourceResponse> addServiceModelFromSource(
    ServiceInitSourceRequest request)
```
- Add new service to source file
- Generates complete service declaration

#### Function/Resource Operations

**getFunctionModel**
```java
@JsonRequest
CompletableFuture<FunctionModelResponse> getFunctionModel(FunctionModelRequest request)
```
- Extract function/resource model from source

**getFunctionModelFromSource**
```java
@JsonRequest
CompletableFuture<FunctionFromSourceResponse> getFunctionModelFromSource(
    FunctionSourceRequest request)
```
- Generate function source code from visual model

**updateFunction**
```java
@JsonRequest
CompletableFuture<CommonSourceResponse> updateFunction(FunctionModifierRequest request)
```
- Modify existing function/resource
- Update parameters, return types, annotations

#### Service Class Operations

**getServiceClassModel**
```java
@JsonRequest
CompletableFuture<ServiceClassModelResponse> getServiceClassModel(
    ClassModelFromSourceRequest request)
```
- Extract service class model
- For service classes (object service)

**addServiceClassField**
```java
@JsonRequest
CompletableFuture<CommonSourceResponse> addServiceClassField(AddFieldRequest request)
```
- Add field to service class
- Generates field declaration

**updateServiceClassField**
```java
@JsonRequest
CompletableFuture<CommonSourceResponse> updateServiceClassField(
    ClassFieldModifierRequest request)
```
- Modify service class field

#### Type Completion

**getTypesForCompletion**
```java
@JsonRequest
CompletableFuture<Either<List<CompletionItem>, CompletionList>> getTypesForCompletion(
    TypesRequest request)
```
- Type-ahead completion for type selection
- Returns available types from semantic model

#### Specialized Generators

**generateOpenAPIService**
```java
@JsonRequest
CompletableFuture<CommonSourceResponse> generateOpenAPIService(TriggerRequest request)
```
- Generate Ballerina service from OpenAPI spec
- Uses OpenAPIServiceGenerator

**generateGraphQLService**
```java
@JsonRequest
CompletableFuture<CommonSourceResponse> generateGraphQLService(TriggerRequest request)
```
- Generate Ballerina service from GraphQL schema
- Uses GraphqlServiceGenerator

### Core Components

#### Builder System

**ServiceBuilderRouter** (`builder/ServiceBuilderRouter.java`)

**Purpose**: Routes service generation to protocol-specific builders

**Supported Protocols**:
- HTTP (HttpServiceBuilder)
- GraphQL (GraphqlServiceBuilder)
- Kafka (KafkaServiceBuilder)
- RabbitMQ (RabbitMQServiceBuilder)
- Solace (SolaceServiceBuilder)
- MCP (McpServiceBuilder)
- TCP (TCPServiceBuilder)
- Default (for other protocols)

**FunctionBuilderRouter** (`builder/FunctionBuilderRouter.java`)

**Purpose**: Routes function/resource generation to protocol-specific builders

**Function Builders**:
- HttpFunctionBuilder
- GraphqlFunctionBuilder
- KafkaFunctionBuilder
- RabbitMQFunctionBuilder
- SolaceFunctionBuilder
- McpFunctionBuilder
- DefaultFunctionBuilder

**NodeBuilder** (`builder/NodeBuilder.java`)

**Purpose**: Generates syntax tree nodes from visual models

**ServiceNodeBuilder** (`builder/ServiceNodeBuilder.java`)

**Purpose**: Builds complete service declaration syntax trees

#### Extractors

**ServiceDescriptionExtractor** (`extractor/ServiceDescriptionExtractor.java`)

**Purpose**: Extracts service metadata from syntax tree

**ListenerParamExtractor** (`extractor/ListenerParamExtractor.java`)

**Purpose**: Extracts listener configuration parameters

**AnnotationExtractor** (`extractor/AnnotationExtractor.java`)

**Purpose**: Extracts service/resource annotations

**CustomExtractor** (`extractor/CustomExtractor.java`)

**Purpose**: Protocol-specific custom extraction

**ReadOnlyMetadataExtractor** (`extractor/ReadOnlyMetadataExtractor.java`)

**Purpose**: Extracts read-only metadata from services

#### Modifiers

**ServiceModifier** (`util/ServiceModifier.java`)

**Purpose**: Modifies existing service nodes
- Update service configuration
- Change listener bindings
- Modify service path

**ServiceClassModifier** (`util/ServiceClassModifier.java`)

**Purpose**: Modifies service class definitions

#### Utilities

**HttpUtil** (`util/HttpUtil.java`)
- HTTP-specific utilities
- HTTP method mapping
- Response type handling

**ListenerUtil** (`util/ListenerUtil.java`)
- Listener discovery and creation
- Default listener generation

**ServiceClassUtil** (`util/ServiceClassUtil.java`)
- Service class manipulation

**DatabindUtil** (`util/DatabindUtil.java`)
- Data binding utilities for request/response types

**JmsUtil** (`util/JmsUtil.java`)
- JMS/messaging utilities

**ServiceModelUtils** (`util/ServiceModelUtils.java`)
- General service model utilities

**TypeCompletionGenerator** (`util/TypeCompletionGenerator.java`)
- Type completion suggestions
- Semantic model integration

### Data Models

**Service** (`model/Service.java`)
- Service declaration model
- Fields: name, path, protocol, functions, metadata

**Function** (`model/Function.java`)
- Function/resource model
- Fields: name, kind (resource/remote), parameters, returnType, path

**Listener** (`model/Listener.java`)
- Listener configuration model
- Fields: protocol, port, host, configurations

**ServiceClass** (`model/ServiceClass.java`)
- Service class model
- Fields: name, fields, functions

**Parameter** (`model/Parameter.java`)
- Function parameter model

**TriggerBasicInfo** (`model/TriggerBasicInfo.java`)
- Trigger metadata
- Available service types

**TriggerProperty** (`model/TriggerProperty.java`)
- Trigger configuration schema
- Validation rules, defaults

**ServiceMetadata** (`model/ServiceMetadata.java`)
- Service metadata and annotations

**Field** (`model/Field.java`)
- Service class field model

**HttpResponse** (`model/HttpResponse.java`)
- HTTP response configuration

**Codedata** (`model/Codedata.java`)
- Code reference metadata

**Value** (`model/Value.java`)
- Generic value representation

### Request/Response Models

Located in `model/request/` and `model/response/` packages

**Pattern**: Each operation has dedicated request/response classes

**Context Models** (`model/context/`):
- GetModelContext
- AddModelContext
- UpdateModelContext
- ModelFromSourceContext
- GetServiceInitModelContext
- AddServiceInitModelContext

## Extension Points / APIs

### LSP Service SPI

**Registration**:
```java
@JavaSPIService("org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService")
@JsonSegment("serviceDesign")
public class ServiceModelGeneratorService implements ExtendedLanguageServerService
```

### Client Integration Example

```typescript
// Get available triggers
const triggers = await client.sendRequest('serviceDesign/getTriggerList', {});

// Show service type selector
const selectedTrigger = await showQuickPick(triggers.triggers);

// Get service init model
const initModel = await client.sendRequest('serviceDesign/getServiceInitModel', {
    trigger: selectedTrigger.id
});

// Show service configuration form
const serviceConfig = await showServiceForm(initModel);

// Generate service source
const sourceEdits = await client.sendRequest('serviceDesign/addServiceModelFromSource', {
    filePath: documentUri,
    model: serviceConfig
});

// Apply edits
await workspace.applyEdit({ changes: { [documentUri]: sourceEdits } });
```

## Dependencies

### Module Dependencies
- **model-generator-commons**: Shared utilities and database access
- **langserver-commons**: LSP interfaces
- **ballerina-tools-api**: Project and semantic model API
- **org.eclipse.lsp4j**: LSP protocol types
- **openapi-service-mapper**: OpenAPI to Ballerina mapping
- **graphql-commons**: GraphQL service generation

### External Libraries
- **gson**: JSON serialization
- **picocli**: CLI parsing (for embedded tools)

## Important Notes for AI Assistants

1. **Comprehensive Service**: 30+ endpoints for complete service design experience
2. **Protocol-Specific**: Specialized builders for each protocol (HTTP, Kafka, etc.)
3. **Database-Backed**: Uses ServiceDatabaseManager for trigger metadata
4. **JSON Configuration**: trigger_properties.json defines available service types
5. **Builder Pattern**: Extensive use of builders for syntax tree generation
6. **Bidirectional**: Supports code→model and model→code transformations
7. **Visual First**: Designed for low-code/no-code service builders
8. **Type-Safe**: Uses semantic model for type validation and completion
9. **Listener Management**: Automatic listener discovery and creation
10. **Service Classes**: Full support for service class pattern

## File Locations

- **Source**: `service-model-generator/modules/service-model-generator-ls-extension/src/main/java/`
  - `io/ballerina/servicemodelgenerator/extension/core/`: Main service
  - `io/ballerina/servicemodelgenerator/extension/builder/`: Syntax builders
  - `io/ballerina/servicemodelgenerator/extension/extractor/`: Model extractors
  - `io/ballerina/servicemodelgenerator/extension/model/`: Data models
  - `io/ballerina/servicemodelgenerator/extension/util/`: Utilities
- **Resources**: `service-model-generator/modules/service-model-generator-ls-extension/src/main/resources/`
  - `trigger_properties.json`: Service type definitions
- **Build**: `service-model-generator/modules/service-model-generator-ls-extension/build.gradle`

## Related Modules

- **service-model-index-generator**: Builds trigger metadata database
- **model-generator-commons**: Shared utilities
- **langserver-core**: Language server hosting this extension
- **openapi-service**: OpenAPI service generation
- **VS Code Extension**: Primary client for visual service builder
