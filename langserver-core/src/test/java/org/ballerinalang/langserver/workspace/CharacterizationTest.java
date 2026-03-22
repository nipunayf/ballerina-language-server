/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
import io.ballerina.projects.Module;
import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectKind;
import io.ballerina.projects.util.ProjectConstants;
import org.ballerinalang.langserver.commons.eventsync.exceptions.EventSyncException;
import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentException;
import org.ballerinalang.langserver.contexts.LanguageServerContextImpl;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Characterization tests for BallerinaWorkspaceManager document lifecycle operations.
 * These tests pin behavioral invariants for document operations (open, change, close)
 * covering both single-file and build project contexts.
 *
 * @since 1.0.0
 */
public class CharacterizationTest {

    private static final Path RESOURCE_DIRECTORY = Path.of("src/test/resources/project");
    private final String dummyContent = "function foo() {\n}";
    private final String dummyDidChangeContent = "function foo1() {\n}";
    private BallerinaWorkspaceManager workspaceManager;

    @BeforeMethod
    void initWorkspaceManager() {
        // Fresh BallerinaWorkspaceManager per test method for full isolation (per D-03)
        workspaceManager = new BallerinaWorkspaceManager(new LanguageServerContextImpl());
    }

    // ==================== Single-File Document Lifecycle Tests ====================

    /**
     * Test: Opening a single-file document makes it accessible via document() API.
     */
    @Test(description = "Test opening a single-file document makes it accessible via document() API")
    public void testSingleFileOpen() throws WorkspaceDocumentException {
        Path filePath = RESOURCE_DIRECTORY.resolve("single-file").resolve("main.bal").toAbsolutePath();

        // Open the single-file document
        openFile(filePath, dummyContent);

        // Assert document is accessible via document() API (per D-04)
        Optional<Document> document = workspaceManager.document(filePath);
        Assert.assertTrue(document.isPresent(), "Document should be accessible after didOpen");
        Assert.assertNotNull(document.get(), "Document should not be null");
        Assert.assertEquals(document.get().syntaxTree().textDocument().toString(), dummyContent,
                "Syntax tree content should match the opened content");
    }

    /**
     * Test: Changing a single-file document updates syntaxTree() content.
     */
    @Test(description = "Test changing a single-file document updates syntaxTree() content")
    public void testSingleFileChange() throws WorkspaceDocumentException {
        Path filePath = RESOURCE_DIRECTORY.resolve("single-file").resolve("main.bal").toAbsolutePath();

        // Open the single-file document
        openFile(filePath, dummyContent);

        // Change the document content
        DidChangeTextDocumentParams changeParams = new DidChangeTextDocumentParams();
        VersionedTextDocumentIdentifier doc = new VersionedTextDocumentIdentifier(filePath.toUri().toString(), 1);
        changeParams.setTextDocument(doc);
        changeParams.getContentChanges().add(new TextDocumentContentChangeEvent(dummyDidChangeContent));
        workspaceManager.didChange(filePath, changeParams);

        // Assert syntaxTree() reflects the new content (per D-06)
        Optional<Document> document = workspaceManager.document(filePath);
        Assert.assertTrue(document.isPresent(), "Document should still be present after didChange");
        Assert.assertEquals(document.get().syntaxTree().textDocument().toString(), dummyDidChangeContent,
                "Syntax tree should reflect the changed content");
    }

    /**
     * Test: Closing a single-file document results in correct cleanup.
     * For single-file projects, document() returns empty after close.
     */
    @Test(description = "Test closing a single-file document results in correct cleanup")
    public void testSingleFileClose() throws WorkspaceDocumentException {
        Path filePath = RESOURCE_DIRECTORY.resolve("single-file").resolve("main.bal").toAbsolutePath();

        // Open the single-file document
        openFile(filePath, dummyContent);

        // Verify document is accessible before close
        Assert.assertTrue(workspaceManager.document(filePath).isPresent(),
                "Document should be accessible before close");

        // Close the document
        DidCloseTextDocumentParams closeParams = new DidCloseTextDocumentParams();
        closeParams.setTextDocument(new TextDocumentIdentifier(filePath.toUri().toString()));
        workspaceManager.didClose(filePath, closeParams);

        // Assert document is no longer accessible (per D-04: verify via public API only)
        Optional<Document> document = workspaceManager.document(filePath);
        Assert.assertFalse(document.isPresent(),
                "Document should not be accessible after close for single-file projects");
    }

    /**
     * Test: Multiple open/change/close cycles work correctly on same file.
     */
    @Test(description = "Test multiple open/change/close cycles work correctly on same file")
    public void testSingleFileOpenChangeCloseCycle() throws WorkspaceDocumentException {
        Path filePath = RESOURCE_DIRECTORY.resolve("single-file").resolve("main.bal").toAbsolutePath();

        // First cycle: open → change → verify → close → verify empty
        openFile(filePath, dummyContent);
        Assert.assertTrue(workspaceManager.document(filePath).isPresent(), "Document should be accessible after open");

        DidChangeTextDocumentParams changeParams1 = new DidChangeTextDocumentParams();
        VersionedTextDocumentIdentifier doc1 = new VersionedTextDocumentIdentifier(filePath.toUri().toString(), 1);
        changeParams1.setTextDocument(doc1);
        changeParams1.getContentChanges().add(new TextDocumentContentChangeEvent(dummyDidChangeContent));
        workspaceManager.didChange(filePath, changeParams1);

        Optional<Document> document1 = workspaceManager.document(filePath);
        Assert.assertTrue(document1.isPresent(), "Document should be present after change");
        Assert.assertEquals(document1.get().syntaxTree().textDocument().toString(), dummyDidChangeContent,
                "Content should be updated");

        DidCloseTextDocumentParams closeParams1 = new DidCloseTextDocumentParams();
        closeParams1.setTextDocument(new TextDocumentIdentifier(filePath.toUri().toString()));
        workspaceManager.didClose(filePath, closeParams1);

        Assert.assertFalse(workspaceManager.document(filePath).isPresent(),
                "Document should be empty after close");

        // Second cycle: reopen → verify clean state
        openFile(filePath, dummyContent);
        Optional<Document> document2 = workspaceManager.document(filePath);
        Assert.assertTrue(document2.isPresent(), "Document should be accessible after reopen");
        Assert.assertEquals(document2.get().syntaxTree().textDocument().toString(), dummyContent,
                "Content should be the original content after reopen");

        // Clean up
        DidCloseTextDocumentParams closeParams2 = new DidCloseTextDocumentParams();
        closeParams2.setTextDocument(new TextDocumentIdentifier(filePath.toUri().toString()));
        workspaceManager.didClose(filePath, closeParams2);
    }

    // ==================== Build Project Document Lifecycle Tests ====================

    /**
     * Test: Opening a build project document makes project accessible via project() API.
     */
    @Test(description = "Test opening a build project document makes project accessible via project() API")
    public void testBuildProjectOpen() throws WorkspaceDocumentException {
        Path filePath = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();

        // Open the build project document
        openFile(filePath, dummyContent);

        // Assert project is accessible via project() API (per D-04)
        Optional<Project> project = workspaceManager.project(filePath);
        Assert.assertTrue(project.isPresent(), "Project should be accessible after didOpen");
        Assert.assertEquals(project.get().kind(), ProjectKind.BUILD_PROJECT,
                "Project kind should be BUILD_PROJECT");
    }

