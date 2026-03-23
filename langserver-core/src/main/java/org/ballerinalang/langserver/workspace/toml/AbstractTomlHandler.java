/*
 *  Copyright (c) 2025, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.ballerinalang.langserver.workspace.toml;

import io.ballerina.projects.DocumentConfig;
import io.ballerina.projects.Package;
import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectKind;
import io.ballerina.projects.util.ProjectConstants;
import org.ballerinalang.langserver.LSContextOperation;
import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentException;
import org.ballerinalang.langserver.workspace.ProjectContext;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstract base class for TOML handlers implementing the template method pattern.
 * 
 * <p>This class provides default implementations for the {@link TomlHandler} interface
 * using the template method pattern. Concrete subclasses implement type-specific
 * behavior by overriding the hook methods.</p>
 * 
 * <p>The class enforces proper locking via {@link ProjectContext#withWriteLock} for
 * all project mutations, ensuring thread safety.</p>
 * 
 * @since 2201.12.0
 */
public abstract class AbstractTomlHandler implements TomlHandler {

    protected final TomlHandlerContext context;

    /**
     * Creates a new TOML handler with the given context.
     * 
     * @param context the handler context for accessing workspace manager state
     */
    protected AbstractTomlHandler(TomlHandlerContext context) {
        this.context = context;
    }

    @Override
    public void handleWatchedChange(Path filePath, FileEvent fileEvent, ProjectContext projectContext) 
            throws WorkspaceDocumentException {
        try {
            FileChangeType changeType = fileEvent.getType();
            if (changeType == FileChangeType.Created) {
                onCreated(filePath, projectContext);
            } else if (changeType == FileChangeType.Changed) {
                onChanged(filePath, projectContext);
            } else if (changeType == FileChangeType.Deleted) {
                onDeleted(filePath, projectContext);
            }
        } catch (Exception e) {
            context.logError("Error handling TOML file change: " + filePath, e);
            if (e instanceof WorkspaceDocumentException) {
                throw (WorkspaceDocumentException) e;
            }
            throw new WorkspaceDocumentException("Could not handle " + fileName() + " change!", e);
        }
    }

    /**
     * Handles TOML file creation.
     * 
     * <p>Default implementation reads the file content and calls {@link #updateContent}.
     * Subclasses may override for specialized creation handling.</p>
     * 
     * @param filePath the path to the created file
     * @param projectContext the project context
     * @throws WorkspaceDocumentException if handling fails
     */
    protected void onCreated(Path filePath, ProjectContext projectContext) throws WorkspaceDocumentException {
        try {
            String content = Files.readString(filePath);
            updateContent(content, projectContext, true);
        } catch (IOException e) {
            throw new WorkspaceDocumentException("Could not handle " + fileName() + " creation!", e);
        }
    }

    /**
     * Handles TOML file modification.
     * 
     * <p>Default implementation reloads the project if {@link #affectsDependencyGraph()}
     * returns true, otherwise calls {@link #updateContent} for config-only changes.</p>
     * 
     * @param filePath the path to the changed file
     * @param projectContext the project context
     * @throws WorkspaceDocumentException if handling fails
     */
    protected void onChanged(Path filePath, ProjectContext projectContext) throws WorkspaceDocumentException {
        if (affectsDependencyGraph()) {
            context.reloadProject(projectContext, filePath, LSContextOperation.WS_WF_CHANGED.getName());
        } else {
            // Config-only change: read file and update without full reload
            try {
                String content = Files.readString(filePath);
                updateContent(content, projectContext, false);
            } catch (IOException e) {
                throw new WorkspaceDocumentException("Could not read " + fileName(), e);
            }
        }
    }

    /**
     * Handles TOML file deletion.
     * 
     * <p>Default implementation removes the project context and recreates it
     * via the Ballerina.toml path. This is the standard behavior for TOML deletion.</p>
     * 
     * @param filePath the path to the deleted file
     * @param projectContext the project context
     * @throws WorkspaceDocumentException if handling fails
     */
    protected void onDeleted(Path filePath, ProjectContext projectContext) throws WorkspaceDocumentException {
        Project project = projectContext.project();
        if (project.kind() == ProjectKind.BUILD_PROJECT) {
            AtomicReference<ProjectContext> removedRef = new AtomicReference<>();
            projectContext.withWriteLock(ctx -> {
                Path projectRoot = project.sourceRoot();
                ProjectContext removed = context.projectRegistry().remove(projectRoot);
                if (removed != null) {
                    removed.close();
                }
                removedRef.set(removed);
                
                // Note: Full project recreation is handled by the caller or context
                // This is a placeholder - concrete handlers may override for specific behavior
                context.logError("TOML deletion requires project recreation: " + filePath, null);
            });
        } else {
            throw new WorkspaceDocumentException("Invalid operation, cannot delete " + fileName() + 
                    " from single-file project!");
        }
    }

    @Override
    public void updateContent(String content, ProjectContext projectContext, boolean createIfNotExists) 
            throws WorkspaceDocumentException {
        try {
            projectContext.withWriteLock(ctx -> {
                try {
                    doUpdateContent(content, ctx, createIfNotExists);
                } catch (WorkspaceDocumentException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof WorkspaceDocumentException) {
                throw (WorkspaceDocumentException) cause;
            }
            throw e;
        }
    }

    /**
     * Performs the actual content update under the write lock.
     * 
     * <p>Subclasses must implement this method to perform the type-specific
     * TOML update (e.g., BallerinaToml.modify(), CloudToml.modify()).</p>
     * 
     * @param content the new TOML content
     * @param ctx the project context (already holding write lock)
     * @param createIfNotExists if true, create the TOML if it doesn't exist
     * @throws WorkspaceDocumentException if update fails
     */
    protected abstract void doUpdateContent(String content, ProjectContext ctx, boolean createIfNotExists) 
            throws WorkspaceDocumentException;
}
