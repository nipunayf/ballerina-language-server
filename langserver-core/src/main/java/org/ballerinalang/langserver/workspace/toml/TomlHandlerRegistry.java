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

import io.ballerina.projects.util.ProjectConstants;
import org.ballerinalang.langserver.commons.BallerinaCompilerApi;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry for TOML handlers providing dispatch table functionality.
 * 
 * <p>This class maintains a mapping from TOML file names to their corresponding
 * handlers. It handles the workspace bifurcation for Ballerina.toml files
 * (distinguishing between workspace root and package-level Ballerina.toml)
 * and provides lookup functionality for dispatching TOML operations.</p>
 * 
 * @since 2201.12.0
 */
public class TomlHandlerRegistry {

    private static final String WORKSPACE_PREFIX = "WORKSPACE_";
    
    private final Map<String, TomlHandler> handlers = new HashMap<>();
    private final TomlHandlerContext context;

    /**
     * Creates a new TOML handler registry and initializes all handlers.
     * 
     * <p>The registry creates handlers for all supported TOML file types:
     * <ul>
     *   <li>Ballerina.toml (package-level)</li>
     *   <li>Ballerina.toml (workspace root - special handling)</li>
     *   <li>Dependencies.toml</li>
     *   <li>Cloud.toml (config-only)</li>
     *   <li>Compiler-plugin.toml (config-only)</li>
     *   <li>BalTool.toml (config-only)</li>
     * </ul>
     * </p>
     * 
     * @param context the handler context for all handlers
     */
    public TomlHandlerRegistry(TomlHandlerContext context) {
        this.context = context;
        initializeHandlers();
    }

    /**
     * Initializes all TOML handlers and registers them by file name.
     */
    private void initializeHandlers() {
        // Package-level Ballerina.toml handler
        handlers.put(ProjectConstants.BALLERINA_TOML, new BallerinaTomlHandler(context));
        
        // Workspace root Ballerina.toml handler (special key for lookup)
        handlers.put(WORKSPACE_PREFIX + ProjectConstants.BALLERINA_TOML, 
                new WorkspaceBallerinaTomlHandler(context));
        
        // Dependencies.toml handler
        handlers.put(ProjectConstants.DEPENDENCIES_TOML, new DependenciesTomlHandler(context));
        
        // Cloud.toml handler (config-only)
        handlers.put(ProjectConstants.CLOUD_TOML, new CloudTomlHandler(context));
        
        // Compiler-plugin.toml handler (config-only)
        handlers.put(ProjectConstants.COMPILER_PLUGIN_TOML, new CompilerPluginTomlHandler(context));
        
        // BalTool.toml handler (config-only)
        handlers.put(ProjectConstants.BAL_TOOL_TOML, new BalToolTomlHandler(context));
    }

    /**
     * Looks up the appropriate handler for the given file path.
     * 
     * <p>This method handles workspace bifurcation for Ballerina.toml files:
     * if the file is at a workspace root (determined by
     * {@link BallerinaCompilerApi#isWorkspaceProjectRoot(Path)}), the workspace
     * handler is returned instead of the regular package handler.</p>
     * 
     * @param filePath the path to the TOML file
     * @return the handler for the file, or empty if no handler is registered
     */
    public Optional<TomlHandler> lookup(Path filePath) {
        if (filePath == null) {
            return Optional.empty();
        }
        
        Path fileNamePath = filePath.getFileName();
        if (fileNamePath == null) {
            return Optional.empty();
        }
        
        String fileName = fileNamePath.toString();
        
        // Handle workspace bifurcation for Ballerina.toml
        if (fileName.equals(ProjectConstants.BALLERINA_TOML)) {
            Path parentDir = filePath.getParent();
            if (parentDir != null && BallerinaCompilerApi.getInstance().isWorkspaceProjectRoot(parentDir)) {
                return Optional.ofNullable(handlers.get(WORKSPACE_PREFIX + fileName));
            }
        }
        
        return Optional.ofNullable(handlers.get(fileName));
    }

    /**
     * Returns the handler for the given file name.
     * 
     * <p>This is a direct lookup without workspace bifurcation logic.
     * Use {@link #lookup(Path)} for file paths that may be workspace roots.</p>
     * 
     * @param fileName the TOML file name
     * @return the handler, or empty if not found
     */
    public Optional<TomlHandler> getHandler(String fileName) {
        return Optional.ofNullable(handlers.get(fileName));
    }

    /**
     * Returns true if a handler is registered for the given file name.
     * 
     * @param fileName the TOML file name
     * @return true if a handler exists
     */
    public boolean hasHandler(String fileName) {
        return handlers.containsKey(fileName);
    }
}
