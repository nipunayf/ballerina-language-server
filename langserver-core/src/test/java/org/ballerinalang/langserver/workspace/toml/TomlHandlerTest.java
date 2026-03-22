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
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Tests for TOML handler dispatch and lifecycle.
 * Per D-26: Dedicated test class in the toml sub-package.
 *
 * @since 2201.12.0
 */
public class TomlHandlerTest {

    private TomlHandlerRegistry registry;

    @BeforeMethod
    void setUp() {
        // Create a mock context for testing
        TomlHandlerContext mockContext = createMockContext();
        registry = new TomlHandlerRegistry(mockContext);
    }

    // ==================== Dispatch Correctness Tests ====================

    @Test(description = "Registry returns BallerinaTomlHandler for Ballerina.toml in non-workspace")
    public void testRegistryReturnsBallerinaTomlHandler() {
        Path tomlPath = Path.of("/some/project").resolve(ProjectConstants.BALLERINA_TOML);
        Optional<TomlHandler> handlerOpt = registry.lookup(tomlPath);
        Assert.assertTrue(handlerOpt.isPresent(), "Handler should be present for Ballerina.toml");
        TomlHandler handler = handlerOpt.get();
        Assert.assertEquals(handler.fileName(), ProjectConstants.BALLERINA_TOML);
        Assert.assertTrue(handler.affectsDependencyGraph(),
                "Ballerina.toml should affect dependency graph");
    }

    @Test(description = "Registry returns DependenciesTomlHandler for Dependencies.toml")
    public void testRegistryReturnsDependenciesHandler() {
        Path tomlPath = Path.of("/some/project").resolve(ProjectConstants.DEPENDENCIES_TOML);
        Optional<TomlHandler> handlerOpt = registry.lookup(tomlPath);
        Assert.assertTrue(handlerOpt.isPresent(), "Handler should be present for Dependencies.toml");
        TomlHandler handler = handlerOpt.get();
        Assert.assertEquals(handler.fileName(), ProjectConstants.DEPENDENCIES_TOML);
        Assert.assertTrue(handler.affectsDependencyGraph(),
                "Dependencies.toml should affect dependency graph");
    }

    @Test(description = "Registry returns GenericTomlHandler for Cloud.toml (config-only)")
    public void testRegistryReturnsGenericHandlerForCloud() {
        Path tomlPath = Path.of("/some/project").resolve(ProjectConstants.CLOUD_TOML);
        Optional<TomlHandler> handlerOpt = registry.lookup(tomlPath);
        Assert.assertTrue(handlerOpt.isPresent(), "Handler should be present for Cloud.toml");
        TomlHandler handler = handlerOpt.get();
        Assert.assertEquals(handler.fileName(), ProjectConstants.CLOUD_TOML);
        Assert.assertFalse(handler.affectsDependencyGraph(),
                "Cloud.toml should be config-only (not affect dependency graph)");
    }

    @Test(description = "Registry returns GenericTomlHandler for Compiler-plugin.toml (config-only)")
    public void testRegistryReturnsGenericHandlerForCompilerPlugin() {
        Path tomlPath = Path.of("/some/project").resolve(ProjectConstants.COMPILER_PLUGIN_TOML);
        Optional<TomlHandler> handlerOpt = registry.lookup(tomlPath);
        Assert.assertTrue(handlerOpt.isPresent(), "Handler should be present for Compiler-plugin.toml");
        TomlHandler handler = handlerOpt.get();
        Assert.assertEquals(handler.fileName(), ProjectConstants.COMPILER_PLUGIN_TOML);
        Assert.assertFalse(handler.affectsDependencyGraph(),
                "Compiler-plugin.toml should be config-only (not affect dependency graph)");
    }

    @Test(description = "Registry returns GenericTomlHandler for BalTool.toml (config-only)")
    public void testRegistryReturnsGenericHandlerForBalTool() {
        Path tomlPath = Path.of("/some/project").resolve(ProjectConstants.BAL_TOOL_TOML);
        Optional<TomlHandler> handlerOpt = registry.lookup(tomlPath);
        Assert.assertTrue(handlerOpt.isPresent(), "Handler should be present for BalTool.toml");
        TomlHandler handler = handlerOpt.get();
        Assert.assertEquals(handler.fileName(), ProjectConstants.BAL_TOOL_TOML);
        Assert.assertFalse(handler.affectsDependencyGraph(),
                "BalTool.toml should be config-only (not affect dependency graph)");
    }

    @Test(description = "Registry returns null for non-TOML files")
    public void testRegistryReturnsNullForNonToml() {
        Path filePath = Path.of("/some/project/main.bal");
        Optional<TomlHandler> handlerOpt = registry.lookup(filePath);
        Assert.assertTrue(handlerOpt.isEmpty(), "Handler should be empty for non-TOML files");
    }

