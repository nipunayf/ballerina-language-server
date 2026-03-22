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
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.Package;
import io.ballerina.projects.util.ProjectConstants;
import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentException;
import org.ballerinalang.langserver.workspace.BallerinaWorkspaceManager.ProjectContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Generic handler for config-only TOML files.
 * 
 * <p>This handler manages Cloud.toml, Compiler-plugin.toml, and BalTool.toml files.
 * These TOML files are config-only - changes do NOT affect the dependency graph,
 * allowing for optimized handling without full project reloads.</p>
 * 
 * <p>The handler is parameterized with functions to get and add the specific TOML type,
 * allowing a single class to handle multiple TOML types.</p>
 * 
 * @since 2201.12.0
 */
public class GenericTomlHandler extends AbstractTomlHandler {

    private final String fileName;
    private final Function<Package, Optional<? extends ModifiableToml>> getTomlFn;
    private final BiFunction<Package.Modifier, DocumentConfig, Package.Modifier> addTomlFn;

    /**
     * Interface for TOML types that support modify operations.
     * <p>This is a functional interface to abstract over CloudToml, CompilerPluginToml, BalToolToml.</p>
     */
    @FunctionalInterface
    public interface ModifiableToml {
        /**
         * Modifies the TOML content.
         * 
         * @return a modifier that accepts new content
         */
        Modifier modify();
        
        /**
         * Modifier interface for applying content changes.
         */
        @FunctionalInterface
        interface Modifier {
            /**
             * Sets the new content.
             * 
             * @param content the new TOML content
             * @return an applicator that applies the change
             */
            Applicator withContent(String content);
        }
        
        /**
         * Applicator interface for finalizing the modification.
         */
        @FunctionalInterface
        interface Applicator {
            /**
             * Applies the modification and returns the modified TOML.
             * 
             * @return the modified TOML object
             */
            ModifiableToml apply();
        }
    }

    /**
     * Creates a new generic TOML handler.
     * 
     * @param context the handler context
     * @param fileName the TOML file name (e.g., ProjectConstants.CLOUD_TOML)
     * @param getTomlFn function to get the TOML from a package
     * @param addTomlFn function to add the TOML to a package modifier
     */
    public GenericTomlHandler(TomlHandlerContext context, String fileName,
                              Function<Package, Optional<? extends ModifiableToml>> getTomlFn,
                              BiFunction<Package.Modifier, DocumentConfig, Package.Modifier> addTomlFn) {
        super(context);
        this.fileName = fileName;
        this.getTomlFn = getTomlFn;
        this.addTomlFn = addTomlFn;
    }

    @Override
    public String fileName() {
        return fileName;
    }

    @Override
    public boolean affectsDependencyGraph() {
        return false; // Config-only: Cloud, CompilerPlugin, BalTool don't affect dependencies
    }

    @Override
    protected void onChanged(Path filePath, ProjectContext projectContext) throws WorkspaceDocumentException {
        // Config-only change: read file and update without full reload
        try {
            String content = Files.readString(filePath);
            updateContent(content, projectContext, false);
        } catch (IOException e) {
            throw new WorkspaceDocumentException("Could not read " + fileName, e);
        }
    }

    @Override
    protected void doUpdateContent(String content, ProjectContext ctx, boolean createIfNotExists) 
            throws WorkspaceDocumentException {
        Optional<? extends ModifiableToml> tomlOpt = getTomlFn.apply(ctx.project().currentPackage());
        
        if (tomlOpt.isEmpty()) {
            if (createIfNotExists) {
                // Create new TOML
                DocumentConfig documentConfig = DocumentConfig.from(
                        DocumentId.create(fileName, null), content, fileName
                );
                Package pkg = addTomlFn.apply(ctx.project().currentPackage().modify(), documentConfig).apply();
                ctx.setProject(pkg.project());
                return;
            }
            throw new WorkspaceDocumentException(fileName + " does not exist!");
        }

        ModifiableToml toml = tomlOpt.get();
        ModifiableToml modifiedToml = toml.modify().withContent(content).apply();
        ctx.setProject(((TomlWrapper) modifiedToml).packageInstance().project());
    }

    /**
     * Wrapper interface to extract package from wrapped TOML types.
     * This is needed because the actual TOML types (CloudToml, etc.) have packageInstance() method.
     */
    private interface TomlWrapper {
        Package packageInstance();
    }
}
