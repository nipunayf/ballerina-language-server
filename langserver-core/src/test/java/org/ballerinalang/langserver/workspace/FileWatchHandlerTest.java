/*
 *  Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com) All Rights Reserved.
 *
 *  WSO2 LLC licenses this file to you under the Apache License,
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

import org.ballerinalang.langserver.workspace.toml.TomlHandler;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import io.ballerina.projects.Document;
import io.ballerina.projects.Project;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;

/**
 * Unit tests for FileWatchHandler delegate.
 *
 * @since 1.7.0
 */
public class FileWatchHandlerTest {

    private MockFileWatchHandlerContext context;
    private FileWatchHandler handler;

    @BeforeMethod
    void setUp() {
        context = new MockFileWatchHandlerContext();
        handler = new FileWatchHandler(context);
    }

    @Test(description = "Test didChangeWatched returns empty list when file watcher is disabled")
    public void testDidChangeWatchedDisabled() throws org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentException {
        context.fileWatcherEnabled = false;
        org.eclipse.lsp4j.DidChangeWatchedFilesParams params = new org.eclipse.lsp4j.DidChangeWatchedFilesParams();
        java.util.List<Path> result = handler.didChangeWatched(params);
        Assert.assertTrue(result.isEmpty(), "didChangeWatched should return empty list when file watcher disabled");
    }

    @Test(description = "Test didChangeWatched returns empty list for empty changes")
    public void testDidChangeWatchedEmptyChanges() throws org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentException {
        context.fileWatcherEnabled = true;
        org.eclipse.lsp4j.DidChangeWatchedFilesParams params = new org.eclipse.lsp4j.DidChangeWatchedFilesParams();
        params.setChanges(java.util.Collections.emptyList());
        
        java.util.List<Path> result = handler.didChangeWatched(params);
        Assert.assertTrue(result.isEmpty(), "didChangeWatched should return empty list for empty changes");
    }

    /**
     * Mock implementation of FileWatchHandlerContext for testing.
     */
    private static class MockFileWatchHandlerContext implements FileWatchHandlerContext {

        boolean fileWatcherEnabled = true;
        private final ProjectRegistry projectRegistry = new ProjectRegistry(new MockProjectRegistryContext());

        @Override
        @Nonnull
        public ProjectRegistry projectRegistry() {
            return projectRegistry;
        }

        @Override
        @Nonnull
        public Optional<Document> document(@Nonnull Path filePath, @Nonnull Project project) {
            return Optional.empty();
        }

        @Override
        @Nonnull
        public Optional<TomlHandler> tomlHandler(@Nonnull Path filePath) {
            return Optional.empty();
        }

        @Override
        @Nonnull
        public Set<Path> openedDocuments() {
            return Set.of();
        }

        @Override
        public boolean isFileWatcherEnabled() {
            return fileWatcherEnabled;
        }

        @Override
        @Nonnull
        public org.ballerinalang.langserver.commons.LanguageServerContext serverContext() {
            throw new UnsupportedOperationException("Not needed for FileWatchHandlerTest");
        }

        @Override
        @Nonnull
        public org.ballerinalang.langserver.LSClientLogger logger() {
            return null;
        }

        @Override
        @Nonnull
        public org.ballerinalang.langserver.commons.client.ExtendedLanguageClient client() {
            throw new UnsupportedOperationException("Not needed for FileWatchHandlerTest");
        }
    }

    /**
     * Mock implementation of ProjectRegistryContext for FileWatchHandler tests.
     */
    private static class MockProjectRegistryContext implements ProjectRegistryContext {

        @Override
        public boolean experimental() {
            return false;
        }

        @Override
        @Nonnull
        public Set<Path> openedDocuments() {
            return Set.of();
        }

        @Override
        public void stopProject(@Nonnull Path projectRoot) {
            // No-op
        }

        @Override
        @Nonnull
        public org.ballerinalang.langserver.commons.LanguageServerContext serverContext() {
            throw new UnsupportedOperationException("Not needed for FileWatchHandlerTest");
        }

        @Override
        @Nonnull
        public org.ballerinalang.langserver.LSClientLogger logger() {
            return null;
        }

        @Override
        @Nonnull
        public org.ballerinalang.langserver.commons.client.ExtendedLanguageClient client() {
            throw new UnsupportedOperationException("Not needed for FileWatchHandlerTest");
        }
    }
}