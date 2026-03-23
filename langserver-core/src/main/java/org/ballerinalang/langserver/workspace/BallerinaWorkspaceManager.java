/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.ballerinalang.langserver.workspace;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.cache.Weigher;
import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import io.ballerina.projects.BuildOptions;
import io.ballerina.projects.Document;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.Module;
import io.ballerina.projects.Package;
import io.ballerina.projects.PackageCompilation;
import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectException;
import io.ballerina.projects.ProjectKind;
import io.ballerina.projects.environment.PackageLockingMode;
import io.ballerina.projects.util.ProjectConstants;
import io.ballerina.projects.util.ProjectPaths;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;
import org.ballerinalang.compiler.BLangCompilerException;
import org.ballerinalang.langserver.LSClientLogger;
import org.ballerinalang.langserver.LSContextOperation;
import org.ballerinalang.langserver.common.utils.CommonUtil;
import org.ballerinalang.langserver.common.utils.PathUtil;
import org.ballerinalang.langserver.commons.BallerinaCompilerApi;
import org.ballerinalang.langserver.commons.DocumentServiceContext;
import org.ballerinalang.langserver.commons.LanguageServerContext;
import org.ballerinalang.langserver.commons.client.ExtendedLanguageClient;
import org.ballerinalang.langserver.commons.eventsync.EventKind;
import org.ballerinalang.langserver.commons.eventsync.exceptions.EventSyncException;
import org.ballerinalang.langserver.commons.workspace.RunContext;
import org.ballerinalang.langserver.commons.workspace.RunResult;
import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentException;
import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentManager;
import org.ballerinalang.langserver.commons.workspace.WorkspaceManager;
import org.ballerinalang.langserver.config.LSClientConfigHolder;
import org.ballerinalang.langserver.contexts.ContextBuilder;
import org.ballerinalang.langserver.eventsync.EventSyncPubSubHolder;
import org.ballerinalang.langserver.workspace.toml.TomlHandler;
import org.ballerinalang.langserver.workspace.toml.TomlHandlerContext;
import org.ballerinalang.langserver.workspace.toml.TomlHandlerRegistry;
import org.ballerinalang.util.diagnostic.DiagnosticErrorCode;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.ballerina.projects.util.ProjectConstants.BALLERINA_TOML;

/**
 * Contains a set of utility methods to manage projects.
 *
 * @since 1.0.0
 */
