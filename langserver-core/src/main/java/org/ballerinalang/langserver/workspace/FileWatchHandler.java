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

import io.ballerina.projects.Document;
import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectException;
import io.ballerina.projects.ProjectKind;
import io.ballerina.projects.util.ProjectConstants;
import io.ballerina.projects.util.ProjectPaths;
import org.ballerinalang.langserver.LSContextOperation;
import org.ballerinalang.langserver.common.utils.PathUtil;
import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentException;
import org.ballerinalang.langserver.workspace.toml.TomlHandler;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;

/**
 * Handles watched-file change routing and project reload decisions.
 *
 * @since 1.7.0
 */
final class FileWatchHandler {

    private final FileWatchHandlerContext context;

    FileWatchHandler(@Nonnull FileWatchHandlerContext context) {
        this.context = context;
    }

    /**
     * Handles a watched file change for a single path.
     *
     * @param filePath changed file path
     * @param fileEvent LSP file change event
     * @throws WorkspaceDocumentException when document state cannot be updated
     */
    void didChangeWatched(@Nonnull Path filePath, @Nonnull FileEvent fileEvent) throws WorkspaceDocumentException {
        if (!context.isFileWatcherEnabled()) {
            return;
        }

        ProjectRegistry projectRegistry = context.projectRegistry();
        Optional<ProjectContext> optProject = getProjectOfWatchedFileChange(filePath, fileEvent);
        if (optProject.isEmpty()) {
            context.logger().logTrace(
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
        Optional<TomlHandler> tomlHandlerOpt = context.tomlHandler(filePath);
        boolean isTomlChange = tomlHandlerOpt.isPresent();

        if (fileEvent.getType() == FileChangeType.Created
                && (isBallerinaSourceChange || isTomlChange)
                && hasDocumentOrToml(filePath, project)) {
            context.logger().logTrace(
                    String.format("Operation '%s' File already exits, {fileUri: '%s' event: '%s'} ignored",
                            LSContextOperation.WS_WF_CHANGED.getName(),
                            fileEvent.getUri(),
                            fileEvent.getType().name()));
            return;
        }

        if (tomlHandlerOpt.isPresent()) {
            tomlHandlerOpt.get().handleWatchedChange(filePath, fileEvent, projectContext);
        } else if (isBallerinaSourceChange) {
            handleWatchedBalSourceChange(filePath, fileEvent, projectContext);
        } else {
            handleWatchedModuleChange(filePath, fileEvent, projectContext);
        }
    }

    /**
     * Handles a batched watched-file change notification.
     *
     * @param params watched file change batch
     * @return reloaded project roots
     * @throws WorkspaceDocumentException when reload/update fails
     */
    @Nonnull
    List<Path> didChangeWatched(@Nonnull DidChangeWatchedFilesParams params) throws WorkspaceDocumentException {
        if (!context.isFileWatcherEnabled()) {
            return Collections.emptyList();
        }

        ProjectRegistry projectRegistry = context.projectRegistry();
        List<FileEvent> changes = params.getChanges();
        if (changes.size() == 1) {
            FileEvent fileEvent = changes.get(0);
            Optional<Path> pathFromURI = PathUtil.getPathFromURI(fileEvent.getUri());
            if (pathFromURI.isEmpty()) {
                return Collections.emptyList();
            }

            Path filePath = pathFromURI.get();
            if (!context.openedDocuments().contains(filePath) || fileEvent.getType() == FileChangeType.Deleted) {
                didChangeWatched(filePath, fileEvent);
                return getProjectOfWatchedFileChange(filePath, fileEvent)
                        .map(projectContext -> List.of(projectContext.project().sourceRoot()))
                        .orElseGet(Collections::emptyList);
            }
            return Collections.emptyList();
        }

        Set<Path> reloadableProjects = new HashSet<>();
        for (FileEvent fileEvent : changes) {
            Optional<Path> pathFromURI = PathUtil.getPathFromURI(fileEvent.getUri());
            if (pathFromURI.isEmpty()) {
                return Collections.emptyList();
            }

            Path filePath = pathFromURI.get();
            try {
                reloadableProjects.add(ProjectPaths.packageRoot(filePath));
            } catch (ProjectException ignored) {
                // Ignore path identification failures so other events can proceed.
            }
        }

        reloadableProjects.forEach(path -> projectRegistry.projectContext(path).ifPresent(projectContext ->
                projectContext.withWriteLock(ctx -> {
                    Optional<ProjectContext> updatedProject =
                            projectRegistry.createProjectContext(path, LSContextOperation.WS_WF_CHANGED.getName());
                    if (updatedProject.isEmpty()) {
                        throw new IllegalStateException("Cannot find the project of uri: " + path);
                    }
                    ctx.setProject(updatedProject.get().project());
                })));
        return new ArrayList<>(reloadableProjects);
    }

    private Optional<ProjectContext> getProjectOfWatchedFileChange(Path filePath, FileEvent fileEvent) {
        String fileName = filePath.getFileName().toString();
        boolean isBallerinaSourceChange = fileName.endsWith(ProjectConstants.BLANG_SOURCE_EXT);
        boolean isBallerinaTomlChange = filePath.endsWith(ProjectConstants.BALLERINA_TOML);
        boolean isDependenciesTomlChange = filePath.endsWith(ProjectConstants.DEPENDENCIES_TOML);
        boolean isCloudTomlChange = filePath.endsWith(ProjectConstants.CLOUD_TOML);
        boolean isCompilerPluginTomlChange = filePath.endsWith(ProjectConstants.COMPILER_PLUGIN_TOML);
        boolean isBalToolTomlChange = filePath.endsWith(ProjectConstants.BAL_TOOL_TOML);

        boolean isModuleChange = filePath.toFile().isDirectory()
                && filePath.getParent().endsWith(ProjectConstants.MODULES_ROOT)
                || filePath.getParent().endsWith(ProjectConstants.GENERATED_MODULES_ROOT)
                || (fileEvent.getType() == FileChangeType.Deleted
                && !isBallerinaSourceChange
                && !isBallerinaTomlChange
                && !isCloudTomlChange
                && !isDependenciesTomlChange
                && !isCompilerPluginTomlChange
                && !isBalToolTomlChange);

        return projectOfWatchedFileChange(filePath, fileEvent,
                isBallerinaSourceChange, isBallerinaTomlChange, isDependenciesTomlChange,
                isCloudTomlChange, isCompilerPluginTomlChange, isBalToolTomlChange, isModuleChange);
    }

    private Optional<ProjectContext> projectOfWatchedFileChange(Path filePath, FileEvent fileEvent,
                                                                boolean isBallerinaSourceChange,
                                                                boolean isBallerinaTomlChange,
                                                                boolean isDependenciesTomlChange,
                                                                boolean isCloudTomlChange,
                                                                boolean isCompilerPluginTomlChange,
                                                                boolean isBalToolTomlChange,
                                                                boolean isModuleChange) {
        ProjectRegistry projectRegistry = context.projectRegistry();
        if (isBallerinaSourceChange) {
            if (fileEvent.getType() == FileChangeType.Created) {
                return projectRegistry.projectContext(projectRegistry.projectRoot(filePath));
            }

            Optional<ProjectContext> optProject = projectRegistry.projectContext(filePath);
            if (optProject.isPresent()) {
                return optProject;
            }

            Path parent = filePath.getParent();
            if (ProjectConstants.TEST_DIR_NAME.equals(parent.getFileName().toString())) {
                parent = parent.getParent();
            }
            if (ProjectConstants.MODULES_ROOT.equals(parent.getParent().getFileName().toString())
                    || ProjectConstants.GENERATED_MODULES_ROOT.equals(parent.getParent().getFileName().toString())) {
                parent = parent.getParent().getParent();
            }
            if (ProjectConstants.GENERATED_MODULES_ROOT.equals(parent.getFileName().toString())) {
                parent = parent.getParent();
            }
            return projectRegistry.projectContext(parent);
        }

        if (isBallerinaTomlChange) {
            if (fileEvent.getType() == FileChangeType.Created) {
                Optional<ProjectContext> optProject = projectRegistry.projectContext(filePath.getParent()).filter(pc ->
                        pc.project().kind() == ProjectKind.SINGLE_FILE_PROJECT
                                && pc.project().sourceRoot().getParent().equals(filePath.getParent()));
                if (optProject.isEmpty()) {
                    return projectRegistry.getOrCreateProject(filePath.getParent(), filePath,
                            LSContextOperation.WS_WF_CHANGED.getName());
                }
                return optProject;
            }
            return projectRegistry.projectContext(filePath.getParent());
        }

        if (isCloudTomlChange || isCompilerPluginTomlChange || isBalToolTomlChange || isDependenciesTomlChange) {
            return projectRegistry.projectContext(filePath.getParent());
        }

        if (!isModuleChange) {
            return Optional.empty();
        }

        Path projectRoot;
        if (ProjectConstants.MODULES_ROOT.equals(filePath.getFileName().toString())
                || ProjectConstants.GENERATED_MODULES_ROOT.equals(filePath.getFileName().toString())) {
            projectRoot = filePath.getParent();
        } else {
            projectRoot = filePath.getParent().getParent();
        }
        return projectRegistry.projectContext(projectRoot);
    }

    private void handleWatchedBalSourceChange(Path filePath, FileEvent fileEvent, ProjectContext projectContext) {
        ProjectRegistry projectRegistry = context.projectRegistry();
        switch (fileEvent.getType()) {
            case Created:
                projectRegistry.reloadProject(projectContext, filePath, LSContextOperation.WS_WF_CHANGED.getName());
                break;
            case Changed:
                if (!context.openedDocuments().contains(filePath)) {
                    projectRegistry.reloadProject(projectContext, filePath, LSContextOperation.WS_WF_CHANGED.getName());
                }
                break;
            case Deleted:
                Project project = projectContext.project();
                if (project.kind() == ProjectKind.SINGLE_FILE_PROJECT) {
                    Path projectRoot = project.sourceRoot();
                    projectRegistry.removeProjectContext(projectRoot);
                    context.logger().logTrace(String.format("Operation '%s' {project: '%s' kind: '%s'} removed",
                            LSContextOperation.WS_WF_CHANGED.getName(),
                            projectRoot.toUri().toString(),
                            project.kind().name().toLowerCase(Locale.getDefault())));
                    return;
                }

                Optional<Document> document = context.document(filePath, project);
                if (document.isPresent()) {
                    projectContext.withWriteLock(ctx -> {
                        Project updatedProj = document.get().module().modify().removeDocument(
                                document.get().documentId()).apply().project();
                        ctx.setProject(updatedProj);
                        context.logger().logTrace(String.format("Operation '%s' {fileUri: '%s'} removed",
                                LSContextOperation.WS_WF_CHANGED.getName(), fileEvent.getUri()));
                    });
                    return;
                }

                Path ballerinaTomlPath = project.sourceRoot().resolve(ProjectConstants.BALLERINA_TOML);
                projectRegistry.reloadProject(projectContext, ballerinaTomlPath, LSContextOperation.WS_WF_CHANGED.getName());
                break;
            default:
                break;
        }
    }

    private void handleWatchedModuleChange(Path filePath, FileEvent fileEvent, ProjectContext projectContext) {
        ProjectRegistry projectRegistry = context.projectRegistry();
        String fileName = filePath.getFileName().toString();
        switch (fileEvent.getType()) {
            case Created:
                context.logger().logTrace(String.format("Operation '%s' {module: '%s', uri: '%s'} created",
                        LSContextOperation.WS_WF_CHANGED.getName(), fileName, filePath.toUri().toString()));
                projectRegistry.reloadProject(projectContext,
                        filePath.getParent().getParent().resolve(ProjectConstants.BALLERINA_TOML),
                        LSContextOperation.WS_WF_CHANGED.getName());
                break;
            case Deleted:
                if (ProjectConstants.MODULES_ROOT.equals(filePath.getFileName().toString())) {
                    context.logger().logTrace(String.format("Operation '%s' {uri: '%s'} removed all modules",
                            LSContextOperation.WS_WF_CHANGED.getName(), filePath.toUri().toString()));
                    projectRegistry.reloadProject(projectContext,
                            filePath.getParent().resolve(ProjectConstants.BALLERINA_TOML),
                            LSContextOperation.WS_WF_CHANGED.getName());
                    return;
                }

                context.logger().logTrace(String.format("Operation '%s' {module: '%s', uri: '%s'} removed",
                        LSContextOperation.WS_WF_CHANGED.getName(), fileName, filePath.toUri().toString()));
                projectRegistry.reloadProject(projectContext,
                        filePath.getParent().getParent().resolve(ProjectConstants.BALLERINA_TOML),
                        LSContextOperation.WS_WF_CHANGED.getName());
                break;
            default:
                break;
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
            default -> fileName.endsWith(ProjectConstants.BLANG_SOURCE_EXT)
                    && context.document(filePath, project).isPresent();
        };
    }
}
