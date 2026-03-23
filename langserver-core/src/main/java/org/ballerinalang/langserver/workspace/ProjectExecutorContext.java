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

import io.ballerina.projects.PackageCompilation;

import java.nio.file.Path;
import java.util.Optional;

import javax.annotation.Nonnull;

/**
 * Narrow workspace access required by {@link ProjectExecutor}.
 *
 * @since 1.7.0
 */
interface ProjectExecutorContext extends WorkspaceContext {

    /**
     * Resolves the owning project root for the given file path.
     *
     * @param filePath file or project path
     * @return resolved project root
     */
    @Nonnull
    Path projectRoot(@Nonnull Path filePath);

    /**
     * Looks up the project context for the given project root.
     *
     * @param projectRoot project root path
     * @return matching project context if loaded
     */
    @Nonnull
    Optional<ProjectContext> projectContext(@Nonnull Path projectRoot);

    /**
     * Compiles the project for execution.
     *
     * @param filePath source or project root path
     * @param isSourceChange whether execution should treat this as a source change
     * @return package compilation if successful
     */
    @Nonnull
    Optional<PackageCompilation> waitAndGetPackageCompilation(@Nonnull Path filePath, boolean isSourceChange);
}
