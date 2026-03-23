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
import io.ballerina.projects.Module;
import io.ballerina.projects.PackageCompilation;
import io.ballerina.projects.Project;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import io.ballerina.compiler.api.SemanticModel;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;

/**
 * Narrow workspace access required by {@link DocumentManager}.
 *
 * @since 1.7.0
 */
interface DocumentManagerContext extends WorkspaceContext {

    /**
     * Returns the project registry for project lookup operations.
     *
     * @return project registry
     */
    @Nonnull
    ProjectRegistry projectRegistry();

    /**
     * Returns the set of currently opened document paths.
     *
     * @return set of opened document paths
     */
    @Nonnull
    Set<Path> openedDocuments();

    /**
     * Returns whether experimental language features are enabled.
     *
     * @return {@code true} if experimental features are enabled
     */
    boolean experimental();

    /**
     * Looks up a TOML handler for the given file path.
     *
     * @param filePath file path to check
     * @return TOML handler if applicable, empty otherwise
     */
    @Nonnull
    Optional<org.ballerinalang.langserver.workspace.toml.TomlHandler> tomlHandler(@Nonnull Path filePath);
}