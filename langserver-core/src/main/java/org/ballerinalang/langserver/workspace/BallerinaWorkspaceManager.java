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
import io.ballerina.projects.JBallerinaBackend;
import io.ballerina.projects.JarLibrary;
import io.ballerina.projects.JarResolver;
import io.ballerina.projects.JvmTarget;
import io.ballerina.projects.Module;
import io.ballerina.projects.ModuleCompilation;
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
import org.ballerinalang.langserver.exception.UserErrorException;
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

import java.io.File;
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
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.ballerina.projects.util.ProjectConstants.BALLERINA_TOML;
import static io.ballerina.runtime.api.constants.RuntimeConstants.MODULE_INIT_CLASS_NAME;

/**
 * Contains a set of utility methods to manage projects.
 *
 * @since 1.0.0
 */
public class BallerinaWorkspaceManager implements WorkspaceManager {

    // workspace run related constants
    private static final String JAVA_COMMAND = "java.command";
    private static final String USER_DIR = System.getProperty("user.dir");
    private static final String HEAP_DUMP_FLAG = "-XX:+HeapDumpOnOutOfMemoryError";
    private static final String HEAP_DUMP_PATH_FLAG = "-XX:HeapDumpPath=";
    private static final String DEBUG_ARGS = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:";
    private static final String FAILED_TO_LOAD_MODULE = "failed to load the module";

    /**
     * Cache mapping of document path to source root.
     */
    private final Map<Path, Path> pathToSourceRootCache;
    /**
     * Mapping of source root to project instance.
     */
    private final Map<Path, ProjectContext> sourceRootToProject;
    private final Cache<Path, ProjectContext> projectCache;
    private boolean experimental = false;

    protected final LSClientLogger clientLogger;
    private final LanguageServerContext serverContext;
    private final Set<Path> openedDocuments = ConcurrentHashMap.newKeySet();
    private final TomlHandlerRegistry tomlHandlerRegistry;