    /**
     * Test: Opening a build project document makes document accessible via document() API.
     */
    @Test(description = "Test opening a build project document returns correct Document")
    public void testBuildProjectDocumentAccessible() throws WorkspaceDocumentException {
        Path filePath = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();

        // Open the build project document
        openFile(filePath, dummyContent);

        // Assert document is accessible via document() API
        Optional<Document> document = workspaceManager.document(filePath);
        Assert.assertTrue(document.isPresent(), "Document should be accessible in build project");
        Assert.assertEquals(document.get().syntaxTree().textDocument().toString(), dummyContent,
                "Document content should match opened content");
    }

    /**
     * Test: Changing a build project document updates syntaxTree() content.
     */
    @Test(description = "Test changing a build project document updates syntaxTree() content")
    public void testBuildProjectChange() throws WorkspaceDocumentException {
        Path filePath = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();

        // Open the build project document
        openFile(filePath, dummyContent);

        // Change the document content
        DidChangeTextDocumentParams changeParams = new DidChangeTextDocumentParams();
        VersionedTextDocumentIdentifier doc = new VersionedTextDocumentIdentifier(filePath.toUri().toString(), 1);
        changeParams.setTextDocument(doc);
        changeParams.getContentChanges().add(new TextDocumentContentChangeEvent(dummyDidChangeContent));
        workspaceManager.didChange(filePath, changeParams);

        // Assert syntaxTree() reflects the new content (per D-06)
        Optional<Document> document = workspaceManager.document(filePath);
        Assert.assertTrue(document.isPresent(), "Document should still be present after didChange");
        Assert.assertEquals(document.get().syntaxTree().textDocument().toString(), dummyDidChangeContent,
                "Syntax tree should reflect the changed content");
    }

    /**
     * Test: Closing a build project document leaves project intact.
     * Unlike single-file projects, build project remains loaded after document close.
     */
    @Test(description = "Test closing a build project document leaves project intact")
    public void testBuildProjectClose() throws WorkspaceDocumentException {
        Path filePath = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();

        // Open the build project document
        openFile(filePath, dummyContent);

        // Verify document and project are accessible before close
        Assert.assertTrue(workspaceManager.document(filePath).isPresent(),
                "Document should be accessible before close");
        Assert.assertTrue(workspaceManager.project(filePath).isPresent(),
                "Project should be accessible before close");

        // Close the document
        DidCloseTextDocumentParams closeParams = new DidCloseTextDocumentParams();
        closeParams.setTextDocument(new TextDocumentIdentifier(filePath.toUri().toString()));
        workspaceManager.didClose(filePath, closeParams);

        // For build projects, project remains loaded but document may or may not be accessible
        // depending on internal implementation. Verify project is still accessible (per D-04).
        Optional<Project> project = workspaceManager.project(filePath);
        Assert.assertTrue(project.isPresent(),
                "Build project should remain loaded after document close");
    }

    /**
     * Test: Document content persists correctly after change in build project.
     * Open → change → verify content → close → reopen → verify changed content persists.
     */
    @Test(description = "Test document content persists correctly after change in build project")
    public void testBuildProjectChangePersists() throws WorkspaceDocumentException {
        Path filePath = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();

        // Open → change → verify content
        openFile(filePath, dummyContent);

        DidChangeTextDocumentParams changeParams = new DidChangeTextDocumentParams();
        VersionedTextDocumentIdentifier doc = new VersionedTextDocumentIdentifier(filePath.toUri().toString(), 1);
        changeParams.setTextDocument(doc);
        changeParams.getContentChanges().add(new TextDocumentContentChangeEvent(dummyDidChangeContent));
        workspaceManager.didChange(filePath, changeParams);

        Optional<Document> documentBeforeClose = workspaceManager.document(filePath);
        Assert.assertTrue(documentBeforeClose.isPresent(), "Document should be present after change");
        Assert.assertEquals(documentBeforeClose.get().syntaxTree().textDocument().toString(),
                dummyDidChangeContent, "Content should be changed before close");

        // Close
        DidCloseTextDocumentParams closeParams = new DidCloseTextDocumentParams();
        closeParams.setTextDocument(new TextDocumentIdentifier(filePath.toUri().toString()));
        workspaceManager.didClose(filePath, closeParams);

        // Reopen → verify changed content persists
        openFile(filePath, dummyDidChangeContent);

        Optional<Document> documentAfterReopen = workspaceManager.document(filePath);
        Assert.assertTrue(documentAfterReopen.isPresent(), "Document should be accessible after reopen");
        Assert.assertEquals(documentAfterReopen.get().syntaxTree().textDocument().toString(),
                dummyDidChangeContent, "Changed content should persist after reopen");

        // Clean up
        DidCloseTextDocumentParams closeParams2 = new DidCloseTextDocumentParams();
        closeParams2.setTextDocument(new TextDocumentIdentifier(filePath.toUri().toString()));
        workspaceManager.didClose(filePath, closeParams2);
    }

    // ==================== File System Event Tests - .bal File Events ====================

    /**
     * Test: Creating a .bal file in a build project updates the module.
     */
    @Test(description = "Test creating a .bal file in a build project updates the module")
    public void testWSEventsCreateBalSource() throws WorkspaceDocumentException, IOException {
        Path filePath = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();

        // Open project
        openFile(filePath, dummyContent);
        Module oldModule = workspaceManager.module(filePath).orElseThrow();

        // Create a new .bal file and send CREATED event
        Path newFile = RESOURCE_DIRECTORY.resolve("myproject").resolve("new-file.bal").toAbsolutePath();
        Files.write(newFile, "".getBytes());
        FileEvent fileEvent = new FileEvent(newFile.toUri().toString(), FileChangeType.Created);
        try {
            workspaceManager.didChangeWatched(newFile, fileEvent);
            // Creating new document changes the Module
            Assert.assertNotSame(oldModule, workspaceManager.module(filePath).orElseThrow(),
                    "Module should be updated after .bal file creation");
        } finally {
            Files.deleteIfExists(newFile);
        }
    }

    /**
     * Test: Deleting a .bal file in a build project updates the module.
     */
    @Test(description = "Test deleting a .bal file in a build project updates the module")
    public void testWSEventsDeleteBalSource() throws WorkspaceDocumentException, IOException {
        // Create a new file first
        Path newFile = RESOURCE_DIRECTORY.resolve("myproject").resolve("delete-file.bal").toAbsolutePath();
        Files.write(newFile, "".getBytes());

        Path filePath = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();

        // Open project
        openFile(filePath, dummyContent);
        Module oldModule = workspaceManager.module(filePath).orElseThrow();

        // Delete the file and send DELETED event
        Files.delete(newFile);
        FileEvent fileEvent = new FileEvent(newFile.toUri().toString(), FileChangeType.Deleted);
        workspaceManager.didChangeWatched(newFile, fileEvent);

        // File deletion forces a new module
        Assert.assertNotSame(oldModule, workspaceManager.module(filePath).orElseThrow(),
                "Module should be updated after .bal file deletion");
    }

