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
 * Route Definition
 * 
 * <p>Defines configuration for a single API route, including path matching, target service, filters, etc.</p>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * RouteDefinition route = new RouteDefinition();
 * route.setId("booking-api");
 * route.setUri("lb://brix-service-booking");
 * route.setPredicates(List.of("Path=/api/booking/**"));
 * route.setFilters(List.of("StripPrefix=1"));
 * }</pre>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
public class RouteDefinition {

    /**
     * Route unique identifier
     */
    private String id;

    /**
     * Target service URI
     * 
     * <p>Supported formats:</p>
     * <ul>
     *   <li>lb://service-name - Load balance to service</li>
     *   <li>http://host:port - Direct forwarding</li>
     * </ul>
     */
    private String uri;

    /**
     * Route order (lower number = higher priority)
     */
    private int order = 0;

    /**
     * Predicate list (path matching rules)
     * 
     * <p>Example: Path=/api/booking/**, Method=GET</p>
     */
    private List<String> predicates;

    /**
     * Filter list
     * 
     * <p>Example: StripPrefix=1, AddRequestHeader=X-Request-Source, Gateway</p>
     */
    private List<String> filters;

    /**
     * Route metadata
     */
    private Map<String, Object> metadata;

    /**
     * Owning module ID
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