    @Test(description = "Registry returns null for unknown TOML files")
    public void testRegistryReturnsNullForUnknownToml() {
        Path filePath = Path.of("/some/project/Unknown.toml");
        Optional<TomlHandler> handlerOpt = registry.lookup(filePath);
        Assert.assertTrue(handlerOpt.isEmpty(), "Handler should be empty for unknown TOML files");
    }

    @Test(description = "Registry returns null for null path")
    public void testRegistryReturnsNullForNullPath() {
        Optional<TomlHandler> handlerOpt = registry.lookup(null);
        Assert.assertTrue(handlerOpt.isEmpty(), "Handler should be empty for null path");
    }

    @Test(description = "Registry returns null for path with no file name")
    public void testRegistryReturnsNullForPathWithNoFileName() {
        Path filePath = Path.of("/");
        Optional<TomlHandler> handlerOpt = registry.lookup(filePath);
        Assert.assertTrue(handlerOpt.isEmpty(), "Handler should be empty for path with no file name");
    }

    // ==================== Handler Existence Tests ====================

    @Test(description = "Registry has handler for Ballerina.toml")
    public void testRegistryHasHandlerForBallerinaToml() {
        Assert.assertTrue(registry.hasHandler(ProjectConstants.BALLERINA_TOML));
    }

    @Test(description = "Registry has handler for Dependencies.toml")
    public void testRegistryHasHandlerForDependenciesToml() {
        Assert.assertTrue(registry.hasHandler(ProjectConstants.DEPENDENCIES_TOML));
    }

    @Test(description = "Registry has handler for Cloud.toml")
    public void testRegistryHasHandlerForCloudToml() {
        Assert.assertTrue(registry.hasHandler(ProjectConstants.CLOUD_TOML));
    }

    @Test(description = "Registry has handler for Compiler-plugin.toml")
    public void testRegistryHasHandlerForCompilerPluginToml() {
        Assert.assertTrue(registry.hasHandler(ProjectConstants.COMPILER_PLUGIN_TOML));
    }

    @Test(description = "Registry has handler for BalTool.toml")
    public void testRegistryHasHandlerForBalToolToml() {
        Assert.assertTrue(registry.hasHandler(ProjectConstants.BAL_TOOL_TOML));
    }

    @Test(description = "Registry does not have handler for unknown files")
    public void testRegistryDoesNotHaveHandlerForUnknownFiles() {
        Assert.assertFalse(registry.hasHandler("Unknown.toml"));
        Assert.assertFalse(registry.hasHandler("main.bal"));
    }

    // ==================== Direct Handler Lookup Tests ====================

    @Test(description = "Direct lookup returns handler by file name")
    public void testDirectLookupByFileName() {
        Optional<TomlHandler> handlerOpt = registry.getHandler(ProjectConstants.BALLERINA_TOML);
        Assert.assertTrue(handlerOpt.isPresent());
        Assert.assertEquals(handlerOpt.get().fileName(), ProjectConstants.BALLERINA_TOML);
    }

    /**
     * Creates a mock TomlHandlerContext for testing.
     */
    private TomlHandlerContext createMockContext() {
        return new TomlHandlerContext() {
            @Override
            public void reloadProject(org.ballerinalang.langserver.workspace.BallerinaWorkspaceManager.ProjectContext ctx,
                                      Path trigger, String operation) {
                // Mock implementation
            }

            @Override
            public java.util.Map<Path, org.ballerinalang.langserver.workspace.BallerinaWorkspaceManager.ProjectContext>
            projectRegistry() {
                return new java.util.HashMap<>();
            }

            @Override
            public java.util.Set<Path> openedDocuments() {
                return java.util.concurrent.ConcurrentHashMap.newKeySet();
            }

            @Override
            public void logError(String message, Throwable t) {
                // Mock implementation - just print to stderr
                System.err.println("LOG ERROR: " + message);
                if (t != null) {
                    t.printStackTrace();
                }
            }

            @Override
            public void registerWorkspaceChildren(
                    org.ballerinalang.langserver.workspace.BallerinaWorkspaceManager.ProjectContext workspaceCtx) {
                // Mock implementation
            }

            @Override
            public java.util.Optional<org.ballerinalang.langserver.workspace.BallerinaWorkspaceManager.ProjectContext>
            getOrCreateProject(Path projectRoot, Path triggerFile, String operation) {
                // Mock implementation
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<org.ballerinalang.langserver.workspace.BallerinaWorkspaceManager.ProjectContext>
            createProjectContext(Path tomlPath, String operation) {
                // Mock implementation
                return java.util.Optional.empty();
            }

            @Override
            public void invalidateCacheFor(Path path) {
                // Mock implementation
            }
        };
    }
}
