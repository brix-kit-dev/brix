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
package io.brix.platform.gateway.route;

import io.brix.platform.gateway.event.ModuleStartedEvent;
import io.brix.platform.gateway.event.ModuleStoppedEvent;
import io.brix.platform.gateway.event.RouteRefreshRequestedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 事件驱动路由刷新器
 * 
 * <p>v3.0 架构改造的核心组件，替代原有的 RedisRouteRefreshSubscriber。
 * 通过订阅 EventBus 事件实现动态路由刷新，不直接依赖 Redis。</p>
 * 
 * <h3>事件订阅</h3>
 * <ul>
 *   <li>{@link ModuleStartedEvent} - 模块启动时注册路由</li>
 *   <li>{@link ModuleStoppedEvent} - 模块停止时移除路由</li>
 *   <li>{@link RouteRefreshRequestedEvent} - 强制刷新所有路由</li>
 * </ul>
 * 
 * <h3>与改造清单的对应</h3>
 * <p>对应《v3.0-代码改造清单.md》中 3.4 节"网关改造方案"的实现：</p>
 * <ul>
 *   <li>路由刷新从 Redis Pub/Sub 改为 EventBusCapability 订阅</li>
 *   <li>订阅关系在 module-manifest.yaml 中声明</li>
 * </ul>
 * 
 * <h3>使用说明</h3>
 * <p>本组件通过 Spring Event 机制接收事件。实际生产环境中，
 * EventBusCapability 的实现（如 KafkaEventBusCapability）会将
 * Kafka 消息转换为 Spring ApplicationEvent 发布。</p>
 * 
 * @author Brix Team
 * @since 3.0.0
 * @see DynamicRouteService
 */
@Component
public class EventDrivenRouteRefresher {

    private static final Logger log = LoggerFactory.getLogger(EventDrivenRouteRefresher.class);

    /**
     * 动态路由服务
     */
    private final DynamicRouteService routeService;

    /**
     * 构造函数
     * 
     * @param routeService 动态路由服务
     */
    public EventDrivenRouteRefresher(DynamicRouteService routeService) {
        this.routeService = routeService;
        log.info("EventDrivenRouteRefresher 初始化完成（v3.0 事件驱动模式）");
    }

    /**
     * 处理模块启动事件 —— 注册路由
     * 
     * <p>当模块启动完成后，Runtime Orchestrator 会发布 ModuleStartedEvent。
     * 网关监听此事件，解析模块定义的路由并注册。</p>
     * 
     * <h4>事件处理流程</h4>
     * <ol>
     *   <li>从事件中获取模块 ID 和路由定义</li>
     *   <li>调用 DynamicRouteService 注册路由</li>
     *   <li>触发 Spring Cloud Gateway 路由刷新</li>
     * </ol>
     * 
     * @param event 模块启动事件
     */
    @EventListener
    public void onModuleStarted(ModuleStartedEvent event) {
        String moduleId = event.getModuleId();
        List<RouteDefinition> routes = event.getRoutes();

        log.info("收到模块启动事件，准备注册路由: moduleId={}, routeCount={}", 
                moduleId, routes != null ? routes.size() : 0);

        try {
            // 注册路由
            routeService.registerRoutes(moduleId, routes);
            
            log.info("模块 {} 路由注册成功", moduleId);
            
        } catch (Exception e) {
            // 路由注册失败不应阻塞模块启动，记录错误继续
            log.error("模块 {} 路由注册失败: {}", moduleId, e.getMessage(), e);
        }
    }

    /**
     * 处理模块停止事件 —— 移除路由
     * 
     * <p>当模块停止时，Runtime Orchestrator 会发布 ModuleStoppedEvent。
     * 网关监听此事件，移除该模块注册的所有路由。</p>
     * 
     * @param event 模块停止事件
     */
    @EventListener
    public void onModuleStopped(ModuleStoppedEvent event) {
        String moduleId = event.getModuleId();

        log.info("收到模块停止事件，准备移除路由: moduleId={}", moduleId);

        try {
            // 移除路由
            routeService.removeRoutes(moduleId);
            
            log.info("模块 {} 路由移除成功", moduleId);
            
        } catch (Exception e) {
            log.error("模块 {} 路由移除失败: {}", moduleId, e.getMessage(), e);
        }
    }

    /**
     * 处理路由刷新请求事件 —— 强制刷新所有路由
     * 
     * <p>运维人员可以通过发布此事件强制刷新所有路由，用于：</p>
     * <ul>
     *   <li>路由配置变更后手动刷新</li>
     *   <li>排查路由问题时重新加载</li>
     *   <li>灾难恢复场景</li>
     * </ul>
     * 
     * @param event 路由刷新请求事件
     */
    @EventListener
    public void onRouteRefreshRequested(RouteRefreshRequestedEvent event) {
        log.info("收到路由刷新请求: reason={}", event.getReason());

        try {
            // 触发路由刷新
            routeService.refreshRoutes();
            
            log.info("路由刷新完成");
            
        } catch (Exception e) {
            log.error("路由刷新失败: {}", e.getMessage(), e);
        }
    }
}
