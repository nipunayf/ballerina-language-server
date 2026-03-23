/*
 *  Copyright (c) 2026, WSO2 LLC. (http://www.wso2.com)
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
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.ballerinalang.langserver.workspace;

import io.ballerina.compiler.api.Types;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.syntax.tree.ExpressionFunctionBodyNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.projects.BuildOptions;
import io.ballerina.projects.Document;
import io.ballerina.projects.DiagnosticResult;
import io.ballerina.projects.Package;
import io.ballerina.projects.PackageCompilation;
import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectEnvironmentBuilder;
import io.ballerina.projects.ProjectKind;
import io.ballerina.projects.TomlDocument;
import io.ballerina.projects.environment.PackageLockingMode;
import org.apache.commons.io.FileUtils;
import org.ballerinalang.compiler.BLangCompilerException;
import org.ballerinalang.langserver.commons.BallerinaCompilerApi;
import org.ballerinalang.langserver.contexts.LanguageServerContextImpl;
import org.ballerinalang.langserver.version.BallerinaBaseCompilerApi;
import org.ballerinalang.util.diagnostic.DiagnosticErrorCode;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tests for compilation-gate locking mode derivation and recovery behavior.
 *
 * @since 1.7.0
 */
public class CompilationGateTest {

    private static final Path RESOURCE_DIRECTORY = Path.of("src/test/resources/project");

    private BallerinaWorkspaceManager workspaceManager;
    private BallerinaCompilerApi originalCompilerApi;

    @BeforeMethod
    public void setUp() throws Exception {
        this.workspaceManager = new BallerinaWorkspaceManager(new LanguageServerContextImpl());
        this.originalCompilerApi = compilerApiInstance();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        setCompilerApiInstance(this.originalCompilerApi);
    }

    /**
     * Tests that a fresh project without a Dependencies.toml file uses SOFT locking mode.
     */
    @Test
    public void testFreshProjectUsesSoftLockingMode() throws Exception {
        CapturingCompilerApi compilerApi = new CapturingCompilerApi(false);
        setCompilerApiInstance(compilerApi);

        Path projectDir = createProjectCopy("myproject");
        try {
            Files.deleteIfExists(projectDir.resolve("Dependencies.toml"));

            workspaceManager.loadProject(projectDir.resolve("main.bal"));

            Assert.assertEquals(compilerApi.capturedBuildOptions().size(), 1,
                    "Fresh project should load once");
            Assert.assertEquals(compilerApi.lastBuildOptions().lockingMode(), PackageLockingMode.SOFT,
                    "Fresh project should use SOFT locking mode");
        } finally {
            deleteDirectory(projectDir);
        }
    }

    /**
     * Tests that an existing Dependencies.toml file uses MEDIUM locking mode.
     */
    @Test
    public void testDependenciesTomlUsesMediumLockingMode() throws Exception {
        CapturingCompilerApi compilerApi = new CapturingCompilerApi(false);
        setCompilerApiInstance(compilerApi);

        Path projectDir = createProjectCopy("myproject");
        try {
            Files.writeString(projectDir.resolve("Dependencies.toml"), "[ballerina]\\ndependencies-toml-version = \"2\"");

            workspaceManager.loadProject(projectDir.resolve("main.bal"));

            Assert.assertEquals(compilerApi.capturedBuildOptions().size(), 1,
                    "Project with Dependencies.toml should load once");
            Assert.assertEquals(compilerApi.lastBuildOptions().lockingMode(), PackageLockingMode.MEDIUM,
                    "Dependencies.toml should force MEDIUM locking mode");
        } finally {
            deleteDirectory(projectDir);
        }
    }

    /**
     * Tests that optimized dependency compilation forces a SOFT reload even when Dependencies.toml exists.
     */
    @Test
    public void testOptimizedDependencyCompilationForcesSoftLockingMode() throws Exception {
        CapturingCompilerApi compilerApi = new CapturingCompilerApi(true);
        setCompilerApiInstance(compilerApi);

        Path projectDir = createProjectCopy("myproject");
        try {
            Files.writeString(projectDir.resolve("Dependencies.toml"), "[ballerina]\\ndependencies-toml-version = \"2\"");

            workspaceManager.loadProject(projectDir.resolve("main.bal"));

            Assert.assertEquals(compilerApi.capturedBuildOptions().size(), 2,
                    "Optimized dependency compilation should trigger a SOFT reload");
            Assert.assertEquals(compilerApi.capturedBuildOptions().get(0).lockingMode(), PackageLockingMode.MEDIUM,
                    "Initial load should respect Dependencies.toml");
            Assert.assertEquals(compilerApi.lastBuildOptions().lockingMode(), PackageLockingMode.SOFT,
                    "Optimized dependency compilation should force SOFT locking mode");
        } finally {
            deleteDirectory(projectDir);
        }
    }

