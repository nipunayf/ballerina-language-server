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
package org.ballerinalang.langserver.workspace;

import io.ballerina.projects.Document;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.Module;
import io.ballerina.projects.ModuleId;
import io.ballerina.projects.Package;
import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectKind;
import org.ballerinalang.langserver.commons.BallerinaCompilerApi;
import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentException;
import org.ballerinalang.langserver.contexts.LanguageServerContextImpl;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Workspace project tests for workspace hierarchy traversal and correctness.
 * 
 * <p>These tests verify:
 * <ul>
 *   <li>WKSP-01: A workspace project can be traversed as WorkspaceProject → N Projects → 1 Package → M Modules → K Documents</li>
 *   <li>WKSP-02: Opening a file in pkgB resolves to pkgB's package, not sibling pkgA or workspace root</li>
 *   <li>WKSP-03: A file change in pkgA does not cause pkgB to reload (same object reference)</li>
 * </ul>
 * 
 * @since 2201.12.0
 */
public class WorkspaceProjectTest {

    private static final Path RESOURCE_DIRECTORY = Path.of("src/test/resources/project");
    private final String dummyContent = "function foo() {\n}";
    private BallerinaWorkspaceManager workspaceManager;

    @BeforeMethod
    void initWorkspaceManager() {
        // Fresh BallerinaWorkspaceManager per test method for full isolation
        workspaceManager = new BallerinaWorkspaceManager(new LanguageServerContextImpl());
    }

    // ==================== WKSP-01: Hierarchy Traversal Tests ====================

    /**
     * Test: Workspace hierarchy can be traversed as 
     * WorkspaceProject → N Projects → 1 Package → M Modules → K Documents.
     */
    @Test(description = "Test workspace hierarchy traversal: WorkspaceProject → Projects → Package → Module → Document")
    public void testWorkspaceHierarchyTraversal() throws Exception {
        // Arrange: Use existing workspace fixture with 2 packages
        Path workspaceRoot = RESOURCE_DIRECTORY.resolve("workspace-projects")
                .resolve("simple-workspace").toAbsolutePath();
        
        Path fileInPkgA = workspaceRoot.resolve("package-a").resolve("main.bal").toAbsolutePath();
        Path fileInPkgB = workspaceRoot.resolve("package-b").resolve("main.bal").toAbsolutePath();
        
        // Load the workspace root to get WORKSPACE_PROJECT
        workspaceManager.loadProject(workspaceRoot);
        
        // Open files in both packages
        openFile(fileInPkgA, "import pkg_b; public function testA() {}");
        openFile(fileInPkgB, "import pkg_a; public function testB() {}");
        
        // Get workspace root project (WORKSPACE_PROJECT kind)
        Optional<Project> workspaceProjectOpt = workspaceManager.project(workspaceRoot);
        Assert.assertTrue(workspaceProjectOpt.isPresent(), "Workspace root project should be present");
        
        Project workspaceProject = workspaceProjectOpt.get();
        
        // Assert 1: Workspace root is WORKSPACE_PROJECT kind
        Assert.assertEquals(workspaceProject.kind(), ProjectKind.WORKSPACE_PROJECT,
                "Workspace root should be WORKSPACE_PROJECT kind");
        
        // Assert 2: Get all workspace packages via compiler API
        BallerinaCompilerApi compilerApi = BallerinaCompilerApi.getInstance();
        List<Project> workspacePackages = compilerApi.getWorkspaceProjectsInOrder(workspaceProject);
        Assert.assertEquals(workspacePackages.size(), 2, "Should have 2 workspace packages");
        
        // Assert 3: Navigate each package hierarchy
        for (Project wsProject : workspacePackages) {
            // Each workspace project is BUILD_PROJECT kind
            Assert.assertEquals(wsProject.kind(), ProjectKind.BUILD_PROJECT,
                    "Workspace member should be BUILD_PROJECT kind");
            
            // Each project has exactly 1 package
            Package pkg = wsProject.currentPackage();
            Assert.assertNotNull(pkg, "Package should not be null");
            
            // Each package has modules (accessed via Iterable<Module>)
            boolean hasModules = false;
            for (Module module : pkg.modules()) {
                hasModules = true;
                Assert.assertNotNull(module.moduleId(), "Module ID should not be null");
                
                // Each module has documents (accessed via documentIds())
                Collection<DocumentId> docIds = module.documentIds();
                Assert.assertFalse(docIds.isEmpty(), "Module should have document IDs");
                
                // Verify each document is accessible
                for (DocumentId docId : docIds) {
                    Document doc = module.document(docId);
                    Assert.assertNotNull(doc.name(), "Document name should not be null");
                    Assert.assertNotNull(doc.textDocument(), "Document text should not be null");
                }
            }
            Assert.assertTrue(hasModules, "Package should have at least one module");
        }
    }