public class BallerinaWorkspaceManager implements WorkspaceManager, ProjectExecutorContext, FileWatchHandlerContext,
        ProjectRegistryContext {

    private static final String FAILED_TO_LOAD_MODULE = "failed to load the module";

    protected final LSClientLogger clientLogger;
    private final LanguageServerContext serverContext;
    private final Set<Path> openedDocuments = ConcurrentHashMap.newKeySet();
    private final TomlHandlerRegistry tomlHandlerRegistry;
    private final ProjectExecutor projectExecutor;
    private final FileWatchHandler fileWatchHandler;
    private final ProjectRegistry projectRegistry;
    private boolean experimental = false;

    public BallerinaWorkspaceManager(LanguageServerContext serverContext) {
        this.serverContext = serverContext;
        this.clientLogger = LSClientLogger.getInstance(serverContext);
        this.projectRegistry = new ProjectRegistry(this);
        this.projectExecutor = new ProjectExecutor(this);
        this.fileWatchHandler = new FileWatchHandler(this);
        this.tomlHandlerRegistry = new TomlHandlerRegistry(new TomlHandlerContextImpl());

        Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().unstarted(() -> {
            projectExecutor.stopAll();
            for (ProjectContext projectContext : projectRegistry.sourceRootToProject().values()) {
                projectContext.close();
            }
        }));
    }

    @Override
    public Optional<String> relativePath(Path path) {
        Optional<Document> document = this.document(path);
        return document.map(Document::name);
    }

    @Override
    public Optional<String> relativePath(Path path, @Nonnull CancelChecker cancelChecker) {
        Optional<Document> document = this.document(path, cancelChecker);
        return document.map(Document::name);
    }

    /**
     * Returns a project root from the path provided.
     *
     * @param filePath ballerina project or standalone file path
     * @return project root
     */
    @Override
    public Path projectRoot(Path filePath) {
        return projectRegistry.projectRoot(filePath);
    }

    @Override
    public Path projectRoot(Path filePath, @Nonnull CancelChecker cancelChecker) {
        cancelChecker.checkCanceled();
        return projectRegistry.projectRoot(filePath);
    }

    /**
     * Returns project from the path provided.
     *
     * @param filePath ballerina project or standalone file path
     * @return project of applicable type
     */
    @Override
    public Optional<Project> project(Path filePath) {
        return projectRegistry.projectContext(projectRegistry.projectRoot(filePath)).map(ProjectContext::project);
    }

    /**
     * Loads the project from the path provided.
     *
     * @param filePath ballerina project or standalone file path
     * @return project of applicable type
     */
    @Override
    public Project loadProject(Path filePath) throws ProjectException, WorkspaceDocumentException, EventSyncException {
        Optional<Project> optionalProject = project(filePath);
        if (optionalProject.isPresent()) {
            return optionalProject.get();
        }

        Path root = projectRegistry.projectRoot(filePath);
        ProjectContext projectContext =
                projectRegistry.getOrCreateProjectOrThrow(root, filePath, LSContextOperation.LOAD_PROJECT.getName());

        DocumentServiceContext context = ContextBuilder.buildDocumentServiceContext(
                filePath.toUri().toString(),
                this,
                LSContextOperation.LOAD_PROJECT, this.serverContext);
        EventSyncPubSubHolder.getInstance(this.serverContext)
                .getPublisher(EventKind.PROJECT_UPDATE)
                .publish(this.serverContext.get(ExtendedLanguageClient.class), this.serverContext, context);
        return projectContext.project();
    }

    /**
     * Returns module from the path provided.
     *
     * @param filePath file path of the document
     * @return project of applicable type
     */
    @Override
    public Optional<Module> module(Path filePath) {
        return projectRegistry.projectContext(projectRegistry.projectRoot(filePath))
                .flatMap(context -> context.withReadLock(ctx -> {
                    Optional<Project> project = project(filePath);
                    if (project.isEmpty()) {
                        return Optional.empty();
                    }
                    Optional<Document> document = document(filePath, project.get(), null);
                    if (document.isEmpty()) {
                        if (filePath.equals(projectRegistry.projectRoot(filePath))) {
                            return Optional.of(project.get().currentPackage().getDefaultModule());
                        }
                        return Optional.<Module>empty();
                    }
                    return Optional.of(document.get().module());
                }));
    }

    @Override
    public Optional<Module> module(Path filePath, @Nonnull CancelChecker cancelChecker) {
        cancelChecker.checkCanceled();
        return projectRegistry.projectContext(projectRegistry.projectRoot(filePath))
                .flatMap(context -> context.withReadLock(ctx -> {
                    Optional<Project> project = project(filePath);
                    if (project.isEmpty()) {
                        return Optional.empty();
                    }
                    Optional<Document> document = document(filePath, project.get(), cancelChecker);
                    if (document.isEmpty()) {
                        return Optional.<Module>empty();
                    }
                    return Optional.of(document.get().module());
                }));
    }

    /**
     * Returns document of the project of this path.
     *
     * @param filePath file path of the document
     * @return {@link Document}
     */
    @Override
    public Optional<Document> document(Path filePath) {
        return projectRegistry.projectContext(projectRegistry.projectRoot(filePath))
                .flatMap(context -> context.withReadLock(ctx -> {
                    Optional<Project> project = project(filePath);
                    return project.isPresent() ? document(filePath, project.get(), null) : Optional.<Document>empty();
                }));
    }

    @Override
    public Optional<Document> document(Path filePath, @Nonnull CancelChecker cancelChecker) {
        return projectRegistry.projectContext(projectRegistry.projectRoot(filePath))
                .flatMap(context -> context.withReadLock(ctx -> {
                    Optional<Project> project = project(filePath);
                    return project.isPresent() ? document(filePath, project.get(), cancelChecker) : Optional.<Document>empty();
                }));
    }

    /**
     * Returns syntax tree from the path provided.
     *
     * @param filePath file path of the document
     * @return {@link SyntaxTree}
     */
    @Override
    public Optional<SyntaxTree> syntaxTree(Path filePath) {
        return projectRegistry.projectContext(projectRegistry.projectRoot(filePath))
                .flatMap(context -> context.withReadLock(ctx -> {
                    Optional<Document> document = this.document(filePath);
                    if (document.isEmpty()) {
                        return Optional.<SyntaxTree>empty();
                    }
                    return Optional.ofNullable(document.get().syntaxTree());
                }));
    }

    @Override
    public Optional<SyntaxTree> syntaxTree(Path filePath, @Nonnull CancelChecker cancelChecker) {
        return projectRegistry.projectContext(projectRegistry.projectRoot(filePath))
                .flatMap(context -> context.withReadLock(ctx -> {
                    Optional<Document> document = this.document(filePath, cancelChecker);
                    if (document.isEmpty()) {
                        return Optional.<SyntaxTree>empty();
                    }
                    return Optional.ofNullable(document.get().syntaxTree());
                }));
    }

    /**
     * Returns semantic model from the path provided.
     *
     * @param filePath file path of the document
     * @return {@link SemanticModel}
     */
    @Override
    public Optional<SemanticModel> semanticModel(Path filePath) {
        Optional<PackageCompilation> packageCompilation = waitAndGetPackageCompilation(filePath);
        return projectRegistry.projectContext(projectRegistry.projectRoot(filePath))
                .flatMap(context -> context.withReadLock(ctx -> {
                    Optional<Module> module = this.module(filePath);
                    if (module.isEmpty() || packageCompilation.isEmpty() || context.compilationCrashed()) {
                        return Optional.<SemanticModel>empty();
                    }
                    return Optional.of(packageCompilation.get().getSemanticModel(module.get().moduleId()));
                }));
    }

    @Override
    public Optional<SemanticModel> semanticModel(Path filePath, @Nonnull CancelChecker cancelChecker) {
        Optional<PackageCompilation> packageCompilation = waitAndGetPackageCompilation(filePath, cancelChecker);
        return projectRegistry.projectContext(projectRegistry.projectRoot(filePath))
                .flatMap(context -> context.withReadLock(ctx -> {
                    Optional<Module> module = this.module(filePath, cancelChecker);
                    if (module.isEmpty() || packageCompilation.isEmpty() || context.compilationCrashed()) {
                        return Optional.<SemanticModel>empty();
                    }
                    return Optional.of(packageCompilation.get().getSemanticModel(module.get().moduleId()));
                }));
    }

    /**
     * Returns module compilation from the file path provided.
     *
     * @param filePath       file path of the document
     * @param isSourceChange True if the given file's source is changed
     * @return {@link ModuleCompilation}
     */
    public Optional<PackageCompilation> waitAndGetPackageCompilation(Path filePath, boolean isSourceChange) {
        // Get Project and Lock
        Optional<ProjectContext> projectPair = projectRegistry.projectContext(projectRegistry.projectRoot(filePath));
        if (projectPair.isEmpty() || (projectPair.get().compilationCrashed() && !isSourceChange)) {
            return Optional.empty();
        }

        AtomicReference<PackageCompilation> compilationRef = new AtomicReference<>();
        projectPair.get().withWriteLock(ctx -> {
            try {
                PackageCompilation compilation = getPackageCompilationWithRecovery(ctx, filePath);
                if (ctx.compilationCrashed()) {
                    ctx.setCompilationCrashed(false);
                }
                if (hasCompilationCrashDiagnostic(compilation)) {
                    ctx.setCompilationCrashed(true);
                    ctx.project().clearCaches();
                }
                compilationRef.set(compilation);
            } catch (BLangCompilerException e) {
                if (shouldCrashImmediately(e)) {
                    ctx.setCompilationCrashed(true);
                    ctx.project().clearCaches();
                }
                throw e;
            }
        });
        return Optional.ofNullable(compilationRef.get());
    }

    /**
     * Returns module compilation from the file path provided.
     *
     * @param filePath file path of the document
     * @return {@link ModuleCompilation}
     */
    @Override
    public Optional<PackageCompilation> waitAndGetPackageCompilation(Path filePath) {
        return waitAndGetPackageCompilation(filePath, false);
    }

    @Override
    public Optional<PackageCompilation> waitAndGetPackageCompilation(Path filePath,
                                                                     @Nonnull CancelChecker cancelChecker) {
        cancelChecker.checkCanceled();
        return waitAndGetPackageCompilation(filePath);
    }

    /**
     * The document open notification is sent from the client to the server to signal newly opened text documents.
     *
     * @param filePath {@link Path} of the document
     * @param params   {@link DidOpenTextDocumentParams}
     */
    @Override
    public void didOpen(Path filePath, DidOpenTextDocumentParams params) throws WorkspaceDocumentException {
        // Add the document to the opened documents set and the entry will only be removed via didClose.
        // Hence we assume the safe concurrent access for a given document path
        this.openedDocuments.add(filePath);
        ProjectContext projectContext = createOrGetProjectPair(filePath,
                LSContextOperation.TXT_DID_OPEN.getName(), true);
        Project project = projectContext.project();

        // Route TOML files through the registry
        Optional<TomlHandler> tomlHandlerOpt = tomlHandlerRegistry.lookup(filePath);
        if (tomlHandlerOpt.isPresent()) {
            tomlHandlerOpt.get().updateContent(params.getTextDocument().getText(), projectContext, true);
            return;
        }

        if (ProjectPaths.isBalFile(filePath) && project.kind() != ProjectKind.BALA_PROJECT) {
            // Create a new .bal document.
            createBalDocument(filePath, params.getTextDocument().getText(), projectContext);
        }
    }

    /**
     * The document change notification is sent from the client to the server to signal changes to a text document.
     *
     * @param filePath {@link Path} of the document
     * @param params   {@link DidChangeTextDocumentParams}
     * @throws WorkspaceDocumentException when project or document not found
     */
    @Override
    public void didChange(Path filePath, DidChangeTextDocumentParams params) throws WorkspaceDocumentException {
        // Get Project and Lock
        ProjectContext projectContext = createOrGetProjectPair(filePath,
                LSContextOperation.TXT_DID_CHANGE.getName(), true);

        Project project = projectContext.project();

        // Route TOML files through the registry
        Optional<TomlHandler> tomlHandlerOpt = tomlHandlerRegistry.lookup(filePath);
        if (tomlHandlerOpt.isPresent()) {
            tomlHandlerOpt.get().updateContent(params.getContentChanges().get(0).getText(), projectContext, false);
            return;
        }

        if (ProjectPaths.isBalFile(filePath) && project.kind() != ProjectKind.BALA_PROJECT) {
            // Update .bal document
            updateBalDocument(filePath, params.getContentChanges().get(0).getText(), projectContext);
        }
    }

    /**
     * The file change notification is sent from the client to the server to signal changes to watched files.
     *
     * @param filePath  {@link Path} of the document
     * @param fileEvent {@link FileEvent}
     * @throws WorkspaceDocumentException when project or document not found
     */
    @Override
    public void didChangeWatched(Path filePath, FileEvent fileEvent) throws WorkspaceDocumentException {
        fileWatchHandler.didChangeWatched(filePath, fileEvent);
    }

    @Override
    public List<Path> didChangeWatched(DidChangeWatchedFilesParams params) throws WorkspaceDocumentException {
        return fileWatchHandler.didChangeWatched(params);
    }

    @Override
    public String uriScheme() {
        return "file";
    }

    @Override
    public RunResult run(RunContext executionContext) throws IOException {
        return projectExecutor.run(executionContext);
    }

    @Override
    public boolean stop(Path filePath) {
        return projectExecutor.stop(filePath);
    }

    @Override
    public CompletableFuture<Map<Path, Project>> workspaceProjects() {
        ExtendedLanguageClient extendedLanguageClient = serverContext.get(ExtendedLanguageClient.class);
        CompletableFuture<List<WorkspaceFolder>> future = extendedLanguageClient.workspaceFolders();
        return future.thenApply(workspaceFolders -> {
            Map<Path, Project> filteredProjects = new HashMap<>();
            workspaceFolders.forEach(workspaceFolder -> {
                Path workspaceFolderPath = Path.of(URI.create(workspaceFolder.getUri()));
                projectRegistry.sourceRootToProject().entrySet().stream()
                        .filter(pathProjectContextEntry -> pathProjectContextEntry.getKey().toAbsolutePath()
                                .startsWith(workspaceFolderPath))
                        .forEach(pathProjectContextEntry ->
                                filteredProjects.put(pathProjectContextEntry.getKey(),
                                        pathProjectContextEntry.getValue().project()));
            });
            return filteredProjects;
        });
    }

    /**
     * Refresh the project by cloning it internally and clearing caches.
     *
     * @param filePath A path of a file in the project
     */
    public void refreshProject(Path filePath) throws WorkspaceDocumentException {
        Optional<ProjectContext> projectPairOpt = projectRegistry.projectContext(projectRegistry.projectRoot(filePath));
        if (projectPairOpt.isEmpty()) {
            throw new WorkspaceDocumentException("Project not found for filePath: " + filePath);
        }

        projectPairOpt.get().withWriteLock(ctx -> ctx.project().clearCaches());
    }

    /**
     * Sets whether experimental language features should be enabled for subsequent project loads.
     *
     * @param experimental {@code true} to enable experimental language features
     */
    public void setExperimental(boolean experimental) {
        this.experimental = experimental;
    }

    private static WorkspaceDocumentException unwrapWorkspaceDocumentException(RuntimeException exception) {
        if (exception.getCause() instanceof WorkspaceDocumentException workspaceDocumentException) {
            return workspaceDocumentException;
        }
        throw exception;
    }

    private void updateBalDocument(Path filePath, String content, ProjectContext projectContext)
            throws WorkspaceDocumentException {
        try {
            projectContext.withWriteLock(ctx -> {
                try {
                    Optional<Document> document = document(filePath, ctx.project(), null);
                    if (document.isEmpty()) {
                        throw new WorkspaceDocumentException("Document does not exist in path: " + filePath);
                    }
                    document.get().modify().withContent(content).apply();
                } catch (WorkspaceDocumentException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            throw unwrapWorkspaceDocumentException(e);
        }
    }

    private void createBalDocument(Path filePath, String content, ProjectContext projectContext)
            throws WorkspaceDocumentException {
        try {
            projectContext.withWriteLock(ctx -> {
                try {
                    Optional<ProjectContext> newProjectContext =
                            projectRegistry.createProjectContext(filePath, LSContextOperation.TXT_DID_OPEN.getName());
                    if (newProjectContext.isEmpty()) {
                        throw new WorkspaceDocumentException("Could not find the project for file path: " + filePath);
                    }
                    Optional<Document> document = document(filePath, newProjectContext.get().project(), null);
                    if (document.isEmpty()) {
                        ctx.setProjectCrashed(true);
                        throw new WorkspaceDocumentException(
                                "Could not create a new document for file path: " + filePath);
                    }
                    Document updatedDoc = document.get().modify().withContent(content).apply();
                    ctx.setProject(updatedDoc.module().project());
                } catch (WorkspaceDocumentException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            throw unwrapWorkspaceDocumentException(e);
        }
    }

    /**
     * The document close notification is sent from the client to the server when the document got closed in the
     * client.
     *
     * @param filePath {@link Path} of the document
     * @param params   {@link DidCloseTextDocumentParams}
     */
    @Override
    public void didClose(Path filePath, DidCloseTextDocumentParams params) {
        this.openedDocuments.remove(filePath);
        Optional<Project> project = project(filePath);
        if (project.isEmpty()) {
            return;
        }
        // If it is a single file project, remove project from mapping
        if (project.get().kind() == ProjectKind.SINGLE_FILE_PROJECT) {
            Path projectRoot = project.get().sourceRoot();
            projectRegistry.removeProjectContext(projectRoot);
            clientLogger.logTrace("Operation '" + LSContextOperation.TXT_DID_CLOSE.getName() +
                    "' {project: '" + projectRoot.toUri().toString() +
                    "' kind: '" + project.get().kind().name().toLowerCase(Locale.getDefault()) +
                    "'} removed");
        }
    }

// ============================================================================================================== //

    @Override
    public ProjectRegistry projectRegistry() {
        return projectRegistry;
    }

    @Override
    public Optional<ProjectContext> projectContext(Path projectRoot) {
        return projectRegistry.projectContext(projectRoot);
    }

    protected void cacheProjectContext(Path projectRoot, ProjectContext projectContext) {
        projectRegistry.cacheProjectContext(projectRoot, projectContext);
    }

    void removeProjectContextInternal(Path projectRoot) {
        projectRegistry.removeProjectContext(projectRoot);
    }

    Optional<ProjectContext> createProjectContextInternal(Path filePath, String operationName) {
        return projectRegistry.createProjectContext(filePath, operationName);
    }

    Optional<ProjectContext> getOrCreateProjectInternal(Path projectRoot, Path filePath, String operationName) {
        return projectRegistry.getOrCreateProject(projectRoot, filePath, operationName);
    }

    void reloadProjectInternal(ProjectContext projectContext, Path filePath, String operationName) {
        projectRegistry.reloadProject(projectContext, filePath, operationName);
    }

    @Override
    public Optional<Document> document(Path filePath, Project project) {
        return document(filePath, project, null);
    }

    private Optional<Document> document(Path filePath, Project project, @Nullable CancelChecker cancelChecker) {
        if (cancelChecker != null) {
            cancelChecker.isCanceled();
        }
        try {
            DocumentId documentId = project.documentId(filePath);
            Module module = project.currentPackage().module(documentId.moduleId());
            return Optional.of(module.document(documentId));
        } catch (ProjectException e) {
            return Optional.empty();
        }
    }

    void reloadProject(ProjectContext projectContext, Path filePath, String operationName,
                       boolean offline, @Nullable PackageLockingMode lockingModeOverride) {
        projectRegistry.reloadProject(projectContext, filePath, operationName, offline, lockingModeOverride);
    }

    private PackageCompilation getPackageCompilationWithRecovery(ProjectContext ctx, Path filePath) {
        try {
            return ctx.project().currentPackage().getCompilation();
        } catch (BLangCompilerException e) {
            if (shouldCrashImmediately(e) || !isModuleLoadingFailure(e)) {
                throw e;
            }

            if (!projectRegistry.reloadProjectWithoutLock(ctx, filePath, LSContextOperation.WS_WF_CHANGED.getName(), false, null)) {
                throw e;
            }
            try {
                return ctx.project().currentPackage().getCompilation();
            } catch (BLangCompilerException onlineRetryFailure) {
                if (shouldCrashImmediately(onlineRetryFailure) || !isModuleLoadingFailure(onlineRetryFailure)) {
                    throw onlineRetryFailure;
                }

                if (!projectRegistry.reloadProjectWithoutLock(ctx, filePath, LSContextOperation.WS_WF_CHANGED.getName(), false,
                        PackageLockingMode.SOFT)) {
                    throw onlineRetryFailure;
                }
                try {
                    return ctx.project().currentPackage().getCompilation();
                } catch (BLangCompilerException softRetryFailure) {
                    ctx.setCompilationCrashed(true);
                    ctx.project().clearCaches();
                    throw softRetryFailure;
                }
            }
        }
    }

    private boolean isModuleLoadingFailure(BLangCompilerException exception) {
        String message = exception.getMessage();
        return message != null && (message.contains(FAILED_TO_LOAD_MODULE) ||
                message.contains(DiagnosticErrorCode.BAD_SAD_FROM_COMPILER.diagnosticId()));
    }

    private boolean shouldCrashImmediately(BLangCompilerException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains(DiagnosticErrorCode.CYCLIC_MODULE_IMPORTS_DETECTED.diagnosticId());
    }

    private boolean hasCompilationCrashDiagnostic(PackageCompilation compilation) {
        return compilation.diagnosticResult().diagnostics().stream()
                .anyMatch(diagnostic -> Arrays.asList(
                        DiagnosticErrorCode.BAD_SAD_FROM_COMPILER.diagnosticId(),
                        DiagnosticErrorCode.CYCLIC_MODULE_IMPORTS_DETECTED.diagnosticId())
                        .contains(diagnostic.diagnosticInfo().code()));
    }

    private ProjectContext createOrGetProjectPair(Path filePath, String operationName)
            throws WorkspaceDocumentException {
        return createOrGetProjectPair(filePath, operationName, false);
    }

    private ProjectContext createOrGetProjectPair(Path filePath, String operationName, boolean isSourceChange)
            throws WorkspaceDocumentException {
        Path projectRoot = projectRegistry.projectRoot(filePath);
        Optional<ProjectContext> existingContext = projectRegistry.projectContext(projectRoot);
        if (existingContext.isPresent() && !(existingContext.get().isProjectCrashed() && isSourceChange)) {
            return existingContext.get();
        }

        if (existingContext.isPresent() && existingContext.get().isProjectCrashed() && isSourceChange) {
            projectRegistry.removeProjectContext(projectRoot);
        }

        return projectRegistry.getOrCreateProjectOrThrow(projectRoot, filePath, operationName);
    }

    /**
     * Implementation of TomlHandlerContext providing narrow BWM access to TOML handlers.
     */
    private class TomlHandlerContextImpl implements TomlHandlerContext {

        @Override
        public void reloadProject(ProjectContext ctx, Path trigger, String operation) {
            projectRegistry.reloadProject(ctx, trigger, operation);
        }

        @Override
        public Map<Path, ProjectContext> projectRegistry() {
            return projectRegistry.sourceRootToProject();
        }

        @Override
        public Set<Path> openedDocuments() {
            return BallerinaWorkspaceManager.this.openedDocuments;
        }

        @Override
        public void logError(String message, Throwable t) {
            BallerinaWorkspaceManager.this.clientLogger.logError(LSContextOperation.WS_WF_CHANGED, message, t, null,
                    (org.eclipse.lsp4j.Position) null);
        }

        @Override
        public void registerWorkspaceChildren(ProjectContext workspaceCtx) {
            Project workspaceProject = workspaceCtx.project();
            BallerinaCompilerApi compilerApi = BallerinaCompilerApi.getInstance();

            if (!compilerApi.isWorkspaceProject(workspaceProject)) {
                return;
            }

            List<Project> workspacePackages = compilerApi.getWorkspaceProjectsInOrder(workspaceProject);
            for (Project workspacePackage : workspacePackages) {
                Path packageRoot = workspacePackage.sourceRoot();
                projectRegistry.sourceRootToProject().put(packageRoot,
                        ProjectContext.from(workspacePackage, true, workspaceProject.sourceRoot()));
                projectRegistry.invalidateCacheFor(packageRoot);
            }
        }

        @Override
        public Optional<ProjectContext> getOrCreateProject(Path projectRoot, Path triggerFile, String operation) {
            return projectRegistry.getOrCreateProject(projectRoot, triggerFile, operation);
        }

        @Override
        public Optional<ProjectContext> createProjectContext(Path tomlPath, String operation) {
            return projectRegistry.createProjectContext(tomlPath, operation);
        }

        @Override
        public void invalidateCacheFor(Path path) {
            projectRegistry.invalidateCacheFor(path);
        }
    }

    private static boolean isError(Diagnostic diagnostic) {
        return diagnostic.diagnosticInfo().severity().equals(DiagnosticSeverity.ERROR);
    }

    @Override
    public LanguageServerContext serverContext() {
        return this.serverContext;
    }

    @Override
    public LSClientLogger logger() {
        return this.clientLogger;
    }

    @Override
    public ExtendedLanguageClient client() {
        return this.serverContext.get(ExtendedLanguageClient.class);
    }

    @Override
    public Optional<TomlHandler> tomlHandler(Path filePath) {
        return tomlHandlerRegistry.lookup(filePath);
    }

    @Override
    public Set<Path> openedDocuments() {
        return openedDocuments;
    }

    @Override
    public boolean isFileWatcherEnabled() {
        return LSClientConfigHolder.getInstance(serverContext).getConfig().isEnableFileWatcher();
    }

    @Override
    public boolean experimental() {
        return experimental;
    }

    @Override
    public void stopProject(@Nonnull Path projectRoot) {
        projectExecutor.stopProject(projectRoot);
    }
}
