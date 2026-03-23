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

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

/**
 * Unit tests for DocumentManager delegate.
 *
 * @since 1.7.0
 */
public class DocumentManagerTest {

    private MockDocumentManagerContext context;
    private DocumentManager documentManager;

    @BeforeMethod
    void setUp() {
        context = new MockDocumentManagerContext();
        documentManager = new DocumentManager(context);
    }

    @Test(description = "Test openedDocuments returns empty set initially")
    public void testOpenedDocumentsInitiallyEmpty() {
        Set<Path> docs = documentManager.openedDocuments();
        Assert.assertNotNull(docs, "openedDocuments should not return null");
        Assert.assertTrue(docs.isEmpty(), "openedDocuments should be empty initially");
    }

    @Test(description = "Test openedDocuments reflects documents tracked")
    public void testOpenedDocumentsReflectsTracking() {
        // The openedDocuments set is managed internally by DocumentManager
        // Initial state should be empty
        Set<Path> docs = documentManager.openedDocuments();
        Assert.assertEquals(docs.size(), 0, "No documents should be tracked initially");
    }

    /**
     * Mock implementation of DocumentManagerContext for testing.
     */
    private static class MockDocumentManagerContext implements DocumentManagerContext {

        private final ProjectRegistry projectRegistry = new ProjectRegistry(new MockProjectRegistryContext());

        @Override
        @Nonnull
        public ProjectRegistry projectRegistry() {
            return projectRegistry;
        }

        @Override
        @Nonnull
        public Set<Path> openedDocuments() {
            return Set.of();
        }

        @Override
        public boolean experimental() {
            return false;
        }

        @Override
        @Nonnull
        public Optional<TomlHandler> tomlHandler(@Nonnull Path filePath) {
            return Optional.empty();
        }

        @Override
        @Nonnull
        public org.ballerinalang.langserver.commons.LanguageServerContext serverContext() {
            throw new UnsupportedOperationException("Not needed for DocumentManagerTest");
        }

        @Override
        @Nonnull
        public org.ballerinalang.langserver.LSClientLogger logger() {
            return null;
        }

        @Override
        @Nonnull
        public org.ballerinalang.langserver.commons.client.ExtendedLanguageClient client() {
            throw new UnsupportedOperationException("Not needed for DocumentManagerTest");
        }
    }

    /**
     * Mock implementation of ProjectRegistryContext for DocumentManager tests.
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
            throw new UnsupportedOperationException("Not needed for DocumentManagerTest");
        }

        @Override
        @Nonnull
        public org.ballerinalang.langserver.LSClientLogger logger() {
            return null;
        }

        @Override
        @Nonnull
        public org.ballerinalang.langserver.commons.client.ExtendedLanguageClient client() {
            throw new UnsupportedOperationException("Not needed for DocumentManagerTest");
        }
    }
}