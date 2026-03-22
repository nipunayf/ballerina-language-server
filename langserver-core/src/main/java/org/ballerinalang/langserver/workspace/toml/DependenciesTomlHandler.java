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

import io.ballerina.projects.DependenciesToml;
import io.ballerina.projects.DocumentConfig;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.Package;
import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectKind;
import io.ballerina.projects.util.ProjectConstants;
import org.ballerinalang.langserver.LSContextOperation;
import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentException;
import org.ballerinalang.langserver.workspace.BallerinaWorkspaceManager.ProjectContext;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handler for Dependencies.toml files.
 * 
 * <p>This handler manages Dependencies.toml files. Changes to Dependencies.toml
 * always affect the dependency graph and require full project reloads.</p>
 * 
 * @since 2201.12.0
 */
public class DependenciesTomlHandler extends AbstractTomlHandler {

    /**
     * Creates a new Dependencies.toml handler.
     * 
     * @param context the handler context
     */
    public DependenciesTomlHandler(TomlHandlerContext context) {
        super(context);
    }

    @Override
    public String fileName() {
        return ProjectConstants.DEPENDENCIES_TOML;
    }

    @Override
    public boolean affectsDependencyGraph() {
        return true;
    }

    @Override
    protected void onChanged(Path filePath, ProjectContext projectContext) throws WorkspaceDocumentException {
        // Dependencies.toml changes always require full project reload (dependency graph affected)
        context.reloadProject(projectContext, filePath, LSContextOperation.WS_WF_CHANGED.getName());
    }

    @Override
    protected void onDeleted(Path filePath, ProjectContext projectContext) throws WorkspaceDocumentException {
        Project project = projectContext.project();
        if (project.kind() == ProjectKind.BUILD_PROJECT) {
            AtomicReference<ProjectContext> removedRef = new AtomicReference<>();
            projectContext.withWriteLock(ctx -> {
                // When removing Dependencies.toml, reload the project
                Path ballerinaTomlFile = filePath.getParent().resolve(ProjectConstants.BALLERINA_TOML);
                context.logError("Dependencies.toml deleted, recreating project context: " + ballerinaTomlFile, null);
                
                // Note: Project recreation is complex and requires BWM-specific logic
                // This is handled by the context or caller
            });
        }
    }

    @Override
    protected void doUpdateContent(String content, ProjectContext ctx, boolean createIfNotExists) 
            throws WorkspaceDocumentException {
        Optional<DependenciesToml> dependenciesToml = ctx.project().currentPackage().dependenciesToml();
        
        if (dependenciesToml.isEmpty()) {
            if (createIfNotExists) {
                // Create new Dependencies.toml
                DocumentConfig documentConfig = DocumentConfig.from(
                        DocumentId.create(ProjectConstants.DEPENDENCIES_TOML, null), content,
                        ProjectConstants.DEPENDENCIES_TOML
                );
                Package pkg = ctx.project().currentPackage().modify()
                        .addDependenciesToml(documentConfig)
                        .apply();
                ctx.setProject(pkg.project());
                return;
            }
            throw new WorkspaceDocumentException(ProjectConstants.DEPENDENCIES_TOML + " does not exist!");
        }

        DependenciesToml updatedToml = dependenciesToml.get().modify().withContent(content).apply();
        ctx.setProject(updatedToml.packageInstance().project());
    }
}
