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

import io.ballerina.projects.JBallerinaBackend;
import io.ballerina.projects.JarLibrary;
import io.ballerina.projects.JarResolver;
import io.ballerina.projects.JvmTarget;
import io.ballerina.projects.Module;
import io.ballerina.projects.Package;
import io.ballerina.projects.PackageCompilation;
import io.ballerina.projects.Project;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;
import org.ballerinalang.langserver.LSContextOperation;
import org.ballerinalang.langserver.commons.workspace.RunContext;
import org.ballerinalang.langserver.commons.workspace.RunResult;
import org.ballerinalang.langserver.exception.UserErrorException;
import org.eclipse.lsp4j.Position;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;

import static io.ballerina.runtime.api.constants.RuntimeConstants.MODULE_INIT_CLASS_NAME;

/**
 * Handles workspace project execution and process tracking.
 *
 * @since 1.7.0
 */
final class ProjectExecutor {

    private static final String USER_DIR = System.getProperty("user.dir");
    private static final String HEAP_DUMP_FLAG = "-XX:+HeapDumpOnOutOfMemoryError";
    private static final String HEAP_DUMP_PATH_FLAG = "-XX:HeapDumpPath=";
    private static final String DEBUG_ARGS = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:";

    private final ProjectExecutorContext context;
    private final Map<Path, Process> processMap = new ConcurrentHashMap<>();

    ProjectExecutor(@Nonnull ProjectExecutorContext context) {
        this.context = context;
    }

    /**
     * Executes the current package for the given run request.
     *
     * @param executionContext run request
     * @return execution result with diagnostics and process
     * @throws IOException if launching the process fails
     */
    @Nonnull
    RunResult run(@Nonnull RunContext executionContext) throws IOException {
        Path projectRoot = context.projectRoot(executionContext.balSourcePath());
        Optional<ProjectContext> projectContext = validateProjectContext(projectRoot);
        if (projectContext.isEmpty()) {
            return new RunResult(null, Collections.emptyList());
        }

        if (!stop(projectRoot)) {
            logError("Run command execution aborted because couldn't stop the previous run");
            return new RunResult(null, Collections.emptyList());
        }

        Project project = projectContext.get().project();
        Optional<PackageCompilation> packageCompilation =
                context.waitAndGetPackageCompilation(project.sourceRoot(), true);
        if (packageCompilation.isEmpty()) {
            logError("Run command execution aborted because package compilation failed");
            return new RunResult(null, Collections.emptyList());
        }

        JBallerinaBackend jBallerinaBackend = execBackend(projectContext.get(), packageCompilation.get());
        Collection<Diagnostic> diagnostics = new LinkedList<>();
        diagnostics.addAll(jBallerinaBackend.diagnosticResult().diagnostics(false));
        diagnostics.addAll(project.currentPackage().getBuildToolResolution().getDiagnosticList());

        if (diagnostics.stream().anyMatch(d -> d.diagnosticInfo().severity() == DiagnosticSeverity.ERROR)) {
            return new RunResult(null, diagnostics);
        }

        Optional<Process> process = executeProject(projectRoot, projectContext.get(), executionContext);
        return process.map(value -> new RunResult(value, diagnostics))
                .orElseGet(() -> new RunResult(null, diagnostics));
    }

    /**
     * Stops the running process for the project containing the given file path.
     *
     * @param filePath file or project path
     * @return {@code true} if the process is stopped or absent
     */
    boolean stop(@Nonnull Path filePath) {
        Path projectRoot = context.projectRoot(filePath).toAbsolutePath();
        Optional<ProjectContext> projectContext = context.projectContext(projectRoot);
        if (projectContext.isEmpty()) {
            context.logger().logWarning("Failed to stop process: Project not found");
            return false;
        }
        return stopProject(projectRoot);
    }

    /**
     * Stops the tracked process for the given project root, if any.
     *
     * @param projectRoot project root path
     * @return {@code true} if the process is stopped or absent
     */
    boolean stopProject(@Nonnull Path projectRoot) {
        Process process = processMap.get(projectRoot);
        if (process == null) {
            return true;
        }

        boolean killed = killProcess(process);
        if (killed) {
            processMap.remove(projectRoot, process);
        }
        return killed;
    }

