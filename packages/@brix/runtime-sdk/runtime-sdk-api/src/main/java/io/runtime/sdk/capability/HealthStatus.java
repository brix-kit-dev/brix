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
 * 模块健康状态枚举
 * 
 * <p>定义模块的三种健康状态，用于健康检查和监控。</p>
 * 
 * <h3>状态说明</h3>
 * <ul>
 *   <li><b>UP</b>：模块完全正常，所有功能可用</li>
 *   <li><b>DEGRADED</b>：模块部分功能受限，但核心功能可用</li>
 *   <li><b>DOWN</b>：模块不可用，需要关注或重启</li>
 * </ul>
 * 
 * <h3>状态转换</h3>
 * <pre>{@code
 * UP <-> DEGRADED <-> DOWN
 *  \__________|________/
 * }</pre>
 * 
 * <h3>处理策略</h3>
 * <table border="1">
 *   <tr><th>状态</th><th>流量处理</th><th>告警级别</th></tr>
 *   <tr><td>UP</td><td>正常接收</td><td>无</td></tr>
 *   <tr><td>DEGRADED</td><td>限流/降级</td><td>WARN</td></tr>
 *   <tr><td>DOWN</td><td>拒绝/转移</td><td>ERROR</td></tr>
 * </table>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see LifecycleCapability#healthCheck()
 */
public enum HealthStatus {

    /**
     * 健康状态 - 模块完全正常
     */
    UP("模块正常运行"),

    /**
     * 降级状态 - 部分功能受限
     * 
     * <p>典型场景：</p>
     * <ul>
     *   <li>外部依赖响应慢</li>
     *   <li>缓存不可用，降级为数据库直查</li>
     *   <li>非核心功能异常</li>
     * </ul>
     */
    DEGRADED("模块降级运行"),

    /**
     * 不可用状态 - 模块无法提供服务
     * 
     * <p>典型场景：</p>
     * <ul>
     *   <li>数据库连接断开</li>
     *   <li>关键配置缺失</li>
     *   <li>初始化失败</li>
     * </ul>
     */
    DOWN("模块不可用");

    /**
     * 状态描述
     */
    private final String description;

    HealthStatus(String description) {
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
     * 判断是否健康（UP 或 DEGRADED）
     * 
     * @return 如果可以提供服务返回 true
     */
    public boolean isHealthy() {
        return this == UP || this == DEGRADED;
    }

    /**
     * 判断是否完全健康
     * 
     * @return 如果状态为 UP 返回 true
     */
    public boolean isFullyHealthy() {
        return this == UP;
    }
}