    /**
     * Test: Workspace project root resolves correctly.
     */
    @Test(description = "Test workspace project root resolution")
    public void testWorkspaceRootResolution() throws Exception {
        // Arrange
        Path workspaceRoot = RESOURCE_DIRECTORY.resolve("workspace-projects")
                .resolve("simple-workspace").toAbsolutePath();
        Path packageAFile = workspaceRoot.resolve("package-a").resolve("main.bal").toAbsolutePath();
        
        // Act: Open file in workspace package
        openFile(packageAFile, dummyContent);
        
        // Assert: Project root is package-a, not workspace root
        Path projectRoot = workspaceManager.projectRoot(packageAFile);
        Assert.assertEquals(projectRoot.getFileName().toString(), "package-a",
                "Project root should be package-a, not workspace root");
        
        // Assert: Workspace root is the parent of package-a
        Assert.assertEquals(projectRoot.getParent(), workspaceRoot,
                "Workspace root should be parent of package-a");
    }

    /**
     * Test: Multiple packages in workspace are independently accessible.
     */
    @Test(description = "Test multiple packages in workspace are independently accessible")
    public void testMultiplePackagesAccessible() throws Exception {
        // Arrange
        Path workspaceRoot = RESOURCE_DIRECTORY.resolve("workspace-projects")
                .resolve("simple-workspace").toAbsolutePath();
        Path fileInPkgA = workspaceRoot.resolve("package-a").resolve("main.bal").toAbsolutePath();
        Path fileInPkgB = workspaceRoot.resolve("package-b").resolve("main.bal").toAbsolutePath();
        
        // Act
        openFile(fileInPkgA, "public function pkgAFunction() {}");
        openFile(fileInPkgB, "public function pkgBFunction() {}");
        
        // Assert: Both packages accessible
        Optional<Project> projectA = workspaceManager.project(fileInPkgA);
        Optional<Project> projectB = workspaceManager.project(fileInPkgB);
        
        Assert.assertTrue(projectA.isPresent(), "Package A project should be accessible");
        Assert.assertTrue(projectB.isPresent(), "Package B project should be accessible");
        
        // Assert: Different project roots
        Assert.assertNotEquals(workspaceManager.projectRoot(fileInPkgA),
                workspaceManager.projectRoot(fileInPkgB),
                "Different packages should have different project roots");
    }

    // ==================== WKSP-02: Path Resolution Tests ====================

    /**
     * Test: Opening a file in pkgB resolves to pkgB's package, not sibling pkgA or workspace root.
     * 
     * <p>WKSP-02: "Opening a file inside a workspace project resolves to the Package that contains 
     * that file's source root — it does not resolve to a sibling package or the workspace root itself"
     */
    @Test(description = "Test file in pkgB resolves to pkgB's package, not sibling pkgA or workspace root")
    public void testPathResolution() throws Exception {
        // Arrange: Create workspace with 2 packages
        Path workspaceRoot = RESOURCE_DIRECTORY.resolve("workspace-projects")
                .resolve("simple-workspace").toAbsolutePath();
        
        Path fileInPkgA = workspaceRoot.resolve("package-a").resolve("main.bal").toAbsolutePath();
        Path fileInPkgB = workspaceRoot.resolve("package-b").resolve("main.bal").toAbsolutePath();
        
        // Open files in both packages
        openFile(fileInPkgA, "import pkg_b; public function testA() {}");
        openFile(fileInPkgB, "import pkg_a; public function testB() {}");
        
        // Act: Get module for file in pkgB
        Optional<Module> moduleOptB = workspaceManager.module(fileInPkgB);
        
        // Assert: Module should be present and resolve to pkgB
        Assert.assertTrue(moduleOptB.isPresent(), "Module should be present for file in pkgB");
        
        Module moduleB = moduleOptB.get();
        ModuleId moduleIdB = moduleB.moduleId();
        
        // The module should be from pkgB
        String pkgBModuleStr = moduleIdB.toString();
        Assert.assertTrue(pkgBModuleStr.contains("package_b") || 
                          pkgBModuleStr.contains("pkg_b"),
                "Module should resolve to pkgB for file in pkgB, got: " + pkgBModuleStr);
        
        // Also verify pkgA's module is different
        Optional<Module> moduleOptA = workspaceManager.module(fileInPkgA);
        Assert.assertTrue(moduleOptA.isPresent(), "Module should be present for file in pkgA");
        
        ModuleId moduleIdA = moduleOptA.get().moduleId();
        Assert.assertNotEquals(moduleIdA, moduleIdB,
                "pkgA and pkgB should have different module IDs");
    }

