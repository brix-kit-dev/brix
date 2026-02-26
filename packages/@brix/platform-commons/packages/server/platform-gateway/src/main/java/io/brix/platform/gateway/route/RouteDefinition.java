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

import java.util.List;
import java.util.Map;

/**
 * 路由定义
 * 
 * <p>定义单个 API 路由的配置信息，包括路径匹配、目标服务、过滤器等。</p>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * RouteDefinition route = new RouteDefinition();
 * route.setId("booking-api");
 * route.setUri("lb://shinwa-service-booking");
 * route.setPredicates(List.of("Path=/api/booking/**"));
 * route.setFilters(List.of("StripPrefix=1"));
 * }</pre>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
public class RouteDefinition {

    /**
     * 路由唯一标识
     */
    private String id;

    /**
     * 目标服务 URI
     * 
     * <p>支持格式：</p>
     * <ul>
     *   <li>lb://service-name - 负载均衡到服务</li>
     *   <li>http://host:port - 直接转发</li>
     * </ul>
     */
    private String uri;

    /**
     * 路由顺序（数字越小优先级越高）
     */
    private int order = 0;

    /**
     * 断言列表（路径匹配规则）
     * 
     * <p>示例：Path=/api/booking/**, Method=GET</p>
     */
    private List<String> predicates;

    /**
     * 过滤器列表
     * 
     * <p>示例：StripPrefix=1, AddRequestHeader=X-Request-Source, Gateway</p>
     */
    private List<String> filters;

    /**
     * 路由元数据
     */
    private Map<String, Object> metadata;

    /**
     * 所属模块 ID
     */
    private String moduleId;

    // ==================== Getter / Setter ====================

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public List<String> getPredicates() {
        return predicates;
    }

    public void setPredicates(List<String> predicates) {
        this.predicates = predicates;
    }

    public List<String> getFilters() {
        return filters;
    }

    public void setFilters(List<String> filters) {
        this.filters = filters;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getModuleId() {
        return moduleId;
    }

    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }

    @Override
    public String toString() {
        return String.format("RouteDefinition{id='%s', uri='%s', predicates=%s, moduleId='%s'}",
                id, uri, predicates, moduleId);
    }
}
