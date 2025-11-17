# service-model-index-generator

## Module Overview

**Purpose**: Standalone tool to build SQLite database indexes of service-related metadata from Ballerina packages. Pre-indexes listeners, service types, service declarations, and initialization properties to enable fast lookups in the service-model-generator extension without requiring full semantic analysis.

**Module Name**: `io.ballerina.indexgenerator`

**Type**: Standalone indexing tool (main class executable)

## Key Responsibilities

- **Database Creation**: Create and populate `service-index.sqlite` database
- **Service Metadata Indexing**: Index listener types, service declarations, service types
- **Package Resolution**: Pull and analyze Ballerina packages from Central
- **Semantic Analysis**: Extract metadata from semantic models
- **Property Extraction**: Extract listener initialization properties and defaults
- **Parallel Processing**: Multi-threaded package analysis for performance

## Architecture

### Entry Point

**ServiceIndexGenerator** (`ServiceIndexGenerator.java`)

**Main Method**:
```java
public static void main(String[] args)
```

**Workflow**:
1. Create database schema (via DatabaseManager)
2. Load `service_artifacts.json` (list of packages to index)
3. Resolve packages from Ballerina Central
4. Extract semantic models for each module
5. Index listeners, services, service types
6. Store metadata in SQLite database

### Core Components

#### 1. Database Manager

**DatabaseManager** (`DatabaseManager.java`)

**Purpose**: SQLite database schema and CRUD operations

**Schema Tables**:

**Package** Table:
- package_id (PRIMARY KEY)
- package_name
- module_name
- org
- version
- keywords

**Listener** Table:
- listener_id (PRIMARY KEY)
- listener_kind
- package_id (FOREIGN KEY)
- module_name
- class_name
- metadata (JSON)

**ServiceDeclaration** Table:
- service_id (PRIMARY KEY)
- display_name
- listener_kind
- package_id (FOREIGN KEY)
- service_type_descriptors (JSON)
- resource_paths (JSON array)

**ServiceType** Table:
- service_type_id (PRIMARY KEY)
- type_name
- package_id (FOREIGN KEY)
- module_name
- metadata (JSON)

**ServiceFunction** Table:
- function_id (PRIMARY KEY)
- function_name
- service_type_id (FOREIGN KEY)
- kind (resource/remote)
- parameters (JSON)
- return_type
- resource_path

**ServiceInitProperty** Table:
- property_id (PRIMARY KEY)
- listener_kind
- property_name
- property_type
- is_required
- default_value
- description

**Key Methods**:
- `createDatabase()`: Initialize schema
- `insertPackage(org, name, version, keywords)`: Insert package record
- `insertListener(listenerData)`: Insert listener metadata
- `insertServiceDeclaration(serviceData)`: Insert service declaration
- `insertServiceType(typeData)`: Insert service type
- `insertServiceFunction(functionData)`: Insert service function
- `insertServiceInitProperty(propertyData)`: Insert init property

#### 2. Indexing Logic

**Package Resolution**:
- Reads `service_artifacts.json` for package list
- Uses PackageUtil.getModulePackage() to pull from Central
- ForkJoinPool for parallel processing

**Metadata Extraction**:
- Analyzes ClassSymbol for listener classes
- Identifies service types (object types with service qualifier)
- Extracts resource/remote function signatures
- Extracts init method parameters as properties
- Documents default values from syntax tree

**Service Detection**:
- Looks for classes in specific modules (http, graphql, kafka, etc.)
- Identifies listener pattern (object with `attach()` method)
- Extracts service type constraints

### Data Flow

1. **Input**: `service_artifacts.json`
   ```json
   {
       "ballerina": [
           {"name": "http", "version": "2.10.0"},
           {"name": "graphql", "version": "1.10.0"}
       ],
       "ballerinax": [
           {"name": "kafka", "version": "3.7.0"}
       ]
   }
   ```

2. **Package Resolution**: Pull packages from Ballerina Central

3. **Semantic Analysis**: Get SemanticModel for each module

4. **Metadata Extraction**:
   - Scan module symbols
   - Identify listeners (Client classes with Listener qualifier)
   - Identify service types (object types)
   - Extract init parameters
   - Extract resource/remote functions

5. **Database Insertion**: Store all metadata in SQLite

6. **Output**: `service-index.sqlite` database file

## Extension Points / APIs

### Input Configuration

