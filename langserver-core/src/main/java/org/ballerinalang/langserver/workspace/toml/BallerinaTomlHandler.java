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

import io.ballerina.projects.BallerinaToml;
import io.ballerina.projects.DocumentConfig;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.Package;
import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectKind;
import io.ballerina.projects.util.ProjectConstants;
import org.ballerinalang.langserver.LSContextOperation;
import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentException;
import org.ballerinalang.langserver.workspace.ProjectContext;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handler for package-level Ballerina.toml files.
 * 
 * <p>This handler manages Ballerina.toml files in regular (non-workspace) packages.
 * Changes to Ballerina.toml affect the dependency graph and require full project reloads.</p>
 * 
 * @since 2201.12.0
 */
public class BallerinaTomlHandler extends AbstractTomlHandler {

    /**
     * Creates a new Ballerina.toml handler.
     * 
     * @param context the handler context
     */
    public BallerinaTomlHandler(TomlHandlerContext context) {
        super(context);
    }

    @Override
    public String fileName() {
        return ProjectConstants.BALLERINA_TOML;
    }

    @Override
    public boolean affectsDependencyGraph() {
        return true;
    }

    @Override
    protected void onChanged(Path filePath, ProjectContext projectContext) throws WorkspaceDocumentException {
        // Ballerina.toml changes always require full project reload (dependency graph affected)
        context.reloadProject(projectContext, filePath, LSContextOperation.WS_WF_CHANGED.getName());
    }

    @Override
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
                
                // For single-file project downgrade, just remove from registry
                // The project will be recreated as single-file on next access
                context.logError("Ballerina.toml deleted, project downgraded to single-file: " + projectRoot, null);
            });
        } else {
            throw new WorkspaceDocumentException("Invalid operation, cannot delete Ballerina.toml!");
        }
    }

    @Override
    protected void doUpdateContent(String content, ProjectContext ctx, boolean createIfNotExists) 
            throws WorkspaceDocumentException {
        Optional<BallerinaToml> ballerinaToml = ctx.project().currentPackage().ballerinaToml();
        
        if (ballerinaToml.isEmpty()) {
            if (createIfNotExists) {
                if (ctx.project().kind() == ProjectKind.SINGLE_FILE_PROJECT) {
                    Path projectRoot = ctx.project().sourceRoot();
                    context.projectRegistry().remove(projectRoot);
                    context.invalidateCacheFor(projectRoot);
                    Path ballerinaTomlFilePath = projectRoot.getParent().resolve(ProjectConstants.BALLERINA_TOML);
                    context.getOrCreateProject(projectRoot.getParent(), ballerinaTomlFilePath,
                            LSContextOperation.WS_WF_CHANGED.getName());
                    return;
                }
                throw new WorkspaceDocumentException("Invalid operation, cannot create Ballerina.toml!");
            }
            throw new WorkspaceDocumentException(ProjectConstants.BALLERINA_TOML + " does not exist!");
        }

        BallerinaToml updatedToml = ballerinaToml.get().modify().withContent(content).apply();
        ctx.setProject(updatedToml.packageInstance().project());
    }
}
