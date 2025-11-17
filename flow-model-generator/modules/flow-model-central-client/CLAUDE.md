# flow-model-central-client

## Module Overview

**Purpose**: Client library for interacting with Ballerina Central API to fetch package metadata, function information, connector details, and symbols. Provides REST and GraphQL clients for querying Central's package registry and function catalog.

**Module Name**: `io.ballerina.centralconnector`

**Type**: Library module (HTTP/GraphQL client)

## Key Responsibilities

- **Package Search**: Query Ballerina Central for packages by keywords, organization
- **Symbol Search**: Search for functions, types, and connectors across all packages
- **Function Metadata**: Fetch detailed function signatures and documentation
- **Connector Discovery**: Find and retrieve connector client information
- **Version Management**: Query latest package versions
- **Authorization**: Support authenticated access to Central

## Architecture

### Entry Points

**CentralAPI** (`CentralAPI.java:55 lines`)
- Main interface defining all Central operations
- Implemented by: `RemoteCentral` (live API) and potentially local/mock implementations

**Interface Methods**:
```java
public interface CentralAPI {
    // Package operations
    PackageResponse searchPackages(Map<String, String> queryMap);
    String latestPackageVersion(String org, String name);

    // Symbol search
    SymbolResponse searchSymbols(Map<String, String> queryMap);

    // Function operations
    FunctionsResponse functions(String org, String name, String version);
    FunctionResponse function(String org, String name, String version, String functionName);

    // Connector operations
    ConnectorsResponse connectors(Map<String, String> queryMap);
    ConnectorResponse connector(String id);
    ConnectorResponse connector(String org, String name, String version, String clientName);

    // Authorization
    boolean hasAuthorizedAccess();
}
```

### Core Components

#### 1. Remote Central Client

**RemoteCentral** (`RemoteCentral.java:300+ lines`)
- Implementation of `CentralAPI` for live Ballerina Central
- Uses both REST API and GraphQL API
- Handles authentication and authorization

**Configuration**:
- **Base URL**: `https://central.ballerina.io/`
- **API Endpoints**:
  - REST: `/api/v1/`, `/api/v2/`
  - GraphQL: `/api/graphql`

**Authentication**:
- Reads token from `Settings.toml`
- Sends `Authorization: Bearer <token>` header
- Supports both authenticated and anonymous access

**Constructor**:
```java
public RemoteCentral() {
    this.restClient = new RestClient();
    this.graphQlClient = new GraphQlClient();
    loadAuthToken();
}
```

#### 2. REST Client

**RestClient** (`RestClient.java:200+ lines`)

**Purpose**: HTTP client for Central's REST API

**Endpoints**:

**Search Packages**:
```http
GET /api/v1/packages/search?q=http&org=ballerina&limit=50
```

**Get Package**:
```http
GET /api/v1/packages/{org}/{name}/{version}
```

**Latest Version**:
```http
GET /api/v1/packages/{org}/{name}/versions/latest
```

**Get Functions**:
```http
GET /api/v1/packages/{org}/{name}/{version}/functions
```

**Get Function**:
```http
GET /api/v1/packages/{org}/{name}/{version}/functions/{functionName}
```

**Implementation**:
```java
public PackageResponse searchPackages(Map<String, String> params) {
    String url = buildUrl("/api/v1/packages/search", params);
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Accept", "application/json")
        .GET()
        .build();

    HttpResponse<String> response = httpClient.send(request);
    return gson.fromJson(response.body(), PackageResponse.class);
}
```

#### 3. GraphQL Client

**GraphQlClient** (`GraphQlClient.java:250+ lines`)

**Purpose**: GraphQL client for Central's GraphQL API

**Endpoint**: `POST https://central.ballerina.io/api/graphql`

**Queries**:

**Search Symbols**:
```graphql
query SearchSymbols($query: String!, $offset: Int, $limit: Int) {
  search(query: $query, offset: $offset, limit: $limit) {
    symbols {
      id
      name
      description
      kind
      package {
        org
        name
        version
      }
    }
  }
}
```

**Get Connectors**:
```graphql
query GetConnectors($org: String, $limit: Int, $offset: Int) {
  connectors(org: $org, limit: $limit, offset: $offset) {
    id
    name
    description
    displayName
    packageName
    orgName
    version
    iconURL
    keywords
    methods {
      name
      description
      returnType
      parameters {
        name
        type
        description
      }
    }
  }
}
```

**Get Connector**:
```graphql
query GetConnector($id: ID!) {
  connector(id: $id) {
    id
    name
    description
    # ... full connector details
  }
}
```

**Implementation**:
```java
public SymbolResponse searchSymbols(Map<String, String> variables) {
    String query = loadQuery("searchSymbols.graphql");
    GraphQLRequest request = new GraphQLRequest(query, variables);

    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(GRAPHQL_ENDPOINT))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
        .build();

    HttpResponse<String> response = httpClient.send(httpRequest);
    return gson.fromJson(response.body(), SymbolResponse.class);
}
```

## Response Models

All response models are in the `response/` package.

### PackageResponse

**File**: `response/PackageResponse.java`

