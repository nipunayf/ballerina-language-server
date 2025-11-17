# trigger-service

## Module Overview

**Purpose**: Language server extension that provides Ballerina trigger discovery and scaffolding functionality. This service allows IDEs to search for available triggers from Ballerina Central, display trigger metadata, and generate trigger template code for services.

**Module Name**: `io.ballerina.trigger`

**Type**: LSP Extension Service

**Size**: 10 Java source files

## Key Responsibilities

- **Trigger Discovery**: Search for available triggers in Ballerina Central
- **Trigger Metadata**: Retrieve detailed trigger information (description, examples, parameters)
- **Trigger Scaffolding**: Generate Ballerina service templates for triggers
- **Central Integration**: Query Ballerina Central for trigger packages
- **IDE Integration**: Provide trigger browsing and selection UI support

## Architecture

### Entry Points

**BallerinaTriggerService** (`BallerinaTriggerService.java`)
- LSP extension service implementation
- Implements `ExtendedLanguageServerService` SPI
- JSON-RPC segment: `ballerinaTrigger`
- Registers via ServiceLoader: `@JavaSPIService("org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService")`

**JSON-RPC Methods**:
```java
@JsonRequest
CompletableFuture<BallerinaTriggerListResponse> triggers(BallerinaTriggerListRequest)

@JsonRequest
CompletableFuture<String> trigger(BallerinaTriggerRequest)
```

### Core Components

#### 1. Request/Response Models

**BallerinaTriggerListRequest** (`entity/BallerinaTriggerListRequest.java`)
- Request to list available triggers
- Fields:
  - `query`: Optional search query
  - `offset`: Pagination offset
  - `limit`: Number of results

**BallerinaTriggerListResponse** (`entity/BallerinaTriggerListResponse.java`)
- Response containing trigger list
- Fields:
  - `triggers`: List of Trigger objects
  - `count`: Total count
  - `central`: Whether from Central or local

**BallerinaTriggerRequest** (`entity/BallerinaTriggerRequest.java`)
- Request to generate trigger code
- Fields:
  - `id`: Trigger identifier
  - `orgName`: Organization name
  - `packageName`: Package name
  - `version`: Package version
  - `moduleName`: Module name

#### 2. Trigger Model

**Trigger** (`entity/Trigger.java`)
- Represents a trigger definition
- Fields:
  - `id`: Unique identifier
  - `type`: Trigger type (HTTP, Kafka, etc.)
  - `name`: Display name
  - `displayName`: User-friendly name
  - `description`: Trigger description
  - `keywords`: Search keywords
  - `examples`: Code examples
  - `orgName`, `packageName`, `version`: Package coordinates
  - `moduleName`: Module name
  - `listenerName`: Listener type name
  - `parameters`: Trigger parameters

**CentralTriggerListResult** (`entity/CentralTriggerListResult.java`)
- Result from Ballerina Central query
- Contains list of triggers from Central

#### 3. Central Integration

The service queries Ballerina Central to:
- Search for trigger packages
- Retrieve trigger metadata
- Get trigger examples and documentation

**Trigger Package Structure**:
- Trigger packages expose trigger metadata
- Metadata includes listener configuration
- Templates for service scaffolding

#### 4. Capability Management

**BallerinaTriggerServerCapabilities** (`BallerinaTriggerServerCapabilities.java`)
- Server capability flags

**BallerinaTriggerServiceServerCapabilitySetter** (`BallerinaTriggerServiceServerCapabilitySetter.java`)
- Registers server capabilities
- Implements `BallerinaServerCapabilitySetter` SPI

**Constants** (`entity/Constants.java`)
- Capability name: `"ballerinaTrigger"`
- Central API endpoints
- Configuration keys

## Extension Points / SPIs

### 1. ExtendedLanguageServerService SPI

**Implementation**: BallerinaTriggerService

**Registration**: META-INF/services/org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService

**Annotation**: `@JsonSegment("ballerinaTrigger")`

### 2. Server Capability Registration

**Implementation**: BallerinaTriggerServiceServerCapabilitySetter

**Purpose**: Advertise trigger service support to clients

## Dependencies

### Module Dependencies
- **langserver-commons**: LSP service interfaces
- **central-client**: Ballerina Central API client

### External Libraries
- **gson**: JSON processing

## Common Patterns

### 1. Service Provider Interface
- Implements LSP extension SPI
- Loaded dynamically via ServiceLoader

### 2. Async Operations
- All methods return CompletableFuture
- Non-blocking Central API calls

### 3. Entity Pattern
- Separate entity classes for domain models
- Clean separation of concerns

### 4. Pagination Support
- List requests support offset/limit
- Efficient handling of large result sets

