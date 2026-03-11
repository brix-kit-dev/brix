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
 * Module Health Status Enumeration
 * 
 * <p>Defines three health states for modules, used for health checks and monitoring.</p>
 * 
 * <h3>Status Description</h3>
 * <ul>
 *   <li><b>UP</b>: Module is fully functional, all features available</li>
 *   <li><b>DEGRADED</b>: Module has limited functionality, but core features are available</li>
 *   <li><b>DOWN</b>: Module is unavailable, requires attention or restart</li>
 * </ul>
 * 
 * <h3>State Transitions</h3>
 * <pre>{@code
 * UP <-> DEGRADED <-> DOWN
 *  \__________|________/
 * }</pre>
 * 
 * <h3>Handling Strategy</h3>
 * <table border="1">
 *   <tr><th>Status</th><th>Traffic Handling</th><th>Alert Level</th></tr>
 *   <tr><td>UP</td><td>Normal accept</td><td>None</td></tr>
 *   <tr><td>DEGRADED</td><td>Rate limit/Degrade</td><td>WARN</td></tr>
 *   <tr><td>DOWN</td><td>Reject/Failover</td><td>ERROR</td></tr>
 * </table>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see LifecycleCapability#healthCheck()
 */
public enum HealthStatus {

    /**
     * Healthy state - Module is fully functional
     */
    UP("Module running normally"),

    /**
     * Degraded state - Some functionality is limited
     * 
     * <p>Typical scenarios:</p>
     * <ul>
     *   <li>External dependency responding slowly</li>
     *   <li>Cache unavailable, falling back to direct database queries</li>
     *   <li>Non-core functionality abnormal</li>
     * </ul>
     */
    DEGRADED("Module running in degraded mode"),

    /**
     * Unavailable state - Module cannot provide service
     * 
     * <p>Typical scenarios:</p>
     * <ul>
     *   <li>Database connection lost</li>
     *   <li>Critical configuration missing</li>
     *   <li>Initialization failed</li>
     * </ul>
     */
    DOWN("Module unavailable");

    /**
     * Status description
     */
    private final String description;

    HealthStatus(String description) {
        this.description = description;
    }

    /**
     * Gets the status description
     * 
     * @return the status description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if healthy (UP or DEGRADED)
     * 
     * @return true if the service can be provided
     */
    public boolean isHealthy() {
        return this == UP || this == DEGRADED;
    }

    /**
     * Checks if fully healthy
     * 
     * @return true if the status is UP
     */
    public boolean isFullyHealthy() {
        return this == UP;
    }
}