**Structure**:
```java
public class PackageResponse {
    private List<Package> packages;
    private int count;
    private Pagination pagination;

    public static class Package {
        private String org;
        private String name;
        private String version;
        private String description;
        private String summary;
        private List<String> keywords;
        private String iconURL;
        private String createdDate;
        private int pullCount;
    }
}
```

### SymbolResponse

**File**: `response/SymbolResponse.java`

**Structure**:
```java
public class SymbolResponse {
    private List<Symbol> symbols;
    private int count;

    public static class Symbol {
        private String id;
        private String name;
        private String description;
        private SymbolKind kind; // FUNCTION, CLASS, TYPE_DEFINITION
        private PackageInfo package;
        private String signature;
    }
}
```

### FunctionsResponse

**File**: `response/FunctionsResponse.java`

**Structure**:
```java
public class FunctionsResponse {
    private List<Function> functions;
    private int count;
}
```

### FunctionResponse

**File**: `response/FunctionResponse.java`

**Structure**:
```java
public class FunctionResponse {
    private Function function;

    public static class Function {
        private String name;
        private String description;
        private String returnType;
        private List<Parameter> parameters;
        private boolean isRemote;
        private String resourcePath;
        private PackageInfo package;
    }
}
```

### ConnectorsResponse

**File**: `response/ConnectorsResponse.java`

**Structure**:
```java
public class ConnectorsResponse {
    private List<Connector> connectors;
    private int count;
    private Pagination pagination;
}
```

### ConnectorResponse

**File**: `response/ConnectorResponse.java`

**Structure**:
```java
public class ConnectorResponse {
    private Connector connector;

    public static class Connector {
        private String id;
        private String name;
        private String displayName;
        private String description;
        private String orgName;
        private String packageName;
        private String version;
        private String iconURL;
        private List<String> keywords;
        private List<Method> methods;
        private InitMethod init;
        private List<Example> examples;
    }

    public static class Method {
        private String name;
        private String description;
        private String returnType;
        private List<Parameter> parameters;
        private boolean isRemote;
    }
}
```

## Extension Points / APIs

### Usage Examples

**Search Packages**:
```java
CentralAPI central = new RemoteCentral();

Map<String, String> query = Map.of(
    "q", "http",
    "org", "ballerina",
    "limit", "20"
);

PackageResponse response = central.searchPackages(query);
for (Package pkg : response.getPackages()) {
    System.out.println(pkg.getName() + " - " + pkg.getDescription());
}
```

**Search Symbols**:
```java
Map<String, String> query = Map.of(
    "query", "get request",
    "limit", "50",
    "offset", "0"
);

SymbolResponse response = central.searchSymbols(query);
for (Symbol symbol : response.getSymbols()) {
    System.out.println(symbol.getName() + " (" + symbol.getKind() + ")");
}
```

**Get Functions for Package**:
```java
FunctionsResponse functions = central.functions("ballerina", "http", "2.8.0");

for (Function func : functions.getFunctions()) {
    System.out.println(func.getName() + ": " + func.getReturnType());
}
```

**Get Specific Function**:
```java
FunctionResponse response = central.function(
    "ballerina",
    "http",
    "2.8.0",
    "Client.get"
);

Function func = response.getFunction();
System.out.println("Return type: " + func.getReturnType());
for (Parameter param : func.getParameters()) {
    System.out.println("  " + param.getName() + ": " + param.getType());
}
```

**Search Connectors**:
```java
Map<String, String> query = Map.of(
    "org", "ballerinax",
    "limit", "20"
);

ConnectorsResponse response = central.connectors(query);
for (Connector conn : response.getConnectors()) {
    System.out.println(conn.getDisplayName() + " - " + conn.getDescription());
}
```

**Get Connector by ID**:
```java
ConnectorResponse response = central.connector("ballerinax-openai.chat-1.0.0-Client");

Connector connector = response.getConnector();
System.out.println("Methods:");
for (Method method : connector.getMethods()) {
    System.out.println("  " + method.getName());
}
```

**Get Latest Version**:
```java
String latestVersion = central.latestPackageVersion("ballerina", "http");
System.out.println("Latest version: " + latestVersion);
```

**Check Authorization**:
```java
if (central.hasAuthorizedAccess()) {
    // Use authenticated features
} else {
    // Limited to public access
}
```

## Dependencies

### External Libraries
- **Java 11+ HttpClient**: HTTP communication
- **gson**: JSON serialization/deserialization

### Configuration Files
- **Settings.toml**: Ballerina settings for auth token
  - Location: `~/.ballerina/Settings.toml`
  - Format:
    ```toml
    [central]
    accessToken = "your-token-here"
    ```

## Common Patterns

### 1. Interface-Based Design
- `CentralAPI` defines contract
- `RemoteCentral` implements for live API
- Easy to mock for testing

### 2. Dual Protocol Support
- REST for simple queries
- GraphQL for complex nested queries
- Chooses appropriate protocol per operation

### 3. Builder Pattern
- Query parameters via maps
- Flexible query construction

### 4. Response Wrapper Pattern
- Consistent response structure
- Includes metadata (count, pagination)

