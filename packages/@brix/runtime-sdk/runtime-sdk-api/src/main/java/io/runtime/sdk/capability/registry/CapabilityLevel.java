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
package io.runtime.sdk.capability.registry;

/**
 * 能力级别枚举
 * 
 * <p>定义能力的分级，用于配置验证和能力组装。</p>
 * 
 * <table border="1">
 *   <tr><th>级别</th><th>说明</th><th>必要性</th></tr>
 *   <tr><td>CORE</td><td>核心能力</td><td>所有 Host 必须实现</td></tr>
 *   <tr><td>STANDARD</td><td>标准能力</td><td>推荐实现</td></tr>
 *   <tr><td>EXTENDED</td><td>扩展能力</td><td>按需实现</td></tr>
 *   <tr><td>EXPERIMENTAL</td><td>实验能力</td><td>不稳定，可能变更</td></tr>
 * </table>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public enum CapabilityLevel {

    /**
     * 核心能力
     * 
     * <p>所有 Host 必须实现的能力，如：</p>
     * <ul>
     *   <li>EventBusCapability - 事件总线</li>
     *   <li>StateStoreCapability - 状态存储</li>
     *   <li>AuthContextCapability - 认证上下文</li>
     *   <li>ObservabilityCapability - 可观测性</li>
     *   <li>ConfigStoreCapability - 配置存储</li>
     *   <li>LifecycleCapability - 生命周期</li>
     * </ul>
     */
    CORE(1, "核心能力", true),

    /**
     * 标准能力
     * 
     * <p>推荐实现的能力，Full Product Host 应该提供：</p>
     * <ul>
     *   <li>SchedulingCapability - 定时任务</li>
     *   <li>LockCapability - 分布式锁</li>
     * </ul>
     */
    STANDARD(2, "标准能力", false),

    /**
     * 扩展能力
     * 
     * <p>按需实现的能力，针对特定场景：</p>
     * <ul>
     *   <li>ResilienceCapability - 韧性能力</li>
     *   <li>IdGeneratorCapability - ID 生成</li>
     *   <li>DataAccessCapability - 数据访问授权</li>
     * </ul>
     */
    EXTENDED(3, "扩展能力", false),

    /**
     * 实验能力
     * 
     * <p>处于实验阶段的能力，API 可能变更。</p>
     */
    EXPERIMENTAL(4, "实验能力", false),

    /**
     * 回退能力
     * 
     * <p>当没有更高优先级的实现可用时启用的后备实现。
     * 通常是零依赖的简单实现，用于开发或测试场景。</p>
     * 
     * @since 3.2.0
     */
    FALLBACK(5, "回退能力", false);

    private final int order;
    private final String displayName;
    private final boolean requiredForAllHosts;

    CapabilityLevel(int order, String displayName, boolean requiredForAllHosts) {
        this.order = order;
        this.displayName = displayName;
        this.requiredForAllHosts = requiredForAllHosts;
    }

    /**
     * 获取排序顺序
     * 
     * @return 排序顺序
     */
    public int getOrder() {
        return order;
    }

    /**
     * 获取显示名称
     * 
     * @return 显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 是否为所有 Host 必需
     * 
     * @return 是否必需
     */
    public boolean isRequiredForAllHosts() {
        return requiredForAllHosts;
    }
}
