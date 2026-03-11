/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.runtime.sdk.capability;

import java.time.Instant;

/**
 * Scheduled Task Handle
 * 
 * <p>Represents a scheduled task, can be used to query status or cancel task.</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see SchedulingCapability
 */
public interface ScheduledTaskHandle {

    /**
     * Get task ID
     * 
     * @return unique task identifier
     */
    String getTaskId();

    /**
     * Cancel task
     * 
     * @param mayInterruptIfRunning whether to interrupt if task is running
     * @return true if task was successfully cancelled
     */
    boolean cancel(boolean mayInterruptIfRunning);

    /**
     * Check if task is cancelled
     * 
     * @return true if task is cancelled
     */
    boolean isCancelled();

    /**
     * Check if task is done (only meaningful for one-time tasks)
     * 
     * @return true if task is done
     */
    boolean isDone();

    /**
     * Get next execution time
     * 
     * @return next execution time, null if task is cancelled or done
     */
    Instant getNextExecutionTime();

    /**
     * Get last execution time
     * 
     * @return last execution time, null if never executed
     */
    Instant getLastExecutionTime();

    /**
     * Get execution count
     * 
     * @return number of executions
     */
    long getExecutionCount();
}
