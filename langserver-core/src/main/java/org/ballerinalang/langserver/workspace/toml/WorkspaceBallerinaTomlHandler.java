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

import io.ballerina.projects.Project;
import io.ballerina.projects.TomlDocument;
import io.ballerina.projects.util.ProjectConstants;
import org.ballerinalang.langserver.LSContextOperation;
import org.ballerinalang.langserver.commons.BallerinaCompilerApi;
import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentException;
import org.ballerinalang.langserver.workspace.BallerinaWorkspaceManager.ProjectContext;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Handler for workspace root Ballerina.toml files.
 * 
 * <p>This handler manages Ballerina.toml files at workspace roots (projects containing
 * multiple packages). It uses special compiler API methods for workspace-aware updates
 * and manages workspace children registration.</p>
 * 
 * @since 2201.12.0
 */
public class WorkspaceBallerinaTomlHandler extends AbstractTomlHandler {

    /**
     * Creates a new workspace Ballerina.toml handler.
     * 
     * @param context the handler context
     */
    public WorkspaceBallerinaTomlHandler(TomlHandlerContext context) {
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
        // Workspace Ballerina.toml changes require special handling via compiler API
        context.reloadProject(projectContext, filePath, LSContextOperation.WS_WF_CHANGED.getName());
        // Register workspace children after reload
        context.registerWorkspaceChildren(projectContext);
    }

    @Override
    protected void doUpdateContent(String content, ProjectContext ctx, boolean createIfNotExists) 
            throws WorkspaceDocumentException {
        Project project = ctx.project();
        BallerinaCompilerApi compilerApi = BallerinaCompilerApi.getInstance();

        if (!compilerApi.isWorkspaceProject(project)) {
            throw new WorkspaceDocumentException("Project is not a workspace project!");
        }

        Optional<TomlDocument> workspaceTomlOpt = compilerApi.getWorkspaceToml(project);
        if (workspaceTomlOpt.isEmpty()) {
            if (createIfNotExists) {
                throw new WorkspaceDocumentException(
                        "Cannot create workspace Ballerina.toml - not yet supported!");
            }
            throw new WorkspaceDocumentException(
                    "Workspace " + ProjectConstants.BALLERINA_TOML + " does not exist!");
        }

        Optional<Project> reloadedProjectOpt = compilerApi.updateWorkspaceToml(project, content);
        if (reloadedProjectOpt.isEmpty()) {
            throw new WorkspaceDocumentException(
                    "Failed to update workspace " + ProjectConstants.BALLERINA_TOML);
        }

        Project reloadedProject = reloadedProjectOpt.get();
        
        // Update registry and cache for workspace root
        Path sourceRoot = reloadedProject.sourceRoot();
        ProjectContext newContext = ProjectContext.from(reloadedProject, false, null);
        context.projectRegistry().put(sourceRoot, newContext);
        
        // Register all workspace children
        List<Project> workspacePackages = compilerApi.getWorkspaceProjectsInOrder(reloadedProject);
        for (Project workspacePackage : workspacePackages) {
            Path packageRoot = workspacePackage.sourceRoot();
            context.projectRegistry().put(packageRoot,
                    ProjectContext.from(workspacePackage, true, sourceRoot));
        }

        ctx.setProject(reloadedProject);
    }
}
