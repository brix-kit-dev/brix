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

/**
 * 定时任务能力契约（可选能力）
 * 
 * <p>提供定时任务调度的统一抽象，支持 Cron 表达式和固定频率调度。
 * 模块通过此接口注册定时任务，无需直接使用 Quartz 等框架。</p>
 * 
 * <h3>核心功能</h3>
 * <ul>
 *   <li><b>Cron 调度</b>：基于 Cron 表达式的灵活调度</li>
 *   <li><b>固定频率</b>：按固定间隔执行</li>
 *   <li><b>固定延迟</b>：上次执行完成后延迟执行</li>
 *   <li><b>一次性任务</b>：延迟指定时间后执行一次</li>
 * </ul>
 * 
 * <h3>分布式调度</h3>
 * <p>在分布式环境中，定时任务默认只在一个节点执行。
 * 通过结合 {@link LockCapability} 实现分布式锁，确保任务不重复执行。</p>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Inject
 * private SchedulingCapability scheduling;
 * 
 * public void setupScheduledTasks() {
 *     // Cron 调度：每天凌晨 2 点执行
 *     scheduling.scheduleWithCron("daily-cleanup", "0 0 2 * * ?", 
 *         () -> cleanupExpiredData());
 *     
 *     // 固定频率：每 5 分钟执行一次
 *     scheduling.scheduleAtFixedRate("health-check", Duration.ofMinutes(5),
 *         () -> performHealthCheck());
 *     
 *     // 一次性任务：30 分钟后执行
 *     scheduling.scheduleOnce("delayed-task", Duration.ofMinutes(30),
 *         () -> sendReminder());
 * }
 * }</pre>
 * 
 * <h3>注意事项</h3>
 * <ul>
 *   <li>任务应该是幂等的，以处理重复执行情况</li>
 *   <li>长时间运行的任务应考虑超时处理</li>
 *   <li>任务中的异常应被捕获，避免影响调度器</li>
 *   <li>此为可选能力，使用前应检查是否可用</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public interface SchedulingCapability {

    /**
     * 使用 Cron 表达式调度任务
     * 
     * <p>Cron 表达式格式：秒 分 时 日 月 周</p>
     * 
     * @param taskId         任务唯一标识
     * @param cronExpression Cron 表达式
     * @param task           要执行的任务
     * @return 任务句柄，用于取消任务
     */
    ScheduledTaskHandle scheduleWithCron(String taskId, String cronExpression, Runnable task);

    /**
     * 固定频率调度
     * 
     * <p>无论上次执行是否完成，都按固定间隔触发下次执行</p>
     * 
     * @param taskId       任务唯一标识
     * @param period       执行间隔
     * @param task         要执行的任务
     * @return 任务句柄
     */
    ScheduledTaskHandle scheduleAtFixedRate(String taskId, Duration period, Runnable task);

    /**
     * 固定频率调度（带初始延迟）
     * 
     * @param taskId       任务唯一标识
     * @param initialDelay 初始延迟
     * @param period       执行间隔
     * @param task         要执行的任务
     * @return 任务句柄
     */
    ScheduledTaskHandle scheduleAtFixedRate(String taskId, Duration initialDelay, Duration period, Runnable task);

    /**
     * 固定延迟调度
     * 
     * <p>上次执行完成后，等待指定延迟再触发下次执行</p>
     * 
     * @param taskId 任务唯一标识
     * @param delay  执行延迟
     * @param task   要执行的任务
     * @return 任务句柄
     */
    ScheduledTaskHandle scheduleWithFixedDelay(String taskId, Duration delay, Runnable task);

    /**
     * 一次性延迟任务
     * 
     * @param taskId 任务唯一标识
     * @param delay  延迟时间
     * @param task   要执行的任务
     * @return 任务句柄
     */
    ScheduledTaskHandle scheduleOnce(String taskId, Duration delay, Runnable task);

    /**
     * 取消任务
     * 
     * @param taskId 任务唯一标识
     * @return 如果任务存在并被取消返回 true
     */
    boolean cancel(String taskId);

    /**
     * 检查任务是否正在运行
     * 
     * @param taskId 任务唯一标识
     * @return 如果任务正在运行返回 true
     */
    boolean isRunning(String taskId);

    /**
     * 检查任务是否已调度
     * 
     * @param taskId 任务唯一标识
     * @return 如果任务已调度返回 true
     */
    boolean isScheduled(String taskId);
}
