/*
 *  Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com)
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

package io.ballerina.flowmodelgenerator.core.difference;

import com.google.gson.JsonElement;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Debouncing specifically designed for flow model difference requests. This debouncer ensures that difference
 * processing is only executed after a specified delay has passed since the last invocation, cancelling any pending
 * executions in between. This class follows the Singleton pattern, ensuring only one instance exists across the
 * application for difference operations.
 *
 * @since 1.0.0
 */
public class DifferenceDebouncer {

    // Time unit for the delay
    private static final TimeUnit TIME_UNIT = TimeUnit.MILLISECONDS;

    // Default delay for difference debouncing (in milliseconds)
    private static final long DELAY = 300;

    // Map to hold scheduled difference tasks
    private final ConcurrentHashMap<String, ScheduledDifferenceTaskHolder<?>> delayedMap;

    // Single-thread scheduler to debounce difference tasks.
    private final ScheduledExecutorService scheduler;

    private DifferenceDebouncer() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DifferenceDebouncer");
            t.setDaemon(true);
            return t;
        });
        delayedMap = new ConcurrentHashMap<>();
    }

    /**
     * Debounce the given difference request by scheduling it to execute after the default delay. Any previously
     * scheduled task with the same key is cancelled.
     *
     * @param request the difference request to debounce
     * @return a CompletableFuture that will complete with the result of the difference operation
     */
    public CompletableFuture<JsonElement> debounce(DifferenceRequest request) {
        String key = request.getKey();
        CompletableFuture<JsonElement> promise = new CompletableFuture<>();

        // Schedule the task to run after the default delay.
        Future<?> scheduledFuture = scheduler.schedule(() -> {
            try {
                JsonElement result = request.call();
                promise.complete(result);
            } catch (Exception ex) {
                promise.completeExceptionally(ex);
            } finally {
                request.revertDocuments();
                delayedMap.remove(key);
            }
        }, DELAY, TIME_UNIT);

        // Replace any existing scheduled task with the new one.
        @SuppressWarnings("unchecked")
        ScheduledDifferenceTaskHolder<JsonElement> prev =
                (ScheduledDifferenceTaskHolder<JsonElement>) delayedMap.put(key,
                        new ScheduledDifferenceTaskHolder<>(promise, scheduledFuture));
        if (prev != null) {
            prev.future().cancel(true);
            prev.promise().completeExceptionally(new CancellationException("Debounced by a new difference request"));
        }
        return promise;
    }

    /**
     * Get the singleton instance of DifferenceDebouncer.
     *
     * @return the DifferenceDebouncer instance
     */
    public static DifferenceDebouncer getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {

        private static final DifferenceDebouncer INSTANCE = new DifferenceDebouncer();
    }

    /**
     * Holder for scheduled difference task information.
     *
     * @param <T>     the type of result promised by the CompletableFuture.
     * @param promise the CompletableFuture that will eventually complete with the result of the scheduled difference
     *                task.
     * @param future  the Future representing the scheduled difference task, allowing for control over task execution.
     */
    private record ScheduledDifferenceTaskHolder<T>(CompletableFuture<T> promise, Future<?> future) {
    }
}
