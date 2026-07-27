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
package io.runtime.sdk.context;

import java.util.Optional;
import java.util.Set;

import io.runtime.sdk.capability.AuthContextCapability;
import io.runtime.sdk.capability.ConfigStoreCapability;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.LifecycleCapability;
import io.runtime.sdk.capability.LockCapability;
import io.runtime.sdk.capability.ObservabilityCapability;
import io.runtime.sdk.capability.ResilienceCapability;
import io.runtime.sdk.capability.SchedulingCapability;
import io.runtime.sdk.capability.StateStoreCapability;
import io.runtime.sdk.capability.registry.CapabilityDescriptor;
import io.runtime.sdk.capability.registry.CapabilityRegistry;

/**
 * Legacy Runtime Context.
 * 
 * <p>This interface is the v3.0.9 compatibility entry point for modules to access
 * Runtime Shell capabilities. Under the v3.0.10 Runtime Shell baseline, newly migrated
 * plugins must use {@link io.runtime.sdk.plugin.BrixPlugin} and
 * {@link io.runtime.sdk.plugin.PluginContext} instead of adding new dependencies on this
 * interface.</p>
 *
 * <p>The interface remains source and binary compatible so existing modules can continue
 * to compile while the migration proceeds one plugin at a time.</p>
 * 
 * <h3>Core Responsibilities</h3>
 * <ul>
 *   <li>Provide access to core capabilities</li>
 *   <li>Provide safe access to optional capabilities</li>
 *   <li>Provide context information (tenant, module, etc.)</li>
 * </ul>
 * 
 * <h3>Capability Classification</h3>
 * <table border="1">
 *   <tr><th>Category</th><th>Capability</th><th>Description</th></tr>
 *   <tr><td rowspan="6">Core Capabilities (Required)</td><td>EventBus</td><td>Event Publishing</td></tr>
 *   <tr><td>StateStore</td><td>State Storage</td></tr>
 *   <tr><td>AuthContext</td><td>Authentication Context</td></tr>
 *   <tr><td>Observability</td><td>Observability</td></tr>
 *   <tr><td>ConfigStore</td><td>Configuration Storage</td></tr>
 *   <tr><td>Lifecycle</td><td>Lifecycle Management</td></tr>
 *   <tr><td rowspan="3">Optional Capabilities</td><td>Scheduling</td><td>Scheduled Tasks</td></tr>
 *   <tr><td>Lock</td><td>Distributed Lock</td></tr>
 *   <tr><td>Resilience</td><td>Circuit Breaker/Rate Limiting</td></tr>
 * </table>
 * 
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><b>Compatibility Entry Point</b>: Existing v3.0.9 modules access capabilities through RuntimeContext</li>
 *   <li><b>Capability Isolation</b>: Different capabilities implemented independently</li>
 *   <li><b>Optional Capability Safety</b>: Optional capabilities return Optional to avoid null pointers</li>
 * </ul>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * public class BookingModule implements LifecycleCapability {
 *     private RuntimeContext context;
 *     
 *     @Override
 *     public void onInit(RuntimeContext context) {
 *         this.context = context;
 *         
 *         // Use core capability
 *         context.getObservability().info("Module initializing...");
 *         
 *         // Use config capability
 *         int maxDays = context.getConfigStore().getInt("booking.max-days-ahead", 30);
 *     }
 *     
 *     public void createBooking(BookingCommand command) {
 *         // Use auth capability
 *         if (!context.getAuthContext().hasPermission("booking:create")) {
 *             throw new AccessDeniedException();
 *         }
 *         
 *         // Use optional distributed lock capability
 *         context.getLock().ifPresent(lock -> {
 *             if (lock.tryLock("booking:" + command.getSlotId())) {
 *                 try {
 *                     // Execute booking logic...
 *                 } finally {
 *                     lock.unlock("booking:" + command.getSlotId());
 *                 }
 *             }
 *         });
 *         
 *         // Publish event
 *         context.getEventBus().publish(new BookingCreatedEvent(bookingId));
 *     }
 * }
 * }</pre>
 * 
 * <h3>Implementation Notes</h3>
 * <p>RuntimeContext is provided by the runtime compatibility layer. Different Hosts provide different capability combinations:</p>
 * <ul>
 *   <li>Full Product Host: Provides complete implementation of all capabilities</li>
 *   <li>Embedded Host: Provides core capabilities; optional capabilities configured based on customer environment</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @deprecated since 3.0.10 for new plugin migrations. Existing modules may continue to use this
 *             compatibility API until they are moved to the v3.0.10 plugin SPI.
 * @see EventBusCapability
 * @see StateStoreCapability
 * @see AuthContextCapability
 * @see ObservabilityCapability
 * @see ConfigStoreCapability
 * @see LifecycleCapability
 */
@Deprecated(since = "3.0.10", forRemoval = false)
public interface RuntimeContext {

    // ==================== Capability Registry (Core API) ====================

    /**
     * Get the capability registry
     * 
     * <p>The capability registry is the unified entry point for accessing all capabilities,
     * supporting dynamic capability discovery and retrieval.
     * This is the recommended capability access approach in v3.0.</p>
     * 
     * @return capability registry instance
     */
    CapabilityRegistry getCapabilityRegistry();