## Development Guidelines

### Using Trigger Service from IDE

**List Available Triggers**:
```typescript
// Request all triggers
const request = {
  query: "",
  offset: 0,
  limit: 50
};

const response = await client.sendRequest(
  'ballerinaTrigger/triggers',
  request
);

response.triggers.forEach(trigger => {
  console.log(`${trigger.name}: ${trigger.description}`);
});
```

**Search Triggers**:
```typescript
// Search for HTTP triggers
const request = {
  query: "http",
  offset: 0,
  limit: 10
};

const response = await client.sendRequest(
  'ballerinaTrigger/triggers',
  request
);
```

**Generate Trigger Code**:
```typescript
// Generate HTTP trigger service
const request = {
  id: "http-trigger-1",
  orgName: "ballerina",
  packageName: "http",
  version: "2.0.0",
  moduleName: "http"
};

const triggerCode = await client.sendRequest(
  'ballerinaTrigger/trigger',
  request
);

// Returns generated Ballerina service code
console.log(triggerCode);
```

### Trigger Code Generation

The service generates template code like:
```ballerina
import ballerina/http;

service / on new http:Listener(8080) {
    resource function get greeting() returns string {
        return "Hello, World!";
    }
}
```

## Usage Examples

### Example 1: Browse HTTP Triggers

```typescript
const triggers = await client.sendRequest(
  'ballerinaTrigger/triggers',
  { query: "http", offset: 0, limit: 20 }
);

// Results might include:
// - HTTP Service Trigger
// - HTTP Load Balancer Trigger
// - HTTP API Gateway Trigger
// etc.
```

### Example 2: Create Kafka Trigger

```typescript
const kafkaTrigger = await client.sendRequest(
  'ballerinaTrigger/trigger',
  {
    id: "kafka-consumer",
    orgName: "ballerinax",
    packageName: "kafka",
    version: "3.0.0",
    moduleName: "kafka"
  }
);

// Generated code:
// import ballerinax/kafka;
//
// service on new kafka:Listener(...) {
//     remote function onConsumerRecord(kafka:Caller caller,
//                                     kafka:ConsumerRecord[] records) {
//         // Implementation
//     }
// }
```

### Example 3: Create Database Trigger

```typescript
const dbTrigger = await client.sendRequest(
  'ballerinaTrigger/trigger',
  {
    id: "mysql-listener",
    orgName: "ballerinax",
    packageName: "mysql",
    version: "1.0.0",
    moduleName: "mysql"
  }
);
```

## File Locations

- **Source**: `misc/ls-extensions/modules/trigger-service/src/main/java/io/ballerina/trigger/`
  - `BallerinaTriggerService.java`: Main service
  - `entity/`: Request/response models
  - `BallerinaTriggerServer*.java`: Capability management
- **Build**: `misc/ls-extensions/modules/trigger-service/build.gradle`
- **SPI Registration**: `src/main/resources/META-INF/services/`

## Important Notes for AI Assistants

1. **Central Dependency**: Relies on Ballerina Central for trigger discovery
2. **Template Generation**: Generates starter code, not complete implementations
3. **Package Coordination**: Requires org/package/version for code generation
4. **IDE Feature**: Primarily used by IDE "New Trigger" wizards
5. **Stateless**: Each request is independent
6. **Pagination**: Large trigger lists use offset/limit pagination
7. **Keyword Search**: Supports keyword-based trigger search
8. **Code Scaffolding**: Provides boilerplate, user fills in logic
9. **Async API**: All operations non-blocking via CompletableFuture
10. **Central Availability**: Requires network access to Ballerina Central

## Related Modules

- **langserver-core**: Loads this extension service
- **central-client**: Used for Central API calls

## RPC Methods

| Method | Purpose | Request | Response |
|--------|---------|---------|----------|
| `ballerinaTrigger/triggers` | List triggers | `{query?, offset, limit}` | `{triggers[], count}` |
| `ballerinaTrigger/trigger` | Generate code | `{id, org, package, version, module}` | `string` (code) |

## Trigger Types

Common trigger types include:
- **HTTP**: HTTP service listeners
- **Kafka**: Kafka consumers
- **RabbitMQ**: Message queue listeners
- **MySQL**: Database event listeners
- **gRPC**: gRPC service listeners
- **WebSocket**: WebSocket listeners
- **Timer**: Scheduled tasks
- **Email**: Email listeners
- **FTP**: File transfer listeners

## Performance Considerations

- **Central API Latency**: Trigger list queries depend on Central response time
- **Caching**: Consider caching trigger lists for better performance
- **Network Dependency**: Requires network access to function
- **Code Generation**: Template generation is lightweight
