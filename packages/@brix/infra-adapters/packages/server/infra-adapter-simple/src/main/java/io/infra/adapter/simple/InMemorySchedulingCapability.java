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
 * 基于内存的定时任务能力实现
 * 
 * <p>本类是 {@link SchedulingCapability} 的轻量级内存实现，基于 ScheduledExecutorService。
 * 适用于本地开发和测试场景，无需依赖 Quartz 等外部调度框架。</p>
 * 
 * <h3>核心特性</h3>
 * <ul>
 *   <li><b>Cron 调度</b>：基于简易 Cron 解析（仅支持基本表达式）</li>
 *   <li><b>固定频率</b>：按固定间隔执行</li>
 *   <li><b>固定延迟</b>：上次执行完成后延迟执行</li>
 *   <li><b>一次性任务</b>：延迟指定时间后执行一次</li>
 * </ul>
 * 
 * <h3>限制说明</h3>
 * <ul>
 *   <li>任务仅在当前 JVM 内调度，不支持分布式调度</li>
 *   <li>进程重启后所有任务丢失</li>
 *   <li>Cron 表达式支持有限（建议生产环境使用 Quartz 适配器）</li>
 * </ul>
 * 
 * @author Brix Team
 * @since 3.0.0
 * @see SchedulingCapability
 */
@Capability(
    type = SchedulingCapability.class,
    name = "in-memory-scheduling",
    description = "基于 ScheduledExecutorService 的内存定时任务实现",
    level = CapabilityLevel.STANDARD,
    aliases = {"simpleScheduling", "inMemoryScheduling"}
)
public class InMemorySchedulingCapability implements SchedulingCapability, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(InMemorySchedulingCapability.class);

    /**
     * 默认线程池大小
     */
    private static final int DEFAULT_POOL_SIZE = 4;

    /**
     * 调度执行器
     */
    private final ScheduledExecutorService scheduler;

    /**
     * 任务映射（taskId -> TaskHolder）
     */
    private final Map<String, TaskHolder> tasks = new ConcurrentHashMap<>();

    /**
     * 创建内存调度能力（默认配置）
     */
    public InMemorySchedulingCapability() {
        this(DEFAULT_POOL_SIZE);
    }

    /**
     * 创建内存调度能力
     * 
     * @param poolSize 线程池大小
     */
    public InMemorySchedulingCapability(int poolSize) {
        this.scheduler = Executors.newScheduledThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "inmemory-scheduler");
            t.setDaemon(true);
            return t;
        });
        log.info("内存调度能力已创建: poolSize={}", poolSize);
    }

    /**
     * {@inheritDoc}
     * 
     * <p>注意：内存实现的 Cron 支持有限，仅模拟基本周期调度。
     * 生产环境建议使用 Quartz 适配器。</p>
     */
    @Override
    public ScheduledTaskHandle scheduleWithCron(String taskId, String cronExpression, Runnable task) {
        Objects.requireNonNull(taskId, "任务 ID 不能为空");
        Objects.requireNonNull(cronExpression, "Cron 表达式不能为空");
        Objects.requireNonNull(task, "任务不能为空");

        // 简易 Cron 解析：仅支持固定间隔模拟
        // 真实 Cron 解析需要专门的库（如 cron-utils）
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
        
        log.debug("调度 Cron 任务: taskId={}, cron={}, interval={}", taskId, cronExpression, interval);
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
        Objects.requireNonNull(taskId, "任务 ID 不能为空");
        Objects.requireNonNull(initialDelay, "初始延迟不能为空");
        Objects.requireNonNull(period, "执行间隔不能为空");
        Objects.requireNonNull(task, "任务不能为空");

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
        
        log.debug("调度固定频率任务: taskId={}, initialDelay={}, period={}", taskId, initialDelay, period);
        return new InMemoryTaskHandle(holder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ScheduledTaskHandle scheduleWithFixedDelay(String taskId, Duration delay, Runnable task) {
        Objects.requireNonNull(taskId, "任务 ID 不能为空");
        Objects.requireNonNull(delay, "执行间隔不能为空");
        Objects.requireNonNull(task, "任务不能为空");

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
        
        log.debug("调度固定延迟任务: taskId={}, delay={}", taskId, delay);
        return new InMemoryTaskHandle(holder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ScheduledTaskHandle scheduleOnce(String taskId, Duration delay, Runnable task) {
        Objects.requireNonNull(taskId, "任务 ID 不能为空");
        Objects.requireNonNull(delay, "延迟时间不能为空");
        Objects.requireNonNull(task, "任务不能为空");

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
        
        log.debug("调度一次性任务: taskId={}, delay={}", taskId, delay);
        return new InMemoryTaskHandle(holder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean cancel(String taskId) {
        Objects.requireNonNull(taskId, "任务 ID 不能为空");

        TaskHolder holder = tasks.remove(taskId);
        if (holder == null) {
            log.debug("任务不存在: taskId={}", taskId);
            return false;
        }

        boolean cancelled = holder.cancel(false);
        log.debug("取消任务: taskId={}, result={}", taskId, cancelled);
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
     * 获取活跃任务数量
     * 
     * @return 任务数量
     */
    public int getActiveTaskCount() {
        return (int) tasks.values().stream()
                .filter(h -> !h.isCancelled() && !h.isDone())
                .count();
    }

    /**
     * 简易 Cron 解析（仅用于演示）
     * 
     * <p>仅支持简单的间隔模式，真实场景应使用专业的 Cron 库</p>
     */
    private Duration parseCronToInterval(String cronExpression) {
        // 简化实现：默认 1 分钟间隔
        // 真实实现应解析 Cron 表达式
        if (cronExpression.contains("* * * * *")) {
            return Duration.ofMinutes(1);
        } else if (cronExpression.contains("0 * * * *")) {
            return Duration.ofHours(1);
        } else if (cronExpression.contains("0 0 * * *")) {
            return Duration.ofDays(1);
        }
        // 默认 1 分钟
        return Duration.ofMinutes(1);
    }

    /**
     * 执行任务
     */
    private void executeTask(TaskHolder holder, Runnable task) {
        holder.markRunning();
        holder.setLastExecutionTime(Instant.now());
        try {
            task.run();
            holder.incrementExecutionCount();
            log.trace("任务执行完成: taskId={}", holder.getTaskId());
        } catch (Exception e) {
            log.error("任务执行异常: taskId={}", holder.getTaskId(), e);
        } finally {
            holder.markNotRunning();
            // 更新下次执行时间
            if (holder.getPeriod() != null) {
                holder.setNextExecutionTime(Instant.now().plus(holder.getPeriod()));
            } else {
                holder.setNextExecutionTime(null);
            }
        }
    }

    @Override
    public void close() {
        log.info("关闭内存调度能力...");
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
     * 任务持有者（内部类）
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
     * 内存任务句柄实现
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
