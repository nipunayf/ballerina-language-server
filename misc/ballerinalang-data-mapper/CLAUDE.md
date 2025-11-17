# ballerinalang-data-mapper

## Module Overview

**Purpose**: AI-powered code action provider for automatic data mapping between incompatible Ballerina types. This module integrates with the language server to offer intelligent code suggestions that generate mapping functions to transform data from one type to another using AI/LLM capabilities.

**Module Name**: `org.ballerinalang.datamapper`

**Type**: Language Server Code Action Provider (SPI implementation)

**Size**: 9 Java source files

## Key Responsibilities

- **AI-Powered Data Mapping**: Generate mapping functions using AI to transform between incompatible types
- **Code Action Provider**: Implement diagnostic-based code actions for type incompatibility errors
- **HTTP Communication**: Communicate with external AI services for mapping generation
- **Default Value Generation**: Generate appropriate default values for Ballerina types
- **Client Configuration**: Manage AI data mapper configuration and feature enablement

## Architecture

### Entry Points

**AIDataMapperCodeAction** (`AIDataMapperCodeAction.java`)
- Main SPI implementation for code actions
- Implements `DiagnosticBasedCodeActionProvider`
- Triggered by "incompatible types" compiler diagnostics
- Registers via ServiceLoader: `@JavaSPIService("org.ballerinalang.langserver.commons.codeaction.spi.DiagnosticBasedCodeActionProvider")`

**Key Methods**:
- `validate(Diagnostic, DiagBasedPositionDetails, CodeActionContext)`: Pre-validate if action applicable
- `getCodeActions(Diagnostic, DiagBasedPositionDetails, CodeActionContext)`: Generate mapping code actions
- `isEnabled(LanguageServerContext)`: Check if AI data mapper is enabled in client config
- `getName()`: Returns "AI Data Mapper"

### Core Components

#### 1. Code Action Utility

**AIDataMapperCodeActionUtil** (`AIDataMapperCodeActionUtil.java`)
- Core logic for generating data mapping code actions
- Analyzes type incompatibility and constructs mapping requests
- Communicates with AI service endpoint
- Processes AI responses and generates Ballerina code

**Key Responsibilities**:
- Extract source and target types from diagnostic
- Validate type compatibility scenarios
- Construct HTTP requests to AI service
- Parse AI-generated mapping code
- Create TextEdit for inserting generated function
- Handle errors and timeouts gracefully

**Key Methods**:
- Type extraction from diagnostic properties
- Variable name generation for mapping functions
- Code insertion point calculation
- HTTP communication with AI endpoint

#### 2. Default Value Generator

**DefaultValueGenerator** (`utils/DefaultValueGenerator.java`)
- Generates default/sample values for Ballerina types
- Supports primitive and complex types
- Used for creating test data and examples

**Supported Types**:
- Primitives: int, float, boolean, string, decimal
- Complex: records, arrays, maps, tuples
- Special: nil, error, xml, json

**Key Methods**:
- `getDefaultValueForType(TypeSymbol)`: Generate default value string
- Type-specific generators for each category
- Recursive handling of nested types

#### 3. HTTP Client Integration

**HttpClientRequest** (`utils/HttpClientRequest.java`)
- HTTP client for communicating with AI mapping service
- Handles request/response serialization
- Manages timeouts and error handling

**HttpResponse** (`utils/HttpResponse.java`)
- Response wrapper for HTTP calls
- Contains status code and response body
- Handles success/failure scenarios

#### 4. Configuration

**DataMapperConfig** (`config/DataMapperConfig.java`)
- Configuration model for AI data mapper
- Fields:
  - `enabled`: Whether data mapper is active
  - `endpoint`: AI service endpoint URL
  - `timeout`: Request timeout duration

**ClientExtendedConfigImpl** (`config/ClientExtendedConfigImpl.java`)
- Extended client configuration holder
- Implements configuration interface
- Accessed via `LSClientConfigHolder`

#### 5. Node Visitor

**DataMapperNodeVisitor** (`DataMapperNodeVisitor.java`)
- Syntax tree visitor for analyzing code context
- Identifies mapping opportunities
- Extracts relevant code information for AI context

## Extension Points / SPIs

### 1. DiagnosticBasedCodeActionProvider SPI

**Implementation**: AIDataMapperCodeAction

**Registration**: META-INF/services/org.ballerinalang.langserver.commons.codeaction.spi.DiagnosticBasedCodeActionProvider

**Purpose**: Provide code actions for type mismatch diagnostics

**Interface Methods**:
```java
boolean validate(Diagnostic diagnostic, DiagBasedPositionDetails positionDetails,
                CodeActionContext context)
List<CodeAction> getCodeActions(Diagnostic diagnostic,
                               DiagBasedPositionDetails positionDetails,
                               CodeActionContext context)
String getName()
boolean isEnabled(LanguageServerContext serverContext)
```

## Dependencies

### Module Dependencies
- **langserver-commons**: Language server SPIs and interfaces
- **ballerina-lang**: Language core for symbols and types

### External Libraries
- **gson**: JSON serialization for AI service communication
- HTTP client libraries for external API calls

## Common Patterns

### 1. Service Provider Interface (SPI)
- Implements DiagnosticBasedCodeActionProvider
- Registered via Java ServiceLoader
- Loaded dynamically by language server core

