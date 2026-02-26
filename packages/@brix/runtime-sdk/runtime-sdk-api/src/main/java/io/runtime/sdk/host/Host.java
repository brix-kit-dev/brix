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
package io.runtime.sdk.host;

import io.runtime.sdk.capability.*;
import io.runtime.sdk.context.RuntimeContext;

import java.util.Optional;

/**
 * Host Abstract Interface
 * 
 * <p>Host is the concrete implementor of Runtime Shell capability contracts, responsible for
 * mapping abstract capabilities to specific infrastructure.
 * Different Host implementations provide varying levels of capability support,
 * but the interface remains completely consistent for modules.</p>
 * 
 * <h3>Host Types</h3>
 * <table border="1">
 *   <tr><th>Host Type</th><th>Description</th><th>Typical Scenario</th></tr>
 *   <tr><td>Full Product Host</td><td>Complete capability implementation</td><td>Standalone deployed Brix Platform</td></tr>
 *   <tr><td>Embedded Host</td><td>Streamlined capability implementation</td><td>Plugin embedded in customer systems</td></tr>
 *   <tr><td>Test Host</td><td>Mock implementation for testing</td><td>Unit tests, integration tests</td></tr>
 * </table>
 * 
 * <h3>Core Responsibilities</h3>
 * <ul>
 *   <li>Provide RuntimeContext implementation</li>
 *   <li>Manage module lifecycle</li>
 *   <li>Coordinate creation and destruction of capability instances</li>
 * </ul>
 * 
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><b>Capability Equivalence</b>: Different Hosts provide the same Capability interfaces</li>
 *   <li><b>Implementation Transparency</b>: Modules are unaware of specific Host type</li>
 *   <li><b>Configuration Driven</b>: Host behavior is adjusted through configuration</li>
 * </ul>
 * 
 * <h3>Implementation Example</h3>
 * <pre>{@code
 * public class FullProductHost implements Host {
 *     private final KafkaEventBusCapability eventBus;
 *     private final RedisStateStoreCapability stateStore;
 *     // ... other capability implementations
 *     
 *     @Override
 *     public RuntimeContext createContext(String moduleId) {
 *         return new DefaultRuntimeContext(moduleId, this);
 *     }
 *     
 *     @Override
 *     public EventBusCapability getEventBus() {
 *         return eventBus;
 *     }
 *     
 *     // ... other capability getter methods
 * }
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see RuntimeContext
 */
public interface Host {

    /**
     * Get Host type
     * 
     * @return Host type identifier
     */
    HostType getType();

    /**
     * Get Host version
     * 
     * @return Host version number
     */
    String getVersion();

    /**
     * Create runtime context for a module
     * 
     * <p>Each module should have its own independent RuntimeContext instance</p>
     * 
     * @param moduleId module ID
     * @return runtime context instance
     */
    RuntimeContext createContext(String moduleId);

    // ==================== Core Capability Providers ====================

    /**
     * Get event bus capability implementation
     * 
     * @return event bus capability
     */
    EventBusCapability getEventBus();

    /**
     * Get state store capability implementation
     * 
     * @return state store capability
     */
    StateStoreCapability getStateStore();

    /**
     * Get authentication context capability implementation
     * 
     * @return authentication context capability
     */
    AuthContextCapability getAuthContext();

    /**
     * Get observability capability implementation
     * 
     * @return observability capability
     */
    ObservabilityCapability getObservability();

    /**
     * Get config store capability implementation
     * 
     * @return config store capability
     */
    ConfigStoreCapability getConfigStore();

    // ==================== Optional Capability Providers ====================

    /**
     * Get scheduling capability implementation (optional)
     * 
     * @return scheduling capability, returns empty if not supported
     */
    default Optional<SchedulingCapability> getScheduling() {
        return Optional.empty();
    }

    /**
     * Get distributed lock capability implementation (optional)
     * 
     * @return distributed lock capability, returns empty if not supported
     */
    default Optional<LockCapability> getLock() {
        return Optional.empty();
    }

    /**
     * Get resilience capability implementation (optional)
     * 
     * @return resilience capability, returns empty if not supported
     */
    default Optional<ResilienceCapability> getResilience() {
        return Optional.empty();
    }

    // ==================== Lifecycle Management ====================

    /**
     * Initialize Host
     * 
     * <p>Called before any modules are loaded</p>
     */
    void initialize();

    /**
     * Shutdown Host
     * 
     * <p>Called after all modules are unloaded, releases resources</p>
     */
    void shutdown();

    /**
     * Check if Host is initialized
     * 
     * @return true if initialized
     */
    boolean isInitialized();
}