### 5. Authorization Pattern
- Token-based authentication
- Graceful fallback to anonymous

## Development Guidelines

### Adding a New Endpoint

1. **Add to Interface**:
   ```java
   // In CentralAPI.java
   MyResponse myOperation(Map<String, String> params);
   ```

2. **Implement in RemoteCentral**:
   ```java
   @Override
   public MyResponse myOperation(Map<String, String> params) {
       if (useGraphQL) {
           return graphQlClient.myOperation(params);
       } else {
           return restClient.myOperation(params);
       }
   }
   ```

3. **Implement in Client**:
   ```java
   // In RestClient.java or GraphQlClient.java
   public MyResponse myOperation(Map<String, String> params) {
       String url = buildUrl("/api/v1/my-endpoint", params);
       // ... HTTP call
       return gson.fromJson(response, MyResponse.class);
   }
   ```

4. **Create Response Model**:
   ```java
   // In response/MyResponse.java
   public class MyResponse {
       private List<MyData> data;
       // getters/setters
   }
   ```

### Error Handling

```java
try {
    PackageResponse response = central.searchPackages(query);
} catch (IOException e) {
    // Network error
    logger.error("Failed to connect to Central", e);
} catch (InterruptedException e) {
    // Request interrupted
    Thread.currentThread().interrupt();
} catch (JsonSyntaxException e) {
    // Invalid response format
    logger.error("Invalid response from Central", e);
}
```

## Usage in Other Modules

### In flow-model-index-generator

```java
// Fetch packages to index
CentralAPI central = new RemoteCentral();
PackageResponse packages = central.searchPackages(
    Map.of("org", "ballerina", "limit", "1000")
);

// Index each package
for (Package pkg : packages.getPackages()) {
    indexPackage(pkg);
}
```

### In model-generator-commons

```java
// Get latest version
CentralAPI central = new RemoteCentral();
String latest = central.latestPackageVersion("ballerina", "http");

// Use for package resolution
Package pkg = PackageUtil.getModulePackage(project, "ballerina", "http", latest);
```

### In flow-model-generator-core

```java
// Search for connectors
CentralAPI central = new RemoteCentral();
ConnectorsResponse connectors = central.connectors(
    Map.of("org", "ballerinax", "limit", "50")
);

// Display in available nodes
for (Connector conn : connectors.getConnectors()) {
    addToNodeCatalog(conn);
}
```

## File Locations

- **Source**: `flow-model-generator/modules/flow-model-central-client/src/main/java/`
  - `io/ballerina/centralconnector/`: Client implementation
  - `io/ballerina/centralconnector/response/`: Response models
- **Build**: `flow-model-generator/modules/flow-model-central-client/build.gradle`

## Important Notes for AI Assistants

1. **Two Protocols**: Uses both REST and GraphQL based on operation
2. **Authentication**: Supports both authenticated and anonymous access
3. **Rate Limiting**: Central API may have rate limits (respect them)
4. **Version Handling**: Always specify version or use "latest"
5. **Error Handling**: Network operations can fail, handle gracefully
6. **Response Caching**: Consider caching responses for performance
7. **Settings.toml**: Auth token stored in Ballerina settings
8. **API Evolution**: Central API may change, check documentation
9. **Organization Filter**: Most queries support filtering by org
10. **Pagination**: Use offset/limit for large result sets

## API Endpoints Reference

### REST API

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/v1/packages/search` | GET | Search packages |
| `/api/v1/packages/{org}/{name}/{version}` | GET | Get package details |
| `/api/v1/packages/{org}/{name}/versions/latest` | GET | Get latest version |
| `/api/v1/packages/{org}/{name}/{version}/functions` | GET | Get functions |
| `/api/v1/packages/{org}/{name}/{version}/functions/{name}` | GET | Get function |

### GraphQL API

| Query | Purpose |
|-------|---------|
| `search` | Search symbols across packages |
| `connectors` | List connectors with filters |
| `connector(id)` | Get connector by ID |
| `connector(org, name, version, clientName)` | Get connector by coordinates |

## Performance Considerations

- **Network Latency**: Remote API calls are slow
- **Response Caching**: Cache responses locally
- **Batch Queries**: Use GraphQL for nested data
- **Parallel Requests**: Use async HTTP client
- **Connection Pooling**: Reuse HTTP connections

## Testing

### Unit Testing

```java
@Test
public void testSearchPackages() {
    CentralAPI central = new RemoteCentral();

    Map<String, String> query = Map.of("q", "http");
    PackageResponse response = central.searchPackages(query);

    assertNotNull(response);
    assertTrue(response.getPackages().size() > 0);
}
```

### Mocking Central

```java
public class MockCentral implements CentralAPI {
    @Override
    public PackageResponse searchPackages(Map<String, String> query) {
        // Return mock data
        return new PackageResponse(List.of(
            new Package("ballerina", "http", "2.8.0")
        ));
    }
}
```

## Related Modules

- **flow-model-index-generator**: Uses this to fetch package list
- **model-generator-commons**: Uses for version queries
- **flow-model-generator-core**: Uses for connector discovery
- **langserver-core**: Indirectly uses via model generators
