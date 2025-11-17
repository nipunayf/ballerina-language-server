# xml-to-record-converter

## Module Overview

**Purpose**: Core library and LSP service for converting XML documents to Ballerina record type definitions. This module parses XML structure and generates idiomatic Ballerina record types with support for XML namespaces, attributes, nested elements, and text content.

**Module Name**: `io.ballerina.xmltorecordconverter`

**Type**: Hybrid - Utility library + LSP Extension Service

**Size**: Primary converter in `XMLToRecordConverter.java` (1400+ lines)

## Key Responsibilities

- **XML to Record Conversion**: Parse XML and generate Ballerina record type definitions
- **Namespace Handling**: Support XML namespaces with proper Ballerina annotations
- **Attribute Mapping**: Convert XML attributes to record fields with `@Attribute` annotation
- **Element Mapping**: Convert XML elements to nested records or fields
- **Text Content Support**: Handle mixed content with configurable text field names
- **LSP Service Integration**: Provide XML conversion as language server extension
- **Client/Server Capability Management**: Register and manage LSP capabilities

## Architecture

### Entry Points

**XMLToRecordConverter** (`XMLToRecordConverter.java`)
- Main conversion engine
- **Primary Method**: `convert(String, boolean, boolean, boolean, String, boolean, boolean, Map<String, String>)`
- Parameters:
  - `xmlValue`: Input XML string
  - `isRecordTypeDesc`: Generate as type descriptor (vs type definition)
  - `isClosed`: Generate closed record type
  - `forceFormatRecordFields`: Apply forced formatting
  - `textFieldName`: Field name for XML text content (default: "#content")
  - `withNameSpaces`: Include namespace support
  - `withoutAttributes`: Exclude XML attributes from conversion
  - `nameSpaceMap`: Custom namespace prefix mappings
- Returns: `XMLToRecordResponse` with generated code or diagnostics

**XMLToRecordConverterService** (`XMLToRecordConverterService.java`)
- LSP extension service implementation
- Implements `ExtendedLanguageServerService` SPI
- Exposes conversion functionality via JSON-RPC
- Registered via ServiceLoader

### Core Components

#### 1. XML Parsing and Analysis

**XML Processing**:
- Uses standard Java DOM parser (`javax.xml.parsers.DocumentBuilder`)
- Validates XML syntax and structure
- Extracts elements, attributes, namespaces
- Builds internal representation

**Supported XML Features**:
- **Elements**: Mapped to record fields or nested records
- **Attributes**: Mapped to fields with `@Attribute` annotation
- **Text Content**: Mapped to field with configurable name
- **Namespaces**: Supported with `@Namespace` annotations
- **Nested Elements**: Generate nested record types
- **Repeated Elements**: Generate array types

#### 2. Type Inference

**Element to Type Mapping**:
- **Simple Elements** (text only): Primitive types (string, int, decimal, boolean)
- **Complex Elements** (with children): Nested record types
- **Mixed Content** (text + children): Record with text field + child fields
- **Repeated Elements**: Array types
- **Optional Elements**: Optional fields (with `?`)

**Attribute Handling**:
- All attributes become record fields
- Annotated with `@xmldata:Attribute`
- Type inferred from attribute value

**Namespace Support**:
- Namespace declarations → import statements
- Element namespaces → `@xmldata:Namespace` annotations
- Namespace prefixes preserved or customizable

#### 3. Record Generation

**Record Construction Flow**:
1. Parse XML to DOM tree
2. Analyze element structure recursively
3. Generate record types bottom-up (leaves first)
4. Add namespace imports if needed
5. Format generated code
6. Return as `XMLToRecordResponse`

**Annotation Generation**:
- `@xmldata:Name`: For name mappings
- `@xmldata:Namespace`: For namespace declarations
- `@xmldata:Attribute`: For XML attributes

#### 4. LSP Service Layer

**XMLToRecordConverterService**
- JSON-RPC endpoint: `xmlToRecordConverter/convert`
- Accepts `XMLToRecordRequest` objects
- Returns `XMLToRecordResponse` objects
- Integrates with language server lifecycle

**Request/Response Models**:
- `XMLToRecordRequest`: Wraps conversion parameters
- `XMLToRecordResponse`: Contains generated code + diagnostics

**Capability Management**:
- `XMLToRecordConverterClientCapabilities`: Client capability flags
- `XMLToRecordConverterServerCapabilities`: Server capability advertising
- `XMLToRecordConverterClientCapabilitySetter`: Registers client capabilities
- `XMLToRecordConverterServerCapabilitySetter`: Registers server capabilities

