/*
 *  Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com)
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
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package io.ballerina.flowmodelgenerator.core.difference;

import com.google.gson.JsonElement;
import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.flowmodelgenerator.core.ModelGenerator;
import io.ballerina.projects.Document;
import io.ballerina.projects.Project;
import io.ballerina.tools.text.TextDocument;
import org.ballerinalang.langserver.commons.eventsync.exceptions.EventSyncException;
import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentException;
import org.ballerinalang.langserver.commons.workspace.WorkspaceManager;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Represents a request for obtaining flow model differences by applying file content changes to a shadowed project.
 * This class implements {@link Callable}, and returns a {@link JsonElement} with the flow model after applying 
 * the specified file changes.
 *
 * @since 1.1.0
 */
public class DifferenceRequest implements Callable<JsonElement> {

    private final String projectPath;
    private final Map<String, String> fileContentMap;
    private final WorkspaceManager workspaceManager;
    private final Map<Path, TextDocument> originalDocuments;

    public DifferenceRequest(String projectPath, Map<String, String> fileContentMap, 
                            WorkspaceManager workspaceManager) {
        this.projectPath = projectPath;
        this.fileContentMap = fileContentMap;
        this.workspaceManager = workspaceManager;
        this.originalDocuments = new HashMap<>();
    }

    @Override
    public JsonElement call() {
        try {
            Path projectDir = Path.of(projectPath);
            Project project = workspaceManager.loadProject(projectDir);

            // Apply file content changes to the project
            for (Map.Entry<String, String> entry : fileContentMap.entrySet()) {
                String fileName = entry.getKey();
                String fileContent = entry.getValue();
                Path filePath = projectDir.resolve(fileName);

                Optional<Document> document = workspaceManager.document(filePath);
                if (document.isEmpty()) {
                    continue;
                }

                // Store original document for reverting later
                originalDocuments.put(filePath, document.get().textDocument());

                // Apply the new content to the document
                document.get()
                        .modify()
                        .withContent(fileContent)
                        .apply();
            }

            // Generate flow model for the first modified file
            // (This matches the current implementation behavior)
            for (Map.Entry<String, String> entry : fileContentMap.entrySet()) {
                String fileName = entry.getKey();
                Path filePath = projectDir.resolve(fileName);

                Optional<SemanticModel> semanticModel = workspaceManager.semanticModel(filePath);
                Optional<Document> document = workspaceManager.document(filePath);

                if (semanticModel.isEmpty() || document.isEmpty()) {
                    continue;
                }

                // Generate the flow model with the updated content
                ModelGenerator modelGenerator = new ModelGenerator(project, semanticModel.get(), filePath);
                JsonElement flowModel = modelGenerator.getModuleNodes();
                return flowModel;
            }

            return null;
        } catch (WorkspaceDocumentException | EventSyncException e) {
            return null;
        }
    }

    /**
     * Generates a unique key for this difference request based on project path and file content map.
     *
     * @return a unique key for this request
     */
    public String getKey() {
        return projectPath + ":" + fileContentMap.hashCode();
    }

    /**
     * Reverts all modified documents back to their original state.
     */
    public void revertDocuments() {
        for (Map.Entry<Path, TextDocument> entry : originalDocuments.entrySet()) {
            Path filePath = entry.getKey();
            TextDocument originalDoc = entry.getValue();
            
            workspaceManager.document(filePath).ifPresent(document -> 
                document.modify()
                    .withContent(String.join(System.lineSeparator(), originalDoc.textLines()))
                    .apply()
            );
        }
        originalDocuments.clear();
    }
}
