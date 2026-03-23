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

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;

/**
 * Unit tests for ProjectRegistry delegate.
 *
 * @since 1.7.0
 */
public class ProjectRegistryTest {

    private MockProjectRegistryContext context;
    private ProjectRegistry registry;

    @BeforeMethod
    void setUp() {
        context = new MockProjectRegistryContext();
        registry = new ProjectRegistry(context);
    }

    @Test(description = "Test sourceRootToProject returns empty map initially")
    public void testSourceRootToProjectInitiallyEmpty() {
        Assert.assertNotNull(registry.sourceRootToProject(), "sourceRootToProject should not return null");
        Assert.assertTrue(registry.sourceRootToProject().isEmpty(), "sourceRootToProject should start empty");
    }

    @Test(description = "Test projectContext returns empty for unknown project root")
    public void testProjectContextReturnsEmptyForUnknown() {
        Path unknownRoot = Path.of("/unknown/project");
        Optional<ProjectContext> result = registry.projectContext(unknownRoot);
        Assert.assertTrue(result.isEmpty(), "projectContext should return empty for unknown root");
    }

    @Test(description = "Test workspaceChildren returns empty list for unknown root")
    public void testWorkspaceChildrenReturnsEmptyForUnknown() {
        Path unknownRoot = Path.of("/unknown/workspace");
        Assert.assertTrue(registry.workspaceChildren(unknownRoot).isEmpty(),
                "workspaceChildren should return empty for unknown root");
    }

    /**
     * Mock implementation of ProjectRegistryContext for testing.
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
            // No-op for mock
        }

        @Override
        @Nonnull
        public org.ballerinalang.langserver.commons.LanguageServerContext serverContext() {
            throw new UnsupportedOperationException("Not needed for ProjectRegistryTest");
        }

        @Override
        @Nonnull
        public org.ballerinalang.langserver.LSClientLogger logger() {
            return null;
        }

        @Override
        @Nonnull
        public org.ballerinalang.langserver.commons.client.ExtendedLanguageClient client() {
            throw new UnsupportedOperationException("Not needed for ProjectRegistryTest");
        }
    }
}