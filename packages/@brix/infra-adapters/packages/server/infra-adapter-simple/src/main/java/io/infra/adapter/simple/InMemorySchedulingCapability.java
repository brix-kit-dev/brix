/*
 * Copyright 2026 Brix Authors
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
package io.infra.adapter.simple;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.capability.ScheduledTaskHandle;
import io.runtime.sdk.capability.SchedulingCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

/**
 * In-Memory Scheduling Capability Implementation
 * 
 * <p>This class is a lightweight in-memory implementation of {@link SchedulingCapability},
 * based on ScheduledExecutorService. Suitable for local development and testing scenarios
 * without requiring external scheduling frameworks like Quartz.</p>
 * 
 * <h3>Key Features</h3>
 * <ul>
 *   <li><b>Cron Scheduling</b>: Based on simplified Cron parsing (supports basic expressions only)</li>
 *   <li><b>Fixed Rate</b>: Executes at fixed intervals</li>
 *   <li><b>Fixed Delay</b>: Executes after delay from previous completion</li>
 *   <li><b>One-time Tasks</b>: Executes once after specified delay</li>
 * </ul>
 * 
 * <h3>Limitations</h3>
 * <ul>
 *   <li>Tasks are scheduled only within the current JVM, distributed scheduling is not supported</li>
 *   <li>All tasks are lost after process restart</li>
 *   <li>Limited Cron expression support (use Quartz adapter for production)</li>
 * </ul>
 * 
 * @author Brix Team
 * @since 3.0.0
 * @see SchedulingCapability
 */