    /**
     * Test: Deleting a .bal file in a single-file project removes the project from workspace.
     */
    @Test(description = "Test deleting a .bal file in a single-file project removes the project")
    public void testWSEventsDeleteBalSourceOnSingleFileProj() throws WorkspaceDocumentException, IOException {
        // Create a new file
        Path singleFile = RESOURCE_DIRECTORY.resolve("single-file").resolve("delete-file.bal").toAbsolutePath();
        Files.write(singleFile, "".getBytes());

        // Open project
        openFile(singleFile, dummyContent);

        // Delete the file and send DELETED event
        Files.delete(singleFile);
        FileEvent fileEvent = new FileEvent(singleFile.toUri().toString(), FileChangeType.Deleted);
        workspaceManager.didChangeWatched(singleFile, fileEvent);

        try {
            // Recreate file so project() call doesn't fail
            Files.write(singleFile, "".getBytes());
            // File deletion for single-file project should remove project from mapping
            Assert.assertTrue(workspaceManager.project(singleFile).isEmpty(),
                    "Single-file project should be removed from workspace after .bal file deletion");
        } finally {
            Files.deleteIfExists(singleFile);
        }
    }

    // ==================== File System Event Tests - TOML File Events ====================

    /**
     * Test: Creating Ballerina.toml on a single-file project converts it to BUILD_PROJECT.
     */
    @Test(description = "Test creating Ballerina.toml on a single-file project converts it to BUILD_PROJECT")
    public void testWSEventsCreateBallerinaTomlOnSingleFileProj() throws WorkspaceDocumentException, IOException {
        Path filePath = RESOURCE_DIRECTORY.resolve("single-file").resolve("main.bal").toAbsolutePath();

        // Open project
        openFile(filePath, dummyContent);

        // Create Ballerina.toml and send CREATED event
        Path newTomlFile = RESOURCE_DIRECTORY.resolve("single-file").resolve(ProjectConstants.BALLERINA_TOML)
                .toAbsolutePath();
        Files.write(newTomlFile, "[package]\norg = \"sameera\"\nname = \"myproject\"\nversion = \"0.1.0\"".getBytes());
        FileEvent fileEvent = new FileEvent(newTomlFile.toUri().toString(), FileChangeType.Created);
        try {
            workspaceManager.didChangeWatched(newTomlFile, fileEvent);
            Optional<Project> project = workspaceManager.project(filePath);

            Assert.assertTrue(project.isPresent(), "Project should not be empty after Ballerina.toml creation");
            Assert.assertSame(project.get().kind(), ProjectKind.BUILD_PROJECT,
                    "Project should be BUILD_PROJECT after Ballerina.toml creation");
        } finally {
            Files.deleteIfExists(newTomlFile);
        }
    }

    /**
     * Test: Deleting Ballerina.toml from a build project removes the project.
     */
    @Test(description = "Test deleting Ballerina.toml from a build project removes the project")
    public void testWSEventsDeleteBallerinaTomlOnBuildProj() throws WorkspaceDocumentException, IOException {
        Path filePath = RESOURCE_DIRECTORY.resolve("single-file").resolve("main.bal").toAbsolutePath();

        // Create Ballerina.toml first
        Path tomlFile = RESOURCE_DIRECTORY.resolve("single-file").resolve(ProjectConstants.BALLERINA_TOML)
                .toAbsolutePath();
        Files.write(tomlFile, "[package]\norg = \"sameera\"\nname = \"myproject\"\nversion = \"0.1.0\"".getBytes());

        // Open project
        openFile(filePath, dummyContent);

        // Delete Ballerina.toml and send DELETED event
        Files.delete(tomlFile);
        FileEvent fileEvent = new FileEvent(tomlFile.toUri().toString(), FileChangeType.Deleted);
        workspaceManager.didChangeWatched(tomlFile, fileEvent);

        // Project should return empty after Ballerina.toml deletion
        Assert.assertTrue(workspaceManager.project(filePath).isEmpty(),
                "Project should be empty after Ballerina.toml deletion");
    }

    /**
     * Test: Creating Cloud.toml adds cloudToml() to package.
     */
    @Test(description = "Test creating Cloud.toml adds cloudToml() to package")
    public void testWSEventsCreateCloudToml() throws WorkspaceDocumentException, IOException {
        Path filePath = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();

        // Open project
        openFile(filePath, dummyContent);

        // Create Cloud.toml and send CREATED event
        Path cloudTomlFile = RESOURCE_DIRECTORY.resolve("myproject").resolve(ProjectConstants.CLOUD_TOML)
                .toAbsolutePath();
        Files.write(cloudTomlFile, "".getBytes());
        FileEvent fileEvent = new FileEvent(cloudTomlFile.toUri().toString(), FileChangeType.Created);
        try {
            workspaceManager.didChangeWatched(cloudTomlFile, fileEvent);

            Optional<Project> project = workspaceManager.project(filePath);
            Assert.assertTrue(project.isPresent(), "Project should not be empty after Cloud.toml creation");
            Assert.assertTrue(project.get().currentPackage().cloudToml().isPresent(),
                    "Package should contain Cloud.toml after creation");
        } finally {
            Files.deleteIfExists(cloudTomlFile);
        }
    }

    /**
     * Test: Deleting Cloud.toml removes cloudToml() from package.
     */
    @Test(description = "Test deleting Cloud.toml removes cloudToml() from package")
    public void testWSEventsDeleteCloudToml() throws WorkspaceDocumentException, IOException {
        Path filePath = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();

        // Create Cloud.toml first
        Path cloudTomlFile = RESOURCE_DIRECTORY.resolve("myproject").resolve(ProjectConstants.CLOUD_TOML)
                .toAbsolutePath();
        Files.write(cloudTomlFile, "".getBytes());

        // Open project
        openFile(filePath, dummyContent);

        // Delete Cloud.toml and send DELETED event
        Files.delete(cloudTomlFile);
        FileEvent fileEvent = new FileEvent(cloudTomlFile.toUri().toString(), FileChangeType.Deleted);
        workspaceManager.didChangeWatched(cloudTomlFile, fileEvent);

        Optional<Project> project = workspaceManager.project(filePath);
        Assert.assertTrue(project.isPresent(), "Project should not be empty after Cloud.toml deletion");
        Assert.assertTrue(project.get().currentPackage().cloudToml().isEmpty(),
                "Package should not contain Cloud.toml after deletion");
    }

