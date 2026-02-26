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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态路由服务
 * 
 * <p>管理 API 网关的动态路由，支持运行时注册、移除和刷新路由。</p>
 * 
 * <h3>核心功能</h3>
 * <ul>
 *   <li>注册路由：接收模块的路由定义并注册到网关</li>
 *   <li>移除路由：模块停止时移除对应的路由</li>
 *   <li>刷新路由：触发 Spring Cloud Gateway 重新加载路由</li>
 * </ul>
 * 
 * <h3>与 EventDrivenRouteRefresher 的关系</h3>
 * <p>EventDrivenRouteRefresher 负责监听事件，DynamicRouteService 负责实际的路由操作。</p>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
@Service
public class DynamicRouteService {

    private static final Logger log = LoggerFactory.getLogger(DynamicRouteService.class);

    /**
     * Spring Cloud Gateway 路由定义写入器
     */
    private final RouteDefinitionWriter routeDefinitionWriter;

    /**
     * 事件发布器（用于触发路由刷新）
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 模块路由映射表
     * 
     * <p>记录每个模块注册的路由 ID 列表，用于模块停止时批量移除。</p>
     */
    private final Map<String, List<String>> moduleRoutes = new ConcurrentHashMap<>();

    /**
     * 构造函数
     * 
     * @param routeDefinitionWriter 路由定义写入器
     * @param eventPublisher        事件发布器
     */
    public DynamicRouteService(RouteDefinitionWriter routeDefinitionWriter,
                               ApplicationEventPublisher eventPublisher) {
        this.routeDefinitionWriter = routeDefinitionWriter;
        this.eventPublisher = eventPublisher;
        
        log.info("DynamicRouteService 初始化完成");
    }

    /**
     * 注册模块的路由
     * 
     * <p>将模块定义的路由注册到网关，并记录模块-路由的映射关系。</p>
     * 
     * @param moduleId 模块 ID
     * @param routes   路由定义列表
     */
    public void registerRoutes(String moduleId, List<RouteDefinition> routes) {
        if (routes == null || routes.isEmpty()) {
            log.debug("模块 {} 没有定义路由", moduleId);
            return;
        }

        log.info("为模块 {} 注册 {} 条路由", moduleId, routes.size());

        List<String> routeIds = new ArrayList<>();

        for (RouteDefinition route : routes) {
            try {
                // 转换为 Spring Cloud Gateway 的路由定义
                org.springframework.cloud.gateway.route.RouteDefinition gatewayRoute = 
                        convertToGatewayRoute(route);
                
                // 保存路由
                routeDefinitionWriter.save(Mono.just(gatewayRoute)).subscribe();
                routeIds.add(route.getId());
                
                log.debug("路由注册成功: id={}, uri={}, predicates={}", 
                        route.getId(), route.getUri(), route.getPredicates());
                
            } catch (Exception e) {
                log.error("路由注册失败: moduleId={}, routeId={}", moduleId, route.getId(), e);
            }
        }

        // 记录模块-路由映射
        moduleRoutes.put(moduleId, routeIds);

        // 触发路由刷新
        refreshRoutes();

        log.info("模块 {} 路由注册完成，共 {} 条", moduleId, routeIds.size());
    }

    /**
     * 移除模块的所有路由
     * 
     * @param moduleId 模块 ID
     */
    public void removeRoutes(String moduleId) {
        List<String> routeIds = moduleRoutes.remove(moduleId);
        
        if (routeIds == null || routeIds.isEmpty()) {
            log.debug("模块 {} 没有已注册的路由", moduleId);
            return;
        }

        log.info("移除模块 {} 的 {} 条路由", moduleId, routeIds.size());

        for (String routeId : routeIds) {
            try {
                routeDefinitionWriter.delete(Mono.just(routeId)).subscribe();
                log.debug("路由移除成功: id={}", routeId);
            } catch (Exception e) {
                log.error("路由移除失败: moduleId={}, routeId={}", moduleId, routeId, e);
            }
        }

        // 触发路由刷新
        refreshRoutes();

        log.info("模块 {} 路由移除完成", moduleId);
    }

    /**
     * 刷新所有路由
     * 
     * <p>触发 Spring Cloud Gateway 重新加载路由配置。</p>
     */
    public void refreshRoutes() {
        log.debug("触发路由刷新");
        eventPublisher.publishEvent(new RefreshRoutesEvent(this));
    }

    /**
     * 获取模块注册的路由 ID 列表
     * 
     * @param moduleId 模块 ID
     * @return 路由 ID 列表，如果模块没有注册路由返回空列表
     */
    public List<String> getRoutesByModule(String moduleId) {
        return moduleRoutes.getOrDefault(moduleId, List.of());
    }

    /**
     * 获取所有已注册的模块 ID
     * 
     * @return 模块 ID 列表
     */
    public List<String> getRegisteredModules() {
        return new ArrayList<>(moduleRoutes.keySet());
    }

    /**
     * 转换为 Spring Cloud Gateway 的路由定义
     * 
     * @param route 自定义路由定义
     * @return Spring Cloud Gateway 路由定义
     */
    private org.springframework.cloud.gateway.route.RouteDefinition convertToGatewayRoute(
            RouteDefinition route) {
        
        org.springframework.cloud.gateway.route.RouteDefinition gatewayRoute = 
                new org.springframework.cloud.gateway.route.RouteDefinition();
        
        gatewayRoute.setId(route.getId());
        gatewayRoute.setUri(URI.create(route.getUri()));
        gatewayRoute.setOrder(route.getOrder());
        
        // 转换断言
        if (route.getPredicates() != null) {
            List<org.springframework.cloud.gateway.handler.predicate.PredicateDefinition> predicates = 
                    new ArrayList<>();
            for (String predicate : route.getPredicates()) {
                predicates.add(new org.springframework.cloud.gateway.handler.predicate.PredicateDefinition(predicate));
            }
            gatewayRoute.setPredicates(predicates);
        }
        
        // 转换过滤器
        if (route.getFilters() != null) {
            List<org.springframework.cloud.gateway.filter.FilterDefinition> filters = 
                    new ArrayList<>();
            for (String filter : route.getFilters()) {
                filters.add(new org.springframework.cloud.gateway.filter.FilterDefinition(filter));
            }
            gatewayRoute.setFilters(filters);
        }
        
        // 设置元数据
        if (route.getMetadata() != null) {
            gatewayRoute.setMetadata(route.getMetadata());
        }
        
        return gatewayRoute;
    }
}
