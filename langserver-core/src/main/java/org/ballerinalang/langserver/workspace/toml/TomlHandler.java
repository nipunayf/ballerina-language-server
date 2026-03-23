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

import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentException;
import org.ballerinalang.langserver.workspace.ProjectContext;
import org.eclipse.lsp4j.FileEvent;

import java.nio.file.Path;

/**
 * Strategy interface for handling TOML file changes in Ballerina projects.
 * 
 * <p>Implementations handle specific TOML file types (Ballerina.toml, Dependencies.toml,
 * Cloud.toml, etc.) with specialized behavior for dependency graph impact and
 * workspace vs. package context.</p>
 * 
 * @since 2201.12.0
 */
public interface TomlHandler {

    /**
     * Returns the TOML file name this handler manages.
     * 
     * @return the file name constant (e.g., ProjectConstants.BALLERINA_TOML)
     */
    String fileName();

    /**
     * Returns whether changes to this TOML file affect the dependency graph.
     * 
     * <p>If true, changes require a full project reload. If false, changes can be
     * handled with config-only updates (optimization for Cloud.toml, CompilerPlugin.toml,
     * BalTool.toml).</p>
     * 
     * @return true if changes affect dependencies, false for config-only files
     */
    boolean affectsDependencyGraph();

    /**
     * Handles a file system watch event for this TOML file.
     * 
     * <p>This method is called when the file watcher detects a change to the TOML file
     * (Created, Changed, or Deleted). The implementation should handle the appropriate
     * project update or reload based on the event type.</p>
     * 
     * @param filePath the path to the TOML file
     * @param fileEvent the file event containing the change type
     * @param projectContext the project context for the affected project
     * @throws WorkspaceDocumentException if handling fails
     */
    void handleWatchedChange(Path filePath, FileEvent fileEvent, ProjectContext projectContext) 
            throws WorkspaceDocumentException;

    /**
     * Updates the content of this TOML file in the project.
     * 
     * <p>This method is called for both explicit content updates and file creation.
     * Implementations should acquire appropriate locks before modifying project state.</p>
     * 
     * @param content the new TOML content
     * @param projectContext the project context to update
     * @param createIfNotExists if true, create the TOML if it doesn't exist
     * @throws WorkspaceDocumentException if update fails
     */
    void updateContent(String content, ProjectContext projectContext, boolean createIfNotExists) 
            throws WorkspaceDocumentException;
}