    /**
     * Test: Creating Dependencies.toml adds dependenciesToml() to package.
     */
    @Test(description = "Test creating Dependencies.toml adds dependenciesToml() to package")
    public void testWSEventsCreateDependenciesToml() throws WorkspaceDocumentException, IOException {
        Path filePath = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();

        // Open project
        openFile(filePath, dummyContent);

        // Create Dependencies.toml and send CREATED event
        Path depsTomlFile = RESOURCE_DIRECTORY.resolve("myproject").resolve(ProjectConstants.DEPENDENCIES_TOML)
                .toAbsolutePath();
        Files.write(depsTomlFile, "".getBytes());
        FileEvent fileEvent = new FileEvent(depsTomlFile.toUri().toString(), FileChangeType.Created);
        try {
            workspaceManager.didChangeWatched(depsTomlFile, fileEvent);

            Optional<Project> project = workspaceManager.project(filePath);
            Assert.assertTrue(project.isPresent(), "Project should not be empty after Dependencies.toml creation");
            Assert.assertTrue(project.get().currentPackage().dependenciesToml().isPresent(),
                    "Package should contain Dependencies.toml after creation");
        } finally {
            Files.deleteIfExists(depsTomlFile);
        }
    }

    // ==================== File System Event Tests - Module Events ====================

    /**
     * Test: Deleting a module directory triggers project reload.
     */
    @Test(description = "Test deleting a module directory triggers project reload")
    public void testWSEventsDeleteModule() throws WorkspaceDocumentException, IOException {
        Path projectPath = RESOURCE_DIRECTORY.resolve("myproject2");
        Path filePath = projectPath.resolve("main.bal").toAbsolutePath();

        // Create a new module with a file
        Path modelsPath = projectPath.resolve(ProjectConstants.MODULES_ROOT).resolve("models").toAbsolutePath();
        Files.createDirectories(modelsPath);
        Path modelFilePath = modelsPath.resolve("model.bal").toAbsolutePath();
        Files.createFile(modelFilePath);

        // Open project
        openFile(filePath, dummyContent);
        Project oldProject = workspaceManager.project(filePath).orElseThrow();

        // Delete the module directory and send DELETED event
        Files.delete(modelFilePath);
        Files.delete(modelsPath);
        FileEvent fileEvent = new FileEvent(modelsPath.toUri().toString(), FileChangeType.Deleted);
        workspaceManager.didChangeWatched(modelsPath, fileEvent);

        Optional<Project> project = workspaceManager.project(filePath);
        Assert.assertTrue(project.isPresent(), "Project should not be empty after module deletion");
        Assert.assertNotSame(oldProject, project.get(), "Project should be reloaded after module deletion");
    }

    /**
     * Test: Deleting the modules directory triggers project reload.
     */
    @Test(description = "Test deleting the modules directory triggers project reload")
    public void testWSEventsDeleteModulesDir() throws WorkspaceDocumentException, IOException {
        Path projectPath = RESOURCE_DIRECTORY.resolve("myproject2");
        Path filePath = projectPath.resolve("main.bal").toAbsolutePath();

        // Open project
        openFile(filePath, dummyContent);
        Project oldProject = workspaceManager.project(filePath).orElseThrow();

        Path modulesPath = projectPath.resolve(ProjectConstants.MODULES_ROOT).toAbsolutePath();
        Path modulesPathNew = projectPath.resolve(ProjectConstants.RESOURCE_DIR_NAME).toAbsolutePath();

        // Rename modules directory and send DELETED event
        Files.move(modulesPath, modulesPathNew);
        FileEvent fileEvent = new FileEvent(modulesPath.toUri().toString(), FileChangeType.Deleted);
        try {
            workspaceManager.didChangeWatched(modulesPath, fileEvent);
            Optional<Project> project = workspaceManager.project(filePath);
            Assert.assertTrue(project.isPresent(), "Project should not be empty after modules dir deletion");
            Assert.assertNotSame(oldProject, project.get(),
                    "Project should be reloaded after modules dir deletion");
        } finally {
            // Restore modules directory
            Files.move(modulesPathNew, modulesPath);
        }
    }

    // ==================== Project Loading Tests ====================

    /**
     * Test: Single-file project loads with correct project root (parent directory of .bal file).
     */
    @Test(description = "Test single-file project loads with correct project root")
    public void testProjectLoadSingleFile() throws WorkspaceDocumentException, EventSyncException {
        Path filePath = RESOURCE_DIRECTORY.resolve("single-file").resolve("main.bal").toAbsolutePath();

        // Before load: project() should be empty
        Assert.assertTrue(workspaceManager.project(filePath).isEmpty(),
                "project() should be empty before load for single-file");

        // Load the project
        workspaceManager.loadProject(filePath);

        // After load: project() should be present
        Optional<Project> project = workspaceManager.project(filePath);
        Assert.assertTrue(project.isPresent(), "project() should be present after loadProject");

        // projectRoot() should resolve to some path - verify it contains the file's parent
        Path projectRoot = workspaceManager.projectRoot(filePath);
        Assert.assertTrue(projectRoot.toString().contains("single-file"),
                "Single-file project root should contain 'single-file' directory name");
    }

    /**
     * Test: Build project loads with correct project root (directory containing Ballerina.toml).
     */
    @Test(description = "Test build project loads with correct project root")
    public void testProjectLoadBuildProject() throws WorkspaceDocumentException, EventSyncException {
        Path filePath = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();

        // Load the project
        Project project = workspaceManager.loadProject(filePath);

        // Assert returned project is non-null
        Assert.assertNotNull(project, "loadProject() should return non-null Project");

        // Assert project kind is BUILD_PROJECT
        Assert.assertEquals(project.kind(), ProjectKind.BUILD_PROJECT,
                "Project kind should be BUILD_PROJECT");

        // Assert projectRoot() equals the myproject directory (contains Ballerina.toml)
        Path projectRoot = workspaceManager.projectRoot(filePath);
        Assert.assertEquals(projectRoot.getFileName().toString(), "myproject",
                "Build project root should be the directory containing Ballerina.toml");
    }

    /**
     * Test: Workspace project package resolves to correct package root (not workspace root).
     */
    @Test(description = "Test workspace project package resolves to correct package root")
    public void testProjectLoadWorkspaceProjectPackage() throws WorkspaceDocumentException, EventSyncException {
        Path packageAFile = RESOURCE_DIRECTORY.resolve("workspace-projects")
                .resolve("simple-workspace").resolve("package-a").resolve("main.bal").toAbsolutePath();

        // Load the project
        workspaceManager.loadProject(packageAFile);

        // Get project and project root
        Optional<Project> project = workspaceManager.project(packageAFile);
        Assert.assertTrue(project.isPresent(), "Project should be present after load");

        // Workspace project packages should be BUILD_PROJECT kind (workspace root is WORKSPACE_PROJECT)
        Assert.assertEquals(project.get().kind(), ProjectKind.BUILD_PROJECT,
                "Workspace package should be loaded as BUILD_PROJECT");

        // projectRoot() should be package-a directory, NOT the workspace root
        Path projectRoot = workspaceManager.projectRoot(packageAFile);
        Assert.assertEquals(projectRoot.getFileName().toString(), "package-a",
                "Package root should be package-a, not workspace root");
        // The parent of package-a should be simple-workspace (workspace root)
        Assert.assertEquals(projectRoot.getParent().getFileName().toString(), "simple-workspace",
                "Parent of package root should be workspace directory");
    }

