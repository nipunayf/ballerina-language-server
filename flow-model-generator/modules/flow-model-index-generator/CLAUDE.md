# flow-model-index-generator

## Module Overview

**Purpose**: Build-time indexer that generates SQLite databases containing metadata for Ballerina functions, connectors, types, and services. Creates searchable indexes consumed by flow-model-generator-core and model-generator-commons for fast lookups without semantic analysis.

**Module Name**: `io.ballerina.flowmodelgenerator.indexgenerator`

**Type**: Command-line application (runs during build/setup)

## Key Responsibilities

- **Package List Generation**: Generate list of Ballerina packages to index
- **Function Indexing**: Extract and index all functions from Ballerina packages
- **Connector Indexing**: Index connector clients and their methods
- **Type Indexing**: Index record types, classes, and objects
- **Parameter Metadata**: Extract parameter types, defaults, and documentation
- **Search Index Building**: Create full-text search indexes (FTS5) for fast queries
- **Database Generation**: Create SQLite databases (`central-index.sqlite`, `service-index.sqlite`)
- **Metadata Caching**: Cache expensive semantic analysis results

## Architecture

### Entry Points

**IndexGenerator** (`IndexGenerator.java:200+ lines`)
- Main entry point for index generation
- Method: `main(String[] args)`: CLI entry point
- Workflow:
  1. Creates database schema
  2. Reads package list from JSON
  3. Resolves packages from Ballerina Central
  4. Analyzes each package with semantic model
  5. Extracts functions, connectors, parameters
  6. Inserts into SQLite database
  7. Creates search indexes

**Run Command**:
```bash
java -jar flow-model-index-generator.jar
```

### Core Components

#### 1. Package List Generator

**PackageListGenerator** (`PackageListGenerator.java`)

**Purpose**: Generate list of packages to index from Ballerina Central