**service_artifacts.json**:
```json
{
    "organization": [
        {
            "name": "package-name",
            "version": "x.y.z"
        }
    ]
}
```

**Location**: `src/main/resources/service_artifacts.json`

### Database Access

**From service-model-generator-ls-extension**:
```java
import io.ballerina.modelgenerator.commons.ServiceDatabaseManager;

// Get listeners for package
List<Listener> listeners = ServiceDatabaseManager.getInstance()
    .getListeners("ballerina", "http", "http", "2.10.0");

// Get service declarations
List<ServiceDeclaration> services = ServiceDatabaseManager.getInstance()
    .getServiceDeclarations("ballerina", "http", "http", "2.10.0");

// Get init properties for listener kind
List<ServiceInitProperty> properties = ServiceDatabaseManager.getInstance()
    .getServiceInitProperties("HTTP");
```

## Dependencies

### Module Dependencies
- **model-generator-commons**: PackageUtil, CommonUtils, ModuleInfo
- **ballerina-lang**: Semantic model API
- **ballerina-tools-api**: Compiler and project API

### External Libraries
- **sqlite-jdbc**: SQLite database access
- **gson**: JSON parsing
- **guava**: Utilities

## Usage

### Building the Index

```bash
# Run the indexer
./gradlew :service-model-generator:modules:service-model-index-generator:run

# Output: service-index.sqlite in resources directory
```

### Updating Package List

1. Edit `src/main/resources/service_artifacts.json`
2. Add new packages to index
3. Run indexer
4. Database will be updated with new metadata

**Example**:
```json
{
    "ballerina": [
        {"name": "http", "version": "2.11.0"},
        {"name": "grpc", "version": "1.10.0"}
    ],
    "ballerinax": [
        {"name": "kafka", "version": "3.8.0"},
        {"name": "rabbitmq", "version": "3.0.0"}
    ]
}
```

## Important Notes for AI Assistants

1. **Standalone Tool**: Not an LSP extension, runs as batch process
2. **Build-Time Execution**: Run during build to generate index
3. **Database Output**: Produces `service-index.sqlite` file
4. **Package Pulling**: Downloads packages from Ballerina Central
5. **Parallel Processing**: Uses ForkJoinPool for performance
6. **Semantic Analysis Required**: Needs full compilation of packages
7. **Version-Specific**: Indexes specific package versions
8. **Incremental Updates**: Can re-run to update database
9. **Read-Only Usage**: LSP extension only reads the database
10. **Resource File**: Database bundled in service-model-generator-ls-extension JAR

## Performance Considerations

- **Parallel Analysis**: ForkJoinPool with available processors
- **Package Caching**: PackageUtil caches resolved packages
- **Batch Inserts**: Database transactions for efficiency
- **Index Creation**: SQLite indexes on frequently queried columns

## File Locations

- **Source**: `service-model-generator/modules/service-model-index-generator/src/main/java/`
  - `io/ballerina/indexgenerator/`: Main indexer and database manager
- **Resources**: `service-model-generator/modules/service-model-index-generator/src/main/resources/`
  - `service_artifacts.json`: Input package list
- **Output**: `service-index.sqlite` (generated)
- **Build**: `service-model-generator/modules/service-model-index-generator/build.gradle`

## Development Guidelines

### Adding New Package to Index

1. **Update service_artifacts.json**:
   ```json
   {
       "ballerina": [
           {"name": "websocket", "version": "2.10.0"}
       ]
   }
   ```

2. **Run Indexer**:
   ```bash
   ./gradlew :service-model-generator:modules:service-model-index-generator:run
   ```

3. **Verify Database**:
   ```bash
   sqlite3 service-index.sqlite "SELECT * FROM Listener WHERE package_name='websocket';"
   ```

### Extending Database Schema

1. **Update DatabaseManager.createDatabase()**:
   ```java
   statement.execute("CREATE TABLE IF NOT EXISTS NewTable (" +
       "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
       "..." +
   ")");
   ```

2. **Add Insert Method**:
   ```java
   public static int insertNewRecord(String data) {
       // SQL INSERT
   }
   ```

3. **Update Indexing Logic** in ServiceIndexGenerator

4. **Update ServiceDatabaseManager** in model-generator-commons for queries

## Related Modules

- **service-model-generator-ls-extension**: Primary consumer of the database
- **model-generator-commons**: Provides ServiceDatabaseManager for queries
- **flow-model-index-generator**: Similar indexer for function/connector metadata