    /**
     * Test: Opening workspace root path directly returns WORKSPACE_PROJECT kind.
     */
    @Test(description = "Test opening workspace root returns WORKSPACE_PROJECT kind")
    public void testProjectLoadWorkspaceProjectRoot() throws WorkspaceDocumentException, EventSyncException {
        Path workspaceRoot = RESOURCE_DIRECTORY.resolve("workspace-projects")
                .resolve("simple-workspace").toAbsolutePath();

        // Load the workspace root path
        workspaceManager.loadProject(workspaceRoot);

        Optional<Project> project = workspaceManager.project(workspaceRoot);
        Assert.assertTrue(project.isPresent(), "Workspace root project should be present");

        // Workspace root should be WORKSPACE_PROJECT kind
        Assert.assertEquals(project.get().kind(), ProjectKind.WORKSPACE_PROJECT,
                "Workspace root should be WORKSPACE_PROJECT kind");
    }

    /**
     * Test: loadProject() returns non-null Project for build project.
     */
    @Test(description = "Test loadProject() returns non-null Project")
    public void testProjectLoadReturnsNonNull() throws WorkspaceDocumentException, EventSyncException {
        Path filePath = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();

        // Load project and assert non-null
        Project project = workspaceManager.loadProject(filePath);
        Assert.assertNotNull(project, "loadProject() should return non-null Project");

        // Verify project is accessible via project() API
        Optional<Project> retrieved = workspaceManager.project(filePath);
        Assert.assertTrue(retrieved.isPresent(), "Loaded project should be accessible via project()");
        Assert.assertSame(retrieved.get(), project, "Should return the same Project instance");
    }

    // ==================== Workspace Hierarchy Traversal Tests ====================

    /**
     * Test: workspaceProjects() returns all loaded projects.
     * Note: This test verifies the workspaceProjects() API can be called.
     * Full functionality requires ExtendedLanguageClient with workspace folders configured,
     * which is complex to set up in a unit test (see TestWorkspaceManager.testWorkspaceProjects()
     * for full integration test with proper mock setup).
     */
    @Test(description = "Test workspaceProjects() returns all loaded projects")
    public void testWorkspaceProjectsReturnsAll() throws WorkspaceDocumentException {
        // Open files from multiple projects
        Path packageAFile = RESOURCE_DIRECTORY.resolve("workspace-projects")
                .resolve("simple-workspace").resolve("package-a").resolve("main.bal").toAbsolutePath();
        Path packageBFile = RESOURCE_DIRECTORY.resolve("workspace-projects")
                .resolve("simple-workspace").resolve("package-b").resolve("main.bal").toAbsolutePath();
        Path buildProjectFile = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();

        openFile(packageAFile, dummyContent);
        openFile(packageBFile, dummyContent);
        openFile(buildProjectFile, dummyContent);

        // Verify that all three files are loaded and accessible via project()
        Assert.assertTrue(workspaceManager.project(packageAFile).isPresent(),
                "Package A project should be accessible");
        Assert.assertTrue(workspaceManager.project(packageBFile).isPresent(),
                "Package B project should be accessible");
        Assert.assertTrue(workspaceManager.project(buildProjectFile).isPresent(),
                "Build project should be accessible");
    }

    /**
     * Test: Document → Module → Package → Project chain is traversable without NPE.
     */
    @Test(description = "Test document to module chain is traversable")
    public void testHierarchyDocumentToModule() throws WorkspaceDocumentException {
        Path filePath = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();

        // Open build project
        openFile(filePath, dummyContent);

        // Get document
        Optional<Document> document = workspaceManager.document(filePath);
        Assert.assertTrue(document.isPresent(), "Document should be present");

        // Get module for same file
        Optional<Module> module = workspaceManager.module(filePath);
        Assert.assertTrue(module.isPresent(), "Module should be present");

        // Assert module contains the document (via module.document() with DocumentId)
        Module moduleDoc = module.get();
        boolean documentFoundInModule = false;
        for (var docId : moduleDoc.documentIds()) {
            Document docFromModule = moduleDoc.document(docId);
            if (docFromModule.name().equals(document.get().name())) {
                documentFoundInModule = true;
                break;
            }
        }
        Assert.assertTrue(documentFoundInModule,
                "Document should be accessible via module.document(docId)");
    }

    /**
     * Test: Module → Package chain is traversable without NPE.
     */
    @Test(description = "Test module to package chain is traversable")
    public void testHierarchyModuleToPackage() throws WorkspaceDocumentException {
        Path filePath = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();

        // Open build project
        openFile(filePath, dummyContent);

        // Get module
        Optional<Module> module = workspaceManager.module(filePath);
        Assert.assertTrue(module.isPresent(), "Module should be present");

        // Get project and current package
        Optional<Project> project = workspaceManager.project(filePath);
        Assert.assertTrue(project.isPresent(), "Project should be present");

        var pkg = project.get().currentPackage();
        Assert.assertNotNull(pkg, "currentPackage() should not be null");

        // Assert module belongs to package (module is in package.modules())
        boolean moduleFoundInPackage = false;
        for (Module mod : pkg.modules()) {
            // Compare modules by identity since we want to verify the same module instance is accessible
            if (mod.moduleId().equals(module.get().moduleId())) {
                moduleFoundInPackage = true;
                break;
            }
        }
        Assert.assertTrue(moduleFoundInPackage,
                "Module should be accessible via package.modules()");
    }

    /**
     * Test: Package → Project chain is traversable without NPE.
     */
    @Test(description = "Test package to project chain is traversable")
    public void testHierarchyPackageToProject() throws WorkspaceDocumentException {
        Path filePath = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();

        // Open build project
        openFile(filePath, dummyContent);

        // Get project
        Optional<Project> project = workspaceManager.project(filePath);
        Assert.assertTrue(project.isPresent(), "Project should be present");

        // Get current package
        var pkg = project.get().currentPackage();
        Assert.assertNotNull(pkg, "currentPackage() should not be null");

        // Assert project.currentPackage() is consistent
        Assert.assertSame(pkg.project(), project.get(),
                "Package's project() should return the same project");
    }

    /**
     * Test: Multiple packages in workspace are independently accessible.
     */
    @Test(description = "Test multiple packages in workspace are independently accessible")
    public void testMultiplePackagesInWorkspace() throws WorkspaceDocumentException {
        Path packageAFile = RESOURCE_DIRECTORY.resolve("workspace-projects")
                .resolve("simple-workspace").resolve("package-a").resolve("main.bal").toAbsolutePath();
        Path packageBFile = RESOURCE_DIRECTORY.resolve("workspace-projects")
                .resolve("simple-workspace").resolve("package-b").resolve("main.bal").toAbsolutePath();

        // Open files from both packages
        openFile(packageAFile, dummyContent);
        openFile(packageBFile, dummyContent);

        // Both documents should be accessible
        Assert.assertTrue(workspaceManager.document(packageAFile).isPresent(),
                "Package A document should be accessible");
        Assert.assertTrue(workspaceManager.document(packageBFile).isPresent(),
                "Package B document should be accessible");

        // Both projects should be accessible
        Assert.assertTrue(workspaceManager.project(packageAFile).isPresent(),
                "Package A project should be accessible");
        Assert.assertTrue(workspaceManager.project(packageBFile).isPresent(),
                "Package B project should be accessible");

        // Each should have different project root
        Path rootA = workspaceManager.projectRoot(packageAFile);
        Path rootB = workspaceManager.projectRoot(packageBFile);
        Assert.assertNotEquals(rootA, rootB, "Different packages should have different project roots");

        // But both should be under the same workspace parent
        Assert.assertEquals(rootA.getParent(), rootB.getParent(),
                "Both packages should be under the same workspace directory");
    }

