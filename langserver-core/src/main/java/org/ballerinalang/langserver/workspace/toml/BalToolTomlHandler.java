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

import io.ballerina.projects.BalToolToml;
import io.ballerina.projects.DocumentConfig;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.Package;
import io.ballerina.projects.util.ProjectConstants;
import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentException;
import org.ballerinalang.langserver.workspace.ProjectContext;

import java.util.Optional;

/**
 * Generic handler for BalTool.toml files.
 * 
 * <p>This handler manages BalTool.toml files which are config-only -
 * changes do NOT affect the dependency graph, allowing for optimized
 * handling without full project reloads.</p>
 * 
 * @since 2201.12.0
 */
class BalToolTomlHandler extends AbstractTomlHandler {

    BalToolTomlHandler(TomlHandlerContext context) {
        super(context);
    }

    @Override
    public String fileName() {
        return ProjectConstants.BAL_TOOL_TOML;
    }

    @Override
    public boolean affectsDependencyGraph() {
        return false; // Config-only
    }

    @Override
    protected void doUpdateContent(String content, ProjectContext ctx, boolean createIfNotExists) 
            throws WorkspaceDocumentException {
        Optional<BalToolToml> balToolToml = ctx.project().currentPackage().balToolToml();
        
        if (balToolToml.isEmpty()) {
            if (createIfNotExists) {
                DocumentConfig documentConfig = DocumentConfig.from(
                        DocumentId.create(ProjectConstants.BAL_TOOL_TOML, null), content,
                        ProjectConstants.BAL_TOOL_TOML
                );
                Package pkg = ctx.project().currentPackage().modify()
                        .addBalToolToml(documentConfig)
                        .apply();
                ctx.setProject(pkg.project());
                return;
            }
            throw new WorkspaceDocumentException(ProjectConstants.BAL_TOOL_TOML + " does not exist!");
        }

        BalToolToml updatedToml = balToolToml.get().modify().withContent(content).apply();
        ctx.setProject(updatedToml.packageInstance().project());
    }
}
