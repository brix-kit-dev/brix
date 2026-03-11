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
package io.runtime.sdk.capability.registry;

/**
 * Capability Level Enumeration
 * 
 * <p>Defines capability levels for configuration validation and capability assembly.</p>
 * 
 * <table border="1">
 *   <tr><th>Level</th><th>Description</th><th>Requirement</th></tr>
 *   <tr><td>CORE</td><td>Core Capability</td><td>All Hosts must implement</td></tr>
 *   <tr><td>STANDARD</td><td>Standard Capability</td><td>Recommended</td></tr>
 *   <tr><td>EXTENDED</td><td>Extended Capability</td><td>On-demand</td></tr>
 *   <tr><td>EXPERIMENTAL</td><td>Experimental Capability</td><td>Unstable, may change</td></tr>
 * </table>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public enum CapabilityLevel {

    /**
     * Core Capability
     * 
     * <p>Capabilities that all Hosts must implement:</p>
     * <ul>
     *   <li>EventBusCapability - Event Bus</li>
     *   <li>StateStoreCapability - State Storage</li>
     *   <li>AuthContextCapability - Authentication Context</li>
     *   <li>ObservabilityCapability - Observability</li>
     *   <li>ConfigStoreCapability - Configuration Store</li>
     *   <li>LifecycleCapability - Lifecycle</li>
     * </ul>
     */
    CORE(1, "Core", true),

    /**
     * Standard Capability
     * 
     * <p>Recommended capabilities that Full Product Host should provide:</p>
     * <ul>
     *   <li>SchedulingCapability - Scheduled Tasks</li>
     *   <li>LockCapability - Distributed Lock</li>
     * </ul>
     */
    STANDARD(2, "Standard", false),

    /**
     * Extended Capability
     * 
     * <p>On-demand capabilities for specific scenarios:</p>
     * <ul>
     *   <li>ResilienceCapability - Resilience</li>
     *   <li>IdGeneratorCapability - ID Generation</li>
     *   <li>DataAccessCapability - Data Access Authorization</li>
     * </ul>
     */
    EXTENDED(3, "Extended", false),

    /**
     * Experimental Capability
     * 
     * <p>Capabilities in experimental stage, API may change.</p>
     */
    EXPERIMENTAL(4, "Experimental", false),

    /**
     * Fallback Capability
     * 
     * <p>Fallback implementation enabled when no higher priority implementation is available.
     * Usually a zero-dependency simple implementation for development or testing scenarios.</p>
     * 
     * @since 3.2.0
     */
    FALLBACK(5, "Fallback", false);

    private final int order;
    private final String displayName;
    private final boolean requiredForAllHosts;

    CapabilityLevel(int order, String displayName, boolean requiredForAllHosts) {
        this.order = order;
        this.displayName = displayName;
        this.requiredForAllHosts = requiredForAllHosts;
    }

    /**
     * Get sort order
     * 
     * @return sort order
     */
    public int getOrder() {
        return order;
    }

    /**
     * Get display name
     * 
     * @return display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Whether required for all Hosts
     * 
     * @return whether required
     */
    public boolean isRequiredForAllHosts() {
        return requiredForAllHosts;
    }
}
