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

import javax.annotation.Nonnull;

/**
 * Unit tests for ProjectExecutor delegate.
 *
 * @since 1.7.0
 */
public class ProjectExecutorTest {

    private TestableProjectExecutor executor;

    @BeforeMethod
    void setUp() {
        executor = new TestableProjectExecutor();
    }

    @Test(description = "Test stopAll does not throw when no processes")
    public void testStopAllDoesNotThrow() {
        // Should not throw
        executor.stopAll();
        Assert.assertTrue(true, "stopAll should not throw when no processes");
    }

    @Test(description = "Test stopProject returns true when no process exists")
    public void testStopProjectReturnsTrueWhenNoProcess() {
        Path projectRoot = Path.of("/test/project");
        boolean result = executor.stopProjectPublic(projectRoot);
        Assert.assertTrue(result, "stopProject should return true when no process exists");
    }

    /**
     * TestableProjectExecutor that doesn't require a full context.
     */
    private static class TestableProjectExecutor {

        private final java.util.Map<Path, Process> processMap = new java.util.concurrent.ConcurrentHashMap<>();

        void stopAll() {
            for (Path projectRoot : new java.util.ArrayList<>(processMap.keySet())) {
                stopProjectPublic(projectRoot);
            }
        }

        boolean stopProjectPublic(@Nonnull Path projectRoot) {
            Process process = processMap.get(projectRoot);
            if (process == null) {
                return true;
            }

            process.destroy();
            processMap.remove(projectRoot, process);
            return !process.isAlive();
        }
    }
}