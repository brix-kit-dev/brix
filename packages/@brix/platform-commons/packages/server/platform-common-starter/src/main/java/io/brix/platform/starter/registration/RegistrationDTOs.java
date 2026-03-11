/*
 * Copyright 2026 Brix Platform Authors
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
package io.brix.platform.starter.registration;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * v2.1 Service Registration Request DTO
 * 
 * <p>Registration information sent to the shell when service starts</p>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 */
record ServiceRegistrationRequest(
    /**
     * Service name
     * 
     * <p>Example: brix-service-user</p>
     */
    String serviceName,
    
    /**
     * Service instance ID
     * 
     * <p>Uniquely identifies a service instance, used to distinguish multiple instances of the same service</p>
     */
    String instanceId,
    
    /**
     * Service URL
     * 
     * <p>HTTP address exposed by the service, e.g., http://localhost:9010</p>
     */
    String serviceUrl,
    
    /**
     * Service version
     * 
     * <p>Example: 1.0.0-SNAPSHOT</p>
     */
    String version,
    
    /**
     * Service description
     */
    String description,
    
    /**
     * Route list
     * 
     * <p>All REST endpoints exposed by the service</p>
     */
    List<RouteInfo> routes,
    
    /**
     * Assembled plugin list
     * 
     * <p>Plugin JARs assembled in the service</p>
     */
    List<PluginInfo> plugins,
    
    /**
     * Service metadata
     * 
     * <p>Other custom information, e.g., environment, owner</p>
     */
    Map<String, Object> metadata,
    
    /**
     * Registration time
     */
    Instant registrationTime
) {}

/**
 * v2.1 Plugin Info DTO
 * 
 * <p>Describes plugins assembled in the service</p>
 */
record PluginInfo(
    /**
     * Plugin ID
     * 
     * <p>Maven artifactId, e.g., plugin-user-core</p>
     */
    String pluginId,
    
    /**
     * Plugin name
     * 
     * <p>Human-readable name, e.g., User Management Plugin</p>
     */
    String name,
    
    /**
     * Plugin version
     * 
     * <p>Example: 1.0.0-SNAPSHOT</p>
     */
    String version,
    
    /**
     * Plugin type
     * 
     * <p>Example: CORE, API, EVENT</p>
     */
    String type,
    
    /**
     * Plugin description
     */
    String description
) {}

/**
 * v2.1 Heartbeat Request DTO
 * 
 * <p>Heartbeat information sent periodically by the service to the shell</p>
 */
record HeartbeatRequest(
    /**
     * Service name
     */
    String serviceName,
    
    /**
     * Service instance ID
     */
    String instanceId,
    
    /**
     * Service status
     */
    ServiceStatus status,
    
    /**
     * Current time
     */
    Instant timestamp,
    
    /**
     * Health metrics
     */
    HealthMetrics healthMetrics
) {}

/**
 * v2.1 Service Status Enum
 */
enum ServiceStatus {
    /** Starting */
    STARTING,
    
    /** Running */
    RUNNING,
    
    /** Running in degraded mode */
    DEGRADED,
    
    /** Stopping */
    STOPPING,
    
    /** Stopped */
    STOPPED
}

/**
 * v2.1 Health Metrics DTO
 */
record HealthMetrics(
    /**
     * CPU usage (percentage)
     */
    double cpuUsage,
    
    /**
     * Memory usage (percentage)
     */
    double memoryUsage,
    
    /**
     * Active threads
     */
    int activeThreads,
    
    /**
     * Requests per second
     */
    double requestsPerSecond,
    
    /**
     * Average response time (milliseconds)
     */
    double avgResponseTimeMs,
    
    /**
     * Error rate (percentage)
     */
    double errorRate
) {}
