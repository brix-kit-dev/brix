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
 * Module Started Event
 * 
 * <p>Published by Runtime Orchestrator when a module finishes starting.
 * Gateway listens to this event to register routes defined by the module.</p>
 * 
 * <h3>Event Source</h3>
 * <p>Published by Runtime Orchestrator when module enters STARTED state,
 * transmitted to gateway via EventBusCapability.</p>
 * 
 * <h3>Event Data</h3>
 * <ul>
 *   <li>moduleId - Module unique identifier</li>
 *   <li>moduleName - Module name (human-readable)</li>
 *   <li>routes - List of routes declared by the module</li>
 *   <li>metadata - Module metadata</li>
 *   <li>timestamp - Event occurrence time</li>
 * </ul>
 * 
 * <h3>Relationship with Manifest</h3>
 * <p>Corresponds to events.subscriptions configuration in module-manifest.yaml:</p>
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
     * Module unique identifier
     */
    private final String moduleId;

    /**
     * Module name
     */
    private final String moduleName;

    /**
     * Module version
     */
    private final String moduleVersion;

    /**
     * List of routes defined by the module
     */
    private final List<RouteDefinition> routes;

    /**
     * Module metadata
     */
    private final Map<String, Object> metadata;

    /**
     * Event occurrence time
     */
    private final Instant timestamp;

    /**
     * Constructor
     * 
     * @param source        event source
     * @param moduleId      module unique identifier
     * @param moduleName    module name
     * @param moduleVersion module version
     * @param routes        list of routes defined by the module
     * @param metadata      module metadata
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
     * Simplified constructor
     * 
     * @param source   event source
     * @param moduleId module unique identifier
     * @param routes   list of routes defined by the module
     */
    public ModuleStartedEvent(Object source, String moduleId, List<RouteDefinition> routes) {
        this(source, moduleId, null, null, routes, null);
    }

    /**
     * Get module unique identifier
     * 
     * @return module ID
     */
    public String getModuleId() {
        return moduleId;
    }

    /**
     * Get module name
     * 
     * @return module name
     */
    public String getModuleName() {
        return moduleName;
    }

    /**
     * Get module version
     * 
     * @return module version
     */
    public String getModuleVersion() {
        return moduleVersion;
    }

    /**
     * Get list of routes defined by the module
     * 
     * @return route list (immutable)
     */
    public List<RouteDefinition> getRoutes() {
        return routes;
    }

    /**
     * Get module metadata
     * 
     * @return metadata (immutable)
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Get event occurrence time
     * 
     * @return timestamp
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
