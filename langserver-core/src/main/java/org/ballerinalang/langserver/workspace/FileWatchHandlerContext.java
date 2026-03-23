/*
 *  Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com) All Rights Reserved.
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
 *  KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.ballerinalang.langserver.workspace;

import io.ballerina.projects.Document;
import io.ballerina.projects.Project;
import org.ballerinalang.langserver.workspace.toml.TomlHandler;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;

/**
 * Narrow workspace access required by {@link FileWatchHandler}.
 *
 * @since 1.7.0
 */
interface FileWatchHandlerContext extends WorkspaceContext {

    @Nonnull
    Path projectRoot(@Nonnull Path filePath);

    @Nonnull
    Optional<ProjectContext> projectContext(@Nonnull Path projectRoot);

    @Nonnull
    Optional<ProjectContext> createProjectContext(@Nonnull Path filePath, @Nonnull String operationName);

    @Nonnull
    Optional<ProjectContext> getOrCreateProject(@Nonnull Path projectRoot, @Nonnull Path filePath,
                                                @Nonnull String operationName);

    void reloadProject(@Nonnull ProjectContext projectContext, @Nonnull Path filePath, @Nonnull String operationName);

    void removeProjectContext(@Nonnull Path projectRoot);

    @Nonnull
    Optional<Document> document(@Nonnull Path filePath, @Nonnull Project project);

    @Nonnull
    Optional<TomlHandler> tomlHandler(@Nonnull Path filePath);

    @Nonnull
    Set<Path> openedDocuments();

    boolean isFileWatcherEnabled();
}
