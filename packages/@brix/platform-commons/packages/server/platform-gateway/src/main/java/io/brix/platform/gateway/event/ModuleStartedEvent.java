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
package io.brix.platform.gateway.event;

import io.brix.platform.gateway.route.RouteDefinition;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 模块启动事件
 * 
 * <p>当模块启动完成后，Runtime Orchestrator 发布此事件。
 * 网关监听此事件以注册模块定义的路由。</p>
 * 
 * <h3>事件来源</h3>
 * <p>由 Runtime Orchestrator 在模块 STARTED 状态时发布，
 * 通过 EventBusCapability 传递到网关。</p>
 * 
 * <h3>事件数据</h3>
 * <ul>
 *   <li>moduleId - 模块唯一标识</li>
 *   <li>moduleName - 模块名称（人类可读）</li>
 *   <li>routes - 模块声明的路由列表</li>
 *   <li>metadata - 模块元数据</li>
 *   <li>timestamp - 事件发生时间</li>
 * </ul>
 * 
 * <h3>与 Manifest 的关系</h3>
 * <p>对应 module-manifest.yaml 中 events.subscriptions 配置：</p>
 * <pre>{@code
 * events:
 *   subscriptions:
 *     - topic: "brix.module.lifecycle"
 *       event-type: "ModuleStartedEvent"
 * }</pre>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
public class ModuleStartedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 模块唯一标识
     */
    private final String moduleId;

    /**
     * 模块名称
     */
    private final String moduleName;

    /**
     * 模块版本
     */
    private final String moduleVersion;

    /**
     * 模块定义的路由列表
     */
    private final List<RouteDefinition> routes;

    /**
     * 模块元数据
     */
    private final Map<String, Object> metadata;

    /**
     * 事件发生时间
     */
    private final Instant timestamp;

    /**
     * 构造函数
     * 
     * @param source        事件源
     * @param moduleId      模块唯一标识
     * @param moduleName    模块名称
     * @param moduleVersion 模块版本
     * @param routes        模块定义的路由列表
     * @param metadata      模块元数据
     */
    public ModuleStartedEvent(Object source, 
                              String moduleId,
                              String moduleName,
                              String moduleVersion,
                              List<RouteDefinition> routes,
                              Map<String, Object> metadata) {
        super(source);
        this.moduleId = moduleId;
        this.moduleName = moduleName;
        this.moduleVersion = moduleVersion;
        this.routes = routes != null ? List.copyOf(routes) : Collections.emptyList();
        this.metadata = metadata != null ? Map.copyOf(metadata) : Collections.emptyMap();
        this.timestamp = Instant.now();
    }

    /**
     * 简化构造函数
     * 
     * @param source   事件源
     * @param moduleId 模块唯一标识
     * @param routes   模块定义的路由列表
     */
    public ModuleStartedEvent(Object source, String moduleId, List<RouteDefinition> routes) {
        this(source, moduleId, null, null, routes, null);
    }

    /**
     * 获取模块唯一标识
     * 
     * @return 模块 ID
     */
    public String getModuleId() {
        return moduleId;
    }

    /**
     * 获取模块名称
     * 
     * @return 模块名称
     */
    public String getModuleName() {
        return moduleName;
    }

    /**
     * 获取模块版本
     * 
     * @return 模块版本
     */
    public String getModuleVersion() {
        return moduleVersion;
    }

    /**
     * 获取模块定义的路由列表
     * 
     * @return 路由列表（不可变）
     */
    public List<RouteDefinition> getRoutes() {
        return routes;
    }

    /**
     * 获取模块元数据
     * 
     * @return 元数据（不可变）
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * 获取事件发生时间
     * 
     * @return 时间戳
     */
    public Instant getEventTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "ModuleStartedEvent{" +
                "moduleId='" + moduleId + '\'' +
                ", moduleName='" + moduleName + '\'' +
                ", moduleVersion='" + moduleVersion + '\'' +
                ", routeCount=" + routes.size() +
                ", timestamp=" + timestamp +
                '}';
    }
}
