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
 * Dynamic Route Service
 * 
 * <p>Manages API Gateway dynamic routes, supports runtime registration, removal and refresh.</p>
 * 
 * <h3>Core Features</h3>
 * <ul>
 *   <li>Register routes: Receives module route definitions and registers to gateway</li>
 *   <li>Remove routes: Removes corresponding routes when module stops</li>
 *   <li>Refresh routes: Triggers Spring Cloud Gateway to reload routes</li>
 * </ul>
 * 
 * <h3>Relationship with EventDrivenRouteRefresher</h3>
 * <p>EventDrivenRouteRefresher handles event listening, DynamicRouteService handles actual route operations.</p>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
@Service
public class DynamicRouteService {

    private static final Logger log = LoggerFactory.getLogger(DynamicRouteService.class);

    /**
     * Spring Cloud Gateway route definition writer
     */
    private final RouteDefinitionWriter routeDefinitionWriter;

    /**
     * Event publisher (for triggering route refresh)
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Module route mapping table
     * 
     * <p>Records list of route IDs registered by each module for batch removal when module stops.</p>
     */
    private final Map<String, List<String>> moduleRoutes = new ConcurrentHashMap<>();

    /**
     * Constructor
     * 
     * @param routeDefinitionWriter route definition writer
     * @param eventPublisher        event publisher
     */
    public DynamicRouteService(RouteDefinitionWriter routeDefinitionWriter,
                               ApplicationEventPublisher eventPublisher) {
        this.routeDefinitionWriter = routeDefinitionWriter;
        this.eventPublisher = eventPublisher;
        
        log.info("DynamicRouteService initialization complete");
    }

    /**
     * Register module routes
     * 
     * <p>Registers module-defined routes to gateway and records module-route mapping.</p>
     * 
     * @param moduleId module ID
     * @param routes   route definition list
     */
    public void registerRoutes(String moduleId, List<RouteDefinition> routes) {
        if (routes == null || routes.isEmpty()) {
            log.debug("Module {} has no defined routes", moduleId);
            return;
        }

        log.info("Registering {} routes for module {}", routes.size(), moduleId);

        List<String> routeIds = new ArrayList<>();

        for (RouteDefinition route : routes) {
            try {
                // Convert to Spring Cloud Gateway route definition
                org.springframework.cloud.gateway.route.RouteDefinition gatewayRoute = 
                        convertToGatewayRoute(route);
                
                // Save route
                routeDefinitionWriter.save(Mono.just(gatewayRoute)).subscribe();
                routeIds.add(route.getId());
                
                log.debug("Route registration successful: id={}, uri={}, predicates={}", 
                        route.getId(), route.getUri(), route.getPredicates());
                
            } catch (Exception e) {
                log.error("Route registration failed: moduleId={}, routeId={}", moduleId, route.getId(), e);
            }
        }

        // Record module-route mapping
        moduleRoutes.put(moduleId, routeIds);

        // Trigger route refresh
        refreshRoutes();

        log.info("Module {} route registration complete, total {} routes", moduleId, routeIds.size());
    }

    /**
     * Remove all routes for a module
     * 
     * @param moduleId module ID
     */
    public void removeRoutes(String moduleId) {
        List<String> routeIds = moduleRoutes.remove(moduleId);
        
        if (routeIds == null || routeIds.isEmpty()) {
            log.debug("Module {} has no registered routes", moduleId);
            return;
        }

        log.info("Removing {} routes for module {}", routeIds.size(), moduleId);

        for (String routeId : routeIds) {
            try {
                routeDefinitionWriter.delete(Mono.just(routeId)).subscribe();
                log.debug("Route removal successful: id={}", routeId);
            } catch (Exception e) {
                log.error("Route removal failed: moduleId={}, routeId={}", moduleId, routeId, e);
            }
        }

        // Trigger route refresh
        refreshRoutes();

        log.info("Module {} route removal complete", moduleId);
    }

    /**
     * Refresh all routes
     * 
     * <p>Triggers Spring Cloud Gateway to reload route configuration.</p>
     */
    public void refreshRoutes() {
        log.debug("Triggering route refresh");
        eventPublisher.publishEvent(new RefreshRoutesEvent(this));
    }

    /**
     * Get route ID list registered by a module
     * 
     * @param moduleId module ID
     * @return route ID list, empty list if module has no registered routes
     */
    public List<String> getRoutesByModule(String moduleId) {
        return moduleRoutes.getOrDefault(moduleId, List.of());
    }

    /**
     * Get all registered module IDs
     * 
     * @return module ID list
     */
    public List<String> getRegisteredModules() {
        return new ArrayList<>(moduleRoutes.keySet());
    }

    /**
     * Convert to Spring Cloud Gateway route definition
     * 
     * @param route custom route definition
     * @return Spring Cloud Gateway route definition
     */
    private org.springframework.cloud.gateway.route.RouteDefinition convertToGatewayRoute(
            RouteDefinition route) {
        
        org.springframework.cloud.gateway.route.RouteDefinition gatewayRoute = 
                new org.springframework.cloud.gateway.route.RouteDefinition();
        
        gatewayRoute.setId(route.getId());
        gatewayRoute.setUri(URI.create(route.getUri()));
        gatewayRoute.setOrder(route.getOrder());
        
        // convertbreaklanguage
        if (route.getPredicates() != null) {
            List<org.springframework.cloud.gateway.handler.predicate.PredicateDefinition> predicates = 
                    new ArrayList<>();
            for (String predicate : route.getPredicates()) {
                predicates.add(new org.springframework.cloud.gateway.handler.predicate.PredicateDefinition(predicate));
            }
            gatewayRoute.setPredicates(predicates);
        }
        
        // convertfilter
        if (route.getFilters() != null) {
            List<org.springframework.cloud.gateway.filter.FilterDefinition> filters = 
                    new ArrayList<>();
            for (String filter : route.getFilters()) {
                filters.add(new org.springframework.cloud.gateway.filter.FilterDefinition(filter));
            }
            gatewayRoute.setFilters(filters);
        }
        
        // setelementcountdata
        if (route.getMetadata() != null) {
            gatewayRoute.setMetadata(route.getMetadata());
        }
        
        return gatewayRoute;
    }
}
