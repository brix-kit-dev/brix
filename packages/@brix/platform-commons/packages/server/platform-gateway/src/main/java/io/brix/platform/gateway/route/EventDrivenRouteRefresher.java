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
 * Event-Driven Route Refresher
 * 
 * <p>v3.0 architecturerefactoringofcorecomponent，replacegenerationoriginalhasof RedisRouteRefreshSubscriber。
 * viasubscribe EventBus eventimplementationdynamicrouterefresh，notdirectlydepend on Redis。</p>
 * 
 * <h3>eventsubscribe</h3>
 * <ul>
 *   <li>{@link ModuleStartedEvent} - modulestartmovetimeregisterroute</li>
 *   <li>{@link ModuleStoppedEvent} - modulestoptimeremoveroute</li>
 *   <li>{@link RouteRefreshRequestedEvent} - forcerefreshallhasroute</li>
 * </ul>
 * 
 * <h3>withrefactoringclearsingleofforshould</h3>
 * <p>forshould《v3.0-generationcoderefactoringclearsingle.md》in 3.4 section"Gatewayrefactoringwayscheme"ofimplementation：</p>
 * <ul>
 *   <li>routerefreshfrom Redis Pub/Sub changeis EventBusCapability subscribe</li>
 *   <li>subscriberelationshipon module-manifest.yaml indeclare</li>
 * </ul>
 * 
 * <h3>usedescription</h3>
 * <p>thiscomponentvia Spring Event mechanismreceiveevent。actualproductionenvironmentin，
 * EventBusCapability ofimplementation（like KafkaEventBusCapability）willwill
 * Kafka messageconvertis Spring ApplicationEvent publish。</p>
 * 
 * @author Brix Team
 * @since 3.0.0
 * @see DynamicRouteService
 */
@Component
public class EventDrivenRouteRefresher {

    private static final Logger log = LoggerFactory.getLogger(EventDrivenRouteRefresher.class);

    /**
     * Dynamic route service
     */
    private final DynamicRouteService routeService;

    /**
     * Constructor
     * 
     * @param routeService dynamic route service
     */
    public EventDrivenRouteRefresher(DynamicRouteService routeService) {
        this.routeService = routeService;
        log.info("EventDrivenRouteRefresher initialization complete (v3.0 event-driven mode)");
    }

    /**
     * Handle module started event — register routes
     * 
     * <p>When a module finishes starting, Runtime Orchestrator publishes ModuleStartedEvent.
     * Gateway listens to this event, parses module-defined routes and registers them.</p>
     * 
     * <h4>Event Processing Flow</h4>
     * <ol>
     *   <li>Get module ID and route definitions from event</li>
     *   <li>Call DynamicRouteService to register routes</li>
     *   <li>Trigger Spring Cloud Gateway route refresh</li>
     * </ol>
     * 
     * @param event module started event
     */
    @EventListener
    public void onModuleStarted(ModuleStartedEvent event) {
        String moduleId = event.getModuleId();
        List<RouteDefinition> routes = event.getRoutes();

        log.info("Received module started event, preparing to register routes: moduleId={}, routeCount={}", 
                moduleId, routes != null ? routes.size() : 0);

        try {
            // Register routes
            routeService.registerRoutes(moduleId, routes);
            
            log.info("Module {} route registration successful", moduleId);
            
        } catch (Exception e) {
            // Route registration failure should not block module startup, log error and continue
            log.error("Module {} route registration failed: {}", moduleId, e.getMessage(), e);
        }
    }

    /**
     * Handle module stopped event — remove routes
     * 
     * <p>When a module stops, Runtime Orchestrator publishes ModuleStoppedEvent.
     * Gateway listens to this event and removes all routes registered by this module.</p>
     * 
     * @param event module stopped event
     */
    @EventListener
    public void onModuleStopped(ModuleStoppedEvent event) {
        String moduleId = event.getModuleId();

        log.info("Received module stopped event, preparing to remove routes: moduleId={}", moduleId);

        try {
            // Remove routes
            routeService.removeRoutes(moduleId);
            
            log.info("Module {} route removal successful", moduleId);
            
        } catch (Exception e) {
            log.error("Module {} route removal failed: {}", moduleId, e.getMessage(), e);
        }
    }

    /**
     * Handle route refresh request event — force refresh all routes
     * 
     * <p>Operations personnel can publish this event to force refresh all routes, useful for:</p>
     * <ul>
     *   <li>Manual refresh after route configuration changes</li>
     *   <li>Reloading during route troubleshooting</li>
     *   <li>Disaster recovery scenarios</li>
     * </ul>
     * 
     * @param event route refresh request event
     */
    @EventListener
    public void onRouteRefreshRequested(RouteRefreshRequestedEvent event) {
        log.info("Received route refresh request: reason={}", event.getReason());

        try {
            // Trigger route refresh
            routeService.refreshRoutes();
            
            log.info("Route refresh complete");
            
        } catch (Exception e) {
            log.error("Route refresh failed: {}", e.getMessage(), e);
        }
    }
}
