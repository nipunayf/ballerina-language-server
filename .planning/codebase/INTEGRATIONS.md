# External Integrations

**Analysis Date:** 2026-03-22

## APIs & External Services

**Ballerina Central:**
- Ballerina Central - The central repository for Ballerina packages.
  - SDK/Client: `org.ballerinalang:central-client` (used for package management and discovery).
  - Auth: Usually managed via `BALLERINA_CENTRAL_ACCESS_TOKEN` or configured in `Settings.toml`.

**GitHub Packages:**
- GitHub Maven Registry - Used for hosting and fetching project dependencies (e.g., standard libraries).
  - Auth: `packageUser` and `packagePAT` (configured via environment variables in `build.gradle`).

## Data Storage

**Databases:**
- Not detected in the core Language Server. The codebase is primarily for language processing and does not interact with a persistent database directly. Some extensions like `persist-service` might interact with databases through generated code, but the server itself is stateless regarding data storage.

**File Storage:**
- Local filesystem - The `BallerinaWorkspaceManager` heavily uses the local filesystem for managing Ballerina projects, source files, and temporary artifacts like heap dumps (`-XX:+HeapDumpOnOutOfMemoryError`).

**Caching:**
- Guava Cache - In-memory caching for document-to-project mappings in `BallerinaWorkspaceManager.java` (configured to expire after 10 minutes).

## Authentication & Identity

**Auth Provider:**
- Custom - Authentication for Ballerina Central is handled via tokens stored in the developer's local environment.
- GitHub Packages Auth - Environment-based authentication for dependency resolution during the build process.

## Monitoring & Observability

**Error Tracking:**
- None detected - Errors are primarily logged using `slf4j` and reported to the IDE via the LSP `window/showMessage` or `window/logMessage` notifications.

**Logs:**
- SLF4J (with JDK14 backend) - Used throughout the Language Server for diagnostic logging.
- `LSClientLogger` - A wrapper over LSP notifications used to send log messages back to the IDE client.

## CI/CD & Deployment

**Hosting:**
- Local execution - Usually bundled within IDE extensions (like VS Code or IntelliJ).

**CI Pipeline:**
- GitHub Actions - Used for automated testing and releases (inferred from the repository structure and release plugins).

## Environment Configuration

**Required env vars:**
- `packageUser` - GitHub username for fetching dependencies.
- `packagePAT` - Personal Access Token for GitHub Packages.
- `JAVA_HOME` - Path to the Java 21+ installation.

**Secrets location:**
- Not committed to the repository. Tokens are expected to be present in the development environment or CI runner's secret store.

## Webhooks & Callbacks

**Incoming:**
- Language Server Protocol (LSP) requests/notifications - The server listens for messages like `textDocument/didOpen`, `textDocument/didChange`, etc., over a JSON-RPC channel.

**Outgoing:**
- LSP Notifications/Responses - The server sends diagnostics, log messages (`window/logMessage`), and other asynchronous updates back to the client.

---

*Integration audit: 2026-03-22*