**Workflow**:
1. Queries Ballerina Central API
2. Filters packages (ballerina/*, ballerinax/*)
3. Groups by organization
4. Writes to `packages.json`

**Output Format**:
```json
{
  "ballerina": [
    {"name": "http", "version": "2.8.0", "keywords": ["network", "web"]},
    {"name": "sql", "version": "1.9.0", "keywords": ["database"]}
  ],
  "ballerinax": [
    {"name": "openai.chat", "version": "1.0.0", "keywords": ["ai", "llm"]},
    {"name": "postgresql", "version": "1.5.0", "keywords": ["database", "sql"]}
  ]
}
```

**PackageMetadataInfo**:
- `name`: Package name
- `version`: Package version
- `keywords`: Search keywords

#### 2. Database Manager

**DatabaseManager** (`DatabaseManager.java:500+ lines`)

**Purpose**: Manages SQLite database operations for function/connector index

**Pattern**: Singleton

**Database Schema**:

**Package Table**:
```sql
CREATE TABLE Package (
    package_id INTEGER PRIMARY KEY AUTOINCREMENT,
    package_name TEXT NOT NULL,
    module_name TEXT NOT NULL,
    org TEXT NOT NULL,
    version TEXT NOT NULL,
    keywords TEXT,
    UNIQUE(org, package_name, module_name, version)
);
```

**Function Table**:
```sql
CREATE TABLE Function (
    function_id INTEGER PRIMARY KEY AUTOINCREMENT,
    kind TEXT NOT NULL, -- FUNCTION, CONNECTOR, LISTENER, AI_CLASS, etc.
    name TEXT NOT NULL,
    description TEXT,
    package_id INTEGER NOT NULL,
    return_type TEXT,
    resource_path TEXT,
    is_error_return INTEGER DEFAULT 0,
    is_remote INTEGER DEFAULT 0,
    FOREIGN KEY(package_id) REFERENCES Package(package_id)
);
```

**Parameter Table**:
```sql
CREATE TABLE Parameter (
    parameter_id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    description TEXT,
    type TEXT NOT NULL,
    kind TEXT NOT NULL, -- REQUIRED, DEFAULTABLE, REST, INCLUDED_RECORD
    default_value TEXT,
    placeholder TEXT,
    function_id INTEGER NOT NULL,
    import_statements TEXT,
    FOREIGN KEY(function_id) REFERENCES Function(function_id)
);
```

**ParameterMemberType Table**:
```sql
CREATE TABLE ParameterMemberType (
    member_id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT NOT NULL,
    kind TEXT NOT NULL, -- UNION, INTERSECTION member types
    parameter_id INTEGER NOT NULL,
    package_identifier TEXT,
    FOREIGN KEY(parameter_id) REFERENCES Parameter(parameter_id)
);
```

**FunctionConnector Table** (junction table):
```sql
CREATE TABLE FunctionConnector (
    function_id INTEGER NOT NULL,
    connector_id INTEGER NOT NULL,
    PRIMARY KEY(function_id, connector_id),
    FOREIGN KEY(function_id) REFERENCES Function(function_id),
    FOREIGN KEY(connector_id) REFERENCES Function(function_id)
);
```

**Key Methods**:
- `createDatabase()`: Create schema
- `insertPackage(org, name, module, version, keywords)`: Insert package
- `insertFunction(FunctionData)`: Insert function with parameters
- `insertParameter(functionId, ParameterData)`: Insert parameter
- `updateTypeParameter(module, type, replacement)`: Fix type mappings
- `executeQuery(sql)`: Execute SQL

#### 3. Search Database Manager

**SearchDatabaseManager** (`SearchDatabaseManager.java:400+ lines`)

**Purpose**: Manages SQLite FTS5 (Full-Text Search) indexes

**Pattern**: Singleton

**FTS5 Tables**:

**Function Search Table**:
```sql
CREATE VIRTUAL TABLE FunctionSearch USING fts5(
    function_id UNINDEXED,
    org,
    package_name,
    module_name,
    function_name,
    description,
    keywords,
    tokenize='porter unicode61'
);
```

**Connector Search Table**:
```sql
CREATE VIRTUAL TABLE ConnectorSearch USING fts5(
    connector_id UNINDEXED,
    org,
    package_name,
    module_name,
    connector_name,
    description,
    keywords,
    tokenize='porter unicode61'
);
```

**Type Search Table**:
```sql
CREATE VIRTUAL TABLE TypeSearch USING fts5(
    type_id UNINDEXED,
    org,
    package_name,
    module_name,
    type_name,
    description,
    keywords,
    tokenize='porter unicode61'
);
```

**Key Methods**:
- `createSearchDatabase()`: Create FTS5 schema
- `insertFunctionIndex(FunctionData)`: Index function for search
- `insertConnectorIndex(ConnectorData)`: Index connector for search
- `insertTypeIndex(TypeData)`: Index type for search
- `buildSearchIndex()`: Build from main database

**Search Features**:
- Porter stemming for English words
- Unicode support
- BM25 ranking algorithm
- Phrase search support
- Prefix matching

#### 4. Search List Generator

**SearchListGenerator** (`SearchListGenerator.java`)

**Purpose**: Generate search index from main database

**Workflow**:
1. Reads all functions from database
2. Extracts searchable metadata
3. Inserts into FTS5 tables
4. Optimizes indexes for performance

#### 5. Search Index Logger

**SearchIndexLogger** (`SearchIndexLogger.java`)

**Purpose**: Logging for index generation process

**Features**:
- Progress tracking
- Error reporting
- Statistics collection
- Performance metrics

### Indexing Workflow

**Step-by-Step Process**:

1. **Package Resolution**:
   ```java
   BuildProject sampleProject = PackageUtil.getSampleProject();
   Package pkg = PackageUtil.getModulePackage(
       sampleProject, org, packageName, version
   ).orElseThrow();
   ```

2. **Semantic Analysis**:
   ```java
   SemanticModel semanticModel = PackageUtil.getSemanticModel(moduleInfo);
   ```

3. **Symbol Extraction**:
   ```java
   for (Symbol symbol : semanticModel.moduleSymbols()) {
       if (symbol instanceof FunctionSymbol funcSymbol) {
           // Process function
       } else if (symbol instanceof ClassSymbol classSymbol) {
           // Process connector/class
       }
   }
   ```

4. **Function Metadata Extraction**:
   ```java
   FunctionData functionData = new FunctionDataBuilder()
       .semanticModel(semanticModel)
       .functionSymbol(funcSymbol)
       .moduleInfo(moduleInfo)
       .build();
   ```

5. **Database Insertion**:
   ```java
   DatabaseManager.insertFunction(functionData);
   ```

6. **Parameter Indexing**:
   ```java
   for (ParameterData param : functionData.parameters().values()) {
       DatabaseManager.insertParameter(functionId, param);
   }
   ```

7. **Search Index Building**:
   ```java
   SearchDatabaseManager.insertFunctionIndex(functionData);
   ```

## Key Classes

### PackageListGenerator

**Static Fields**:
- `PACKAGE_JSON_FILE`: Output filename
- `BALLERINA_ORG`: "ballerina"
- `BALLERINAX_ORG`: "ballerinax"

**Methods**:
- `generatePackageList()`: Main generation method
- `fetchPackagesFromCentral(org)`: Query Central API
- `filterPackages(packages)`: Filter by criteria
- `writeToJson(packages)`: Write output file

### DatabaseManager

**Database File**: `central-index.sqlite`

**Location**: Embedded in resources or generated at runtime

**Key Operations**:
- INSERT: Add new packages/functions/parameters
- UPDATE: Fix type mappings
- QUERY: Retrieve metadata for flow generator

### SearchDatabaseManager

**Database File**: `search-index.sqlite` (can be same as main database)

**Features**:
- FTS5 full-text search
- Ranked results (BM25)
- Fast prefix matching
- Multi-field search

## Extension Points / APIs

### Command-Line Interface

**Run Indexer**:
```bash
# Generate package list
java -cp indexgen.jar io.ballerina.indexgenerator.PackageListGenerator

# Build index
java -jar indexgen.jar

# Output: central-index.sqlite
```

### Programmatic API

```java
// Create database
DatabaseManager.createDatabase();

// Insert package
int packageId = DatabaseManager.insertPackage("ballerina", "http", "http", "2.8.0", "network,web");

// Insert function
FunctionData funcData = ...; // Build function data
DatabaseManager.insertFunction(funcData);

// Build search index
SearchListGenerator.buildIndex();
```

## Dependencies

### Module Dependencies
- **model-generator-commons**: FunctionDataBuilder, PackageUtil, utilities
- **ballerina-tools-api**: Project and semantic model API
- **ballerina-lang**: Language core

### External Libraries
- **sqlite-jdbc**: SQLite database driver
- **gson**: JSON parsing for package list

## Common Patterns

### 1. Singleton Pattern
- DatabaseManager and SearchDatabaseManager are singletons
- Single database connection throughout lifecycle

### 2. Builder Pattern
- Uses FunctionDataBuilder for metadata extraction
- Incremental construction of complex objects

### 3. Batch Processing Pattern
- Processes packages in parallel (ForkJoinPool)
- Maximizes throughput

### 4. Resource Management Pattern
- Try-with-resources for database connections
- Automatic cleanup

### 5. Factory Pattern
- Creates sample projects for package resolution
- Standardized project setup

## Development Guidelines

### Adding a New Index Table

1. **Define Schema**:
   ```java
   // In DatabaseManager.createDatabase()
   stmt.execute("""
       CREATE TABLE MyEntity (
           entity_id INTEGER PRIMARY KEY,
           name TEXT NOT NULL,
           metadata TEXT
       )
   """);
   ```

2. **Add Insert Method**:
   ```java
   public static int insertMyEntity(String name, String metadata) {
       String sql = "INSERT INTO MyEntity (name, metadata) VALUES (?, ?)";
       try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
           pstmt.setString(1, name);
           pstmt.setString(2, metadata);
           pstmt.executeUpdate();
           return getLastInsertId();
       }
   }
   ```

3. **Add to Indexing Workflow**:
   ```java
   // In IndexGenerator.main()
   MyEntity entity = extractMyEntity(symbol);
   DatabaseManager.insertMyEntity(entity.name(), entity.metadata());
   ```

### Adding to Search Index

1. **Create FTS5 Table**:
   ```java
   // In SearchDatabaseManager
   stmt.execute("""
       CREATE VIRTUAL TABLE MyEntitySearch USING fts5(
           entity_id UNINDEXED,
           name,
           description,
           tokenize='porter unicode61'
       )
   """);
   ```

2. **Add Indexing Method**:
   ```java
   public static void indexMyEntity(MyEntity entity) {
       String sql = "INSERT INTO MyEntitySearch VALUES (?, ?, ?)";
       try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
           pstmt.setInt(1, entity.id());
           pstmt.setString(2, entity.name());
           pstmt.setString(3, entity.description());
           pstmt.executeUpdate();
       }
   }
   ```

## Usage Examples

### Generate Index

```bash
# Step 1: Generate package list
cd flow-model-index-generator
./gradlew run -PmainClass=io.ballerina.indexgenerator.PackageListGenerator

# Step 2: Build index
./gradlew run -PmainClass=io.ballerina.indexgenerator.IndexGenerator

# Output: central-index.sqlite in resources
```

### Query Index (from model-generator-commons)

```java
// Get database instance
DatabaseManager db = DatabaseManager.getInstance();

// Search functions
List<FunctionData> functions = db.searchFunctions(
    "http get",
    50,  // limit
    0,   // offset
    "ballerina"  // org filter
);

// Get specific function
Optional<FunctionData> func = db.getFunction(
    "ballerina",
    "http",
    "http",
    "Client.get",
    "CONNECTOR",
    "2.8.0"
);

// Get function parameters
List<ParameterData> params = db.getFunctionParameters(functionId);
```

### Full-Text Search

```java
// Get search database
SearchDatabaseManager searchDb = SearchDatabaseManager.getInstance();

// Search functions
List<SearchResult> results = searchDb.searchFunctions(
    "send email",
    null,  // no org filter
    20,    // limit
    0      // offset
);

// Results ranked by BM25 relevance
for (SearchResult result : results) {
    System.out.println(result.getName() + " - " + result.getScore());
}
```

## File Locations

- **Source**: `flow-model-generator/modules/flow-model-index-generator/src/main/java/`
  - `io/ballerina/indexgenerator/`: Indexer implementation
- **Resources**: `flow-model-generator/modules/flow-model-index-generator/src/main/resources/`
  - `packages.json`: Package list to index
  - `central-index.sqlite`: Generated database (if bundled)
- **Build**: `flow-model-generator/modules/flow-model-index-generator/build.gradle`

## Important Notes for AI Assistants

1. **Build-Time Tool**: This runs during build, not at runtime
2. **Database Output**: Generates SQLite databases for consumption by other modules
3. **Semantic Analysis**: Uses full semantic model for accurate metadata
4. **Package Resolution**: Downloads packages from Ballerina Central
5. **Parallel Processing**: Uses ForkJoinPool for performance
6. **FTS5 Search**: Full-text search with ranking and stemming
7. **Singleton DBs**: Database managers are singletons
8. **Type Fixes**: Includes workarounds for type parameter issues
9. **Resource Intensive**: Requires significant memory and network
10. **Periodic Updates**: Should be re-run when new packages are published

## Performance Considerations

- **Parallel Processing**: Uses all CPU cores for package analysis
- **Batch Inserts**: Groups database operations for efficiency
- **FTS5 Indexing**: Creates optimized full-text search indexes
- **Memory Usage**: Loads entire semantic models (can be large)
- **Network I/O**: Downloads packages from Central
- **Disk I/O**: Writes large SQLite databases

## Optimization Strategies

1. **Incremental Updates**: Only re-index changed packages
2. **Caching**: Cache downloaded packages locally
3. **Selective Indexing**: Index only popular packages
4. **Compression**: Use SQLite compression for database
5. **Batch Size Tuning**: Optimize batch sizes for inserts

## Testing

### Manual Testing

```bash
# Run indexer
./gradlew :flow-model-index-generator:run

# Verify database
sqlite3 central-index.sqlite
> SELECT COUNT(*) FROM Function;
> SELECT * FROM Package LIMIT 10;
> SELECT * FROM FunctionSearch WHERE FunctionSearch MATCH 'http get';
```

### Automated Testing

- Verify schema creation
- Test package resolution
- Validate function extraction
- Test search queries
- Check data integrity

## Related Modules

- **model-generator-commons**: Primary consumer of generated indexes
- **flow-model-generator-core**: Uses indexes for fast lookups
- **flow-model-central-client**: Fetches package metadata
- **langserver-core**: Indirectly uses via model generators
