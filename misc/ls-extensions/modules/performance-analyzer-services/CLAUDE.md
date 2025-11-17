# performance-analyzer-services

## Module Overview

**Purpose**: Language server extension that analyzes Ballerina service performance characteristics by identifying resource endpoints, discovering service URLs, and generating performance testing graphs. This service helps developers understand and visualize API endpoints for performance testing and monitoring.

**Module Name**: `io.ballerina.performanceanalyzer`

**Type**: LSP Extension Service

**Size**: 15 Java source files

## Key Responsibilities

- **Resource Discovery**: Find all HTTP resource functions in services
- **URL Extraction**: Extract base URLs and endpoint paths from services
- **Performance Graph Generation**: Generate performance analysis graphs
- **Endpoint Analysis**: Identify all API endpoints with HTTP methods
- **Service Mapping**: Map services to their resources and URLs

## Architecture

### Entry Points

The service provides JSON-RPC methods for:
- Discovering service endpoints
- Extracting URLs from services
- Generating performance graphs

### Core Components

#### 1. Node Visitor

**PerformanceAnalyzerNodeVisitor** (`PerformanceAnalyzerNodeVisitor.java`)
- Visitor pattern for traversing syntax trees
- Discovers service declarations
- Identifies resource functions
- Extracts endpoint information
- Tracks remote method calls

**Visits**:
- `ServiceDeclarationNode`: Service definitions
- `FunctionDefinitionNode`: Resource and remote functions
- `RemoteMethodCallActionNode`: Client calls
- `IfElseStatementNode`, `ForEachStatementNode`, `WhileStatementNode`: Control flow

#### 2. Resource Discovery

**Resource** (`Resource.java`)
- Represents a discovered API endpoint
- Fields:
  - `path`: Resource path
  - `method`: HTTP method (GET, POST, etc.)
  - `parameters`: Resource parameters
  - `returnType`: Return type

**EndpointsFinder** (`EndpointsFinder.java`)
- Finds all endpoints in a project
- Analyzes service declarations
- Extracts resource metadata
- Returns list of Resource objects

#### 3. URL Discovery

**UrlFinder** (`UrlFinder.java`)
- Extracts service URLs
- Analyzes listener configurations
- Identifies base paths
- Combines service path with resource paths

#### 4. Request/Response Models

**PerformanceAnalyzerRequest** (`PerformanceAnalyzerRequest.java`)
- Request for performance analysis
- Fields:
  - `documentUri`: Document to analyze
  - `projectPath`: Project root path

**PerformanceAnalyzerResponse** (`PerformanceAnalyzerResponse.java`)
- Response with discovered endpoints
- Fields:
  - `resources`: List of Resource objects
  - `baseUrls`: Service base URLs

**PerformanceAnalyzerGraphRequest** (`PerformanceAnalyzerGraphRequest.java`)
- Request for graph generation
- Fields:
  - `resources`: Resources to include in graph
  - `graphType`: Type of graph to generate

**BallerinaProjectParams** (`BallerinaProjectParams.java`)
- Project parameters
- Contains project path and configuration

#### 5. Utilities

**ParserUtil** (`utils/ParserUtil.java`)
- Parsing utilities
- Syntax tree helpers
- Type resolution

**ReturnFinder** (`utils/ReturnFinder.java`)
- Finds return statements in functions
- Analyzes control flow for returns
- Helps determine resource return types

**Constants** (`Constants.java`)
- Service-wide constants
- HTTP method names
- Configuration keys

#### 6. Capability Management

**PerformanceAnalyzerClientCapabilities**
**PerformanceAnalyzerServerCapabilities**
- Client and server capability flags

## Extension Points / SPIs

### 1. ExtendedLanguageServerService SPI

**Registration**: META-INF/services/org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService

**JSON-RPC Segment**: `performanceAnalyzer`

## Dependencies

### Module Dependencies
- **langserver-commons**: LSP service interfaces
- **ballerina-parser**: Syntax tree API
- **ballerina-compiler-api**: Semantic model

### External Libraries
- **gson**: JSON processing

## Common Patterns

### 1. Visitor Pattern
- Traverses syntax trees to find services and resources
- Dedicated visitors for different analysis types

### 2. Service Provider Interface
- Implements LSP extension SPI
- Loaded dynamically

### 3. Async Operations
- Methods return CompletableFuture
- Non-blocking analysis

### 4. Model Classes
- Separate request/response models
- Clean domain separation

## Development Guidelines

### Using Performance Analyzer from IDE

**Analyze Project Endpoints**:
```typescript
const request = {
  documentUri: "file:///path/to/service.bal",
  projectPath: "/path/to/project"
};

const response = await client.sendRequest(
  'performanceAnalyzer/analyze',
  request
);

response.resources.forEach(resource => {
  console.log(`${resource.method} ${resource.path}`);
});
```

**Generate Performance Graph**:
```typescript
const graphRequest = {
  resources: discoveredResources,
  graphType: "performance"
};

const graph = await client.sendRequest(
  'performanceAnalyzer/generateGraph',
  graphRequest
);
```

## Usage Examples

### Example 1: Discover HTTP Endpoints

Given service:
```ballerina
import ballerina/http;

service /api on new http:Listener(8080) {
    resource function get users() returns json {
        return {users: []};
    }

    resource function post users(User user) returns int {
        return 201;
    }

    resource function get users/[int id]() returns User? {
        return ();
    }
}
```

Discovered resources:
```json
{
  "resources": [
    {"method": "GET", "path": "/api/users"},
    {"method": "POST", "path": "/api/users"},
    {"method": "GET", "path": "/api/users/{id}"}
  ],
  "baseUrls": ["http://localhost:8080"]
}
```

### Example 2: Multiple Services

```ballerina
service /auth on new http:Listener(8080) {
    resource function post login() { }
}

service /data on new http:Listener(8081) {
    resource function get records() { }
    resource function delete records/[int id]() { }
}
```

Analysis result:
- Auth service: `POST /auth/login` on port 8080
- Data service: `GET /data/records`, `DELETE /data/records/{id}` on port 8081

## File Locations

- **Source**: `misc/ls-extensions/modules/performance-analyzer-services/src/main/java/io/ballerina/`
  - `PerformanceAnalyzerNodeVisitor.java`: Syntax tree visitor
  - `EndpointsFinder.java`: Endpoint discovery
  - `UrlFinder.java`: URL extraction
  - `Resource.java`: Resource model
  - `PerformanceAnalyzer*.java`: Request/response models
  - `utils/`: Utility classes
- **Build**: `misc/ls-extensions/modules/performance-analyzer-services/build.gradle`

## Important Notes for AI Assistants

1. **Performance Focus**: Designed for performance testing scenario
2. **HTTP-Centric**: Focuses on HTTP services and resources
3. **Endpoint Discovery**: Identifies all testable API endpoints
4. **Graph Generation**: Can generate visualization graphs
5. **Stateless**: Each analysis is independent
6. **Project Scope**: Analyzes entire projects, not single files
7. **Semantic Analysis**: Uses semantic model for accurate type information
8. **Multi-Service**: Handles multiple services per project
9. **Path Composition**: Combines service path + resource path
10. **IDE Integration**: Designed for IDE performance testing UIs

## Related Modules

- **langserver-core**: Loads this extension service

## RPC Methods

Methods exposed via JSON-RPC (specific method names depend on implementation)

## Performance Considerations

- **Syntax Tree Traversal**: Can be expensive for large projects
- **Semantic Model**: Requires compilation
- **Caching**: Results should be cached by IDE
- **Incremental**: Should support incremental updates