    public BallerinaWorkspaceManager(LanguageServerContext serverContext) {
        this.serverContext = serverContext;
        this.clientLogger = LSClientLogger.getInstance(serverContext);
        this.tomlHandlerRegistry = new TomlHandlerRegistry(new TomlHandlerContextImpl());
        Cache<Path, Path> cache = CacheBuilder.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();
        this.pathToSourceRootCache = cache.asMap();
        this.projectCache = CacheBuilder.newBuilder()
                .maximumWeight(8)
                .expireAfterAccess(15, TimeUnit.MINUTES)
                .weigher(new Weigher<Path, ProjectContext>() {
                    @Override
                    public int weigh(Path key, ProjectContext value) {
                        return value.isWorkspaceChild() ? 0 : 1;
                    }
                })
                .removalListener(new RemovalListener<Path, ProjectContext>() {
                    @Override
                    public void onRemoval(RemovalNotification<Path, ProjectContext> notification) {
                        if (!notification.wasEvicted()) {
                            return;
                        }

                        ProjectContext ctx = notification.getValue();
                        Path root = notification.getKey();
                        if (ctx == null || root == null) {
                            return;
                        }

                        if (hasOpenDocuments(root)) {
                            projectCache.put(root, ctx);
                            return;
                        }

                        ctx.close();
                        invalidateCacheFor(root);

                        if (!ctx.isWorkspaceChild() && ctx.workspaceRoot() == null) {
                            cascadeEvictWorkspaceChildren(root);
                        }
                    }
                })
                .build();
        this.sourceRootToProject = projectCache.asMap();

        Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().unstarted(() -> {
            for (ProjectContext projectContext : sourceRootToProject.values()) {
                projectContext.close();
            }
        }));

    }

    /**
     * Invalidate all cache entries for paths under the given source root.
     * Uses prefix-match eviction so unrelated cache entries survive project mutations.
     *
     * @param root the source root whose cache entries should be invalidated
     */
    private void invalidateCacheFor(Path root) {
        pathToSourceRootCache.keySet().removeIf(path -> path.startsWith(root));
    }

    private boolean hasOpenDocuments(Path root) {
        for (Path openDoc : openedDocuments) {
            if (openDoc.startsWith(root)) {
                return true;
            }
        }
        return false;
    }

    private void cascadeEvictWorkspaceChildren(Path workspaceRoot) {
        sourceRootToProject.entrySet().removeIf(entry -> {
            ProjectContext ctx = entry.getValue();
            if (ctx != null && ctx.isWorkspaceChild() && workspaceRoot.equals(ctx.workspaceRoot())) {
                ctx.close();
                invalidateCacheFor(entry.getKey());
                return true;
            }
            return false;
        });
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
        return pathToSourceRootCache.computeIfAbsent(filePath, this::computeProjectRoot);
    }

    @Override
    public Path projectRoot(Path filePath, @Nonnull CancelChecker cancelChecker) {
        cancelChecker.checkCanceled();
        return this.projectRoot(filePath);
    }

    /**
     * Returns project from the path provided.
     *
     * @param filePath ballerina project or standalone file path
     * @return project of applicable type
     */
    @Override
    public Optional<Project> project(Path filePath) {
        return projectContext(projectRoot(filePath)).map(ProjectContext::project);
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

        Path root = projectRoot(filePath);
        ProjectContext projectContext = getOrCreateProject(root, filePath, LSContextOperation.LOAD_PROJECT.getName());

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
        Optional<Project> project = project(filePath);
        if (project.isEmpty()) {
            return Optional.empty();
        }
        Optional<Document> document = document(filePath, project.get(), null);
        if (document.isEmpty()) {
            // If the file path points to the project root, then return the default module
            // TODO: Need to extend this to support module paths once we have an API to obtain the module root from
            //  the given file path
            if (filePath.equals(this.projectRoot(filePath))) {
                return Optional.of(project.get().currentPackage().getDefaultModule());
            }
            return Optional.empty();
        }
        return Optional.of(document.get().module());
    }

    @Override
    public Optional<Module> module(Path filePath, @Nonnull CancelChecker cancelChecker) {
        cancelChecker.checkCanceled();
        Optional<Project> project = project(filePath);
        if (project.isEmpty()) {
            return Optional.empty();
        }
        Optional<Document> document = document(filePath, project.get(), cancelChecker);
        if (document.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(document.get().module());
    }

    /**
     * Returns document of the project of this path.
     *
     * @param filePath file path of the document
     * @return {@link Document}
     */
    @Override
    public Optional<Document> document(Path filePath) {
        Optional<Project> project = project(filePath);
        return project.isPresent() ? document(filePath, project.get(), null) : Optional.empty();
    }

    @Override
    public Optional<Document> document(Path filePath, @Nonnull CancelChecker cancelChecker) {
        Optional<Project> project = project(filePath);
        return project.isPresent() ? document(filePath, project.get(), cancelChecker) : Optional.empty();
    }

    /**
     * Returns syntax tree from the path provided.
     *
     * @param filePath file path of the document
     * @return {@link SyntaxTree}
     */
    @Override
    public Optional<SyntaxTree> syntaxTree(Path filePath) {
        Optional<Document> document = this.document(filePath);
        if (document.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(document.get().syntaxTree());
    }

    @Override
    public Optional<SyntaxTree> syntaxTree(Path filePath, @Nonnull CancelChecker cancelChecker) {
        Optional<Document> document = this.document(filePath, cancelChecker);
        if (document.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(document.get().syntaxTree());
    }

    /**
     * Returns semantic model from the path provided.
     *
     * @param filePath file path of the document
     * @return {@link SemanticModel}
     */
    @Override
    public Optional<SemanticModel> semanticModel(Path filePath) {
        Optional<Module> module = this.module(filePath);
        Optional<PackageCompilation> packageCompilation = waitAndGetPackageCompilation(filePath);
        Optional<ProjectContext> projectPair = projectContext(projectRoot(filePath));
        if (module.isEmpty() || packageCompilation.isEmpty() || projectPair.isEmpty()
                || projectPair.get().compilationCrashed()) {
            return Optional.empty();
        }
        return Optional.of(packageCompilation.get().getSemanticModel(module.get().moduleId()));
    }

    @Override
    public Optional<SemanticModel> semanticModel(Path filePath, @Nonnull CancelChecker cancelChecker) {
        Optional<Module> module = this.module(filePath);
        Optional<PackageCompilation> packageCompilation = waitAndGetPackageCompilation(filePath, cancelChecker);
        Optional<ProjectContext> projectPair = projectContext(projectRoot(filePath));
        if (module.isEmpty() || packageCompilation.isEmpty() || projectPair.isEmpty()
                || projectPair.get().compilationCrashed()) {
            return Optional.empty();
        }
        return Optional.of(packageCompilation.get().getSemanticModel(module.get().moduleId()));
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
        Optional<ProjectContext> projectPair = projectContext(projectRoot(filePath));
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
        if (!LSClientConfigHolder.getInstance(serverContext).getConfig().isEnableFileWatcher()) {
            return;
        }
        Optional<ProjectContext> optProject = getProjectOfWatchedFileChange(filePath, fileEvent);
        if (optProject.isEmpty()) {
            clientLogger.logTrace(
                    String.format("Operation '%s' No matching project found, {fileUri: '%s' event: '%s'} ignored",
                            LSContextOperation.WS_WF_CHANGED.getName(),
                            fileEvent.getUri(),
                            fileEvent.getType().name()));
            return;
        }
        ProjectContext projectContext = optProject.get();
        Project project = projectContext.project();
        String fileName = filePath.getFileName().toString();
        boolean isBallerinaSourceChange = fileName.endsWith(ProjectConstants.BLANG_SOURCE_EXT);

        // Check if this is a TOML file using the registry
        Optional<TomlHandler> tomlHandlerOpt = tomlHandlerRegistry.lookup(filePath);
        boolean isTomlChange = tomlHandlerOpt.isPresent();

        if (fileEvent.getType() == FileChangeType.Created &&
                (isBallerinaSourceChange || isTomlChange)
                && hasDocumentOrToml(filePath, project)) {
            // Document might already exists when text/didOpen hits before workspace/didChangeWatchedFiles,
            // Thus, return silently
            clientLogger.logTrace(
                    String.format("Operation '%s' File already exits, {fileUri: '%s' event: '%s'} ignored",
                            LSContextOperation.WS_WF_CHANGED.getName(),
                            fileEvent.getUri(),
                            fileEvent.getType().name()));
            return;
        }

        // Route TOML files through the registry
        if (tomlHandlerOpt.isPresent()) {
            tomlHandlerOpt.get().handleWatchedChange(filePath, fileEvent, projectContext);
        } else if (isBallerinaSourceChange) {
            handleWatchedBalSourceChange(filePath, fileEvent, projectContext);
        } else {
            handleWatchedModuleChange(filePath, fileEvent, projectContext);
        }
    }

    @Override
    public List<Path> didChangeWatched(DidChangeWatchedFilesParams params) throws WorkspaceDocumentException {
        if (!LSClientConfigHolder.getInstance(serverContext).getConfig().isEnableFileWatcher()) {
            return Collections.emptyList();
        }
        List<FileEvent> changes = params.getChanges();
        if (changes.size() == 1) {
            FileEvent fileEvent = changes.get(0);
            String uri = fileEvent.getUri();
            Optional<Path> pathFromURI = PathUtil.getPathFromURI(uri);
            if (pathFromURI.isEmpty()) {
                return Collections.emptyList();
            }
            Path filePath = pathFromURI.get();
            if (!this.openedDocuments.contains(filePath) || fileEvent.getType() == FileChangeType.Deleted) {
                // If already opened in the cache, this will be captured via the textDocument/didChange event
                this.didChangeWatched(filePath, fileEvent);
                Optional<ProjectContext> optProject = getProjectOfWatchedFileChange(filePath, fileEvent);
                if (optProject.isPresent()) {
                    ProjectContext projectContext = optProject.get();
                    Project project = projectContext.project();
                    return List.of(project.sourceRoot());
                }
            }
            return Collections.emptyList();
        }

        Set<Path> reloadableProjects = new HashSet<>();
        for (FileEvent fileEvent : changes) {
            String uri = fileEvent.getUri();
            Optional<Path> pathFromURI = PathUtil.getPathFromURI(uri);

            if (pathFromURI.isEmpty()) {
                return Collections.emptyList();
            }
            Path filePath = pathFromURI.get();

            try {
                reloadableProjects.add(ProjectPaths.packageRoot(filePath));
            } catch (ProjectException e) {
                // ignore the project exception which can be thrown when path identification is failed
            }
        }

        reloadableProjects.forEach(path -> {
            Optional<ProjectContext> projectPair = this.projectContext(path);
            if (projectPair.isEmpty()) {
                return;
            }
            projectPair.get().withWriteLock(ctx -> {
                Optional<ProjectContext> projectContext =
                        createProjectContext(path, LSContextOperation.WS_WF_CHANGED.getName());
                if (projectContext.isEmpty()) {
                    // NOTE: This will never happen since we create a project if not exists
                    throw new IllegalStateException("Cannot find the project of uri: " + path);
                }
                ctx.setProject(projectContext.get().project());
            });
        });
        return new ArrayList<>(reloadableProjects);
    }

    @Override
    public String uriScheme() {
        return "file";
    }

    @Override
    public RunResult run(RunContext executionContext) throws IOException {
        Path projectRoot = projectRoot(executionContext.balSourcePath());
        Optional<ProjectContext> projectContext = validateProjectContext(projectRoot);
        if (projectContext.isEmpty()) {
            return new RunResult(null, Collections.emptyList());
        }

        if (!stopProject(projectContext.get())) {
            logError("Run command execution aborted because couldn't stop the previous run");
            return new RunResult(null, Collections.emptyList());
        }

        Project project = projectContext.get().project();
        Optional<PackageCompilation> packageCompilation = waitAndGetPackageCompilation(project.sourceRoot(), true);
        if (packageCompilation.isEmpty()) {
            logError("Run command execution aborted because package compilation failed");
            return new RunResult(null, Collections.emptyList());
        }

        JBallerinaBackend jBallerinaBackend = execBackend(projectContext.get(), packageCompilation.get());
        Collection<Diagnostic> diagnostics = new LinkedList<>();
        // check for compilation errors
        diagnostics.addAll(jBallerinaBackend.diagnosticResult().diagnostics(false));
        // Add tool resolution diagnostics to diagnostics
        diagnostics.addAll(project.currentPackage().getBuildToolResolution().getDiagnosticList());

        if (diagnostics.stream().anyMatch(d -> d.diagnosticInfo().severity() == DiagnosticSeverity.ERROR)) {
            return new RunResult(null, diagnostics);
        }

        Optional<Process> process = executeProject(projectContext.get(), executionContext);
        return process.map(value -> new RunResult(value, diagnostics))
                .orElseGet(() -> new RunResult(null, diagnostics));
    }

    private Optional<ProjectContext> validateProjectContext(Path projectRoot) {
        Optional<ProjectContext> projectContextOpt = projectContext(projectRoot);
        if (projectContextOpt.isEmpty()) {
            logError("Run command execution aborted because project is not loaded");
            return Optional.empty();
        }

        return projectContextOpt;
    }

    private Optional<Process> executeProject(ProjectContext projectContext, RunContext context) throws IOException {
        Project project = projectContext.project();
        Package pkg = project.currentPackage();
        Module executableModule = pkg.getDefaultModule();
        JBallerinaBackend jBallerinaBackend = execBackend(projectContext, pkg.getCompilation());
        JarResolver jarResolver = jBallerinaBackend.jarResolver();

        List<String> commands = prepareExecutionCommands(context, executableModule, jarResolver);
        ProcessBuilder pb = new ProcessBuilder(commands);
        pb.environment().putAll(context.env());

        AtomicReference<Optional<Process>> processRef = new AtomicReference<>(Optional.empty());
        try {
            projectContext.withWriteLock(ctx -> {
                Optional<Process> existing = ctx.process();
                if (existing.isPresent()) {
                    logError("Run command execution aborted because another run is in progress");
                    return;
                }

                try {
                    Process ps = pb.start();
                    ctx.setProcess(ps);
                    processRef.set(Optional.of(ps));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
        return processRef.get();
    }

    private List<String> prepareExecutionCommands(RunContext context, Module module, JarResolver jarResolver) {
        List<String> commands = new ArrayList<>();
        commands.add(context.javaCmd());
        commands.add(HEAP_DUMP_FLAG);
        commands.add(HEAP_DUMP_PATH_FLAG + USER_DIR);
        if (context.debugPort() > 0) {
            commands.add(DEBUG_ARGS + context.debugPort());
        }

        commands.add("-cp");
        commands.add(getAllClassPaths(jarResolver));

        String initClassName = JarResolver.getQualifiedClassName(
                module.packageInstance().packageOrg().toString(),
                module.packageInstance().packageName().toString(),
                module.packageInstance().packageVersion().toString(),
                MODULE_INIT_CLASS_NAME
        );
        commands.add(initClassName);
        commands.addAll(context.programArgs());
        return commands;
    }

    private static JBallerinaBackend execBackend(ProjectContext projectContext,
                                                 PackageCompilation packageCompilation) {
        AtomicReference<JBallerinaBackend> backendRef = new AtomicReference<>();
        projectContext.withWriteLock(ctx -> {
            JBallerinaBackend jBallerinaBackend = JBallerinaBackend.from(packageCompilation, JvmTarget.JAVA_21, false);
            Package pkg = ctx.project.currentPackage();
            for (Module module : pkg.modules()) {
                for (DocumentId id : module.documentIds()) {
                    module.document(id).modify().apply();
                }
            }
            backendRef.set(jBallerinaBackend);
        });
        return backendRef.get();
    }

    private void logError(String message) {
        UserErrorException e = new UserErrorException(message);
        clientLogger.logError(LSContextOperation.WS_EXEC_CMD, message, e, null, (Position) null);
    }

    @Override
    public boolean stop(Path filePath) {
        Optional<ProjectContext> projectPairOpt = projectContext(projectRoot(filePath).toAbsolutePath());
        if (projectPairOpt.isEmpty()) {
            clientLogger.logWarning("Failed to stop process: Project not found");
            return false;
        }
        ProjectContext projectContext = projectPairOpt.get();
        return stopProject(projectContext);
    }

    @Override
    public CompletableFuture<Map<Path, Project>> workspaceProjects() {
        ExtendedLanguageClient extendedLanguageClient = serverContext.get(ExtendedLanguageClient.class);
        CompletableFuture<List<WorkspaceFolder>> future = extendedLanguageClient.workspaceFolders();
        return future.thenApply(workspaceFolders -> {
            Map<Path, Project> filteredProjects = new HashMap<>();
            workspaceFolders.forEach(workspaceFolder -> {
                Path workspaceFolderPath = Path.of(URI.create(workspaceFolder.getUri()));
                sourceRootToProject.entrySet().stream()
                        .filter(pathProjectContextEntry -> pathProjectContextEntry.getKey().toAbsolutePath()
                                .startsWith(workspaceFolderPath))
                        .forEach(pathProjectContextEntry ->
                                filteredProjects.put(pathProjectContextEntry.getKey(),
                                        pathProjectContextEntry.getValue().project()));
            });
            return filteredProjects;
        });
    }

    private boolean stopProject(ProjectContext projectContext) {
        AtomicReference<Boolean> killedRef = new AtomicReference<>(true);
        projectContext.withWriteLock(ctx -> {
            Optional<Process> existing = ctx.process();
            if (existing.isEmpty()) {
                return;
            }
            boolean killed = killProcess(existing.get());
            if (killed) {
                ctx.removeProcess();
            }
            killedRef.set(killed);
        });
        return killedRef.get();
    }

    private boolean killProcess(Process process) {
        process.destroy();
        try {
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            clientLogger.logWarning("Waiting for process to stop was interrupted");
            Thread.currentThread().interrupt();
        }
        if (process.isAlive()) {
            process.destroyForcibly();
        }
        return !process.isAlive();
    }

    private String getAllClassPaths(JarResolver jarResolver) {
        StringJoiner cp = new StringJoiner(File.pathSeparator);
        for (JarLibrary lib : jarResolver.getJarFilePathsRequiredForExecution()) {
            cp.add(lib.path().toString());
        }
        return cp.toString();
    }

    /**
     * Refresh the project by cloning it internally and clearing caches.
     *
     * @param filePath A path of a file in the project
     */
    public void refreshProject(Path filePath) throws WorkspaceDocumentException {
        Optional<ProjectContext> projectPairOpt = projectContext(projectRoot(filePath));
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

    private Optional<ProjectContext> projectOfWatchedFileChange(Path filePath, FileEvent fileEvent,
                                                                boolean isBallerinaSourceChange,
                                                                boolean isBallerinaTomlChange,
                                                                boolean isDependenciesTomlChange,
                                                                boolean isCloudTomlChange,
                                                                boolean isCompilerPluginTomlChange,
                                                                boolean isBalToolTomlChange,
                                                                boolean isModuleChange) {
        if (isBallerinaSourceChange) {
            if (fileEvent.getType() == FileChangeType.Created) {
                return projectContext(projectRoot(filePath));
            } else {
                // DELETED event
                // First try as a single-file-project
                Optional<ProjectContext> optProject = projectContext(filePath);
                if (optProject.isPresent()) {
                    return optProject;
                }
                // Or Else, try as a build-project
                Path parent = filePath.getParent();
                if (ProjectConstants.TEST_DIR_NAME.equals(parent.getFileName().toString())) {
                    // If inside a tests folder, get parent
                    parent = parent.getParent();
                }
                if (ProjectConstants.MODULES_ROOT.equals(parent.getParent().getFileName().toString()) ||
                        ProjectConstants.GENERATED_MODULES_ROOT.equals(parent.getParent().getFileName().toString())) {
                    // If inside a modules or generated folder, get parent of parent
                    parent = parent.getParent().getParent();
                }
                if (ProjectConstants.GENERATED_MODULES_ROOT.equals(parent.getFileName().toString())) {
                    // If a generated source for a non-default module, get parent of parent
                    parent = parent.getParent();
                }
                return projectContext(parent);
            }
        } else if (isBallerinaTomlChange) {
            if (fileEvent.getType() == FileChangeType.Created) {
                // Check for a project upgrade from a single-file to a build-project
                // In such scenario, project will be only available with the key of that single file path.
                Optional<ProjectContext> optProject = sourceRootToProject.entrySet().stream()
                        .filter(entry -> entry.getValue().project().kind() == ProjectKind.SINGLE_FILE_PROJECT &&
                                entry.getKey().getParent().equals(filePath.getParent()))
                        .findFirst()
                        .map(Map.Entry::getValue);
                if (optProject.isEmpty()) {
                    // Single-file project is unavailable if we just downgraded a build-project removing Ballerina.toml
                    // Thus, loading a new build-project here
                    try {
                        optProject = Optional.of(getOrCreateProject(filePath.getParent(), filePath,
                                LSContextOperation.WS_WF_CHANGED.getName()));
                    } catch (WorkspaceDocumentException e) {
                        optProject = Optional.empty();
                    }
                }
                return optProject;
            } else {
                // Check for a project downgrade from a build-project to a single-file
                return projectContext(filePath.getParent());
            }
        } else if (isCloudTomlChange || isCompilerPluginTomlChange || isBalToolTomlChange || isDependenciesTomlChange) {
            return projectContext(filePath.getParent());
        } else if (isModuleChange) {
            Path projectRoot;
            if (ProjectConstants.MODULES_ROOT.equals(filePath.getFileName().toString()) ||
                    ProjectConstants.GENERATED_MODULES_ROOT.equals(filePath.getFileName().toString())) {
                // If it is **/projectRoot/modules OR **/projectRoot/generated
                projectRoot = filePath.getParent();
            } else {
                // If it is **/projectRoot/modules/mod2 OR **/projectRoot/generated/mod2
                projectRoot = filePath.getParent().getParent();
            }
            return projectContext(projectRoot);
        } else {
            // Skip if unrecognized file change
            return Optional.empty();
        }
    }

    private void handleWatchedBalSourceChange(Path filePath, FileEvent fileEvent, ProjectContext projectContext) {
        switch (fileEvent.getType()) {
            case Created: {
                // Creating new document requires finding the module it resides
                // Thus, reloading the project
                reloadProject(projectContext, filePath, LSContextOperation.WS_WF_CHANGED.getName());
                break;
            }
            case Changed: {
                if (!this.openedDocuments.contains(filePath)) {
                    reloadProject(projectContext, filePath, LSContextOperation.WS_WF_CHANGED.getName());
                }
                break;
            }
            case Deleted: {
                Project project = projectContext.project();
                if (project.kind() == ProjectKind.SINGLE_FILE_PROJECT) {
                    // If it is a single-file-project, remove project from mapping
                    Path projectRoot = project.sourceRoot();
                    ProjectContext removed = sourceRootToProject.remove(projectRoot);
                    invalidateCacheFor(projectRoot);
                    if (removed != null) {
                        removed.close();
                    }
                    clientLogger.logTrace(String.format("Operation '%s' {project: '%s' kind: '%s'} removed",
                            LSContextOperation.WS_WF_CHANGED.getName(),
                            projectRoot.toUri().toString(),
                            project.kind().name()
                                    .toLowerCase(Locale.getDefault())));
                } else {
                    // If it is a build-project, need to remove particular file from project
                    Optional<Document> document = document(filePath, project, null);
                    if (document.isPresent()) {
                        projectContext.withWriteLock(ctx -> {
                            Project updatedProj = document.get().module().modify().removeDocument(
                                    document.get().documentId()).apply().project();
                            ctx.setProject(updatedProj);
                            clientLogger.logTrace(String.format("Operation '%s' {fileUri: '%s'} removed",
                                    LSContextOperation.WS_WF_CHANGED.getName(),
                                    fileEvent.getUri()));
                        });
                    } else {
                        // If document-id not found, reload project
                        Path ballerinaTomlPath = project.sourceRoot().resolve(ProjectConstants.BALLERINA_TOML);
                        reloadProject(projectContext, ballerinaTomlPath, LSContextOperation.WS_WF_CHANGED.getName());
                    }
                }
            }
        }
    }

    private static WorkspaceDocumentException unwrapWorkspaceDocumentException(RuntimeException exception) {
        if (exception.getCause() instanceof WorkspaceDocumentException workspaceDocumentException) {
            return workspaceDocumentException;
        }
        throw exception;
    }

    private void handleWatchedModuleChange(Path filePath, FileEvent fileEvent, ProjectContext projectContext) {
        String fileName = filePath.getFileName().toString();
        switch (fileEvent.getType()) {
            case Created:
                // When adding a new module, it requires search and adding new docs and test docs also.
                // Thus, we are simply reloading the project.
                clientLogger.logTrace(String.format("Operation '%s' {module: '%s', uri: '%s'} created",
                        LSContextOperation.WS_WF_CHANGED.getName(),
                        fileName, filePath.toUri().toString()));
                Path ballerinaTomlPath = filePath.getParent().getParent().resolve(ProjectConstants.BALLERINA_TOML);
                reloadProject(projectContext, ballerinaTomlPath, LSContextOperation.WS_WF_CHANGED.getName());
                break;
            case Deleted:
                if (ProjectConstants.MODULES_ROOT.equals(filePath.getFileName().toString())) {
                    // If removing all modules
                    Path tomlPath = filePath.getParent().resolve(ProjectConstants.BALLERINA_TOML);
                    clientLogger.logTrace(String.format("Operation '%s' {uri: '%s'} removed all modules",
                            LSContextOperation.WS_WF_CHANGED.getName(),
                            filePath.toUri().toString()));
                    reloadProject(projectContext, tomlPath, LSContextOperation.WS_WF_CHANGED.getName());
                } else {
                    // If removing a particular module
                    Path tomlPath = filePath.getParent().getParent().resolve(ProjectConstants.BALLERINA_TOML);
                    clientLogger.logTrace(String.format("Operation '%s' {module: '%s', uri: '%s'} removed",
                            LSContextOperation.WS_WF_CHANGED.getName(),
                            fileName,
                            filePath.toUri().toString()));
                    reloadProject(projectContext, tomlPath, LSContextOperation.WS_WF_CHANGED.getName());
                }
                break;
        }
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
                            createProjectContext(filePath, LSContextOperation.TXT_DID_OPEN.getName());
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
            ProjectContext removed = sourceRootToProject.remove(projectRoot);
            invalidateCacheFor(projectRoot);
            if (removed != null) {
                removed.close();
            }
            clientLogger.logTrace("Operation '" + LSContextOperation.TXT_DID_CLOSE.getName() +
                    "' {project: '" + projectRoot.toUri().toString() +
                    "' kind: '" + project.get().kind().name().toLowerCase(Locale.getDefault()) +
                    "'} removed");
        }
    }

// ============================================================================================================== //

    private Path computeProjectRoot(Path path) {
        if (ProjectPaths.isStandaloneBalFile(path)) {
            return path;
        }

        BallerinaCompilerApi compilerApi = BallerinaCompilerApi.getInstance();
        if (compilerApi.isWorkspaceProjectRoot(path)) {
            return path;
        }

        // Check if the path points to the workspace Ballerina.toml
        Path parentDir = path.getParent();
        if (path.getFileName() != null &&
                path.getFileName().toString().equals(ProjectConstants.BALLERINA_TOML) &&
                parentDir != null && compilerApi.isWorkspaceProjectRoot(parentDir)) {
            return parentDir;
        }

        return ProjectPaths.packageRoot(path);
    }

    Optional<ProjectContext> projectContext(Path projectRoot) {
        return Optional.ofNullable(sourceRootToProject.get(projectRoot));
    }

    protected void cacheProjectContext(Path projectRoot, ProjectContext projectContext) {
        sourceRootToProject.put(projectRoot, projectContext);
        invalidateCacheFor(projectRoot);
    }

    protected void removeProjectContext(Path projectRoot) {
        ProjectContext removed = sourceRootToProject.remove(projectRoot);
        invalidateCacheFor(projectRoot);
        if (removed != null) {
            removed.close();
        }
    }

    private Optional<ProjectContext> createProjectContext(Path filePath, String operationName) {
        Optional<ProjectLoadResult> loadResult = loadProjectResult(filePath, operationName);
        if (loadResult.isEmpty()) {
            return Optional.empty();
        }
        Path projectRoot = computeProjectRoot(filePath);
        ProjectContext projectContext = createProjectContext(loadResult.get().targetProject(),
                loadResult.get().workspaceRootProject());
        cacheLoadedProjects(projectRoot, projectContext, loadResult.get());
        return Optional.of(projectContext);
    }

    private ProjectContext createProjectContext(Project project, @Nullable Project workspaceRootProject) {
        if (workspaceRootProject == null || workspaceRootProject.sourceRoot().equals(project.sourceRoot())) {
            return ProjectContext.from(project, false, null);
        }
        return ProjectContext.from(project, true, workspaceRootProject.sourceRoot());
    }

    /**
     * Get or create a ProjectContext atomically using the project cache loader.
     * Failed loads are not cached, so the next caller retries the load.
     *
     * @param projectRoot cache key for the project
     * @param filePath file path used for project detection
     * @param operationName operation name for logging
     * @return cached or newly created project context
     * @throws WorkspaceDocumentException if the project cannot be created
     */
    private ProjectContext getOrCreateProject(Path projectRoot, Path filePath, String operationName)
            throws WorkspaceDocumentException {
        try {
            return projectCache.get(projectRoot, () -> {
                Optional<ProjectContext> projectContext = createProjectContext(filePath, operationName);
                if (projectContext.isEmpty()) {
                    throw new WorkspaceDocumentException("Cannot find the project of uri: " + filePath);
                }
                return projectContext.get();
            });
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof WorkspaceDocumentException workspaceDocumentException) {
                throw workspaceDocumentException;
            }
            throw new WorkspaceDocumentException("Failed to create project: " + projectRoot, cause);
        }
    }

    private Optional<ProjectLoadResult> loadProjectResult(Path filePath, String operationName) {
        return loadProjectResult(filePath, operationName, CommonUtil.COMPILE_OFFLINE, null);
    }

    private Optional<ProjectLoadResult> loadProjectResult(Path filePath, String operationName, boolean offline,
                                                          @Nullable PackageLockingMode lockingModeOverride) {
        Path projectRoot = computeProjectRoot(filePath);
        BallerinaCompilerApi compilerApi = BallerinaCompilerApi.getInstance();
        try {
            PackageLockingMode lockingMode = lockingModeOverride != null
                    ? lockingModeOverride
                    : deriveLockingMode(projectRoot);
            BuildOptions buildOptions = buildOptions(offline, lockingMode);
            Project project = compilerApi.loadProject(filePath, buildOptions);
            if (lockingMode != PackageLockingMode.SOFT && compilerApi.hasOptimizedDependencyCompilation(project)) {
                buildOptions = buildOptions(offline, PackageLockingMode.SOFT);
                project = compilerApi.loadProject(filePath, buildOptions);
            }

            if (compilerApi.isWorkspaceProject(project)) {
                List<Project> workspacePackages = compilerApi.getWorkspaceProjectsInOrder(project);
                Project targetProject = project;
                for (Project workspacePackage : workspacePackages) {
                    if (workspacePackage.sourceRoot().equals(projectRoot)) {
                        targetProject = workspacePackage;
                        break;
                    }
                }
                clientLogger.logTrace("Operation '" + operationName +
                        "' {workspace package: '" + projectRoot.toUri() + "'} loaded from workspace");
                return Optional.of(new ProjectLoadResult(targetProject, project, workspacePackages));
            }

            clientLogger.logTrace("Operation '" + operationName +
                    "' {project: '" + projectRoot.toUri() + "' kind: '" +
                    project.kind().name().toLowerCase(Locale.getDefault()) + "'} created");
            return Optional.of(new ProjectLoadResult(project, null, List.of()));
        } catch (ProjectException e) {
            this.projectContext(projectRoot).ifPresent(projectContext -> projectContext.setProjectCrashed(true));
            clientLogger.notifyUser("Project load failed: " + e.getMessage(), e);
            clientLogger.logError(LSContextOperation.CREATE_PROJECT, "Operation '" + operationName +
                            "' {project: '" + projectRoot.toUri() + "'" + "} failed", e,
                    new TextDocumentIdentifier(filePath.toUri().toString()));
            return Optional.empty();
        }
    }

    private BuildOptions buildOptions(boolean offline, PackageLockingMode lockingMode) {
        return BuildOptions.builder()
                .setOffline(offline)
                .setExperimental(this.experimental)
                .setLockingMode(lockingMode)
                .build();
    }

    private PackageLockingMode deriveLockingMode(Path projectRoot) {
        Path dependenciesTomlPath = projectRoot.resolve(ProjectConstants.DEPENDENCIES_TOML);
        return Files.exists(dependenciesTomlPath) ? PackageLockingMode.MEDIUM : PackageLockingMode.SOFT;
    }

    private void cacheLoadedProjects(Path primaryRoot, @Nullable ProjectContext primaryContext,
                                     @Nullable ProjectLoadResult loadResult) {
        if (loadResult == null) {
            return;
        }

        if (loadResult.workspaceRootProject() != null) {
            Project workspaceRootProject = loadResult.workspaceRootProject();
            if (workspaceRootProject.sourceRoot().equals(primaryRoot) && primaryContext != null) {
                sourceRootToProject.put(primaryRoot, primaryContext);
            } else {
                sourceRootToProject.put(workspaceRootProject.sourceRoot(),
                        ProjectContext.from(workspaceRootProject, false, null));
            }
            invalidateCacheFor(workspaceRootProject.sourceRoot());
        }

        for (Project workspacePackage : loadResult.workspacePackages()) {
            if (workspacePackage.sourceRoot().equals(primaryRoot) && primaryContext != null) {
                sourceRootToProject.put(primaryRoot, primaryContext);
            } else {
                Project workspaceRoot = loadResult.workspaceRootProject();
                sourceRootToProject.put(workspacePackage.sourceRoot(),
                        ProjectContext.from(workspacePackage, true,
                                workspaceRoot != null ? workspaceRoot.sourceRoot() : null));
            }
            invalidateCacheFor(workspacePackage.sourceRoot());
        }
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

    private boolean hasDocumentOrToml(Path filePath, Project project) {
        String fileName = Optional.of(filePath.getFileName()).get().toString();
        return switch (fileName) {
            case ProjectConstants.BALLERINA_TOML -> project.currentPackage().ballerinaToml().isPresent();
            case ProjectConstants.CLOUD_TOML -> project.currentPackage().cloudToml().isPresent();
            case ProjectConstants.COMPILER_PLUGIN_TOML -> project.currentPackage().compilerPluginToml().isPresent();
            case ProjectConstants.BAL_TOOL_TOML -> project.currentPackage().balToolToml().isPresent();
            case ProjectConstants.DEPENDENCIES_TOML -> project.currentPackage().dependenciesToml().isPresent();
            default -> {
                if (fileName.endsWith(ProjectConstants.BLANG_SOURCE_EXT)) {
                    yield document(filePath, project, null).isPresent();
                }
                yield false;
            }
        };
    }

    private void reloadProject(ProjectContext projectContext, Path filePath, String operationName) {
        reloadProject(projectContext, filePath, operationName, CommonUtil.COMPILE_OFFLINE, null);
    }

    private void reloadProject(ProjectContext projectContext, Path filePath, String operationName, boolean offline,
                               @Nullable PackageLockingMode lockingModeOverride) {
        projectContext.withWriteLock(ctx -> {
            Optional<ProjectLoadResult> loadResult = loadProjectResult(filePath, operationName, offline,
                    lockingModeOverride);
            if (loadResult.isEmpty()) {
                return;
            }
            ProjectLoadResult projectLoadResult = loadResult.get();
            ctx.setProject(projectLoadResult.targetProject());
            cacheLoadedProjects(computeProjectRoot(filePath), ctx, projectLoadResult);
        });
    }

    private PackageCompilation getPackageCompilationWithRecovery(ProjectContext ctx, Path filePath) {
        try {
            return ctx.project().currentPackage().getCompilation();
        } catch (BLangCompilerException e) {
            if (shouldCrashImmediately(e) || !isModuleLoadingFailure(e)) {
                throw e;
            }

            if (!reloadProjectWithoutLock(ctx, filePath, LSContextOperation.WS_WF_CHANGED.getName(), false, null)) {
                throw e;
            }
            try {
                return ctx.project().currentPackage().getCompilation();
            } catch (BLangCompilerException onlineRetryFailure) {
                if (shouldCrashImmediately(onlineRetryFailure) || !isModuleLoadingFailure(onlineRetryFailure)) {
                    throw onlineRetryFailure;
                }

                if (!reloadProjectWithoutLock(ctx, filePath, LSContextOperation.WS_WF_CHANGED.getName(), false,
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

    private boolean reloadProjectWithoutLock(ProjectContext ctx, Path filePath, String operationName, boolean offline,
                                             @Nullable PackageLockingMode lockingModeOverride) {
        Optional<ProjectLoadResult> loadResult = loadProjectResult(filePath, operationName, offline,
                lockingModeOverride);
        if (loadResult.isEmpty()) {
            ctx.setCompilationCrashed(true);
            return false;
        }

        ProjectLoadResult projectLoadResult = loadResult.get();
        ctx.setProject(projectLoadResult.targetProject());
        cacheLoadedProjects(computeProjectRoot(filePath), ctx, projectLoadResult);
        return true;
    }

    private boolean isModuleLoadingFailure(BLangCompilerException exception) {
        String message = exception.getMessage();
        return message != null && message.contains(FAILED_TO_LOAD_MODULE);
    }

    private boolean shouldCrashImmediately(BLangCompilerException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains(DiagnosticErrorCode.BAD_SAD_FROM_COMPILER.diagnosticId())
                || message.contains(DiagnosticErrorCode.CYCLIC_MODULE_IMPORTS_DETECTED.diagnosticId());
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
        Path projectRoot = projectRoot(filePath);
        ProjectContext projectContext = sourceRootToProject.get(projectRoot);
        if (projectContext != null && !(projectContext.isProjectCrashed() && isSourceChange)) {
            return projectContext;
        }

        if (projectContext != null && projectContext.isProjectCrashed() && isSourceChange) {
            ProjectContext removed = sourceRootToProject.remove(projectRoot);
            invalidateCacheFor(projectRoot);
            if (removed != null) {
                removed.close();
            }
        }

        return getOrCreateProject(projectRoot, filePath, operationName);
    }

    private record ProjectLoadResult(Project targetProject, @Nullable Project workspaceRootProject,
                                     List<Project> workspacePackages) {
    }

    /**
     * This class holds project and its lock.
     */
    public static class ProjectContext {

        private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock(true);
        private Project project;

        private volatile boolean compilationCrashed;

        private Process process;

        private volatile boolean projectCrashed;
        private volatile boolean closed = false;
        private final boolean workspaceChild;
        private final Path workspaceRoot;

        private ProjectContext(Project project) {
            this(project, false, null);
        }

        private ProjectContext(Project project, boolean workspaceChild, Path workspaceRoot) {
            this.project = project;
            this.compilationCrashed = false;
            this.workspaceChild = workspaceChild;
            this.workspaceRoot = workspaceRoot;
        }

        public static ProjectContext from(Project project) {
            return new ProjectContext(project);
        }

        public static ProjectContext from(Project project, boolean workspaceChild, Path workspaceRoot) {
            return new ProjectContext(project, workspaceChild, workspaceRoot);
        }

        public boolean isWorkspaceChild() {
            return workspaceChild;
        }

        public Path workspaceRoot() {
            return workspaceRoot;
        }

        /**
         * Execute an action under the read lock. Returns null if the context is closed.
         *
         * @param action the function to execute under read lock
         * @param <T> the return type
         * @return the result of the action, or null if closed
         * @since 1.7.0
         */
        public <T> T withReadLock(Function<ProjectContext, T> action) {
            rwl.readLock().lock();
            try {
                if (closed) {
                    return null;
                }
                return action.apply(this);
            } finally {
                rwl.readLock().unlock();
            }
        }

        /**
         * Execute an action under the write lock.
         *
         * @param action the consumer to execute under write lock
         * @since 1.7.0
         */
        public void withWriteLock(Consumer<ProjectContext> action) {
            rwl.writeLock().lock();
            try {
                if (closed) {
                    return;
                }
                action.accept(this);
            } finally {
                rwl.writeLock().unlock();
            }
        }

        /**
         * Returns the workspace document.
         *
         * @return {@link WorkspaceDocumentManager}
         */
        public Project project() {
            return this.project;
        }

        /**
         * Set workspace document.
         *
         * @param project {@link Project}
         */
        public void setProject(Project project) {
            this.project = project;
        }

        /**
         * Check if the project is in a crashed state.
         *
         * @return whether the compilation is in a crashed state
         */
        public boolean compilationCrashed() {
            return this.compilationCrashed;
        }

        /**
         * Set the crashed state.
         *
         * @param compilationCrashed crashed state
         */
        public void setCompilationCrashed(boolean compilationCrashed) {
            this.compilationCrashed = compilationCrashed;
        }

        /**
         * Set the project crashed status.
         *
         * @param projectCrashed whether the project is in a crashed state
         */
        public void setProjectCrashed(boolean projectCrashed) {
            this.projectCrashed = projectCrashed;
        }

        public boolean isProjectCrashed() {
            return projectCrashed;
        }

        /**
         * Project lock should be acquired before modifying (such as destroying) the process.
         *
         * @return Process associated with the project.
         */
        public Optional<Process> process() {
            return Optional.ofNullable(this.process);
        }

        /**
         * Set the process associated with the project. Project lock should be acquired before calling.
         *
         * @param process Process to be associated with the project.
         */
        public void setProcess(Process process) {
            this.process = process;
        }

        /**
         * Remove the process associated with the project. Project lock should be acquired before calling.
         */
        public void removeProcess() {
            this.process = null;
        }

        /**
         * Close this project context, releasing resources.
         *
         * @since 1.7.0
         */
        public void close() {
            rwl.writeLock().lock();
            try {
                if (!closed) {
                    closed = true;
                    if (process != null) {
                        process.destroy();
                        process = null;
                    }
                    if (project != null) {
                        project.clearCaches();
                        project = null;
                    }
                }
            } finally {
                rwl.writeLock().unlock();
            }
        }

        /**
         * Returns whether this context has been closed.
         *
         * @return true if closed
         * @since 1.7.0
         */
        public boolean isClosed() {
            return closed;
        }
    }

    private Optional<Path> findProjectRoot(Path filePath) {
        if (filePath != null) {
            filePath = filePath.toAbsolutePath().normalize();
            if (filePath.toFile().isDirectory()) {
                if (hasBallerinaToml(filePath) || hasPackageJson(filePath)) {
                    return Optional.of(filePath);
                }
            }
            return findProjectRoot(filePath.getParent());
        }
        return Optional.empty();
    }

    /**
     * Returns all workspace child ProjectContexts for a given workspace root.
     * Filters sourceRootToProject by workspaceChild flag matching the workspaceRoot.
     *
     * @param wsRoot the workspace root path
     * @return list of ProjectContexts for all workspace packages (excluding root)
     */
    private List<ProjectContext> workspaceChildren(Path wsRoot) {
        return sourceRootToProject.entrySet().stream()
                .filter(e -> e.getValue().isWorkspaceChild())
                .filter(e -> {
                    Path childRoot = e.getKey();
                    // workspaceChild's workspaceRoot field points to the workspace root
                    return wsRoot.equals(e.getValue().workspaceRoot());
                })
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    /**
     * Returns the workspace root ProjectContext for a given child path.
     * Returns empty if the path is not a workspace child or is the root itself.
     *
     * @param childRoot any path within the workspace (package root or document)
     * @return Optional containing the workspace root's ProjectContext
     */
    private Optional<ProjectContext> workspaceRoot(Path childRoot) {
        Path resolvedRoot = projectRoot(childRoot);
        ProjectContext ctx = sourceRootToProject.get(resolvedRoot);
        if (ctx == null) {
            return Optional.empty();
        }
        if (!ctx.isWorkspaceChild()) {
            return Optional.empty();
        }
        Path wsRoot = ctx.workspaceRoot();
        if (wsRoot == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sourceRootToProject.get(wsRoot));
    }

    private Optional<ProjectContext> getProjectOfWatchedFileChange(Path filePath, FileEvent fileEvent) {
        String fileName = filePath.getFileName().toString();
        boolean isBallerinaSourceChange = fileName.endsWith(ProjectConstants.BLANG_SOURCE_EXT);
        boolean isBallerinaTomlChange = filePath.endsWith(ProjectConstants.BALLERINA_TOML);
        boolean isDependenciesTomlChange = filePath.endsWith(ProjectConstants.DEPENDENCIES_TOML);
        boolean isCloudTomlChange = filePath.endsWith(ProjectConstants.CLOUD_TOML);
        boolean isCompilerPluginTomlChange = filePath.endsWith(ProjectConstants.COMPILER_PLUGIN_TOML);
        boolean isBalToolTomlChange = filePath.endsWith(ProjectConstants.BAL_TOOL_TOML);

        // NOTE: Need to specifically check Deleted events, since `filePath.toFile().isDirectory()`
        // fails when physical file is deleted from the disk
        boolean isModuleChange = filePath.toFile().isDirectory() &&
                filePath.getParent().endsWith(ProjectConstants.MODULES_ROOT) ||
                filePath.getParent().endsWith(ProjectConstants.GENERATED_MODULES_ROOT) ||
                (fileEvent.getType() == FileChangeType.Deleted && !isBallerinaSourceChange && !isBallerinaTomlChange &&
                        !isCloudTomlChange && !isDependenciesTomlChange && !isCompilerPluginTomlChange &&
                        !isBalToolTomlChange);

        return projectOfWatchedFileChange(filePath, fileEvent,
                isBallerinaSourceChange, isBallerinaTomlChange,
                isDependenciesTomlChange, isCloudTomlChange,
                isCompilerPluginTomlChange, isBalToolTomlChange, isModuleChange);
    }

    private boolean hasBallerinaToml(Path filePath) {
        Path absFilePath = filePath.toAbsolutePath().normalize();
        return absFilePath.resolve(BALLERINA_TOML).toFile().exists();
    }

    private boolean hasPackageJson(Path filePath) {
        Path absFilePath = filePath.toAbsolutePath().normalize();
        return absFilePath.resolve(ProjectConstants.PACKAGE_JSON).toFile().exists();
    }

    /**
     * Implementation of TomlHandlerContext providing narrow BWM access to TOML handlers.
     */
    private class TomlHandlerContextImpl implements TomlHandlerContext {

        @Override
        public void reloadProject(ProjectContext ctx, Path trigger, String operation) {
            BallerinaWorkspaceManager.this.reloadProject(ctx, trigger, operation);
        }

        @Override
        public Map<Path, ProjectContext> projectRegistry() {
            return BallerinaWorkspaceManager.this.sourceRootToProject;
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
                BallerinaWorkspaceManager.this.sourceRootToProject.put(packageRoot,
                        ProjectContext.from(workspacePackage, true, workspaceProject.sourceRoot()));
                BallerinaWorkspaceManager.this.invalidateCacheFor(packageRoot);
            }
        }

        @Override
        public Optional<ProjectContext> getOrCreateProject(Path projectRoot, Path triggerFile, String operation) {
            try {
                return Optional.of(
                        BallerinaWorkspaceManager.this.getOrCreateProject(projectRoot, triggerFile, operation));
            } catch (WorkspaceDocumentException e) {
                BallerinaWorkspaceManager.this.clientLogger.logError(LSContextOperation.WS_WF_CHANGED,
                        "Failed to get or create project: " + projectRoot, e, null, (org.eclipse.lsp4j.Position) null);
                return Optional.empty();
            }
        }

        @Override
        public Optional<ProjectContext> createProjectContext(Path tomlPath, String operation) {
            return BallerinaWorkspaceManager.this.createProjectContext(tomlPath, operation);
        }

        @Override
        public void invalidateCacheFor(Path path) {
            BallerinaWorkspaceManager.this.invalidateCacheFor(path);
        }
    }

    private static boolean isError(Diagnostic diagnostic) {
        return diagnostic.diagnosticInfo().severity().equals(DiagnosticSeverity.ERROR);
    }
}