    /**
     * Get capability instance of specified type (generic method)
     * 
     * <p>Dynamically retrieves capabilities by type, supporting any registered capability type.
     * This method is recommended over specific getter methods.</p>
     * 
     * @param capabilityType capability interface type
     * @param <T> capability type parameter
     * @return Optional wrapper of capability instance
     */
    default <T> Optional<T> getCapability(Class<T> capabilityType) {
        return getCapabilityRegistry().get(capabilityType);
    }

    /**
     * Get capability instance of specified type (required)
     * 
     * @param capabilityType capability interface type
     * @param <T> capability type parameter
     * @return capability instance, never returns null
     * @throws io.runtime.sdk.capability.registry.CapabilityNotFoundException if capability is not registered
     */
    default <T> T getCapabilityRequired(Class<T> capabilityType) {
        return getCapabilityRegistry().getRequired(capabilityType);
    }

    /**
     * Get all registered capability types
     * 
     * @return set of capability types
     */
    default Set<Class<?>> getAvailableCapabilities() {
        return getCapabilityRegistry().getRegisteredTypes();
    }

    /**
     * Get capability descriptor information
     * 
     * @param capabilityType capability interface type
     * @return capability descriptor information
     */
    default Optional<CapabilityDescriptor> getCapabilityDescriptor(Class<?> capabilityType) {
        return getCapabilityRegistry().getDescriptor(capabilityType);
    }

    // ==================== Core Capability Shortcut Methods (Backward Compatible) ====================

    /**
     * Get EventBus capability
     * 
     * <p>Used for publishing domain events and integration events.
     * Equivalent to {@code getCapabilityRequired(EventBusCapability.class)}.</p>
     * 
     * @return EventBus capability instance, never returns null
     */
    default EventBusCapability getEventBus() {
        return getCapabilityRequired(EventBusCapability.class);
    }

    /**
     * Get StateStore capability
     * 
     * <p>Used for caching, session, and temporary data storage.
     * Equivalent to {@code getCapabilityRequired(StateStoreCapability.class)}.</p>
     * 
     * @return StateStore capability instance, never returns null
     */
    default StateStoreCapability getStateStore() {
        return getCapabilityRequired(StateStoreCapability.class);
    }

    /**
     * Get AuthContext capability
     * 
     * <p>Used for retrieving current user identity and permissions.
     * Equivalent to {@code getCapabilityRequired(AuthContextCapability.class)}.</p>
     * 
     * @return AuthContext capability instance, never returns null
     */
    default AuthContextCapability getAuthContext() {
        return getCapabilityRequired(AuthContextCapability.class);
    }

    /**
     * Get Observability capability
     * 
     * <p>Used for logging, metrics, and tracing.
     * Equivalent to {@code getCapabilityRequired(ObservabilityCapability.class)}.</p>
     * 
     * @return Observability capability instance, never returns null
     */
    default ObservabilityCapability getObservability() {
        return getCapabilityRequired(ObservabilityCapability.class);
    }

    /**
     * Get ConfigStore capability
     * 
     * <p>Used for reading module configuration.
     * Equivalent to {@code getCapabilityRequired(ConfigStoreCapability.class)}.</p>
     * 
     * @return ConfigStore capability instance, never returns null
     */
    default ConfigStoreCapability getConfigStore() {
        return getCapabilityRequired(ConfigStoreCapability.class);
    }

    /**
     * Get Lifecycle capability
     * 
     * <p>Used for module lifecycle management.
     * Equivalent to {@code getCapabilityRequired(LifecycleCapability.class)}.</p>
     * 
     * @return Lifecycle capability instance, never returns null
     */
    default LifecycleCapability getLifecycle() {
        return getCapabilityRequired(LifecycleCapability.class);
    }

    // ==================== Optional Capability Shortcut Methods (Backward Compatible) ====================

    /**
     * Get Scheduling capability (optional)
     * 
     * <p>Some Hosts may not provide this capability.
     * Equivalent to {@code getCapability(SchedulingCapability.class)}.</p>
     * 
     * @return Scheduling capability, returns {@link Optional#empty()} if unavailable
     */
    default Optional<SchedulingCapability> getScheduling() {
        return getCapability(SchedulingCapability.class);
    }

    /**
     * Get distributed lock capability (optional)
     * 
     * <p>Some Hosts may not provide this capability.
     * Equivalent to {@code getCapability(LockCapability.class)}.</p>
     * 
     * @return distributed lock capability, returns {@link Optional#empty()} if unavailable
     */
    default Optional<LockCapability> getLock() {
        return getCapability(LockCapability.class);
    }

    /**
     * Get Resilience capability (optional)
     * 
     * <p>Some Hosts may not provide this capability.
     * Equivalent to {@code getCapability(ResilienceCapability.class)}.</p>
     * 
     * @return Resilience capability, returns {@link Optional#empty()} if unavailable
     */
    default Optional<ResilienceCapability> getResilience() {
        return getCapability(ResilienceCapability.class);
    }

    // ==================== Context Information ====================

    /**
     * Get current tenant ID
     * 
     * <p>Returns the tenant identifier for the current request in multi-tenant scenarios</p>
     * 
     * @return tenant ID, returns default value or null in single-tenant scenarios
     */
    String getTenantId();

    /**
     * Get current module ID
     * 
     * <p>Returns the module identifier that this runtime context belongs to</p>
     * 
     * @return module ID
     */
    String getModuleId();

    /**
     * Check if a capability is available
     * 
     * @param capabilityType capability type
     * @return true if the capability is available
     */
    default boolean isCapabilityAvailable(Class<?> capabilityType) {
        return getCapabilityRegistry().isAvailable(capabilityType);
    }
}
