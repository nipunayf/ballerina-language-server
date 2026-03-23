/*
 *  Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com) All Rights Reserved.
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.ballerinalang.langserver.workspace;

import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import io.ballerina.projects.Document;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.Module;
import io.ballerina.projects.PackageCompilation;
import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectException;
import io.ballerina.projects.ProjectKind;
import io.ballerina.projects.util.ProjectConstants;
import io.ballerina.projects.util.ProjectPaths;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;
import org.ballerinalang.compiler.BLangCompilerException;
import org.ballerinalang.langserver.LSContextOperation;
import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentException;
import org.ballerinalang.langserver.workspace.toml.TomlHandler;
import org.ballerinalang.util.diagnostic.DiagnosticErrorCode;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Handles document lifecycle and compilation operations.
 *
 * @since 1.7.0
 */
final class DocumentManager {

    private static final String FAILED_TO_LOAD_MODULE = "failed to load the module";

    private final DocumentManagerContext context;
    private final Set<Path> openedDocuments;

    DocumentManager(@Nonnull DocumentManagerContext context) {
        this.context = context;
        this.openedDocuments = ConcurrentHashMap.newKeySet();
    }

    /**
     * Returns the set of currently opened document paths.
     *
     * @return set of opened document paths
     */
    @Nonnull
    Set<Path> openedDocuments() {
        return openedDocuments;
    }

