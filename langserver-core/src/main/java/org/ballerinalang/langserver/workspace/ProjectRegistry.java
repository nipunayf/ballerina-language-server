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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.cache.Weigher;
import io.ballerina.projects.BuildOptions;
import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectException;
import io.ballerina.projects.environment.PackageLockingMode;
import io.ballerina.projects.util.ProjectConstants;
import io.ballerina.projects.util.ProjectPaths;
import org.ballerinalang.langserver.LSContextOperation;
import org.ballerinalang.langserver.commons.BallerinaCompilerApi;
import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentException;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.ballerina.projects.util.ProjectConstants.BALLERINA_TOML;

/**
 * Handles project registration, caching, and lifecycle management.
 *
 * @since 1.7.0
 */
final class ProjectRegistry {

    private final ProjectRegistryContext context;
    private final Map<Path, Path> pathToSourceRootCache;
    private final Cache<Path, ProjectContext> projectCache;
    private final Map<Path, ProjectContext> sourceRootToProject;

    ProjectRegistry(@Nonnull ProjectRegistryContext context) {
        this.context = context;
        Cache<Path, Path> pathCache = CacheBuilder.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();
        this.pathToSourceRootCache = pathCache.asMap();
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

                        context.stopProject(root);
                        ctx.close();
                        invalidateCacheFor(root);

                        if (!ctx.isWorkspaceChild() && ctx.workspaceRoot() == null) {
                            cascadeEvictWorkspaceChildren(root);
                        }
                    }
                })
                .build();
        this.sourceRootToProject = projectCache.asMap();
    }

    /**
     * Returns the project registry map (source root to project context).
     *
     * @return map of source roots to project contexts
     */
    @Nonnull
    Map<Path, ProjectContext> sourceRootToProject() {
        return sourceRootToProject;
    }

    /**
     * Returns a project root from the path provided.
     *
     * @param filePath ballerina project or standalone file path
     * @return project root
     */
    @Nonnull
    Path projectRoot(@Nonnull Path filePath) {
        return pathToSourceRootCache.computeIfAbsent(filePath, this::computeProjectRoot);
    }

    /**
     * Looks up the project context for the given project root.
     *
     * @param projectRoot project root path
     * @return matching project context if loaded
     */
    @Nonnull
    Optional<ProjectContext> projectContext(@Nonnull Path projectRoot) {
        return Optional.ofNullable(sourceRootToProject.get(projectRoot));
    }

    /**
     * Caches the project context for the given project root.
     *
     * @param projectRoot     project root path
     * @param projectContext project context to cache
     */
    void cacheProjectContext(@Nonnull Path projectRoot, @Nonnull ProjectContext projectContext) {
        sourceRootToProject.put(projectRoot, projectContext);
        invalidateCacheFor(projectRoot);
    }

    /**
     * Removes the project context for the given project root.
     *
     * @param projectRoot project root path
     */
    void removeProjectContext(@Nonnull Path projectRoot) {
        ProjectContext removed = sourceRootToProject.remove(projectRoot);
        invalidateCacheFor(projectRoot);
        if (removed != null) {
            context.stopProject(projectRoot);
            removed.close();
        }
    }

    /**
     * Creates a new project context for the given file path.
     *
     * @param filePath      file path to create project for
     * @param operationName operation name for logging
     * @return created project context, or empty if creation failed
     */
    @Nonnull
    Optional<ProjectContext> createProjectContext(@Nonnull Path filePath, @Nonnull String operationName) {
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

    /**
     * Gets or creates a project context atomically using the project cache loader.
     *
     * @param projectRoot   cache key for the project
     * @param filePath      file path used for project detection
     * @param operationName operation name for logging
     * @return cached or newly created project context
     */
    @Nonnull
    Optional<ProjectContext> getOrCreateProject(@Nonnull Path projectRoot, @Nonnull Path filePath,
                                                @Nonnull String operationName) {
        try {
            return Optional.of(projectCache.get(projectRoot, () -> {
                Optional<ProjectContext> projectContext = createProjectContext(filePath, operationName);
                if (projectContext.isEmpty()) {
                    throw new WorkspaceDocumentException("Cannot find the project of uri: " + filePath);
                }
                return projectContext.get();
            }));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof WorkspaceDocumentException) {
                return Optional.empty();
            }
            return Optional.empty();
        }
    }

    /**
     * Gets or creates a project context, throwing on failure.
     *
     * @param projectRoot   cache key for the project
     * @param filePath      file path used for project detection
     * @param operationName operation name for logging
     * @return cached or newly created project context
     * @throws WorkspaceDocumentException if the project cannot be created
     */
    @Nonnull
    ProjectContext getOrCreateProjectOrThrow(@Nonnull Path projectRoot, @Nonnull Path filePath,
                                             @Nonnull String operationName) throws WorkspaceDocumentException {
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

    /**
     * Reloads the project for the given file path.
     *
     * @param projectContext project context to reload
     * @param filePath       trigger file path
     * @param operationName  operation name for logging
     */
    void reloadProject(@Nonnull ProjectContext projectContext, @Nonnull Path filePath, @Nonnull String operationName) {
        reloadProject(projectContext, filePath, operationName, false, null);
    }

    /**
     * Reloads the project for the given file path with specified locking mode.
     *
     * @param projectContext       project context to reload
     * @param filePath             trigger file path
     * @param operationName        operation name for logging
     * @param offline              whether to load offline
     * @param lockingModeOverride  optional locking mode override
     */
    void reloadProject(@Nonnull ProjectContext projectContext, @Nonnull Path filePath, @Nonnull String operationName,
                       boolean offline, @Nullable PackageLockingMode lockingModeOverride) {
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

    /**
     * Reloads the project without acquiring the write lock.
     *
     * @param ctx                  project context to reload
     * @param filePath             trigger file path
     * @param operationName        operation name for logging
     * @param offline              whether to load offline
     * @param lockingModeOverride  optional locking mode override
     * @return {@code true} if reload succeeded
     */
    boolean reloadProjectWithoutLock(@Nonnull ProjectContext ctx, @Nonnull Path filePath, @Nonnull String operationName,
                                     boolean offline, @Nullable PackageLockingMode lockingModeOverride) {
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

    /**
     * Returns all workspace child ProjectContexts for a given workspace root.
     *
     * @param wsRoot the workspace root path
     * @return list of ProjectContexts for all workspace packages (excluding root)
     */
    @Nonnull
    List<ProjectContext> workspaceChildren(@Nonnull Path wsRoot) {
        return sourceRootToProject.entrySet().stream()
                .filter(e -> e.getValue().isWorkspaceChild())
                .filter(e -> {
                    // workspaceChild's workspaceRoot field points to the workspace root
                    return wsRoot.equals(e.getValue().workspaceRoot());
                })
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    /**
     * Returns the workspace root ProjectContext for a given child path.
     *
     * @param childRoot any path within the workspace (package root or document)
     * @return Optional containing the workspace root's ProjectContext
     */
    @Nonnull
    Optional<ProjectContext> workspaceRoot(@Nonnull Path childRoot) {
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

    /**
     * Invalidates all cache entries for paths under the given source root.
     *
     * @param root the source root whose cache entries should be invalidated
     */
    void invalidateCacheFor(@Nonnull Path root) {
        pathToSourceRootCache.keySet().removeIf(path -> path.startsWith(root));
    }

    // ============================================================================================================== //

    private Path computeProjectRoot(@Nonnull Path path) {
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

    private ProjectContext createProjectContext(@Nonnull Project project, @Nullable Project workspaceRootProject) {
        if (workspaceRootProject == null || workspaceRootProject.sourceRoot().equals(project.sourceRoot())) {
            return ProjectContext.from(project, false, null);
        }
        return ProjectContext.from(project, true, workspaceRootProject.sourceRoot());
    }

    private Optional<ProjectLoadResult> loadProjectResult(@Nonnull Path filePath, @Nonnull String operationName) {
        return loadProjectResult(filePath, operationName, false, null);
    }

    private Optional<ProjectLoadResult> loadProjectResult(@Nonnull Path filePath, @Nonnull String operationName,
                                                          boolean offline,
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
                context.logger().logTrace("Operation '" + operationName +
                        "' {workspace package: '" + projectRoot.toUri() + "'} loaded from workspace");
                return Optional.of(new ProjectLoadResult(targetProject, project, workspacePackages));
            }

            context.logger().logTrace("Operation '" + operationName +
                    "' {project: '" + projectRoot.toUri() + "' kind: '" +
                    project.kind().name().toLowerCase(Locale.getDefault()) + "'} created");
            return Optional.of(new ProjectLoadResult(project, null, List.of()));
        } catch (ProjectException e) {
            this.projectContext(projectRoot).ifPresent(pc -> pc.setProjectCrashed(true));
            context.logger().notifyUser("Project load failed: " + e.getMessage(), e);
            context.logger().logError(LSContextOperation.CREATE_PROJECT, "Operation '" + operationName +
                            "' {project: '" + projectRoot.toUri() + "'" + "} failed", e,
                    new TextDocumentIdentifier(filePath.toUri().toString()));
            return Optional.empty();
        }
    }

    private BuildOptions buildOptions(boolean offline, @Nonnull PackageLockingMode lockingMode) {
        return BuildOptions.builder()
                .setOffline(offline)
                .setExperimental(context.experimental())
                .setLockingMode(lockingMode)
                .build();
    }

    private PackageLockingMode deriveLockingMode(@Nonnull Path projectRoot) {
        Path dependenciesTomlPath = projectRoot.resolve(ProjectConstants.DEPENDENCIES_TOML);
        return Files.exists(dependenciesTomlPath) ? PackageLockingMode.MEDIUM : PackageLockingMode.SOFT;
    }

    private void cacheLoadedProjects(@Nonnull Path primaryRoot, @Nullable ProjectContext primaryContext,
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

    private boolean hasOpenDocuments(@Nonnull Path root) {
        for (Path openDoc : context.openedDocuments()) {
            if (openDoc.startsWith(root)) {
                return true;
            }
        }
        return false;
    }

    private void cascadeEvictWorkspaceChildren(@Nonnull Path workspaceRoot) {
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

    /**
     * Registers workspace children projects after a workspace reload.
     * <p>
     * This method updates the project registry with workspace member projects
     * after a workspace Ballerina.toml change.
     *
     * @param workspaceCtx the workspace project context
     */
    void registerWorkspaceChildren(@Nonnull ProjectContext workspaceCtx) {
        Project workspaceProject = workspaceCtx.project();
        BallerinaCompilerApi compilerApi = BallerinaCompilerApi.getInstance();

        if (!compilerApi.isWorkspaceProject(workspaceProject)) {
            return;
        }

        List<Project> workspacePackages = compilerApi.getWorkspaceProjectsInOrder(workspaceProject);
        for (Project workspacePackage : workspacePackages) {
            Path packageRoot = workspacePackage.sourceRoot();
            sourceRootToProject.put(packageRoot,
                    ProjectContext.from(workspacePackage, true, workspaceProject.sourceRoot()));
            invalidateCacheFor(packageRoot);
        }
    }

    /**
     * Result of a project load operation.
     *
     * @param targetProject        the target project (resolved from workspace if applicable)
     * @param workspaceRootProject the workspace root project, or null if not a workspace
     * @param workspacePackages    all workspace packages in load order
     */
    private record ProjectLoadResult(@Nonnull Project targetProject, @Nullable Project workspaceRootProject,
                                     @Nonnull List<Project> workspacePackages) {
    }
}
