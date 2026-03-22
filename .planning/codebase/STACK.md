# Technology Stack

**Analysis Date:** 2026-03-22

## Languages

**Primary:**
- Java 21 - Core language for the Language Server implementation including `BallerinaWorkspaceManager.java`.

**Secondary:**
- Ballerina (various versions) - The language being supported by the server. Standard libraries and compiler APIs are heavily used.
- Groovy/Kotlin - Used in Gradle build scripts (`build.gradle`, `settings.gradle`).

## Runtime

**Environment:**
- Java Runtime Environment (JRE) 21 - Required to run the Language Server.

**Package Manager:**
- Gradle - Primary build tool and dependency manager.
- Lockfile: Not explicitly detected in the root, but uses `gradle.properties` for version management.

## Frameworks

**Core:**
- Eclipse LSP4J - Used for implementing the Language Server Protocol (LSP).
- Ballerina Compiler API - Used for semantic analysis and syntax tree manipulation within the workspace manager.

**Testing:**
- TestNG - Primary testing framework for Java code.
- Mockito - Used for mocking in unit tests.
- Awaitility - Used for testing asynchronous operations.

**Build/Dev:**
- Gradle Enterprise - Used for build scans and performance tracking.
- SpotBugs - Used for static analysis and bug detection.
- Shadow JAR - Used to create fat JARs for distribution.

## Key Dependencies

**Critical:**
- `org.eclipse.lsp4j:org.eclipse.lsp4j` - Implementation of the Language Server Protocol.
- `org.ballerinalang:ballerina-lang` - Core Ballerina language implementation.
- `org.ballerinalang:ballerina-parser` - Ballerina syntax parser.
- `org.ballerinalang:ballerina-tools-api` - APIs for Ballerina tools.

**Infrastructure:**
- `com.google.guava:guava` - Utility library used for caching (e.g., in `BallerinaWorkspaceManager.java`).
- `com.fasterxml.jackson.core:jackson-databind` - JSON processing.
- `io.netty:netty-buffer` - Used for efficient buffer management.
- `org.slf4j:slf4j-jdk14` - Logging abstraction.

## Configuration

**Environment:**
- Environment variables like `packageUser` and `packagePAT` are required for fetching dependencies from GitHub Packages.
- `JAVA_HOME` should point to a Java 21+ installation.

**Build:**
- `build.gradle`: Root and subproject build configurations.
- `settings.gradle`: Project structure and module inclusions.
- `gradle.properties`: Version definitions and environment-specific properties.

## Platform Requirements

**Development:**
- JDK 21+
- Gradle (provided via `gradlew`)

**Production:**
- Any environment supporting Java 21+. Usually deployed as part of an IDE extension (like VS Code Ballerina extension).

---

*Stack analysis: 2026-03-22*