#### 5. Diagnostic System

**DiagnosticMessage** (`diagnostic/DiagnosticMessage.java`)
- Diagnostic codes for XML conversion:
  - `INVALID_XML`: Malformed XML syntax
  - `PARSER_EXCEPTION`: XML parsing error
  - `CONVERSION_EXCEPTION`: Conversion logic error
  - `EMPTY_XML`: Empty XML input

**DiagnosticUtils** (`diagnostic/DiagnosticUtils.java`)
- Create and format diagnostics
- Track error positions in XML

#### 6. Utility Classes

**ConverterUtils** (`util/ConverterUtils.java`)
- Helper methods:
  - `escapeIdentifier()`: Escape invalid Ballerina identifiers
  - `getPrimitiveTypeName()`: Infer Ballerina type from string value
  - `extractTypeDescriptorNodes()`: Extract type syntax nodes
  - `extractUnionTypeDescNode()`: Create union types
  - `sortTypeDescriptorNodes()`: Sort union members

**Constants** (`Constants.java`)
- XML-specific constants:
  - Default text field name: `#content`
  - XML namespace prefixes
  - Annotation names

## Extension Points / SPIs

### 1. ExtendedLanguageServerService SPI

**Implementation**: XMLToRecordConverterService

**Registration**: META-INF/services/org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService

**Annotation**: `@JavaSPIService` + `@JsonSegment("xmlToRecordConverter")`

**Methods**:
```java
@JsonRequest
CompletableFuture<XMLToRecordResponse> convert(XMLToRecordRequest request);
```

### 2. Client Capability Management

**Implementation**: XMLToRecordConverterClientCapabilitySetter

**Registration**: META-INF/services for BallerinaClientCapabilitySetter

**Purpose**: Register client-side capability support

### 3. Server Capability Management

**Implementation**: XMLToRecordConverterServerCapabilitySetter

**Registration**: META-INF/services for BallerinaServerCapabilitySetter

**Purpose**: Advertise server-side capability support

## Dependencies

### Module Dependencies
- **ballerina-parser**: Syntax tree construction
- **formatter-core**: Code formatting

### External Libraries
- **commons-lang3**: String utilities
- Java XML parsers (javax.xml)

## Common Patterns

### 1. DOM Parsing
- Uses standard Java DOM API
- Tree-based XML processing
- XPath-like navigation

### 2. Bottom-Up Generation
- Processes leaf elements first
- Builds complex types from simple types
- Recursive type construction

### 3. Annotation-Based Mapping
- Uses Ballerina annotations for XML metadata
- Preserves XML semantics in record types
- Configurable via conversion options

### 4. LSP Integration
- Service Provider Interface pattern
- JSON-RPC method exposure
- Asynchronous CompletableFuture responses

### 5. Error Handling
- Collects diagnostics rather than throwing
- Graceful degradation on parse errors
- Detailed error context

## Development Guidelines

### Basic XML to Record Conversion

```java
String xml = """
<person>
  <name>John Doe</name>
  <age>30</age>
  <email>john@example.com</email>
</person>
""";

XMLToRecordResponse response = XMLToRecordConverter.convert(
    xml,
    false,  // isRecordTypeDesc
    false,  // isClosed
    true,   // forceFormatRecordFields
    "#content",  // textFieldName
    false,  // withNameSpaces
    false,  // withoutAttributes
    Map.of()  // nameSpaceMap
);

// Generated:
// type Person record {
//     string name;
//     int age;
//     string email;
// };
```

### XML with Attributes

```java
String xml = """
<book id="123" language="en">
  <title>Ballerina Guide</title>
  <price>29.99</price>
</book>
""";

XMLToRecordResponse response = XMLToRecordConverter.convert(
    xml, false, false, true, "#content", false, false, Map.of()
);

// Generated:
// import ballerina/data.xmldata;
//
// type Book record {
//     @xmldata:Attribute
//     string id;
//     @xmldata:Attribute
//     string language;
//     string title;
//     decimal price;
// };
```

### XML with Namespaces

```java
String xml = """
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <GetPrice xmlns="http://example.com/stock">
      <StockName>GOOG</StockName>
    </GetPrice>
  </soap:Body>
</soap:Envelope>
""";

XMLToRecordResponse response = XMLToRecordConverter.convert(
    xml, false, false, true, "#content", true, false, Map.of()
);

// Generates records with @xmldata:Namespace annotations
```

### Using LSP Service

From language server client (e.g., VS Code):

