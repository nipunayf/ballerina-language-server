# test-manager-service-ls-extension

## Module Overview

**Purpose**: Language Server Protocol (LSP) extension for managing Ballerina test functions. Provides JSON-RPC API for discovering, creating, and modifying test functions, enabling visual test management and test-driven development tools in IDEs.

**Module Name**: `io.ballerina.testmanagerservice.extension`

**Type**: LSP Extension (ExtendedLanguageServerService)

## Key Responsibilities

- **Test Discovery**: Discover test functions and groups in Ballerina modules
- **Test Function Generation**: Generate test function templates
- **Test Function Modification**: Update test function annotations and configurations
- **Test Organization**: Manage test groups and dependencies
- **Test Metadata Extraction**: Extract test function metadata and annotations

## Architecture

### Entry Points - LSP Service

**TestManagerService** (`TestManagerService.java`)

**JSON-RPC Segment**: `@JsonSegment("testManagerService")`

### Core JSON-RPC Endpoints

**discoverInFile**
```java
@JsonRequest
CompletableFuture<TestsDiscoveryResponse> discoverInFile(TestsDiscoveryRequest request)
```
- Discover all test functions in a file
- Returns test functions organized by groups
- Extracts test annotations and configurations

**Request Parameters**:
- `filePath`: Path to Ballerina test file

**Response**:
- `groupsToFunctions`: Map of test groups to test functions
- `diagnostics`: Discovery errors

**getTestFunction**
```java
@JsonRequest
CompletableFuture<GetTestFunctionResponse> getTestFunction(GetTestFunctionRequest request)
```
- Get detailed test function model
- Extract test function metadata

**addTestFunction**
```java
@JsonRequest
CompletableFuture<CommonSourceResponse> addTestFunction(AddTestFunctionRequest request)
```
- Generate new test function
- Add to test file
- Returns TextEdit operations

**updateTestFunction**
```java
@JsonRequest
CompletableFuture<CommonSourceResponse> updateTestFunction(UpdateTestFunctionRequest request)
```
- Modify existing test function
- Update annotations, configurations
- Returns TextEdit operations

### Core Components

#### Test Functions Finder

**TestFunctionsFinder** (`TestFunctionsFinder.java`)

**Purpose**: Discovers test functions in document

**Workflow**:
1. Traverse syntax tree
2. Find functions with @test annotation
3. Extract test metadata
4. Group by @test:Config groups
5. Build test hierarchy

**Detection**:
- Functions with `@test:Config` annotation
- Test groups via `groups` field
- Test dependencies via `dependsOn` field
- Before/After functions via annotations

#### Module Test Details Holder

**ModuleTestDetailsHolder** (`ModuleTestDetailsHolder.java`)

**Purpose**: Holds discovered test metadata

**Data Structure**:
```java
Map<String, List<FunctionTreeNode>> groupsToFunctions
```

**FunctionTreeNode**: Represents test function in tree structure

#### Data Models

**TestFunction** (`model/TestFunction.java`)

**Fields**:
- `name`: Test function name
- `groups`: Test groups
- `dependsOn`: Function dependencies
- `enable`: Whether test is enabled
- `dataProvider`: Data provider function
- `before`: Before function
- `after`: After function
- `location`: Source location

**FunctionTreeNode** (`model/FunctionTreeNode.java`)

**Purpose**: Tree node for test function hierarchy

**Fields**:
- `name`: Function name
- `children`: Child tests (dependencies)
- `parent`: Parent test
- `metadata`: Test metadata
- `codedata`: Code reference

**Annotation** (`model/Annotation.java`)

**Purpose**: Test annotation representation

**AnnotationField** (`model/AnnotationField.java`)

**Purpose**: Annotation field (groups, dependsOn, etc.)

**Property** (`model/Property.java`)

**Purpose**: Test property/configuration

**Metadata** (`model/Metadata.java`)

**Purpose**: Test function metadata

**FunctionParameter** (`model/FunctionParameter.java`)

**Purpose**: Test function parameter