    /**
     * Tests that a module loading failure retries with an online reload and succeeds.
     */
    @Test
    public void testModuleLoadingFailureRetriesOnlineReload() throws Exception {
        CapturingCompilerApi compilerApi = new CapturingCompilerApi(false);
        setCompilerApiInstance(compilerApi);

        Path filePath = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();
        Path projectRoot = filePath.getParent();
        PackageCompilation expectedCompilation = Mockito.mock(PackageCompilation.class);
        compilerApi.enqueueProject(mockProject(filePath, BuildOptions.builder().build(),
                successfulPackage(expectedCompilation)));
        seedProjectContext(projectRoot, mockProject(filePath, BuildOptions.builder().build(),
                failingPackage("failed to load the module")));

        Optional<PackageCompilation> compilation = workspaceManager.waitAndGetPackageCompilation(filePath);

        Assert.assertTrue(compilation.isPresent(), "Online reload should recover the compilation");
        Assert.assertSame(compilation.get(), expectedCompilation, "Recovered compilation should be returned");
        Assert.assertEquals(compilerApi.capturedBuildOptions().size(), 1, "Only one online reload is expected");
        Assert.assertFalse(compilerApi.lastBuildOptions().offlineBuild(), "Recovery should retry online");
        Assert.assertFalse(workspaceManager.projectContext(projectRoot).orElseThrow().compilationCrashed(),
                "Successful recovery should leave the crash flag cleared");
    }

    /**
     * Tests that a failed online retry falls back to a SOFT locking reload.
     */
    @Test
    public void testOnlineRetryFallsBackToSoftReload() throws Exception {
        CapturingCompilerApi compilerApi = new CapturingCompilerApi(false);
        setCompilerApiInstance(compilerApi);

        Path filePath = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();
        Path projectRoot = filePath.getParent();
        PackageCompilation expectedCompilation = Mockito.mock(PackageCompilation.class);
        compilerApi.enqueueProject(mockProject(filePath, BuildOptions.builder().build(),
                failingPackage("failed to load the module")));
        compilerApi.enqueueProject(mockProject(filePath, BuildOptions.builder().build(),
                successfulPackage(expectedCompilation)));
        seedProjectContext(projectRoot, mockProject(filePath, BuildOptions.builder().build(),
                failingPackage("failed to load the module")));

        Optional<PackageCompilation> compilation = workspaceManager.waitAndGetPackageCompilation(filePath);

        Assert.assertTrue(compilation.isPresent(), "SOFT reload should recover the compilation");
        Assert.assertSame(compilation.get(), expectedCompilation, "Recovered compilation should be returned");
        Assert.assertEquals(compilerApi.capturedBuildOptions().size(), 2, "Online and SOFT reloads are expected");
        Assert.assertFalse(compilerApi.capturedBuildOptions().get(0).offlineBuild(),
                "Online retry should disable offline mode");
        Assert.assertEquals(compilerApi.capturedBuildOptions().get(1).lockingMode(), PackageLockingMode.SOFT,
                "Fallback retry should force SOFT locking mode");
        Assert.assertFalse(workspaceManager.projectContext(projectRoot).orElseThrow().compilationCrashed(),
                "Successful fallback should clear the crash flag");
    }