    /**
     * Stops all tracked processes.
     */
    void stopAll() {
        for (Path projectRoot : new ArrayList<>(processMap.keySet())) {
            stopProject(projectRoot);
        }
    }

    private Optional<ProjectContext> validateProjectContext(Path projectRoot) {
        Optional<ProjectContext> projectContextOpt = context.projectContext(projectRoot);
        if (projectContextOpt.isEmpty()) {
            logError("Run command execution aborted because project is not loaded");
            return Optional.empty();
        }

        return projectContextOpt;
    }

    private Optional<Process> executeProject(Path projectRoot, ProjectContext projectContext, RunContext runContext)
            throws IOException {
        Project project = projectContext.project();
        Package pkg = project.currentPackage();
        Module executableModule = pkg.getDefaultModule();
        JBallerinaBackend backend = execBackend(projectContext, pkg.getCompilation());
        JarResolver jarResolver = backend.jarResolver();

        List<String> commands = prepareExecutionCommands(runContext, executableModule, jarResolver);
        ProcessBuilder processBuilder = new ProcessBuilder(commands);
        processBuilder.environment().putAll(runContext.env());

        AtomicReference<Optional<Process>> processRef = new AtomicReference<>(Optional.empty());
        try {
            projectContext.withWriteLock(ctx -> {
                Process existing = processMap.get(projectRoot);
                if (existing != null && existing.isAlive()) {
                    logError("Run command execution aborted because another run is in progress");
                    return;
                }

                try {
                    Process process = processBuilder.start();
                    processMap.put(projectRoot, process);
                    processRef.set(Optional.of(process));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
        return processRef.get();
    }

    private List<String> prepareExecutionCommands(RunContext runContext, Module module, JarResolver jarResolver) {
        List<String> commands = new ArrayList<>();
        commands.add(runContext.javaCmd());
        commands.add(HEAP_DUMP_FLAG);
        commands.add(HEAP_DUMP_PATH_FLAG + USER_DIR);
        if (runContext.debugPort() > 0) {
            commands.add(DEBUG_ARGS + runContext.debugPort());
        }

        commands.add("-cp");
        commands.add(getAllClassPaths(jarResolver));
        commands.add(JarResolver.getQualifiedClassName(
                module.packageInstance().packageOrg().toString(),
                module.packageInstance().packageName().toString(),
                module.packageInstance().packageVersion().toString(),
                MODULE_INIT_CLASS_NAME));
        commands.addAll(runContext.programArgs());
        return commands;
    }

    private boolean killProcess(Process process) {
        process.destroy();
        try {
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            context.logger().logWarning("Waiting for process to stop was interrupted");
            Thread.currentThread().interrupt();
        }
        if (process.isAlive()) {
            process.destroyForcibly();
        }
        return !process.isAlive();
    }

    private String getAllClassPaths(JarResolver jarResolver) {
        StringJoiner cp = new StringJoiner(File.pathSeparator);
        for (JarLibrary lib : jarResolver.getJarFilePathsRequiredForExecution()) {
            cp.add(lib.path().toString());
        }
        return cp.toString();
    }

    private void logError(String message) {
        UserErrorException exception = new UserErrorException(message);
        context.logger().logError(LSContextOperation.WS_EXEC_CMD, message, exception, null, (Position) null);
    }

    private static JBallerinaBackend execBackend(ProjectContext projectContext, PackageCompilation packageCompilation) {
        AtomicReference<JBallerinaBackend> backendRef = new AtomicReference<>();
        projectContext.withWriteLock(ctx -> {
            JBallerinaBackend backend = JBallerinaBackend.from(packageCompilation, JvmTarget.JAVA_21, false);
            Package pkg = ctx.project().currentPackage();
            for (Module module : pkg.modules()) {
                for (io.ballerina.projects.DocumentId id : module.documentIds()) {
                    module.document(id).modify().apply();
                }
            }
            backendRef.set(backend);
        });
        return backendRef.get();
    }
}