**Codedata** (`model/Codedata.java`)

**Purpose**: Code reference for test function

#### Request/Response Models

**TestsDiscoveryRequest** (`request/TestsDiscoveryRequest.java`)
```java
public record TestsDiscoveryRequest(String filePath) {}
```

**TestsDiscoveryResponse** (`response/TestsDiscoveryResponse.java`)
```java
public class TestsDiscoveryResponse {
    private Map<String, List<FunctionTreeNode>> groupsToFunctions;
    private List<Diagnostic> diagnostics;
    // Factory methods: from(map), from(throwable)
}
```

**GetTestFunctionRequest** (`request/GetTestFunctionRequest.java`)

**GetTestFunctionResponse** (`response/GetTestFunctionResponse.java`)

**AddTestFunctionRequest** (`request/AddTestFunctionRequest.java`)

**UpdateTestFunctionRequest** (`request/UpdateTestFunctionRequest.java`)

**CommonSourceResponse** (`response/CommonSourceResponse.java`)
```java
public class CommonSourceResponse {
    private List<TextEdit> textEdits;
    private List<Diagnostic> diagnostics;
}
```

### Utilities

**Utils** (`Utils.java`)

**Purpose**: Test management utilities

**Constants** (`Constants.java`)

**Purpose**: Shared constants

## Extension Points / APIs

### LSP Service SPI

**Registration**:
```java
@JavaSPIService("org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService")
@JsonSegment("testManagerService")
public class TestManagerService implements ExtendedLanguageServerService
```

### Client Integration

**From VS Code Extension**:
```typescript
// Discover tests in file
const response = await client.sendRequest(
    'testManagerService/discoverInFile',
    { filePath: document.uri.fsPath }
);

// Render test tree
const testTree = buildTestTree(response.groupsToFunctions);
testExplorer.updateTree(testTree);

// Add new test function
const addResponse = await client.sendRequest(
    'testManagerService/addTestFunction',
    {
        filePath: document.uri.fsPath,
        functionName: 'testNewFeature',
        groups: ['unit'],
        location: { line: 50, character: 0 }
    }
);

// Apply text edits
await workspace.applyEdit({ changes: { [documentUri]: addResponse.textEdits } });
```

## Dependencies

### Module Dependencies
- **langserver-commons**: LSP extension interfaces
- **ballerina-tools-api**: Syntax tree and semantic model API

### External Libraries
- **org.eclipse.lsp4j**: LSP protocol types

## Common Patterns

### 1. Tree Structure Pattern
- Test functions organized in tree
- Parent-child relationships via dependsOn
- Grouping via test groups

### 2. Visitor Pattern
- TestFunctionsFinder traverses syntax tree
- Identifies test functions
- Extracts annotations

### 3. Request-Response Pattern
- Dedicated request/response for each operation
- Type-safe parameter passing

## File Locations

- **Source**: `test-manager-service/modules/test-manager-service-ls-extension/src/main/java/`
  - `io/ballerina/testmanagerservice/extension/`: Service implementation
  - `io/ballerina/testmanagerservice/extension/model/`: Data models
  - `io/ballerina/testmanagerservice/extension/request/`: Request models
  - `io/ballerina/testmanagerservice/extension/response/`: Response models
- **Build**: `test-manager-service/modules/test-manager-service-ls-extension/build.gradle`

## Important Notes for AI Assistants

1. **Test Discovery**: Finds tests via @test annotation
2. **Test Groups**: Organizes tests into groups
3. **Test Dependencies**: Tracks dependsOn relationships
4. **Tree Structure**: Tests organized as tree for visualization
5. **Code Generation**: Generates test function templates
6. **Annotation Parsing**: Extracts @test:Config fields
7. **Visual Tools**: Enables test explorer UI in IDEs
8. **TDD Support**: Facilitates test-driven development workflows

## Related Modules

- **langserver-core**: Language server hosting this extension
- **ballerina-test**: Test framework and annotations
- **VS Code Extension**: Test explorer and test management UI