    /**
     * Tests that repeated module loading failures mark the project as crashed.
     */
    @Test
    public void testSoftRetryFailureMarksCompilationAsCrashed() throws Exception {
        CapturingCompilerApi compilerApi = new CapturingCompilerApi(false);
        setCompilerApiInstance(compilerApi);

        Path filePath = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();
        Path projectRoot = filePath.getParent();
        Project initialProject = mockProject(filePath, BuildOptions.builder().build(),
                failingPackage("failed to load the module"));
        Project onlineRetryProject = mockProject(filePath, BuildOptions.builder().build(),
                failingPackage("failed to load the module"));
        Project softRetryProject = mockProject(filePath, BuildOptions.builder().build(),
                failingPackage("failed to load the module"));
        compilerApi.enqueueProject(onlineRetryProject);
        compilerApi.enqueueProject(softRetryProject);
        seedProjectContext(projectRoot, initialProject);

        Assert.expectThrows(BLangCompilerException.class,
                () -> workspaceManager.waitAndGetPackageCompilation(filePath));
        Assert.assertTrue(workspaceManager.projectContext(projectRoot).orElseThrow().compilationCrashed(),
                "Repeated recovery failures should mark the project as crashed");
        Assert.assertEquals(compilerApi.capturedBuildOptions().size(), 2, "Both recovery reloads should be attempted");
    }

    /**
     * Tests that fatal compiler errors skip the recovery ladder and crash immediately.
     */
    @Test
    public void testFatalCompilerErrorsSkipRecovery() throws Exception {
        assertImmediateCrash(DiagnosticErrorCode.BAD_SAD_FROM_COMPILER.diagnosticId() + ": compiler crashed");
        assertImmediateCrash(DiagnosticErrorCode.CYCLIC_MODULE_IMPORTS_DETECTED.diagnosticId() + ": cyclic import");
    }

