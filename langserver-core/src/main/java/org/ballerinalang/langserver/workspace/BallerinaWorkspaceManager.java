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

import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import io.ballerina.projects.Document;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.Module;
import io.ballerina.projects.Package;
import io.ballerina.projects.PackageCompilation;
import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectException;
import io.ballerina.projects.environment.PackageLockingMode;
import io.ballerina.projects.util.ProjectConstants;
import io.ballerina.projects.util.ProjectPaths;
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
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Contains a set of utility methods to manage projects.
 *
 * @since 1.0.0
 */
public class BallerinaWorkspaceManager implements WorkspaceManager, ProjectExecutorContext, FileWatchHandlerContext,
        ProjectRegistryContext, DocumentManagerContext {

    protected final LSClientLogger clientLogger;
    private final LanguageServerContext serverContext;
    private final TomlHandlerRegistry tomlHandlerRegistry;
    private final ProjectExecutor projectExecutor;
    private final FileWatchHandler fileWatchHandler;
    private final ProjectRegistry projectRegistry;
    private final DocumentManager documentManager;
    private boolean experimental = false;

    public BallerinaWorkspaceManager(LanguageServerContext serverContext) {
        this.serverContext = serverContext;
        this.clientLogger = LSClientLogger.getInstance(serverContext);
        this.projectRegistry = new ProjectRegistry(this);
        this.projectExecutor = new ProjectExecutor(this);
        this.fileWatchHandler = new FileWatchHandler(this);
        this.documentManager = new DocumentManager(this);
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
        return documentManager.module(filePath);
    }

    @Override
    public Optional<Module> module(Path filePath, @Nonnull CancelChecker cancelChecker) {
        cancelChecker.checkCanceled();
        return documentManager.module(filePath);
    }

    /**
     * Returns document of the project of this path.
     *
     * @param filePath file path of the document
     * @return {@link Document}
     */
    @Override
    public Optional<Document> document(Path filePath) {
        return documentManager.document(filePath);
    }

    @Override
    public Optional<Document> document(Path filePath, @Nonnull CancelChecker cancelChecker) {
        cancelChecker.checkCanceled();
        return documentManager.document(filePath);
    }

    /**
     * Returns syntax tree from the path provided.
     *
     * @param filePath file path of the document
     * @return {@link SyntaxTree}
     */
    @Override
    public Optional<SyntaxTree> syntaxTree(Path filePath) {
        return documentManager.syntaxTree(filePath);
    }

    @Override
    public Optional<SyntaxTree> syntaxTree(Path filePath, @Nonnull CancelChecker cancelChecker) {
        cancelChecker.checkCanceled();
        return documentManager.syntaxTree(filePath);
    }

    /**
     * Returns semantic model from the path provided.
     *
     * @param filePath file path of the document
     * @return {@link SemanticModel}
     */
    @Override
    public Optional<SemanticModel> semanticModel(Path filePath) {
        return documentManager.semanticModel(filePath);
    }

    @Override
    public Optional<SemanticModel> semanticModel(Path filePath, @Nonnull CancelChecker cancelChecker) {
        cancelChecker.checkCanceled();
        return documentManager.semanticModel(filePath);
    }

    /**
     * Returns module compilation from the file path provided.
     *
     * @param filePath file path of the document
     * @return {@link ModuleCompilation}
     */
    @Override
    public Optional<PackageCompilation> waitAndGetPackageCompilation(Path filePath) {
        return documentManager.waitAndGetPackageCompilation(filePath, false);
    }

    @Override
    public Optional<PackageCompilation> waitAndGetPackageCompilation(Path filePath,
                                                                     @Nonnull CancelChecker cancelChecker) {
        cancelChecker.checkCanceled();
        return documentManager.waitAndGetPackageCompilation(filePath, false);
    }

    @Override
    public Optional<PackageCompilation> waitAndGetPackageCompilation(Path filePath, boolean isSourceChange) {
        return documentManager.waitAndGetPackageCompilation(filePath, isSourceChange);
    }

    /**
     * The document open notification is sent from the client to the server to signal newly opened text documents.
     *
     * @param filePath {@link Path} of the document
     * @param params   {@link DidOpenTextDocumentParams}
     */
    @Override
    public void didOpen(Path filePath, DidOpenTextDocumentParams params) throws WorkspaceDocumentException {
        documentManager.didOpen(filePath, params);
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
        documentManager.didChange(filePath, params);
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

    /**
     * The document close notification is sent from the client to the server when the document got closed in the
     * client.
     *
     * @param filePath {@link Path} of the document
     * @param params   {@link DidCloseTextDocumentParams}
     */
    @Override
    public void didClose(Path filePath, DidCloseTextDocumentParams params) {
        documentManager.didClose(filePath, params);
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
            return documentManager.openedDocuments();
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
        return documentManager.openedDocuments();
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