@Capability(
    type = SchedulingCapability.class,
    name = "in-memory-scheduling",
    description = "In-memory scheduling implementation based on ScheduledExecutorService",
    level = CapabilityLevel.STANDARD,
    aliases = {"simpleScheduling", "inMemoryScheduling"}
)
public class InMemorySchedulingCapability implements SchedulingCapability, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(InMemorySchedulingCapability.class);

    /**
     * Default thread pool size
     */
    private static final int DEFAULT_POOL_SIZE = 4;

    /**
     * Scheduled executor
     */
    private final ScheduledExecutorService scheduler;

    /**
     * Task mapping (taskId -> TaskHolder)
     */
    private final Map<String, TaskHolder> tasks = new ConcurrentHashMap<>();

    /**
     * Creates in-memory scheduling capability (default configuration)
     */
    public InMemorySchedulingCapability() {
        this(DEFAULT_POOL_SIZE);
    }

    /**
     * Creates in-memory scheduling capability
     * 
     * @param poolSize Thread pool size
     */
    public InMemorySchedulingCapability(int poolSize) {
        this.scheduler = Executors.newScheduledThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "inmemory-scheduler");
            t.setDaemon(true);
            return t;
        });
        log.info("In-memory scheduling capability created: poolSize={}", poolSize);
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Note: In-memory implementation has limited Cron support, only simulates basic periodic scheduling.
     * Use Quartz adapter for production environments.</p>
     */
    @Override
    public ScheduledTaskHandle scheduleWithCron(String taskId, String cronExpression, Runnable task) {
        Objects.requireNonNull(taskId, "Task ID cannot be null");
        Objects.requireNonNull(cronExpression, "Cron expression cannot be null");
        Objects.requireNonNull(task, "Task cannot be null");

        // Simplified Cron parsing: only supports fixed interval simulation
        // Real Cron parsing requires dedicated libraries (e.g., cron-utils)
        Duration interval = parseCronToInterval(cronExpression);
        
        TaskHolder holder = new TaskHolder(taskId, "cron");
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
            () -> executeTask(holder, task),
            interval.toMillis(),
            interval.toMillis(),
            TimeUnit.MILLISECONDS
        );
        
        holder.setFuture(future);
        tasks.put(taskId, holder);
        
        log.debug("Scheduled cron task: taskId={}, cron={}, interval={}", taskId, cronExpression, interval);
        return new InMemoryTaskHandle(holder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ScheduledTaskHandle scheduleAtFixedRate(String taskId, Duration period, Runnable task) {
        return scheduleAtFixedRate(taskId, Duration.ZERO, period, task);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ScheduledTaskHandle scheduleAtFixedRate(String taskId, Duration initialDelay, Duration period, Runnable task) {
        Objects.requireNonNull(taskId, "Task ID cannot be null");
        Objects.requireNonNull(initialDelay, "Initial delay cannot be null");
        Objects.requireNonNull(period, "Period cannot be null");
        Objects.requireNonNull(task, "Task cannot be null");

        TaskHolder holder = new TaskHolder(taskId, "fixed-rate");
        holder.setNextExecutionTime(Instant.now().plus(initialDelay));
        holder.setPeriod(period);
        
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
            () -> executeTask(holder, task),
            initialDelay.toMillis(),
            period.toMillis(),
            TimeUnit.MILLISECONDS
        );
        
        holder.setFuture(future);
        tasks.put(taskId, holder);
        
        log.debug("Scheduled fixed rate task: taskId={}, initialDelay={}, period={}", taskId, initialDelay, period);
        return new InMemoryTaskHandle(holder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ScheduledTaskHandle scheduleWithFixedDelay(String taskId, Duration delay, Runnable task) {
        Objects.requireNonNull(taskId, "Task ID cannot be null");
        Objects.requireNonNull(delay, "Delay cannot be null");
        Objects.requireNonNull(task, "Task cannot be null");

        TaskHolder holder = new TaskHolder(taskId, "fixed-delay");
        holder.setNextExecutionTime(Instant.now().plus(delay));
        holder.setPeriod(delay);
        
        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(
            () -> executeTask(holder, task),
            delay.toMillis(),
            delay.toMillis(),
            TimeUnit.MILLISECONDS
        );
        
        holder.setFuture(future);
        tasks.put(taskId, holder);
        
        log.debug("Scheduled fixed delay task: taskId={}, delay={}", taskId, delay);
        return new InMemoryTaskHandle(holder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ScheduledTaskHandle scheduleOnce(String taskId, Duration delay, Runnable task) {
        Objects.requireNonNull(taskId, "Task ID cannot be null");
        Objects.requireNonNull(delay, "Delay cannot be null");
        Objects.requireNonNull(task, "Task cannot be null");

        TaskHolder holder = new TaskHolder(taskId, "once");
        holder.setNextExecutionTime(Instant.now().plus(delay));
        
        ScheduledFuture<?> future = scheduler.schedule(
            () -> {
                executeTask(holder, task);
                tasks.remove(taskId);
            },
            delay.toMillis(),
            TimeUnit.MILLISECONDS
        );
        
        holder.setFuture(future);
        tasks.put(taskId, holder);
        
        log.debug("Scheduled one-time task: taskId={}, delay={}", taskId, delay);
        return new InMemoryTaskHandle(holder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean cancel(String taskId) {
        Objects.requireNonNull(taskId, "Task ID cannot be null");

        TaskHolder holder = tasks.remove(taskId);
        if (holder == null) {
            log.debug("Task does not exist: taskId={}", taskId);
            return false;
        }

        boolean cancelled = holder.cancel(false);
        log.debug("Cancelled task: taskId={}, result={}", taskId, cancelled);
        return cancelled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRunning(String taskId) {
        TaskHolder holder = tasks.get(taskId);
        return holder != null && holder.isRunning();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isScheduled(String taskId) {
        TaskHolder holder = tasks.get(taskId);
        return holder != null && !holder.isCancelled() && !holder.isDone();
    }

    /**
     * Gets active task count
     * 
     * @return Task count
     */
    public int getActiveTaskCount() {
        return (int) tasks.values().stream()
                .filter(h -> !h.isCancelled() && !h.isDone())
                .count();
    }

    /**
     * Simplified Cron parsing (for demonstration only)
     * 
     * <p>Only supports simple interval patterns, real scenarios should use professional Cron libraries</p>
     */
    private Duration parseCronToInterval(String cronExpression) {
        // Simplified implementation: default 1 minute interval
        // Real implementation should parse Cron expression
        if (cronExpression.contains("* * * * *")) {
            return Duration.ofMinutes(1);
        } else if (cronExpression.contains("0 * * * *")) {
            return Duration.ofHours(1);
        } else if (cronExpression.contains("0 0 * * *")) {
            return Duration.ofDays(1);
        }
        // Default 1 minute
        return Duration.ofMinutes(1);
    }

    /**
     * Executes task
     */
    private void executeTask(TaskHolder holder, Runnable task) {
        holder.markRunning();
        holder.setLastExecutionTime(Instant.now());
        try {
            task.run();
            holder.incrementExecutionCount();
            log.trace("Task execution completed: taskId={}", holder.getTaskId());
        } catch (Exception e) {
            log.error("Task execution exception: taskId={}", holder.getTaskId(), e);
        } finally {
            holder.markNotRunning();
            // Update next execution time
            if (holder.getPeriod() != null) {
                holder.setNextExecutionTime(Instant.now().plus(holder.getPeriod()));
            } else {
                holder.setNextExecutionTime(null);
            }
        }
    }

    @Override
    public void close() {
        log.info("Closing in-memory scheduling capability...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        tasks.clear();
    }

    /**
     * Task holder (inner class)
     */
    private static class TaskHolder {
        private final String taskId;
        private final String type;
        private volatile ScheduledFuture<?> future;
        private volatile boolean running = false;
        private volatile Instant nextExecutionTime;
        private volatile Instant lastExecutionTime;
        private final AtomicLong executionCount = new AtomicLong(0);
        private Duration period;

        TaskHolder(String taskId, String type) {
            this.taskId = taskId;
            this.type = type;
        }

        String getTaskId() {
            return taskId;
        }

        void setFuture(ScheduledFuture<?> future) {
            this.future = future;
        }

        boolean cancel(boolean mayInterruptIfRunning) {
            return future != null && future.cancel(mayInterruptIfRunning);
        }

        boolean isCancelled() {
            return future != null && future.isCancelled();
        }

        boolean isDone() {
            return future != null && future.isDone();
        }

        void markRunning() {
            this.running = true;
        }

        void markNotRunning() {
            this.running = false;
        }

        boolean isRunning() {
            return running;
        }

        Instant getNextExecutionTime() {
            return nextExecutionTime;
        }

        void setNextExecutionTime(Instant time) {
            this.nextExecutionTime = time;
        }

        Instant getLastExecutionTime() {
            return lastExecutionTime;
        }

        void setLastExecutionTime(Instant time) {
            this.lastExecutionTime = time;
        }

        long getExecutionCount() {
            return executionCount.get();
        }

        void incrementExecutionCount() {
            executionCount.incrementAndGet();
        }

        Duration getPeriod() {
            return period;
        }

        void setPeriod(Duration period) {
            this.period = period;
        }
    }

    /**
     * In-memory task handle implementation
     */
    private static class InMemoryTaskHandle implements ScheduledTaskHandle {
        
        private final TaskHolder holder;

        InMemoryTaskHandle(TaskHolder holder) {
            this.holder = holder;
        }

        @Override
        public String getTaskId() {
            return holder.getTaskId();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return holder.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return holder.isCancelled();
        }

        @Override
        public boolean isDone() {
            return holder.isDone();
        }

        @Override
        public Instant getNextExecutionTime() {
            return holder.getNextExecutionTime();
        }

        @Override
        public Instant getLastExecutionTime() {
            return holder.getLastExecutionTime();
        }

        @Override
        public long getExecutionCount() {
            return holder.getExecutionCount();
        }
    }
}
