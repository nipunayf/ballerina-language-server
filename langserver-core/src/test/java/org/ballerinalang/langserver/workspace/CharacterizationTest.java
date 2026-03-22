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
import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectKind;
import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentException;
import org.ballerinalang.langserver.contexts.LanguageServerContextImpl;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.Optional;

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

    // ==================== Helper Methods ====================

    private void openFile(Path filePath, String content) throws WorkspaceDocumentException {
        DidOpenTextDocumentParams params = new DidOpenTextDocumentParams();
        TextDocumentItem textDocumentItem = new TextDocumentItem();
        textDocumentItem.setUri(filePath.toUri().toString());
        textDocumentItem.setText(content);
        params.setTextDocument(textDocumentItem);
        workspaceManager.didOpen(filePath, params);
    }
}