    /**
     * The document open notification is sent from the client to the server to signal newly opened text documents.
     *
     * @param filePath {@link Path} of the document
     * @param params   {@link DidOpenTextDocumentParams}
     * @throws WorkspaceDocumentException when project or document not found
     */
    void didOpen(@Nonnull Path filePath, @Nonnull DidOpenTextDocumentParams params) throws WorkspaceDocumentException {
        // Add the document to the opened documents set and the entry will only be removed via didClose.
        // Hence we assume the safe concurrent access for a given document path
        this.openedDocuments.add(filePath);
        ProjectContext projectContext = createOrGetProjectPair(filePath,
                LSContextOperation.TXT_DID_OPEN.getName(), true);
        Project project = projectContext.project();

        // Route TOML files through the registry
        Optional<TomlHandler> tomlHandlerOpt = context.tomlHandler(filePath);
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
    void didChange(@Nonnull Path filePath, @Nonnull DidChangeTextDocumentParams params) throws WorkspaceDocumentException {
        // Get Project and Lock
        ProjectContext projectContext = createOrGetProjectPair(filePath,
                LSContextOperation.TXT_DID_CHANGE.getName(), true);

        Project project = projectContext.project();

        // Route TOML files through the registry
        Optional<TomlHandler> tomlHandlerOpt = context.tomlHandler(filePath);
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
     * The document close notification is sent from the client to the server when the document got closed in the
     * client.
     *
     * @param filePath {@link Path} of the document
     * @param params   {@link DidCloseTextDocumentParams}
     */
    void didClose(@Nonnull Path filePath, @Nonnull DidCloseTextDocumentParams params) {
        this.openedDocuments.remove(filePath);
        Optional<Project> project = context.projectRegistry().projectContext(
                context.projectRegistry().projectRoot(filePath)).map(ProjectContext::project);
        if (project.isEmpty()) {
            return;
        }
        // If it is a single file project, remove project from mapping
        if (project.get().kind() == ProjectKind.SINGLE_FILE_PROJECT) {
            Path projectRoot = project.get().sourceRoot();
            context.projectRegistry().removeProjectContext(projectRoot);
            context.logger().logTrace("Operation '" + LSContextOperation.TXT_DID_CLOSE.getName() +
                    "' {project: '" + projectRoot.toUri().toString() +
                    "' kind: '" + project.get().kind().name().toLowerCase() + "'} removed");
        }
    }

    /**
     * Returns module from the path provided.
     *
     * @param filePath file path of the document
     * @return project of applicable type
     */
    @Nonnull
    Optional<Module> module(@Nonnull Path filePath) {
        return context.projectRegistry().projectContext(context.projectRegistry().projectRoot(filePath))
                .flatMap(ctx -> ctx.withReadLock(c -> {
                    Optional<Project> project = context.projectRegistry().projectContext(
                            context.projectRegistry().projectRoot(filePath)).map(ProjectContext::project);
                    if (project.isEmpty()) {
                        return Optional.empty();
                    }
                    Optional<Document> document = document(filePath, project.get(), null);
                    if (document.isEmpty()) {
                        if (filePath.equals(context.projectRegistry().projectRoot(filePath))) {
                            return Optional.of(project.get().currentPackage().getDefaultModule());
                        }
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
    @Nonnull
    Optional<Document> document(@Nonnull Path filePath) {
        return context.projectRegistry().projectContext(context.projectRegistry().projectRoot(filePath))
                .flatMap(ctx -> ctx.withReadLock(c -> {
                    Optional<Project> project = context.projectRegistry().projectContext(
                            context.projectRegistry().projectRoot(filePath)).map(ProjectContext::project);
                    return project.isPresent() ? document(filePath, project.get(), null) : Optional.<Document>empty();
                }));
    }

    /**
     * Returns syntax tree from the path provided.
     *
     * @param filePath file path of the document
     * @return {@link SyntaxTree}
     */
    @Nonnull
    Optional<SyntaxTree> syntaxTree(@Nonnull Path filePath) {
        return context.projectRegistry().projectContext(context.projectRegistry().projectRoot(filePath))
                .flatMap(ctx -> ctx.withReadLock(c -> {
                    Optional<Document> document = document(filePath);
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
    @Nonnull
    Optional<SemanticModel> semanticModel(@Nonnull Path filePath) {
        Optional<PackageCompilation> packageCompilation = waitAndGetPackageCompilation(filePath, false);
        return context.projectRegistry().projectContext(context.projectRegistry().projectRoot(filePath))
                .flatMap(ctx -> ctx.withReadLock(c -> {
                    Optional<Module> module = module(filePath);
                    if (module.isEmpty() || packageCompilation.isEmpty() || ctx.compilationCrashed()) {
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
     * @return {@link PackageCompilation}
     */
    @Nonnull
    Optional<PackageCompilation> waitAndGetPackageCompilation(@Nonnull Path filePath, boolean isSourceChange) {
        // Get Project and Lock
        Optional<ProjectContext> projectPair = context.projectRegistry().projectContext(
                context.projectRegistry().projectRoot(filePath));
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

    // ============================================================================================================== //

    private Optional<Document> document(@Nonnull Path filePath, @Nonnull Project project,
                                        @Nullable CancelChecker cancelChecker) {
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

    private PackageCompilation getPackageCompilationWithRecovery(@Nonnull ProjectContext ctx, @Nonnull Path filePath) {
        try {
            return ctx.project().currentPackage().getCompilation();
        } catch (BLangCompilerException e) {
            if (shouldCrashImmediately(e) || !isModuleLoadingFailure(e)) {
                throw e;
            }

            if (!context.projectRegistry().reloadProjectWithoutLock(ctx, filePath,
                    LSContextOperation.WS_WF_CHANGED.getName(), false, null)) {
                throw e;
            }
            try {
                return ctx.project().currentPackage().getCompilation();
            } catch (BLangCompilerException onlineRetryFailure) {
                if (shouldCrashImmediately(onlineRetryFailure) || !isModuleLoadingFailure(onlineRetryFailure)) {
                    throw onlineRetryFailure;
                }

                if (!context.projectRegistry().reloadProjectWithoutLock(ctx, filePath,
                        LSContextOperation.WS_WF_CHANGED.getName(), false, null)) {
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

    private boolean isModuleLoadingFailure(@Nonnull BLangCompilerException exception) {
        String message = exception.getMessage();
        return message != null && (message.contains(FAILED_TO_LOAD_MODULE) ||
                message.contains(DiagnosticErrorCode.BAD_SAD_FROM_COMPILER.diagnosticId()));
    }

    private boolean shouldCrashImmediately(@Nonnull BLangCompilerException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains(DiagnosticErrorCode.CYCLIC_MODULE_IMPORTS_DETECTED.diagnosticId());
    }

    private boolean hasCompilationCrashDiagnostic(@Nonnull PackageCompilation compilation) {
        return compilation.diagnosticResult().diagnostics().stream()
                .anyMatch(diagnostic -> Arrays.asList(
                        DiagnosticErrorCode.BAD_SAD_FROM_COMPILER.diagnosticId(),
                        DiagnosticErrorCode.CYCLIC_MODULE_IMPORTS_DETECTED.diagnosticId())
                        .contains(diagnostic.diagnosticInfo().code()));
    }

    private ProjectContext createOrGetProjectPair(@Nonnull Path filePath, @Nonnull String operationName,
                                                  boolean isSourceChange) throws WorkspaceDocumentException {
        Path projectRoot = context.projectRegistry().projectRoot(filePath);
        Optional<ProjectContext> existingContext = context.projectRegistry().projectContext(projectRoot);
        if (existingContext.isPresent() && !(existingContext.get().isProjectCrashed() && isSourceChange)) {
            return existingContext.get();
        }

        if (existingContext.isPresent() && existingContext.get().isProjectCrashed() && isSourceChange) {
            context.projectRegistry().removeProjectContext(projectRoot);
        }

        return context.projectRegistry().getOrCreateProjectOrThrow(projectRoot, filePath, operationName);
    }

    private void updateBalDocument(@Nonnull Path filePath, @Nonnull String content,
                                    @Nonnull ProjectContext projectContext) throws WorkspaceDocumentException {
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

    private void createBalDocument(@Nonnull Path filePath, @Nonnull String content,
                                    @Nonnull ProjectContext projectContext) throws WorkspaceDocumentException {
        try {
            projectContext.withWriteLock(ctx -> {
                try {
                    Optional<ProjectContext> newProjectContext =
                            context.projectRegistry().createProjectContext(filePath,
                                    LSContextOperation.TXT_DID_OPEN.getName());
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

    private static WorkspaceDocumentException unwrapWorkspaceDocumentException(@Nonnull RuntimeException exception) {
        if (exception.getCause() instanceof WorkspaceDocumentException workspaceDocumentException) {
            return workspaceDocumentException;
        }
        throw exception;
    }

    /**
     * Cancel checker interface for cancellation support.
     */
    @FunctionalInterface
    interface CancelChecker {
        void isCanceled();
    }

    private static boolean isError(@Nonnull Diagnostic diagnostic) {
        return diagnostic.diagnosticInfo().severity().equals(DiagnosticSeverity.ERROR);
    }
}