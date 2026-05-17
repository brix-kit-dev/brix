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

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.runtime.sdk.annotation.Since;

/**
 * Scheduling Capability Contract (Optional Capability)
 * 
 * <p>Provides a unified abstraction for scheduled task scheduling, supporting Cron expressions and fixed-rate scheduling.
 * Modules register scheduled tasks through this interface without directly using frameworks like Quartz.</p>
 * 
 * <h3>Core Features</h3>
 * <ul>
 *   <li><b>Cron Scheduling</b>: Flexible scheduling based on Cron expressions</li>
 *   <li><b>Fixed Rate</b>: Execute at fixed intervals</li>
 *   <li><b>Fixed Delay</b>: Execute after delay since last completion</li>
 *   <li><b>One-time Task</b>: Execute once after specified delay</li>
 * </ul>
 * 
 * <h3>Distributed Scheduling</h3>
 * <p>In distributed environments, scheduled tasks execute on only one node by default.
 * By combining with {@link LockCapability} distributed locks, tasks won't execute repeatedly.</p>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Inject
 * private SchedulingCapability scheduling;
 * 
 * public void setupScheduledTasks() {
 *     // Cron schedule: execute daily at 2 AM
 *     scheduling.scheduleWithCron("daily-cleanup", "0 0 2 * * ?", 
 *         () -> cleanupExpiredData());
 *     
 *     // Fixed rate: execute every 5 minutes
 *     scheduling.scheduleAtFixedRate("health-check", Duration.ofMinutes(5),
 *         () -> performHealthCheck());
 *     
 *     // One-time task: execute after 30 minutes
 *     scheduling.scheduleOnce("delayed-task", Duration.ofMinutes(30),
 *         () -> sendReminder());
 * }
 * }</pre>
 * 
 * <h3>Notes</h3>
 * <ul>
 *   <li>Tasks should be idempotent to handle repeated execution</li>
 *   <li>Long-running tasks should consider timeout handling</li>
 *   <li>Exceptions in tasks should be caught to avoid affecting scheduler</li>
 *   <li>This is an optional capability; check availability before use</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
@Since("3.0.0")
public interface SchedulingCapability {

    /**
     * Schedule task with Cron expression
     * 
     * <p>Cron expression format: seconds minutes hours day month weekday</p>
     * 
     * @param taskId         unique task identifier
     * @param cronExpression Cron expression
     * @param task           task to execute
     * @return task handle for cancellation
     */
    ScheduledTaskHandle scheduleWithCron(String taskId, String cronExpression, Runnable task);

    /**
     * Fixed rate scheduling
     * 
     * <p>Triggers next execution at fixed intervals regardless of previous completion</p>
     * 
     * @param taskId       unique task identifier
     * @param period       execution interval
     * @param task         task to execute
     * @return task handle
     */
    ScheduledTaskHandle scheduleAtFixedRate(String taskId, Duration period, Runnable task);

    /**
     * Fixed rate scheduling (with initial delay)
     * 
     * @param taskId       unique task identifier
     * @param initialDelay initial delay
     * @param period       execution interval
     * @param task         task to execute
     * @return task handle
     */
    ScheduledTaskHandle scheduleAtFixedRate(String taskId, Duration initialDelay, Duration period, Runnable task);

    /**
     * Fixed delay scheduling
     * 
     * <p>Triggers next execution after specified delay since previous completion</p>
     * 
     * @param taskId unique task identifier
     * @param delay  execution delay
     * @param task   task to execute
     * @return task handle
     */
    ScheduledTaskHandle scheduleWithFixedDelay(String taskId, Duration delay, Runnable task);

    /**
     * One-time delayed task
     * 
     * @param taskId unique task identifier
     * @param delay  delay time
     * @param task   task to execute
     * @return task handle
     */
    ScheduledTaskHandle scheduleOnce(String taskId, Duration delay, Runnable task);

    /**
     * Cancel task
     * 
     * @param taskId unique task identifier
     * @return true if task existed and was cancelled
     */
    boolean cancel(String taskId);

    /**
     * Check if task is running
     * 
     * @param taskId unique task identifier
     * @return true if task is currently running
     */
    boolean isRunning(String taskId);

    /**
     * Check if task is scheduled
     * 
     * @param taskId unique task identifier
     * @return true if task is scheduled
     */
    boolean isScheduled(String taskId);
}
