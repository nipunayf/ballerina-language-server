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

import org.ballerinalang.langserver.workspace.BallerinaWorkspaceManager.ProjectContext;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Context interface exposing narrow access to BallerinaWorkspaceManager state.
 * 
 * <p>This interface provides TOML handlers with controlled access to workspace
 * manager operations without exposing the full internal state. It follows the
 * principle of least privilege, exposing only the operations handlers need.</p>
 * 
 * @since 2201.12.0
 */
public interface TomlHandlerContext {

    /**
     * Reloads a project after a significant change.
     * 
     * <p>This method triggers a full project reload, which re-resolves dependencies
     * and recompiles the project. Use for changes that affect the dependency graph.</p>
     * 
     * @param ctx the project context to reload
     * @param trigger the path that triggered the reload
     * @param operation the operation name for logging
     */
    void reloadProject(ProjectContext ctx, Path trigger, String operation);

    /**
     * Returns the project registry mapping source roots to project contexts.
     * 
     * <p>The registry is used to look up and manage project contexts by their
     * source root paths. Handlers should use this for project lifecycle operations.</p>
     * 
     * @return the project registry map
     */
    Map<Path, ProjectContext> projectRegistry();

    /**
     * Returns the set of currently opened documents.
     * 
     * <p>Used to check if a file is currently open in an editor, which affects
     * how file watch events are handled (open files use didChange events instead).</p>
     * 
     * @return set of opened document paths
     */
    Set<Path> openedDocuments();

    /**
     * Logs an error message.
     * 
     * @param message the error message
     * @param t the throwable that caused the error
     */
    void logError(String message, Throwable t);

    /**
     * Registers workspace children projects after a workspace reload.
     * 
     * <p>This method updates the project registry with workspace member projects
     * after a workspace Ballerina.toml change. It ensures all workspace packages
     * are properly registered and cached.</p>
     * 
     * @param workspaceCtx the workspace project context
     */
    void registerWorkspaceChildren(ProjectContext workspaceCtx);
}