```typescript
// Request to language server
const request = {
  xmlValue: "<root><item>value</item></root>",
  isRecordTypeDesc: false,
  isClosed: false,
  forceFormatRecordFields: true,
  textFieldName: "#content",
  withNameSpaces: false,
  withoutAttributes: false
};

const response = await client.sendRequest(
  'xmlToRecordConverter/convert',
  request
);

console.log(response.codeBlock);
```

### Excluding Attributes

```java
String xml = """
<config debug="true" version="1.0">
  <host>localhost</host>
  <port>8080</port>
</config>
""";

// With attributes (default)
XMLToRecordResponse withAttrs = XMLToRecordConverter.convert(
    xml, false, false, true, "#content", false, false, Map.of()
);
// Includes: @xmldata:Attribute debug, version

// Without attributes
XMLToRecordResponse withoutAttrs = XMLToRecordConverter.convert(
    xml, false, false, true, "#content", false, true, Map.of()
);
// Only includes: host, port
```

## Usage Examples

### Example 1: SOAP Response to Record

```java
String soapResponse = """
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <GetStockPriceResponse xmlns="http://example.com/stock">
      <Price>145.50</Price>
      <Currency>USD</Currency>
      <Timestamp>2024-01-15T10:30:00Z</Timestamp>
    </GetStockPriceResponse>
  </soap:Body>
</soap:Envelope>
""";

XMLToRecordResponse response = XMLToRecordConverter.convert(
    soapResponse, false, false, true, "#content", true, false, Map.of()
);
```

### Example 2: Configuration File to Record

```java
String configXml = """
<database>
  <connection pool="10" timeout="30">
    <host>db.example.com</host>
    <port>5432</port>
    <database>myapp</database>
  </connection>
  <credentials>
    <username>admin</username>
  </credentials>
</database>
""";

XMLToRecordResponse response = XMLToRecordConverter.convert(
    configXml, false, true, true, "#content", false, false, Map.of()
);
```

### Example 3: Array Elements

```java
String xml = """
<catalog>
  <book>
    <title>Book 1</title>
  </book>
  <book>
    <title>Book 2</title>
  </book>
  <book>
    <title>Book 3</title>
  </book>
</catalog>
""";

// Generates:
// type Book record {
//     string title;
// };
//
// type Catalog record {
//     Book[] book;
// };
```

## File Locations

- **Source**: `misc/xml-to-record-converter/src/main/java/io/ballerina/xmltorecordconverter/`
  - `XMLToRecordConverter.java`: Main conversion engine
  - `XMLToRecordConverterService.java`: LSP service
  - `XMLToRecordRequest.java`, `XMLToRecordResponse.java`: Request/response models
  - `XMLToRecordConverter*Capabilities*.java`: Capability management
  - `diagnostic/`: Diagnostic messages
  - `util/`: Utility classes
- **Build**: `misc/xml-to-record-converter/build.gradle`
- **SPI Registration**: `src/main/resources/META-INF/services/`

## Important Notes for AI Assistants

1. **Dual Purpose**: This module is BOTH a utility library AND an LSP extension service
2. **XML Semantics**: Preserves XML semantics using Ballerina's xmldata annotations
3. **Namespace Complexity**: XML namespaces add significant complexity to generated code
4. **Attribute Distinction**: XML attributes vs elements require different handling
5. **Text Content**: Mixed content (text + elements) needs special handling
6. **Array Detection**: Repeated elements with same name become arrays
7. **Type Inference**: Limited - infers from string values, may need manual refinement
8. **Import Requirements**: Generated code requires `ballerina/data.xmldata` import
9. **Formatting**: Always formats output using Ballerina formatter
10. **LSP Integration**: Can be called via language server or as standalone library

## Related Modules

- **misc/json-to-record-converter**: Similar converter for JSON
- **misc/ls-extensions/modules/json-to-record-converter**: JSON LSP service
- **langserver-core**: Loads this as an extension service

## Capability Names

- **Client**: `xmlToRecordConverter`
- **Server**: `xmlToRecordConverter`
- **RPC Method**: `xmlToRecordConverter/convert`

## XML Annotation Mapping

| XML Feature | Ballerina Representation |
|------------|--------------------------|
| Element | Record field |
| Attribute | Field with `@xmldata:Attribute` |
| Namespace | Import + `@xmldata:Namespace` |
| Text content | Field with configurable name |
| Element name != field | `@xmldata:Name` annotation |
| Repeated element | Array field |

## Performance Considerations

- **DOM Parsing**: Loads entire XML into memory
- **Large XML**: May struggle with very large documents
- **Deep Nesting**: Recursion depth limited by stack size
- **Namespace Resolution**: Adds processing overhead
- **Formatting**: Formatter adds latency to response