    private Path createProjectCopy(String projectName) throws IOException {
        Path tempDir = Files.createTempDirectory("compilation-gate-test-");
        Path sourceDir = RESOURCE_DIRECTORY.resolve(projectName).toAbsolutePath();
        FileUtils.copyDirectory(sourceDir.toFile(), tempDir.toFile());
        return tempDir;
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (Files.notExists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private BallerinaCompilerApi compilerApiInstance() throws Exception {
        Field instanceField = BallerinaCompilerApi.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        return (BallerinaCompilerApi) instanceField.get(null);
    }

    private void setCompilerApiInstance(BallerinaCompilerApi compilerApi) throws Exception {
        Field instanceField = BallerinaCompilerApi.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, compilerApi);
    }

    private void seedProjectContext(Path projectRoot, Project project) throws Exception {
        Field sourceRootToProjectField = BallerinaWorkspaceManager.class.getDeclaredField("sourceRootToProject");
        sourceRootToProjectField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Path, BallerinaWorkspaceManager.ProjectContext> sourceRootToProject =
                (Map<Path, BallerinaWorkspaceManager.ProjectContext>) sourceRootToProjectField.get(workspaceManager);
        sourceRootToProject.put(projectRoot, BallerinaWorkspaceManager.ProjectContext.from(project));
    }

    private Package failingPackage(String message) {
        Package currentPackage = Mockito.mock(Package.class);
        Mockito.when(currentPackage.getCompilation()).thenThrow(new BLangCompilerException(message));
        return currentPackage;
    }

    private Package successfulPackage(PackageCompilation compilation) {
        Package currentPackage = Mockito.mock(Package.class);
        DiagnosticResult diagnosticResult = Mockito.mock(DiagnosticResult.class);
        Mockito.when(diagnosticResult.diagnostics()).thenReturn(List.of());
        Mockito.when(compilation.diagnosticResult()).thenReturn(diagnosticResult);
        Mockito.when(currentPackage.getCompilation()).thenReturn(compilation);
        return currentPackage;
    }

    private void assertImmediateCrash(String message) throws Exception {
        CapturingCompilerApi compilerApi = new CapturingCompilerApi(false);
        setCompilerApiInstance(compilerApi);

        Path filePath = RESOURCE_DIRECTORY.resolve("myproject").resolve("main.bal").toAbsolutePath();
        Path projectRoot = filePath.getParent();
        seedProjectContext(projectRoot, mockProject(filePath, BuildOptions.builder().build(), failingPackage(message)));

        Assert.expectThrows(BLangCompilerException.class,
                () -> workspaceManager.waitAndGetPackageCompilation(filePath));
        Assert.assertTrue(workspaceManager.projectContext(projectRoot).orElseThrow().compilationCrashed(),
                "Fatal compiler errors should crash immediately");
        Assert.assertTrue(compilerApi.capturedBuildOptions().isEmpty(),
                "Fatal compiler errors should not trigger reloads");
    }

    private Project mockProject(Path filePath, BuildOptions buildOptions) {
        return mockProject(filePath, buildOptions, Mockito.mock(Package.class));
    }

    private Project mockProject(Path filePath, BuildOptions buildOptions, Package currentPackage) {
        Path projectRoot = filePath.getParent();
        Project project = Mockito.mock(Project.class);
        Mockito.when(project.kind()).thenReturn(ProjectKind.BUILD_PROJECT);
        Mockito.when(project.sourceRoot()).thenReturn(projectRoot);
        Mockito.when(project.currentPackage()).thenReturn(currentPackage);
        Mockito.when(project.buildOptions()).thenReturn(buildOptions);
        return project;
    }

    /**
     * Test compiler API that captures load-time BuildOptions.
     *
     * @since 1.7.0
     */
    private final class CapturingCompilerApi extends BallerinaBaseCompilerApi {

        private final boolean optimizedDependencyCompilation;
        private final List<BuildOptions> capturedBuildOptions = new ArrayList<>();
        private final Deque<Project> queuedProjects = new LinkedList<>();

        private CapturingCompilerApi(boolean optimizedDependencyCompilation) {
            this.optimizedDependencyCompilation = optimizedDependencyCompilation;
        }

        /**
         * Returns the captured BuildOptions list.
         *
         * @return captured build options
         */
        public List<BuildOptions> capturedBuildOptions() {
            return capturedBuildOptions;
        }

        /**
         * Returns the last captured BuildOptions.
         *
         * @return last build options
         */
        public BuildOptions lastBuildOptions() {
            return capturedBuildOptions.get(capturedBuildOptions.size() - 1);
        }

        /**
         * Queues a project to be returned by the next load invocation.
         *
         * @param project project to enqueue
         */
        public void enqueueProject(Project project) {
            this.queuedProjects.add(project);
        }

        @Override
        public boolean hasOptimizedDependencyCompilation(Project project) {
            return this.optimizedDependencyCompilation;
        }

        @Override
        public Project loadProject(Path path, BuildOptions buildOptions) {
            this.capturedBuildOptions.add(buildOptions);
            if (!this.queuedProjects.isEmpty()) {
                return this.queuedProjects.removeFirst();
            }
            return mockProject(path, buildOptions);
        }

        @Override
        public Optional<TypeSymbol> getType(Types types, Document document, String typeName,
                                            Map<String, BLangPackage> packageMap) {
            return Optional.empty();
        }

        @Override
        public Optional<TypeSymbol> getType(Types types, Document document, String typeName) {
            return Optional.empty();
        }

        @Override
        public Optional<Project> getWorkspaceProject(Project project) {
            return Optional.empty();
        }

        @Override
        public boolean isWorkspaceProject(Project project) {
            return false;
        }

        @Override
        public Collection<Project> getWorkspaceDependents(Project workspaceProject, Project packageProject) {
            return Collections.emptyList();
        }

        @Override
        public List<Project> getWorkspaceProjectsInOrder(Project project) {
            return Collections.emptyList();
        }

        @Override
        public Project loadProject(Path path) {
            return mockProject(path, BuildOptions.builder().build());
        }

        @Override
        public Project loadProject(Path path, ProjectEnvironmentBuilder environmentBuilder) {
            return mockProject(path, BuildOptions.builder().build());
        }

        @Override
        public boolean isNaturalExpressionBody(ExpressionFunctionBodyNode expressionFunctionBodyNode) {
            return false;
        }

        @Override
        public boolean isNaturalExpressionBodiedFunction(FunctionDefinitionNode functionDefNode) {
            return false;
        }

        @Override
        public boolean isWorkspaceProjectRoot(Path path) {
            return false;
        }

        @Override
        public Collection<io.ballerina.tools.diagnostics.Diagnostic> getDiagnostics(DiagnosticResult diagnosticResult) {
            return Collections.emptyList();
        }

        @Override
        public List<Project> getWorkspaceProjects(Project project) {
            return Collections.emptyList();
        }

        @Override
        public Optional<TomlDocument> getWorkspaceToml(Project project) {
            return Optional.empty();
        }

        @Override
        public Optional<Project> updateWorkspaceToml(Project project, String content) {
            return Optional.empty();
        }
    }
}
