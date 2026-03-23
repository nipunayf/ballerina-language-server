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

import io.ballerina.projects.Project;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Mutable workspace project state guarded by a per-project read-write lock.
 *
 * @since 1.7.0
 */
public class ProjectContext {

    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock(true);
    private Project project;

    private volatile boolean compilationCrashed;

    private volatile boolean projectCrashed;
    private volatile boolean closed = false;
    private final boolean workspaceChild;
    private final Path workspaceRoot;

    private ProjectContext(Project project) {
        this(project, false, null);
    }

    private ProjectContext(Project project, boolean workspaceChild, Path workspaceRoot) {
        this.project = project;
        this.compilationCrashed = false;
        this.workspaceChild = workspaceChild;
        this.workspaceRoot = workspaceRoot;
    }

    /**
     * Create a project context for a standalone project root.
     *
     * @param project loaded project instance
     * @return project context
     */
    public static ProjectContext from(Project project) {
        return new ProjectContext(project);
    }

    /**
     * Create a project context for a workspace root or workspace child.
     *
     * @param project loaded project instance
     * @param workspaceChild whether the project belongs to a workspace root
     * @param workspaceRoot workspace root path for workspace children
     * @return project context
     */
    public static ProjectContext from(Project project, boolean workspaceChild, Path workspaceRoot) {
        return new ProjectContext(project, workspaceChild, workspaceRoot);
    }

    /**
     * Returns whether this context represents a workspace child project.
     *
     * @return {@code true} when this project belongs to a workspace root
     */
    public boolean isWorkspaceChild() {
        return workspaceChild;
    }

    /**
     * Returns the workspace root for workspace child projects.
     *
     * @return workspace root path or {@code null} for standalone roots
     */
    public Path workspaceRoot() {
        return workspaceRoot;
    }

    /**
     * Execute an action under the read lock. Returns null if the context is closed.
     *
     * @param action the function to execute under read lock
     * @param <T> the return type
     * @return the result of the action, or null if closed
     */
    public <T> T withReadLock(Function<ProjectContext, T> action) {
        rwl.readLock().lock();
        try {
            if (closed) {
                return null;
            }
            return action.apply(this);
        } finally {
            rwl.readLock().unlock();
        }
    }

    /**
     * Execute an action under the write lock.
     *
     * @param action the consumer to execute under write lock
     */
    public void withWriteLock(Consumer<ProjectContext> action) {
        rwl.writeLock().lock();
        try {
            if (closed) {
                return;
            }
            action.accept(this);
        } finally {
            rwl.writeLock().unlock();
        }
    }

    /**
     * Returns the loaded project.
     *
     * @return current project
     */
    public Project project() {
        return this.project;
    }

    /**
     * Replace the loaded project.
     *
     * @param project project to store
     */
    public void setProject(Project project) {
        this.project = project;
    }

    /**
     * Returns whether the last compilation failed irrecoverably.
     *
     * @return compilation crash flag
     */
    public boolean compilationCrashed() {
        return this.compilationCrashed;
    }

    /**
     * Update the compilation crash flag.
     *
     * @param compilationCrashed compilation crash state
     */
    public void setCompilationCrashed(boolean compilationCrashed) {
        this.compilationCrashed = compilationCrashed;
    }

    /**
     * Update the project crash flag.
     *
     * @param projectCrashed project crash state
     */
    public void setProjectCrashed(boolean projectCrashed) {
        this.projectCrashed = projectCrashed;
    }

    /**
     * Returns whether the project is in a crashed state.
     *
     * @return project crash flag
     */
    public boolean isProjectCrashed() {
        return projectCrashed;
    }

    /**
     * Close this project context and release resources.
     */
    public void close() {
        rwl.writeLock().lock();
        try {
            if (!closed) {
                closed = true;
                if (project != null) {
                    project.clearCaches();
                    project = null;
                }
            }
        } finally {
            rwl.writeLock().unlock();
        }
    }

    /**
     * Returns whether this context has been closed.
     *
     * @return {@code true} if closed
     */
    public boolean isClosed() {
        return closed;
    }
}
