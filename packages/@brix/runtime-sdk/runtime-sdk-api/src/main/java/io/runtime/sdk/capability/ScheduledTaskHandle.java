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
 * 定时任务句柄
 * 
 * <p>表示一个已调度的定时任务，可用于查询状态或取消任务。</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see SchedulingCapability
 */
public interface ScheduledTaskHandle {

    /**
     * 获取任务 ID
     * 
     * @return 任务唯一标识
     */
    String getTaskId();

    /**
     * 取消任务
     * 
     * @param mayInterruptIfRunning 如果任务正在运行，是否中断
     * @return 如果任务被成功取消返回 true
     */
    boolean cancel(boolean mayInterruptIfRunning);

    /**
     * 检查任务是否已取消
     * 
     * @return 如果任务已取消返回 true
     */
    boolean isCancelled();

    /**
     * 检查任务是否已完成（仅对一次性任务有意义）
     * 
     * @return 如果任务已完成返回 true
     */
    boolean isDone();

    /**
     * 获取下次执行时间
     * 
     * @return 下次执行时间，如果任务已取消或完成返回 null
     */
    Instant getNextExecutionTime();

    /**
     * 获取上次执行时间
     * 
     * @return 上次执行时间，如果从未执行返回 null
     */
    Instant getLastExecutionTime();

    /**
     * 获取执行次数
     * 
     * @return 已执行次数
     */
    long getExecutionCount();
}