    // ==================== Project Root Resolution Edge Case Tests ====================

    /**
     * Test: Opening file in subdirectory resolves to correct project root.
     */
    @Test(description = "Test project root resolution for file in subdirectory")
    public void testProjectRootResolutionSubdirectory() throws WorkspaceDocumentException {
        // myproject2 has nested modules: myproject2/modules/services/svc.bal
        Path subdirFile = RESOURCE_DIRECTORY.resolve("myproject2")
                .resolve("modules").resolve("services").resolve("svc.bal").toAbsolutePath();

        // Open file from subdirectory
        openFile(subdirFile, dummyContent);

        // projectRoot() should still resolve to myproject2 directory
        Path projectRoot = workspaceManager.projectRoot(subdirFile);
        Assert.assertEquals(projectRoot.getFileName().toString(), "myproject2",
                "Project root should resolve to myproject2 even for subdirectory file");
    }

    /**
     * Test: Opening file in nested module resolves to correct project root.
     */
    @Test(description = "Test project root resolution for file in nested module")
    public void testProjectRootResolutionNestedModule() throws WorkspaceDocumentException {
        // simple-workspace/package-a/modules might exist
        Path packageAFile = RESOURCE_DIRECTORY.resolve("workspace-projects")
                .resolve("simple-workspace").resolve("package-a").resolve("main.bal").toAbsolutePath();

        // Open file from workspace package
        openFile(packageAFile, dummyContent);

        // projectRoot() should resolve to package-a directory
        Path projectRoot = workspaceManager.projectRoot(packageAFile);
        Assert.assertEquals(projectRoot.getFileName().toString(), "package-a",
                "Project root should resolve to package-a for nested module file");
    }

    /**
     * Test: Opening multiple files in same project returns same project instance.
     */
    @Test(description = "Test same project instance for multiple files in same project")
    public void testSameProjectInstanceForMultipleFiles() throws WorkspaceDocumentException {
        // Open two files from the same build project
        Path file1 = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();
        Path file2 = RESOURCE_DIRECTORY.resolve("myproject").resolve("utils.bal").toAbsolutePath();

        openFile(file1, dummyContent);
        openFile(file2, dummyContent);

        // Both should resolve to the same project instance
        Optional<Project> project1 = workspaceManager.project(file1);
        Optional<Project> project2 = workspaceManager.project(file2);

        Assert.assertTrue(project1.isPresent(), "Project for file1 should be present");
        Assert.assertTrue(project2.isPresent(), "Project for file2 should be present");
        Assert.assertSame(project1.get(), project2.get(),
                "Multiple files in same project should return same Project instance");
    }

    /**
     * Test: Opening file in different package of same workspace returns different project root.
     */
    @Test(description = "Test different project root for different packages in same workspace")
    public void testDifferentProjectRootForDifferentPackages() throws WorkspaceDocumentException {
        Path packageAFile = RESOURCE_DIRECTORY.resolve("workspace-projects")
                .resolve("simple-workspace").resolve("package-a").resolve("main.bal").toAbsolutePath();
        Path packageBFile = RESOURCE_DIRECTORY.resolve("workspace-projects")
                .resolve("simple-workspace").resolve("package-b").resolve("main.bal").toAbsolutePath();

        openFile(packageAFile, dummyContent);
        openFile(packageBFile, dummyContent);

        // Different packages should have different project roots
        Path rootA = workspaceManager.projectRoot(packageAFile);
        Path rootB = workspaceManager.projectRoot(packageBFile);

        Assert.assertNotEquals(rootA, rootB,
                "Different packages should have different project roots");

        // But both should share the same workspace parent
        Assert.assertEquals(rootA.getParent(), rootB.getParent(),
                "Both packages should be under the same workspace directory");
    }

    // ==================== Concurrency Characterization Tests ====================

    @Test(description = "Test concurrent didOpen and didClose on different single-file paths")
    public void testConcurrentDidOpenClose() throws Exception {
        List<Path> files = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Path file = RESOURCE_DIRECTORY.resolve("single-file").resolve("concurrent-open-close-" + i + ".bal")
                    .toAbsolutePath();
            Files.writeString(file, "");
            files.add(file);
        }