### 2. Diagnostic-Based Triggering
- Activates on specific compiler diagnostics
- Filters for "incompatible types" messages
- Extracts type information from diagnostic properties

### 3. AI Service Integration
- HTTP-based communication with AI endpoint
- Request construction with type context
- Response parsing and code generation
- Timeout and error handling

### 4. Configuration-Driven
- Feature can be enabled/disabled via client config
- Configurable AI service endpoint
- Timeout settings for API calls

### 5. Code Generation
- Generates complete function definitions
- Inserts mapping logic based on AI response
- Creates appropriate function signatures
- Handles edge cases and error scenarios

## Development Guidelines

### Enabling AI Data Mapper

The data mapper is controlled by client configuration:

```json
{
  "ballerina": {
    "dataMapper": {
      "enabled": true,
      "endpoint": "https://ai-service.example.com/map",
      "timeout": 5000
    }
  }
}
```

### How Code Actions Are Triggered

1. **User writes code with type mismatch**:
   ```ballerina
   type Person record {
       string name;
       int age;
   };

   type Employee record {
       string fullName;
       int yearsOld;
   };

   Person p = {...};
   Employee e = p;  // Type mismatch - triggers diagnostic
   ```

2. **Compiler emits diagnostic**: "incompatible types: expected 'Employee', found 'Person'"

3. **AIDataMapperCodeAction validates**: Checks if diagnostic contains "incompatible types"

4. **Code action generated**: "Generate mapping function"

5. **User accepts code action**: AI service called to generate mapping

6. **Function inserted**:
   ```ballerina
   function mapPersonToEmployee(Person source) returns Employee {
       return {
           fullName: source.name,
           yearsOld: source.age
       };
   }
   ```

### Adding Custom Default Values

To extend default value generation:

1. **Update DefaultValueGenerator**
2. **Add new type kind handling**
3. **Implement type-specific logic**
4. **Handle nested type scenarios**

```java
public static String getDefaultValueForType(TypeSymbol typeSymbol) {
    TypeDescKind kind = typeSymbol.typeKind();
    return switch (kind) {
        case STRING -> "\"\"";
        case INT -> "0";
        case BOOLEAN -> "false";
        // Add new types here
        default -> "()";
    };
}
```

## Usage Examples

### Example 1: AI Mapping Workflow

User code:
```ballerina
type Source record {
    string firstName;
    string lastName;
    int age;
};

type Target record {
    string fullName;
    int years;
};

Source src = {firstName: "John", lastName: "Doe", age: 30};
Target tgt = src;  // Error: incompatible types
```

Code action appears: "Generate mapping function"

After accepting, generated:
```ballerina
function mapSourceToTarget(Source source) returns Target {
    return {
        fullName: source.firstName + " " + source.lastName,
        years: source.age
    };
}

Target tgt = mapSourceToTarget(src);  // Fixed
```

### Example 2: Configuration Check

```java
// Check if data mapper is enabled
LanguageServerContext context = ...;
ClientExtendedConfigImpl config = LSClientConfigHolder.getInstance(context)
    .getConfigAs(ClientExtendedConfigImpl.class);

if (config.getDataMapper().isEnabled()) {
    // Data mapper is active
    String endpoint = config.getDataMapper().getEndpoint();
    int timeout = config.getDataMapper().getTimeout();
}
```

## File Locations

- **Source**: `misc/ballerinalang-data-mapper/src/main/java/org/ballerinalang/datamapper/`
  - `AIDataMapperCodeAction.java`: Main SPI implementation
  - `AIDataMapperCodeActionUtil.java`: Core logic
  - `config/`: Configuration classes
  - `utils/`: Utility classes (HTTP, default values)
  - `DataMapperNodeVisitor.java`: Syntax tree visitor
- **Build**: `misc/ballerinalang-data-mapper/build.gradle`
- **SPI Registration**: `src/main/resources/META-INF/services/`

## Important Notes for AI Assistants

1. **AI/LLM Integration**: This module is designed to work with external AI services for code generation
2. **Diagnostic-Based**: Only activates on specific type incompatibility errors
3. **Configuration Required**: Must be explicitly enabled in client configuration
4. **HTTP Communication**: Makes external HTTP calls - requires network access and service availability
5. **Code Action Kind**: Returns CodeActionKind.QuickFix type actions
6. **Type Property Extraction**: Relies on diagnostic properties containing type symbols
7. **Graceful Degradation**: If AI service fails, no action is shown (doesn't break IDE)
8. **Timeout Handling**: Respects configured timeout for AI service calls
9. **Generated Code Format**: Creates complete function definitions with proper signatures
10. **IDE Integration**: Works within VSCode and other LSP-compliant editors

## Related Modules

- **langserver-core**: Core language server that loads this provider
- **langserver-commons**: Shared interfaces and SPIs
- External AI service (not part of this repository)

## Configuration Schema

```typescript
interface DataMapperConfig {
  enabled: boolean;        // Enable/disable feature
  endpoint: string;        // AI service URL
  timeout: number;         // Request timeout in milliseconds
}
```

## Error Handling

- **AI Service Unavailable**: Silently fails, no code action shown
- **Timeout**: Request cancelled after configured timeout
- **Invalid Response**: Error logged, no code action generated
- **Type Extraction Failure**: Validation returns false, provider not invoked
- **Network Errors**: Caught and logged, user not notified
