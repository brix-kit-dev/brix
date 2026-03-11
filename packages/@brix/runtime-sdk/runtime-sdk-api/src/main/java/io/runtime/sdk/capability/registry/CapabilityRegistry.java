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

import java.util.Optional;
import java.util.Set;

/**
 * Capability Registry Interface
 * 
 * <p>Provides dynamic registration and retrieval of runtime capabilities,
 * serving as the core abstraction of the Runtime Shell.
 * Through the registry pattern, capabilities are declaratively assembled, avoiding hardcoded dependencies.</p>
 * 
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><b>Type Safety</b>: Type-safe capability retrieval through generics</li>
 *   <li><b>Declarative</b>: Capabilities declared via configuration, not hardcoded</li>
 *   <li><b>Extensible</b>: New capabilities require no core code changes, just registration</li>
 *   <li><b>Observable</b>: Provides capability metadata query functionality</li>
 * </ul>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * // Get required capability (throws exception if not found)
 * EventBusCapability eventBus = registry.getRequired(EventBusCapability.class);
 * 
 * // Get optional capability
 * registry.get(LockCapability.class).ifPresent(lock -> {
 *     lock.tryLock("resource-key", Duration.ofSeconds(10));
 * });
 * 
 * // Check if capability is available
 * if (registry.isAvailable(SchedulingCapability.class)) {
 *     // Use scheduling capability
 * }
 * }</pre>
 * 
 * <h3>Industry Reference</h3>
 * <ul>
 *   <li>OSGi BundleContext - Service registration and discovery</li>
 *   <li>Kubernetes API Server - Resource registration</li>
 *   <li>VS Code Extension API - Capability Provider</li>
 *   <li>Eclipse RCP - Service Registry</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see Capability
 * @see CapabilityDescriptor
 */
public interface CapabilityRegistry {

    // ==================== Capability Retrieval ====================

    /**
     * Get capability instance of specified type (optional)
     * 
     * <p>Recommended for retrieving optional capabilities where callers need to handle absence.</p>
     * 
     * @param capabilityType capability interface type
     * @param <T> capability type parameter
     * @return Optional wrapper of capability instance, returns empty if not registered
     */
    <T> Optional<T> get(Class<T> capabilityType);

    /**
     * Get capability instance of specified type (required)
     * 
     * <p>Used for retrieving core capabilities; throws exception if not registered.</p>
     * 
     * @param capabilityType capability interface type
     * @param <T> capability type parameter
     * @return capability instance, never returns null
     * @throws CapabilityNotFoundException if capability is not registered
     */
    <T> T getRequired(Class<T> capabilityType);

    /**
     * Get capability instance of specified type, returning default if not exists
     * 
     * @param capabilityType capability interface type
     * @param defaultValue default value
     * @param <T> capability type parameter
     * @return capability instance or default value
     */
    default <T> T getOrDefault(Class<T> capabilityType, T defaultValue) {
        return get(capabilityType).orElse(defaultValue);
    }

    // ==================== Capability Check ====================

    /**
     * Check if capability is available
     * 
     * @param capabilityType capability interface type
     * @return true if capability is registered and available
     */
    boolean isAvailable(Class<?> capabilityType);

    /**
     * Get all registered capability types
     * 
     * @return set of capability types (unmodifiable)
     */
    Set<Class<?>> getRegisteredTypes();

    /**
     * Get capability descriptor information
     * 
     * @param capabilityType capability interface type
     * @return capability descriptor, returns empty if not registered
     */
    Optional<CapabilityDescriptor> getDescriptor(Class<?> capabilityType);

    /**
     * Get all capability descriptors
     * 
     * @return set of all capability descriptors
     */
    Set<CapabilityDescriptor> getAllDescriptors();

    // ==================== Capability Registration ====================

    /**
     * Register capability instance
     * 
     * @param capabilityType capability interface type
     * @param instance capability instance
     * @param <T> capability type parameter
     * @throws IllegalStateException if registry is frozen
     */
    <T> void register(Class<T> capabilityType, T instance);

    /**
     * Register capability instance (with descriptor)
     * 
     * @param capabilityType capability interface type
     * @param instance capability instance
     * @param descriptor capability descriptor
     * @param <T> capability type parameter
     */
    <T> void register(Class<T> capabilityType, T instance, CapabilityDescriptor descriptor);

    /**
     * Conditionally register capability instance
     * 
     * <p>Only registers if the type is not already registered</p>
     * 
     * @param capabilityType capability interface type
     * @param instance capability instance
     * @param <T> capability type parameter
     * @return true if successfully registered (false means already exists)
     */
    <T> boolean registerIfAbsent(Class<T> capabilityType, T instance);

    // ==================== Lifecycle ====================

    /**
     * Freeze the registry
     * 
     * <p>After freezing, no new capabilities can be registered, ensuring runtime stability.
     * Typically called after application startup completes.</p>
     */
    void freeze();

    /**
     * Check if registry is frozen
     * 
     * @return true if frozen
     */
    boolean isFrozen();

    /**
     * Validate required capabilities are registered
     * 
     * @param requiredTypes array of required capability types
     * @throws CapabilityNotFoundException if any required capability is not registered
     */
    void validateRequired(Class<?>... requiredTypes);

    /**
     * Get number of registered capabilities
     * 
     * @return capability count
     */
    int size();
}