    // ==================== WKSP-03: Reload Isolation Tests ====================

    /**
     * Test: A file change in pkgA does not cause pkgB to reload (same object reference).
     * 
     * <p>WKSP-03: "A file system event (create, delete, or change a .bal file) inside package A 
     * of a workspace project does not cause package B to reload — diagnostic events and compilation 
     * triggers are scoped to the affected package only"
     */
    @Test(description = "Test changing file in pkgA does not cause pkgB to reload - same object reference")
    public void testReloadIsolation() throws Exception {
        // Arrange: Create workspace with 2 packages
        Path workspaceRoot = RESOURCE_DIRECTORY.resolve("workspace-projects")
                .resolve("simple-workspace").toAbsolutePath();
        
        Path fileInPkgA = workspaceRoot.resolve("package-a").resolve("main.bal").toAbsolutePath();
        Path fileInPkgB = workspaceRoot.resolve("package-b").resolve("main.bal").toAbsolutePath();
        
        // Open files in both packages
        openFile(fileInPkgA, "import pkg_b; public function testA() {}");
        openFile(fileInPkgB, "import pkg_a; public function testB() {}");
        
        // Get initial ProjectContext for pkgB
        Path projectRootB = workspaceManager.projectRoot(fileInPkgB);
        Optional<BallerinaWorkspaceManager.ProjectContext> ctxBeforeOpt = 
                workspaceManager.projectContext(projectRootB);
        Assert.assertTrue(ctxBeforeOpt.isPresent(), "ProjectContext should be present for pkgB before change");
        BallerinaWorkspaceManager.ProjectContext ctxBefore = ctxBeforeOpt.get();
        Project projectBefore = ctxBefore.project();
        
        // Act: Change a .bal file in pkgA (NOT pkgB)
        changeFile(fileInPkgA);
        
        // Assert: pkgB's ProjectContext is the SAME object reference
        Optional<BallerinaWorkspaceManager.ProjectContext> ctxAfterOpt = 
                workspaceManager.projectContext(projectRootB);
        Assert.assertTrue(ctxAfterOpt.isPresent(), "ProjectContext should be present for pkgB after change");
        BallerinaWorkspaceManager.ProjectContext ctxAfter = ctxAfterOpt.get();
        
        // D-30: Same ProjectContext object = no reload occurred
        Assert.assertSame(ctxBefore, ctxAfter, 
                "Changing file in pkgA should NOT cause pkgB to reload - same ProjectContext object");
        
        // Also verify the internal project is the same object too
        Assert.assertSame(projectBefore, ctxAfter.project(),
                "pkgB's Project should be the same object reference");
    }

    // ==================== Helper Methods ====================

    /**
     * Opens a file using the workspace manager.
     */
    private void openFile(Path filePath, String content) throws WorkspaceDocumentException {
        workspaceManager.didOpen(filePath, 
                new org.eclipse.lsp4j.DidOpenTextDocumentParams(
                        new org.eclipse.lsp4j.TextDocumentItem(
                                filePath.toUri().toString(),
                                "ballerina",
                                0,
                                content)));
    }

    /**
     * Simulates a file change event.
     */
    private void changeFile(Path filePath) throws WorkspaceDocumentException {
        FileEvent fileEvent = new FileEvent(filePath.toUri().toString(), FileChangeType.Changed);
        workspaceManager.didChangeWatched(filePath, fileEvent);
    }
}
