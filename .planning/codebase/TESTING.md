# Testing Patterns

**Analysis Date:** 2026-03-22

## Test Framework

**Runner:**
- TestNG 7.x (Standard throughout the Ballerina ecosystem)
- Config: `pom.xml` (Maven-based build)

**Assertion Library:**
- TestNG Assertions (`org.testng.Assert`)

**Run Commands:**
```bash
./mvnw clean install   # Run all tests during build
mvn test               # Standard maven test command
```

## Test File Organization

**Location:**
- Separate test source tree: `src/test/java/org/ballerinalang/langserver/...`

**Naming:**
- Files usually end in `Test.java`. Examples: `WorkspaceDiagnosticsTest.java`, `WorkspaceProjectCompletionTest.java`.

**Structure:**
```
langserver-core/src/test/java/
└── org/ballerinalang/langserver/
    ├── diagnostics/
    │   └── WorkspaceDiagnosticsTest.java
    └── workspace/
        └── TestWorkspaceManager.java  # Custom extension for testing
```

## Test Structure

**Suite Organization:**
```java
public class WorkspaceDiagnosticsTest {
    private final LanguageServerContext serverContext = new LanguageServerContextImpl();
    private final BallerinaWorkspaceManager workspaceManager = new BallerinaWorkspaceManager(serverContext);

    @BeforeClass
    public void init() {
        // Setup code
    }

    @Test(description = "Description here", dataProvider = "data-provider-name")
    public void testMethod() {
        // Assertion code
    }
}
```

**Patterns:**
- `@BeforeClass` / `@AfterClass` for managing expensive shared resources like the Language Server instance.
- Extensive use of `@Test(dataProvider = "...")` for parameter-driven testing.

## Mocking

**Framework:** Mockito

**Patterns:**
```java
// Typical Mockito usage in LS tests
MockSettings mockSettings = Mockito.withSettings().stubOnly();
ExtendedLanguageClient languageClient = Mockito.mock(ExtendedLanguageClient.class, mockSettings);

// Verifying interactions
Mockito.verify(mockClient).showMessage(any());

// Stubbing behavior
Mockito.when(mockMemoryMXBean.getHeapMemoryUsage()).thenReturn(mockHeapMemoryUsage);
```

**What to Mock:**
- LS Client interfaces (`ExtendedLanguageClient`).
- JVM/System-level beans (e.g., `MemoryMXBean`).
- Complex external services that are difficult to set up in isolation.

**What NOT to Mock:**
- Core Ballerina compiler components (prefer real instances using `io.ballerina.projects.Project` and `BallerinaWorkspaceManager`).

## Fixtures and Factories

**Test Data:**
```java
// Resource directories for test data
private final Path testRoot = FileUtils.RES_DIR.resolve("diagnostics").resolve("workspace-diag");
```

**Location:**
- Typically in `langserver-core/src/test/resources/`.

## Coverage

**Requirements:**
- Not strictly enforced in documentation, but broad coverage is maintained for core components.

**View Coverage:**
```bash
mvn jacoco:report
```

## Test Types

**Unit Tests:**
- Focus on individual components like `MemoryUsageMonitor`.

**Integration Tests:**
- Most LS tests are integration tests that spin up a Language Server instance and simulate JSON-RPC requests using `TestUtil.initializeLanguageSever()`.

## Common Patterns

**Async Testing:**
- Use `CompletableFuture` handling when calling LS methods that return futures.

**Error Testing:**
- Assertions on diagnostic collections to verify errors are correctly identified or cleared after changes.

---

*Testing analysis: 2026-03-22*
