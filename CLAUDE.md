# CLAUDE.md - AI Assistant Guide for Ballerina Language Server

This document provides comprehensive guidance for AI assistants working on the Ballerina Language Server codebase. It covers the repository structure, development workflows, coding conventions, and best practices.

## Table of Contents

1. [Project Overview](#project-overview)
2. [Repository Structure](#repository-structure)
3. [Technology Stack](#technology-stack)
4. [Development Workflow](#development-workflow)
5. [Coding Conventions](#coding-conventions)
6. [Architecture Patterns](#architecture-patterns)
7. [Testing Strategy](#testing-strategy)
8. [Common Tasks](#common-tasks)
9. [Key Locations](#key-locations)

## Project Overview

**Ballerina Language Server** is a Language Server Protocol (LSP) implementation for the Ballerina programming language. It provides IDE features like auto-completion, hover, diagnostics, code actions, and more.

- **License**: Apache License 2.0
- **Managed by**: WSO2 LLC
- **Language**: Java 21
- **Build Tool**: Gradle 8.x
- **Current Version**: 1.4.2
- **Primary Package**: `org.ballerinalang.langserver`

### Project Goals

- Provide comprehensive IDE support for Ballerina through LSP
- Enable extensibility through SPI-based architecture
- Support visual model generation (architecture, sequence, flow diagrams)
- Integrate with Ballerina compiler APIs for accurate language features

## Repository Structure

The repository follows a modular architecture with clear separation of concerns:

### Core Modules

```
langserver-commons/          # Common interfaces, SPIs, and contracts (~42 Java files)
├── src/main/java/org/ballerinalang/langserver/commons/
    ├── codeaction/spi/      # Code action provider interfaces
    ├── completion/spi/      # Completion provider SPI
    ├── command/spi/         # Command executor SPI
    ├── codelenses/spi/      # Code lens provider SPI
    ├── service/spi/         # Extended language server service SPI
    └── workspace/           # Workspace abstractions

langserver-core/             # Main language server implementation (~507 Java files)
├── src/main/java/org/ballerinalang/langserver/
    ├── completions/         # Auto-completion providers and builders
    ├── codeaction/          # Quick fixes and refactoring actions
    ├── codelenses/          # Code lens providers
    ├── command/             # Command executors and visitors
    ├── hover/               # Hover information provider
    ├── signature/           # Signature help
    ├── definition/          # Go to definition
    ├── references/          # Find references
    ├── rename/              # Rename refactoring
    ├── documentsymbol/      # Document symbol provider
    ├── foldingrange/        # Code folding
    ├── semantictokens/      # Semantic highlighting
    ├── inlayhint/           # Inlay hints
    ├── contexts/            # Context implementations (20+ context classes)
    ├── workspace/           # Workspace and document management
    ├── diagnostic/          # Diagnostics/error reporting
    ├── eventsync/           # Event synchronization
    └── extensions/          # Ballerina-specific extensions

langserver-stdlib/           # Mock implementations for testing
launcher/                    # Entry point for the language server
```

### Extension Modules

**Model Generators** (for visual representations):
```
architecture-model-generator/
├── modules/
    ├── architecture-model-generator-core/
    ├── architecture-model-generator-plugin/
    └── architecture-model-generator-ls-extension/

sequence-model-generator/
flow-model-generator/
service-model-generator/
```

**Service Extensions**:
```
openapi-service/             # OpenAPI/Swagger integration
graphql-model-generator/     # GraphQL schema generation
test-manager-service/        # Test management functionality
xsd-service/                 # XML Schema support
wsdl-service/                # WSDL support
edi-service/                 # Electronic Data Interchange support
```

**Miscellaneous Components**:
```
misc/
├── debug-adapter/           # Debugging support (cli, core, runtime)
├── ls-extensions/           # Additional LS extensions
│   ├── bal-shell-service
│   ├── json-to-record-converter
│   ├── partial-parser
│   ├── performance-analyzer-services
│   └── trigger-service
├── diagram-util/
├── ballerinalang-data-mapper/
├── xml-to-record-converter/
└── json-to-record-converter/
```

### Total Repository Statistics

- **Total Java Files**: 1,968
- **Core Module Java Files**: 507
- **Test Files**: 164
- **Gradle Modules**: 30+
- **Supported LSP Features**: 15+
- **Extension Services**: 40+

## Technology Stack

### Core Dependencies

- **Java**: Version 21 (sourceCompatibility and targetCompatibility)
- **Eclipse LSP4J**: 0.24.0 (Language Server Protocol for Java)
- **Ballerina Lang**: 2201.13.0-snapshot
  - ballerina-lang
  - ballerina-parser
  - ballerina-tools-api
  - ballerina-runtime
  - formatter-core
  - toml-parser
  - diagram-util

### Key Libraries

- **Serialization**:
  - Gson: 2.10.1
  - Jackson: 2.15.3

- **Utilities**:
  - Apache Commons Lang3: 3.18.0
  - Commons IO: 2.15.1
  - Google Guava: 32.0.1-jre
  - Netty Buffer: 4.1.118.Final

- **Web Services**:
  - GraphQL Java: 21.5
  - Swagger Parser: 2.1.22
  - Apache XML Schema: 1.4.7
  - WSDL4J: 1.6.3

### Testing

- **TestNG**: 7.7.0 (primary test framework)
- **Mockito**: 5.14.0
- **Awaitility**: 3.1.6

### Build Tools & Code Quality

- **Gradle**: 8.x with Gradle Enterprise plugin
- **Spotbugs**: 6.0.18 (static analysis)
- **Checkstyle**: 10.12.1 (code style checking)
- **Jacoco**: Code coverage
- **Shadow JAR**: 8.1.1 (fat JAR creation)
- **CycloneDX**: 1.8.2 (SBOM generation)

## Development Workflow

### Prerequisites

1. **JDK 21**: Temurin distribution version 21.0.3 or later
2. **Ballerina**: Runtime version 2201.12.3 or later (for testing)
3. **Environment Variables**:
   - `packageUser`: GitHub username (for accessing GitHub packages)
   - `packagePAT`: GitHub Personal Access Token

### Building the Project

```bash
# Clone the repository
git clone https://github.com/ballerina-platform/ballerina-language-server.git
cd ballerina-language-server

# Build the project
./gradlew build

# Create distributable JAR
./gradlew pack

# Run tests only
./gradlew test

# Check code quality
./gradlew checkstyleMain spotbugsMain
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "org.ballerinalang.langserver.completion.CompletionTest"

# Run with verbose output
./gradlew test --info

# Generate coverage report
./gradlew jacocoTestReport
```

### CI/CD Pipeline

The project uses GitHub Actions with workflows for:

1. **Pull Request Builds** (`.github/workflows/pull-request.yml`):
   - Runs on both Ubuntu and Windows
   - Timeout: 60 minutes
   - Includes concurrency control to cancel outdated builds
   - Caches Ballerina dependencies

2. **Master Branch Builds** (`.github/workflows/build-master.yml`)
3. **Daily Builds** (`.github/workflows/daily-build.yml`)
4. **Release Publishing** (`.github/workflows/publish-release.yml`)
5. **Security Scanning** (`.github/workflows/trivy.yml`)

### Module Dependencies

The dependency chain typically follows:
```
Feature Module
  ↓ depends on
langserver-core
  ↓ depends on
langserver-commons
  ↓ depends on
Ballerina Compiler APIs
```

Extensions are loaded via Java's Service Provider Interface (SPI) mechanism.

## Coding Conventions

### Code Style (Checkstyle Rules)

The project enforces strict code style rules via Checkstyle:

1. **Line Length**: Maximum 120 characters (`build-config/checkstyle/build/checkstyle.xml:74`)
2. **Indentation**: Use spaces, NO tabs (`build-config/checkstyle/build/checkstyle.xml:30-34`)
3. **File Length**: Maximum 3000 lines (warning level)
4. **Line Endings**: Unix-style (LF) line endings
5. **Newline at EOF**: Required

### Naming Conventions

- **Package Names**: `org.ballerinalang.langserver.*` (lowercase, dot-separated)
- **Class Names**: PascalCase (e.g., `BallerinaLanguageServer`)
- **Interface Names**: PascalCase, often ending with Provider/Executor/Service
- **Method Names**: camelCase (e.g., `getCompletionItems`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `LS_ENABLE_SEMANTIC_HIGHLIGHTING`)
- **Variables**: camelCase
- **Module Names**: kebab-case (e.g., `langserver-core`, `flow-model-generator`)

### Comment Conventions

**TODO Comments**:
```java
// TODO: (TICKET-123) - Description of what needs to be done
```
- Must be named with ticket/issue reference
- Use TODO, not FIXME

**Javadoc**:
- Required for public classes and methods
- Use standard Javadoc format

**File Headers**:
```java
/*
 * Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
```

### Import Organization

- Standard Java imports first
- Third-party library imports
- Ballerina project imports
- Static imports last
- Alphabetically ordered within each group

### Method Structure

```java
/**
 * Brief description of what the method does.
 *
 * @param paramName Description of parameter
 * @return Description of return value
 */
@Override
public ReturnType methodName(ParamType paramName) {
    // Implementation
}
```

## Architecture Patterns

### 1. Service Provider Interface (SPI) Pattern

The project extensively uses Java SPI for extensibility. Extensions are registered via `META-INF/services/`:

**Example SPI Files**:
- `META-INF/services/org.ballerinalang.langserver.commons.codeaction.spi.LSCodeActionProvider`
- `META-INF/services/org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService`

**Implementing an SPI**:
1. Implement the interface (e.g., `LSCodeActionProvider`)
2. Register in `META-INF/services/[fully.qualified.interface.name]`
3. The service loader automatically discovers implementations

### 2. Context-Based Architecture

Operations use specialized context objects that carry operation-specific state:

**Key Context Classes** (in `langserver-core/src/main/java/org/ballerinalang/langserver/contexts/`):
- `CompletionContextImpl` - For completion operations
- `CodeActionContextImpl` - For code action operations
- `HoverContextImpl` - For hover operations
- `DefinitionContextImpl` - For definition lookups
- `RenameContextImpl` - For rename operations

**Context Pattern**:
```java
public interface LSContext {
    // Common context methods
    LSClientCapabilities getClientCapabilities();
    LanguageServerContext getLanguageServerContext();
}

public class FeatureContext extends AbstractContext {
    // Feature-specific state and methods
}
```

### 3. Proxy Pattern for Workspace Management

**Key Classes**:
- `BallerinaWorkspaceManagerProxy` - Interface
- `BallerinaWorkspaceManagerProxyImpl` - Implementation

This pattern separates the workspace contract from implementation, allowing for different workspace implementations.

### 4. Event-Driven Synchronization

**Location**: `langserver-core/src/main/java/org/ballerinalang/langserver/eventsync/`

**Pattern**:
- `EventPublisher` - Publishes workspace events
- `EventSubscriber` (SPI) - Subscribers react to events
- Allows modules to react to workspace changes asynchronously

### 5. Extension Points

Multiple mechanisms for extending functionality:

1. **LS Extensions**: Custom LSP extensions specific to Ballerina
2. **Extended Language Server**: Custom endpoints beyond standard LSP
3. **Language Extensions**: Pluggable language-specific features
4. **Code Action Providers**: SPI-based code action implementations
5. **Completion Providers**: SPI-based completion implementations

### 6. Model Generation Architecture

Consistent pattern across all generators:
```
feature-model-generator/
├── feature-model-generator-core/           # Core generation logic
├── feature-model-generator-ls-extension/   # LS integration layer
└── feature-model-index-generator/          # Optional indexing
```

## Testing Strategy

### Test Framework

- **Primary**: TestNG with data-driven testing
- **Test Location**: `langserver-core/src/test/`
- **Test Resources**: `langserver-core/src/test/resources/`
- **Total Test Files**: 164 Java test files

### Test Organization

Tests follow a data-driven approach with three components:

1. **Source Files**: Ballerina test files in `/source/` directories
2. **Test Configurations**: JSON files in `/config/` directories
3. **Expected Results**: Expected output files in `/expected/` directories

**Test Structure**:
```
langserver-core/src/test/resources/
├── completion/
│   ├── expression_context/
│   │   ├── config/
│   │   │   └── test_case_1.json
│   │   ├── source/
│   │   │   └── test_file.bal
│   │   └── expected/
│   │       └── test_case_1.json
├── codeaction/
├── definition/
├── hover/
└── ...
```

### Test Categories

Major test categories in `langserver-core/src/test/resources/`:
- `completion/` - ~25 subcategories
- `codeaction/` - ~50 subcategories (add-import, create-function, etc.)
- `definition/`
- `references/`
- `hover/`
- `signature/`
- `diagnostics/`
- `rename/`
- `codelens/`
- `semantictokens/`
- `inlayhint/`
- `foldingrange/`
- `documentsymbol/`
- `implementation/`
- `command/`
- `eventsync/`
- `performance/`

### Test Pattern

```java
@Test(dataProvider = "completion-data-provider")
public void test(String config, String configPath) {
    // 1. Load test config from JSON
    JsonObject configJson = FileUtils.fileContentAsObject(configPath);

    // 2. Execute language server operation
    CompletionList result = getCompletionResult(configJson);

    // 3. Compare actual vs expected results
    JsonObject expected = FileUtils.fileContentAsObject(expectedPath);
    Assert.assertEquals(result, expected);
}
```

### Test Configuration

System properties used during testing:
- `ballerina.home`: Points to test Ballerina distribution
- `responseTimeThreshold`: 2000ms default
- `org.apache.commons.logging.Log`: NoOpLog to suppress logging

### Writing Tests

**Steps to Add a New Test**:

1. Create test Ballerina source file in appropriate `/source/` directory
2. Create test configuration JSON in `/config/` directory
3. Run test to generate actual output
4. Verify output and copy to `/expected/` directory
5. Add test to data provider

**Example Test Configuration**:
```json
{
  "position": {
    "line": 5,
    "character": 10
  },
  "source": "test_file.bal"
}
```

## Common Tasks

### Adding a New LSP Feature

1. **Create Context Interface** in `langserver-commons`:
   ```java
   public interface FeatureContext extends LSContext {
       // Feature-specific methods
   }
   ```

2. **Implement Context** in `langserver-core/contexts`:
   ```java
   public class FeatureContextImpl extends AbstractContext implements FeatureContext {
       // Implementation
   }
   ```

3. **Create Feature Provider** in `langserver-core/feature/`:
   ```java
   public class FeatureProvider {
       public CompletableFuture<FeatureResult> provide(FeatureContext context) {
           // Implementation
       }
   }
   ```

4. **Register in Main Server** (`BallerinaTextDocumentService.java`):
   ```java
   @Override
   public CompletableFuture<FeatureResult> feature(FeatureParams params) {
       return CompletableFuture.supplyAsync(() -> {
           FeatureContext context = new FeatureContextImpl(...);
           return new FeatureProvider().provide(context);
       });
   }
   ```

5. **Add Tests** following the data-driven pattern

### Adding a Code Action Provider

1. **Create Provider Class** implementing `LSCodeActionProvider`:
   ```java
   @JavaSPIService("org.ballerinalang.langserver.commons.codeaction.spi.LSCodeActionProvider")
   public class MyCodeActionProvider implements LSCodeActionProvider {
       @Override
       public List<CodeAction> getCodeActions(CodeActionContext context) {
           // Implementation
       }
   }
   ```

2. **Register via SPI** in `META-INF/services/`:
   ```
   META-INF/services/org.ballerinalang.langserver.commons.codeaction.spi.LSCodeActionProvider
   ```
   Content:
   ```
   org.ballerinalang.langserver.codeaction.providers.MyCodeActionProvider
   ```

3. **Add Tests** in `langserver-core/src/test/resources/codeaction/`

### Adding a Completion Provider

Similar to code action providers, implement `LSCompletionProvider` and register via SPI.

### Adding a New Extension Service

1. **Create Service Implementation**:
   ```java
   @JavaSPIService("org.ballerinalang.langserver.commons.service.spi.ExtendedLanguageServerService")
   public class MyExtensionService implements ExtendedLanguageServerService {
       @Override
       public void init(LanguageServer langServer, LanguageServerContext context) {
           // Initialization
       }

       @Override
       public Class<?> getRemoteInterface() {
           return MyServiceInterface.class;
       }
   }
   ```

2. **Register via SPI** in `META-INF/services/`

3. **Add to Build Configuration** if creating a new module

### Modifying Dependencies

1. Update version in `gradle.properties`
2. Add dependency in appropriate `build.gradle` file:
   ```gradle
   dependencies {
       implementation "group:artifact:${versionProperty}"
   }
   ```

3. Run `./gradlew build` to verify

## Key Locations

### Essential Files

| File | Purpose | Location |
|------|---------|----------|
| Main Server Class | Language server entry point | `langserver-core/.../BallerinaLanguageServer.java:90` |
| Text Document Service | Handles text document operations | `langserver-core/.../BallerinaTextDocumentService.java` |
| Workspace Service | Handles workspace operations | `langserver-core/.../BallerinaWorkspaceService.java` |
| Workspace Manager | Manages workspace state | `langserver-core/.../BallerinaWorkspaceManager.java` |
| Client Capabilities | Stores client capabilities | `langserver-core/.../LSClientCapabilitiesImpl.java` |

### Configuration Files

| File | Purpose |
|------|---------|
| `build.gradle` | Root build configuration |
| `settings.gradle` | Module declarations |
| `gradle.properties` | Version management (100+ versions) |
| `gradle/javaProject.gradle` | Common Java project config |
| `build-config/checkstyle/build/checkstyle.xml` | Code style rules |
| `spotbugs-exclude.xml` | Spotbugs exclusions |

### Documentation

| File | Purpose |
|------|---------|
| `README.md` | Project overview |
| `docs/UserGuide.md` | End-user documentation (258 lines) |
| `docs/images/` | Feature demonstration GIFs |
| `pull_request_template.md` | PR guidelines |
| `issue_template.md` | Issue reporting template |

### Important Directories

| Directory | Purpose |
|-----------|---------|
| `langserver-commons/src/main/java/` | SPIs and interfaces |
| `langserver-core/src/main/java/` | Core implementation |
| `langserver-core/src/main/resources/META-INF/services/` | SPI registrations |
| `langserver-core/src/test/` | Test suite |
| `langserver-core/src/test/resources/` | Test data |
| `misc/` | Debug adapter and utilities |
| `build-config/` | Build configuration |

## Best Practices for AI Assistants

### When Making Changes

1. **Read Before Writing**: Always read existing files before modifying them
2. **Follow Patterns**: Observe existing code patterns and replicate them
3. **Check Tests**: Run relevant tests after changes
4. **Respect Module Boundaries**: Don't add core features to extension modules
5. **Use SPIs**: When adding extensible features, use the SPI pattern
6. **Update Tests**: Add or update tests for any feature changes

### Code Quality

1. **Run Checkstyle**: Ensure code passes `./gradlew checkstyleMain`
2. **Run Spotbugs**: Ensure code passes `./gradlew spotbugsMain`
3. **Add Javadoc**: Document public APIs with Javadoc
4. **Follow Line Length**: Keep lines under 120 characters
5. **Use Proper Imports**: Organize imports correctly

### Common Pitfalls to Avoid

1. **Don't use tabs**: Always use spaces for indentation
2. **Don't skip SPI registration**: Remember to register SPIs in META-INF/services/
3. **Don't modify module-info.java**: Checkstyle excludes it for a reason
4. **Don't hardcode paths**: Use constants and configuration
5. **Don't skip tests**: Always run tests before committing
6. **Don't mix concerns**: Keep feature logic separate from LSP protocol handling

### Understanding the Request Flow

**Typical LSP Request Flow**:
```
1. IDE sends request → BallerinaTextDocumentService
2. Service creates context → FeatureContextImpl
3. Context gathers necessary state → Workspace, Syntax Tree, Semantic Model
4. Feature provider processes → Uses Ballerina Compiler APIs
5. Result is formatted → LSP4J types
6. Response sent back to IDE
```

### Finding Code

**To find where a feature is implemented**:
1. Look in `langserver-core/src/main/java/org/ballerinalang/langserver/[feature]/`
2. Check SPI registrations in `META-INF/services/`
3. Check `BallerinaTextDocumentService` for request handlers
4. Look for tests in `langserver-core/src/test/resources/[feature]/`

**To find how to extend a feature**:
1. Look in `langserver-commons/src/main/java/.../spi/`
2. Find the relevant SPI interface
3. Check existing implementations for patterns
4. Implement and register via SPI

## Additional Resources

- **Main Repository**: https://github.com/ballerina-platform/ballerina-language-server
- **Ballerina Website**: https://ballerina.io/
- **LSP Specification**: https://microsoft.github.io/language-server-protocol/
- **Eclipse LSP4J**: https://github.com/eclipse-lsp4j/lsp4j
- **Issue Tracker**: https://github.com/ballerina-platform/ballerina-lang/issues?q=is%3Aopen+is%3Aissue+label%3AComponent%2FLanguageServer
- **Discord**: https://discord.gg/ballerinalang

## Maintenance Notes

This document was generated on 2025-11-14 by analyzing the repository structure. When making significant architectural changes, please update this document to reflect the new state of the codebase.

**Document Version**: 1.0
**Last Updated**: 2025-11-14
**Repository Version**: 1.4.2