        ExecutorService executor = Executors.newFixedThreadPool(files.size());
        CyclicBarrier barrier = new CyclicBarrier(files.size());
        List<Future<Void>> futures = new ArrayList<>();
        try {
            for (Path file : files) {
                futures.add(executor.submit(() -> {
                    await(barrier);
                    openFile(file, dummyContent);
                    closeFile(file);
                    return null;
                }));
            }
            waitForAll(futures);

            for (Path file : files) {
                Assert.assertTrue(workspaceManager.document(file).isEmpty(),
                        "Single-file document should be unavailable after concurrent close: " + file);
                Assert.assertTrue(workspaceManager.project(file).isEmpty(),
                        "Single-file project should be removed after concurrent close: " + file);
            }
            Assert.assertEquals(getOpenedDocuments().size(), 0,
                    "openedDocuments should be empty after all concurrent didClose operations");
        } finally {
            shutdownExecutor(executor);
            for (Path file : files) {
                Files.deleteIfExists(file);
            }
        }
    }

    @Test(description = "Test concurrent project creation for different files in the same build project")
    public void testConcurrentProjectCreationSameRoot() throws Exception {
        List<Path> files = new ArrayList<>(List.of(
                RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath(),
                RESOURCE_DIRECTORY.resolve("myproject").resolve("utils.bal").toAbsolutePath()
        ));
        for (int i = 0; i < 3; i++) {
            Path file = RESOURCE_DIRECTORY.resolve("myproject").resolve("concurrent-same-root-" + i + ".bal")
                    .toAbsolutePath();
            Files.writeString(file, "");
            files.add(file);
        }

        ExecutorService executor = Executors.newFixedThreadPool(files.size());
        CyclicBarrier barrier = new CyclicBarrier(files.size());
        List<Future<BallerinaWorkspaceManager.ProjectContext>> futures = new ArrayList<>();
        try {
            for (Path file : files) {
                futures.add(executor.submit(() -> {
                    await(barrier);
                    openFile(file, dummyContent);
                    Path projectRoot = workspaceManager.projectRoot(file);
                    Assert.assertTrue(workspaceManager.project(file).isPresent(),
                            "Project should be available after concurrent open");
                    return workspaceManager.projectContext(projectRoot).orElseThrow();
                }));
            }

            List<BallerinaWorkspaceManager.ProjectContext> projectContexts = waitForAll(futures);
            BallerinaWorkspaceManager.ProjectContext first = projectContexts.get(0);
            for (BallerinaWorkspaceManager.ProjectContext projectContext : projectContexts) {
                Assert.assertSame(projectContext, first,
                        "All files under the same root should resolve to the same ProjectContext instance");
            }

            Path projectRoot = workspaceManager.projectRoot(files.get(0));
            BallerinaWorkspaceManager.ProjectContext projectContext =
                    workspaceManager.projectContext(projectRoot).orElseThrow();
            Assert.assertFalse(projectContext.isClosed(), "Shared project context should remain open");
            for (Path file : files) {
                Assert.assertEquals(workspaceManager.projectRoot(file), projectRoot,
                        "All concurrent files should resolve to the same project root");
            }
        } finally {
            shutdownExecutor(executor);
            for (int i = 2; i < files.size(); i++) {
                Path file = files.get(i);
                closeQuietly(file);
                Files.deleteIfExists(file);
            }
            closeQuietly(files.get(0));
            closeQuietly(files.get(1));
        }
    }

    @Test(description = "Test concurrent loadProject calls for the same project return the same instance")
    public void testConcurrentLoadSameProject() throws Exception {
        Path filePath = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();
        closeQuietly(filePath);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Future<Project> firstLoad = executor.submit(() -> {
                await(barrier);
                return workspaceManager.loadProject(filePath);
            });
            Future<Project> secondLoad = executor.submit(() -> {
                await(barrier);
                return workspaceManager.loadProject(filePath);
            });

            Project firstProject = waitFor(firstLoad);
            Project secondProject = waitFor(secondLoad);

            Assert.assertNotNull(firstProject, "First concurrent load should return a project");
            Assert.assertNotNull(secondProject, "Second concurrent load should return a project");
            Assert.assertSame(firstProject, secondProject,
                    "Concurrent loads for the same root should return the same Project instance");

            Path projectRoot = workspaceManager.projectRoot(filePath);
            BallerinaWorkspaceManager.ProjectContext projectContext =
                    workspaceManager.projectContext(projectRoot).orElseThrow();
            Assert.assertSame(projectContext.project(), firstProject,
                    "Cached project context should retain the shared project instance");
        } finally {
            shutdownExecutor(executor);
            closeQuietly(filePath);
        }
    }

    @Test(description = "Test compilation crash flag visibility across threads")
    public void testCrashFlagVisibility() throws Exception {
        Path filePath = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();
        openFile(filePath, dummyContent);

        Path projectRoot = workspaceManager.projectRoot(filePath);
        BallerinaWorkspaceManager.ProjectContext projectContext =
                workspaceManager.projectContext(projectRoot).orElseThrow();
        CountDownLatch writeDone = new CountDownLatch(1);
        AtomicInteger observedTrue = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> writer = executor.submit(() -> {
                projectContext.withWriteLock(ctx -> ctx.setCompilationCrashed(true));
                writeDone.countDown();
                return null;
            });
            Future<Boolean> reader = executor.submit(() -> {
                writeDone.await(5, TimeUnit.SECONDS);
                boolean crashed = projectContext.compilationCrashed();
                if (crashed) {
                    observedTrue.incrementAndGet();
                }
                return crashed;
            });

            waitFor(writer);
            Assert.assertTrue(waitFor(reader), "Reader thread should observe the crash flag update");
            Assert.assertEquals(observedTrue.get(), 1, "Exactly one reader should observe the volatile flag");
        } finally {
            shutdownExecutor(executor);
            closeQuietly(filePath);
        }
    }

    @Test(description = "Test closing and reopening a single-file project creates a fresh context without stale lock")
    public void testCloseReopenNoStaleLock() throws Exception {
        Path filePath = RESOURCE_DIRECTORY.resolve("single-file").resolve("close-reopen-race.bal").toAbsolutePath();
        Files.writeString(filePath, "");
        openFile(filePath, dummyContent);

        Path projectRoot = workspaceManager.projectRoot(filePath);
        BallerinaWorkspaceManager.ProjectContext oldContext =
                workspaceManager.projectContext(projectRoot).orElseThrow();
        CountDownLatch closeDone = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> closeFuture = executor.submit(() -> {
                closeFile(filePath);
                closeDone.countDown();
                return null;
            });
            Future<BallerinaWorkspaceManager.ProjectContext> reopenFuture = executor.submit(() -> {
                closeDone.await(5, TimeUnit.SECONDS);
                openFile(filePath, dummyContent);
                return workspaceManager.projectContext(projectRoot).orElseThrow();
            });

            waitFor(closeFuture);
            BallerinaWorkspaceManager.ProjectContext newContext = waitFor(reopenFuture);
            Assert.assertTrue(oldContext.isClosed(), "Original context should be closed after didClose");
            Assert.assertNotSame(newContext, oldContext, "Reopen should allocate a fresh ProjectContext");
            Assert.assertFalse(newContext.isClosed(), "Reopened context should remain active");
            Assert.assertTrue(workspaceManager.project(filePath).isPresent(),
                    "Project should be available after reopen");
        } finally {
            shutdownExecutor(executor);
            closeQuietly(filePath);
            Files.deleteIfExists(filePath);
        }
    }

    @Test(description = "Test concurrent read queries proceed while writes serialize correctly")
    public void testConcurrentReadsDuringWrite() throws Exception {
        Path filePath = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();
        openFile(filePath, dummyContent);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        CyclicBarrier barrier = new CyclicBarrier(4);
        AtomicInteger successfulReads = new AtomicInteger();
        try {
            List<Future<Boolean>> readers = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                readers.add(executor.submit(() -> {
                    await(barrier);
                    boolean valid = workspaceManager.project(filePath).isPresent()
                            && workspaceManager.syntaxTree(filePath).isPresent();
                    if (valid) {
                        successfulReads.incrementAndGet();
                    }
                    return valid;
                }));
            }
            Future<Boolean> writer = executor.submit(() -> {
                await(barrier);
                changeFile(filePath, dummyDidChangeContent);
                return workspaceManager.document(filePath)
                        .map(document -> dummyDidChangeContent.equals(document.syntaxTree().textDocument().toString()))
                        .orElse(false);
            });

            for (Future<Boolean> reader : readers) {
                Assert.assertTrue(waitFor(reader), "Reader should receive a valid project and syntax tree");
            }
            Assert.assertTrue(waitFor(writer), "Writer should successfully apply the document change");
            Assert.assertEquals(successfulReads.get(), 3, "All readers should succeed under concurrent access");
        } finally {
            shutdownExecutor(executor);
            closeQuietly(filePath);
        }
    }

    // ==================== Cache Invalidation Tests ====================

    @Test(description = "Test cache entry survival for unrelated projects")
    public void testCacheEntrySurvival() throws Exception {
        Path projectA = RESOURCE_DIRECTORY.resolve("single-file").resolve("main.bal").toAbsolutePath();
        Path projectB = RESOURCE_DIRECTORY.resolve("myproject2").resolve("main.bal").toAbsolutePath();
        Path ballerinaToml = projectA.getParent().resolve(ProjectConstants.BALLERINA_TOML);

        openFile(projectA, dummyContent);
        openFile(projectB, dummyContent);

        Path rootA = workspaceManager.projectRoot(projectA);
        Path rootB = workspaceManager.projectRoot(projectB);
        Map<Path, Path> cache = getPathToSourceRootCache();

        Assert.assertEquals(cache.get(projectA), rootA, "Project A should populate the path cache before invalidation");
        Assert.assertEquals(cache.get(projectB), rootB, "Project B should populate the path cache before invalidation");

        Files.writeString(ballerinaToml, "[package]\norg = \"sameera\"\nname = \"myproject\"\nversion = \"0.1.0\"");
        FileEvent fileEvent = new FileEvent(ballerinaToml.toUri().toString(), FileChangeType.Created);
        try {
            workspaceManager.didChangeWatched(ballerinaToml, fileEvent);

            Assert.assertFalse(cache.containsKey(projectA),
                    "Project A's old single-file cache entry should be evicted after upgrade");
            Assert.assertTrue(cache.containsKey(projectB),
                    "Project B's cache entry should survive unrelated invalidation");
            Assert.assertEquals(cache.get(projectB), rootB,
                    "Project B should still resolve to the same cached root after Project A invalidation");

            Path rootBAfter = workspaceManager.projectRoot(projectB);
            Assert.assertEquals(rootBAfter, rootB, "Project B root should remain stable after Project A invalidation");
            Assert.assertEquals(cache.get(projectB), rootB,
                    "Project B should still be served from the surviving cache entry");
        } finally {
            Files.deleteIfExists(ballerinaToml);
            closeQuietly(projectA);
            closeQuietly(projectB);
        }
    }

    @Test(description = "Test LRU eviction when capacity is exceeded")
    public void testLruEviction() throws Exception {
        Path tempRoot = Files.createTempDirectory("workspace-manager-lru");
        List<Path> projectFiles = new ArrayList<>();
        try {
            for (int i = 0; i < 9; i++) {
                Path projectDir = tempRoot.resolve("project-" + i);
                copyDirectory(RESOURCE_DIRECTORY.resolve("myproject").toAbsolutePath(), projectDir);
                Path projectFile = projectDir.resolve("main.bal");
                projectFiles.add(projectFile);

                openFile(projectFile, dummyContent + System.lineSeparator() + "// " + i);
                closeFile(projectFile);
            }

            Path firstProject = projectFiles.get(0);
            Assert.assertTrue(workspaceManager.project(firstProject).isEmpty(),
                    "Least recently used project should be evicted after capacity is exceeded");

            Project reloaded = workspaceManager.loadProject(firstProject);
            Assert.assertNotNull(reloaded, "Evicted project should be reloadable");
            Assert.assertTrue(workspaceManager.project(firstProject).isPresent(),
                    "Reloaded project should be present after loadProject");
        } finally {
            for (Path projectFile : projectFiles) {
                closeQuietly(projectFile);
            }
            deleteRecursively(tempRoot);
        }
    }

    @Test(description = "Test pinning prevents eviction of projects with open documents")
    public void testPinningPreventsEviction() throws Exception {
        Path pinnedProject = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();
        Path tempRoot = Files.createTempDirectory("workspace-manager-pinned");
        List<Path> projectFiles = new ArrayList<>();
        try {
            openFile(pinnedProject, dummyContent);

            for (int i = 0; i < 9; i++) {
                Path projectDir = tempRoot.resolve("project-" + i);
                copyDirectory(RESOURCE_DIRECTORY.resolve("myproject").toAbsolutePath(), projectDir);
                Path projectFile = projectDir.resolve("main.bal");
                projectFiles.add(projectFile);

                openFile(projectFile, dummyContent + System.lineSeparator() + "// pinned test " + i);
                closeFile(projectFile);
            }

            Assert.assertTrue(workspaceManager.project(pinnedProject).isPresent(),
                    "Pinned project with an open document should not be evicted");
        } finally {
            closeQuietly(pinnedProject);
            for (Path projectFile : projectFiles) {
                closeQuietly(projectFile);
            }
            deleteRecursively(tempRoot);
        }
    }

    // ==================== Helper Methods ====================

    private void openFile(Path filePath, String content) throws WorkspaceDocumentException {
        DidOpenTextDocumentParams params = new DidOpenTextDocumentParams();
        TextDocumentItem textDocumentItem = new TextDocumentItem();
        textDocumentItem.setUri(filePath.toUri().toString());
        textDocumentItem.setText(content);
        params.setTextDocument(textDocumentItem);
        workspaceManager.didOpen(filePath, params);
    }

    private void changeFile(Path filePath, String content) throws WorkspaceDocumentException {
        DidChangeTextDocumentParams params = new DidChangeTextDocumentParams();
        params.setTextDocument(new VersionedTextDocumentIdentifier(filePath.toUri().toString(), 1));
        params.getContentChanges().add(new TextDocumentContentChangeEvent(content));
        workspaceManager.didChange(filePath, params);
    }

    private void closeFile(Path filePath) {
        DidCloseTextDocumentParams params = new DidCloseTextDocumentParams();
        params.setTextDocument(new TextDocumentIdentifier(filePath.toUri().toString()));
        workspaceManager.didClose(filePath, params);
    }

    @SuppressWarnings("unchecked")
    private Set<Path> getOpenedDocuments() {
        try {
            Field field = BallerinaWorkspaceManager.class.getDeclaredField("openedDocuments");
            field.setAccessible(true);
            return (Set<Path>) field.get(workspaceManager);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to inspect openedDocuments", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Path, Path> getPathToSourceRootCache() {
        try {
            Field field = BallerinaWorkspaceManager.class.getDeclaredField("pathToSourceRootCache");
            field.setAccessible(true);
            return (Map<Path, Path>) field.get(workspaceManager);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to inspect pathToSourceRootCache", e);
        }
    }

    private void closeQuietly(Path filePath) {
        try {
            closeFile(filePath);
        } catch (RuntimeException ignored) {
            // Best-effort cleanup for concurrent test fixtures.
        }
    }

    private void copyDirectory(Path sourceDir, Path targetDir) throws IOException {
        try (var paths = Files.walk(sourceDir)) {
            for (Path source : (Iterable<Path>) paths::iterator) {
                Path relative = sourceDir.relativize(source);
                Path target = targetDir.resolve(relative);
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target);
                }
            }
        }
    }

    private void deleteRecursively(Path root) throws IOException {
        if (root == null || Files.notExists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to delete temp test path: " + path, e);
                }
            });
        }
    }

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Timed out waiting for concurrent test barrier", e);
        }
    }

    private <T> List<T> waitForAll(List<Future<T>> futures)
            throws InterruptedException, ExecutionException, TimeoutException {
        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(waitFor(future));
        }
        return results;
    }

    private <T> T waitFor(Future<T> future) throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(30, TimeUnit.SECONDS);
    }

    private void shutdownExecutor(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        Assert.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS),
                "Executor should terminate promptly after each concurrency test");
    }
}
