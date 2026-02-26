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

/**
 * 熔断器状态枚举
 * 
 * <p>定义熔断器的三种状态，基于状态机模式实现故障隔离。</p>
 * 
 * <h3>状态转换规则</h3>
 * <pre>{@code
 *                 失败率超阈值
 * CLOSED ────────────────────> OPEN
 *   ^                            |
 *   |                            | 等待时间到
 *   |     成功率恢复              v
 *   └──────────────────── HALF_OPEN
 *                            |
 *                            | 失败
 *                            v
 *                          OPEN
 * }</pre>
 * 
 * <h3>状态说明</h3>
 * <table border="1">
 *   <tr><th>状态</th><th>说明</th><th>请求处理</th></tr>
 *   <tr><td>CLOSED</td><td>正常状态</td><td>所有请求正常通过</td></tr>
 *   <tr><td>OPEN</td><td>熔断状态</td><td>请求直接拒绝</td></tr>
 *   <tr><td>HALF_OPEN</td><td>半开状态</td><td>允许部分请求通过，用于探测恢复</td></tr>
 * </table>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see ResilienceCapability#getCircuitBreakerState(String)
 */
public enum CircuitBreakerState {

    /**
     * 关闭状态（正常）
     * 
     * <p>熔断器关闭，所有请求正常通过。
     * 当失败率超过阈值时，转换为 OPEN 状态。</p>
     */
    CLOSED("关闭（正常）"),

    /**
     * 打开状态（熔断）
     * 
     * <p>熔断器打开，所有请求直接拒绝。
     * 等待配置的时间后，转换为 HALF_OPEN 状态。</p>
     */
    OPEN("打开（熔断）"),

    /**
     * 半开状态（恢复中）
     * 
     * <p>熔断器半开，允许配置数量的请求通过用于探测。
     * 如果探测请求成功率高，转换为 CLOSED；否则转回 OPEN。</p>
     */
    HALF_OPEN("半开（恢复中）"),

    /**
     * 禁用状态
     * 
     * <p>熔断器被禁用，不进行任何熔断处理。</p>
     */
    DISABLED("禁用"),

    /**
     * 强制打开状态
     * 
     * <p>手动强制打开，用于维护或测试。</p>
     */
    FORCED_OPEN("强制打开");

    /**
     * 状态描述
     */
    private final String description;

    CircuitBreakerState(String description) {
        this.description = description;
    }

    /**
     * 获取状态描述
     * 
     * @return 状态描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 判断是否允许请求通过
     * 
     * @return 如果允许请求通过返回 true
     */
    public boolean isCallPermitted() {
        return this == CLOSED || this == HALF_OPEN || this == DISABLED;
    }
}
