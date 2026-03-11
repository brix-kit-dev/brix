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
package io.runtime.orchestrator.capability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityDescriptor;
import io.runtime.sdk.capability.registry.CapabilityNotFoundException;
import io.runtime.sdk.capability.registry.CapabilityRegistry;

/**
 * Default Capability Registry Implementation.
 *
 * <p>Implements the {@link CapabilityRegistry} interface, providing common capability
 * registration and lookup logic shared across multiple Host modes.</p>
 *
 * <h2>Architecture Position (Runtime Shell Architecture)</h2>
 * <p>
 * This class belongs to <b>runtime-orchestrator</b> (orchestration layer), implementing
 * the {@link CapabilityRegistry} interface defined in runtime-sdk-api (contract layer).
 * The architecture requires the contract layer (Layer 2) to contain only pure interface
 * definitions, while implementations should reside in the orchestration or capability
 * implementation layers.
 * </p>
 * <p>
 * This class evolved from the original {@code StandaloneCapabilityRegistry} in
 * host-shell-standalone, serving as a common default implementation reused or extended
 * by various Hosts (Standalone/Embedded), eliminating duplicate code between Host modes.
 * </p>
 *
 * <h2>Core Features</h2>
 * <ul>
 *   <li><b>Type-safe Registration</b>: Generic registration and retrieval based on {@code Class<T>} as key</li>
 *   <li><b>Capability Descriptors</b>: Manages capability metadata via {@link CapabilityDescriptor}</li>
 *   <li><b>Alias Support</b>: A capability can be looked up through multiple aliases</li>
 *   <li><b>Freeze Mechanism</b>: Freezes registry after startup to prevent runtime tampering</li>
 *   <li><b>Required Capability Validation</b>: Validates all required capabilities are registered at startup</li>
 *   <li><b>Annotation-driven Registration</b>: Supports auto-registration from {@link Capability @Capability} annotation</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * All internal storage is based on {@link ConcurrentHashMap}, supporting concurrent writes during
 * registration phase. After freezing, enters read-only mode and all write operations throw
 * {@link IllegalStateException}.
 * </p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Created and managed by Host layer AutoConfiguration
 * DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
 *
 * // Register capabilities
 * registry.register(EventBusCapability.class, kafkaEventBus);
 * registry.register(StateStoreCapability.class, redisStateStore);
 *
 * // Register from annotation
 * registry.registerFromAnnotation(fallbackAuthContext);
 *
 * // Validate and freeze
 * registry.validateRequired(EventBusCapability.class, StateStoreCapability.class);
 * registry.freeze();
 *
 * // Get capability
 * EventBusCapability eventBus = registry.getRequired(EventBusCapability.class);
 * }</pre>
 *
 * @author Brix Platform Authors
 * @since 3.0.0
 * @see CapabilityRegistry
 * @see CapabilityDescriptor
 */
public class DefaultCapabilityRegistry implements CapabilityRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultCapabilityRegistry.class);

    /** Capability instance storage: interface type -> implementation instance */
    private final Map<Class<?>, Object> capabilities = new ConcurrentHashMap<>();

    /** Capability descriptor storage: interface type -> descriptor (containing name, level, aliases metadata) */
    private final Map<Class<?>, CapabilityDescriptor> descriptors = new ConcurrentHashMap<>();

    /** Capability alias mapping: alias string -> primary interface type (allows lookup by alias) */
    private final Map<String, Class<?>> aliases = new ConcurrentHashMap<>();

    /** Registry frozen flag, all write operations are prohibited after freezing */
    private volatile boolean frozen = false;

    public DefaultCapabilityRegistry() {
        log.debug("Creating DefaultCapabilityRegistry instance");
    }

    // ==================== CapabilityRegistry Interface Implementation ====================

    /**
     * Gets capability instance by type (optional).
     *
     * @param capabilityType capability interface type
     * @param <T> capability type parameter
     * @return Optional wrapper of capability instance, returns empty if not registered
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(Class<T> capabilityType) {
        Objects.requireNonNull(capabilityType, "Capability type cannot be null");
        return Optional.ofNullable((T) capabilities.get(capabilityType));
    }

    /**
     * Gets required capability instance.
     *
     * <p>Throws {@link CapabilityNotFoundException} when capability is not registered,
     * error message includes list of registered capabilities for troubleshooting.</p>
     *
     * @param capabilityType capability interface type
     * @param <T> capability type parameter
     * @return capability instance
     * @throws CapabilityNotFoundException when capability is not registered
     */
    @Override
    public <T> T getRequired(Class<T> capabilityType) {
        return get(capabilityType).orElseThrow(() ->
            new CapabilityNotFoundException(capabilityType,
                "Required capability not registered: " + capabilityType.getSimpleName()
                + ". Registered capabilities: " + getRegisteredTypes().stream()
                    .map(Class::getSimpleName)
                    .collect(Collectors.joining(", ")))
        );
    }

    /**
     * Checks if specified capability is registered.
     *
     * @param capabilityType capability interface type
     * @return true if registered
     */
    @Override
    public boolean isAvailable(Class<?> capabilityType) {
        return capabilities.containsKey(capabilityType);
    }

    /**
     * Gets all registered capability types.
     *
     * @return immutable set of types
     */
    @Override
    public Set<Class<?>> getRegisteredTypes() {
        return Collections.unmodifiableSet(capabilities.keySet());
    }

    /**
     * Gets descriptor for specified capability.
     *
     * @param capabilityType capability interface type
     * @return Optional wrapper of descriptor
     */
    @Override
    public Optional<CapabilityDescriptor> getDescriptor(Class<?> capabilityType) {
        return Optional.ofNullable(descriptors.get(capabilityType));
    }

    /**
     * Gets all capability descriptors.
     *
     * @return immutable set of descriptors
     */
    @Override
    public Set<CapabilityDescriptor> getAllDescriptors() {
        return Collections.unmodifiableSet(new HashSet<>(descriptors.values()));
    }

    /**
     * Registers capability instance (auto-creates default descriptor).
     *
     * <p>If instance has {@link Capability @Capability} annotation, descriptor is inferred from annotation;
     * otherwise creates default descriptor.</p>
     *
     * @param capabilityType capability interface type
     * @param instance capability implementation instance
     * @param <T> capability type parameter
     * @throws IllegalStateException when registry is frozen
     */
    @Override
    public <T> void register(Class<T> capabilityType, T instance) {
        register(capabilityType, instance, createDefaultDescriptor(capabilityType, instance));
    }

    /**
     * Registers capability instance (with descriptor).
     *
     * <p>If same type is already registered, new instance overwrites old one with warning log.
     * Aliases in descriptor are automatically registered to alias mapping table.</p>
     *
     * @param capabilityType capability interface type
     * @param instance capability implementation instance
     * @param descriptor capability descriptor
     * @param <T> capability type parameter
     * @throws IllegalStateException when registry is frozen
     */
    @Override
    public <T> void register(Class<T> capabilityType, T instance, CapabilityDescriptor descriptor) {
        Objects.requireNonNull(capabilityType, "Capability type cannot be null");
        Objects.requireNonNull(instance, "Capability instance cannot be null");

        checkNotFrozen();

        if (capabilities.containsKey(capabilityType)) {
            log.warn("Overwriting registered capability: {} -> {}", capabilityType.getSimpleName(),
                    instance.getClass().getSimpleName());
        }

        capabilities.put(capabilityType, instance);

        if (descriptor != null) {
            descriptors.put(capabilityType, descriptor);
            // Register alias mappings, allowing capability lookup by alias
            for (String alias : descriptor.getAliases()) {
                aliases.put(alias, capabilityType);
                log.debug("Registered capability alias: {} -> {}", alias, capabilityType.getSimpleName());
            }
        }

        log.info("Registered capability: {} -> {} ({})",
                capabilityType.getSimpleName(),
                instance.getClass().getSimpleName(),
                descriptor != null ? descriptor.getLevel() : "DEFAULT");
    }

    /**
     * Registers only when capability is not registered.
     *
     * <p>Used for registering fallback implementations, avoiding overwriting existing formal implementations.</p>
     *
     * @param capabilityType capability interface type
     * @param instance capability implementation instance
     * @param <T> capability type parameter
     * @return true if registered successfully, false if already exists
     */
    @Override
    public <T> boolean registerIfAbsent(Class<T> capabilityType, T instance) {
        if (capabilities.containsKey(capabilityType)) {
            log.debug("Skipping registration (already exists): {}", capabilityType.getSimpleName());
            return false;
        }
        register(capabilityType, instance);
        return true;
    }

    /**
     * Freezes the registry.
     *
     * <p>After freezing, all write operations (register, clear) will throw {@link IllegalStateException}.
     * Typically called after Spring container startup completes, preventing runtime capability mapping modifications.</p>
     */
    @Override
    public void freeze() {
        this.frozen = true;
        log.info("Capability registry frozen, registered {} capabilities: {}",
                capabilities.size(),
                capabilities.keySet().stream()
                    .map(Class::getSimpleName)
                    .collect(Collectors.joining(", ")));
    }

    /**
     * Checks if registry is frozen.
     *
     * @return true if frozen
     */
    @Override
    public boolean isFrozen() {
        return frozen;
    }

    /**
     * Validates that all required capabilities are registered.
     *
     * <p>Called before freezing to ensure all core capabilities are in place.
     * Any missing capability will cause {@link CapabilityNotFoundException}.</p>
     *
     * @param requiredTypes list of required capability types
     * @throws CapabilityNotFoundException when required capability is missing
     */
    @Override
    public void validateRequired(Class<?>... requiredTypes) {
        List<String> missing = new ArrayList<>();

        for (Class<?> type : requiredTypes) {
            if (!isAvailable(type)) {
                missing.add(type.getSimpleName());
            }
        }

        if (!missing.isEmpty()) {
            throw new CapabilityNotFoundException(requiredTypes[0],
                "Following required capabilities are not registered: " + String.join(", ", missing));
        }

        log.debug("Required capability validation passed: {} capabilities", requiredTypes.length);
    }

    /**
     * Gets the count of registered capabilities.
     *
     * @return capability count
     */
    @Override
    public int size() {
        return capabilities.size();
    }

    // ==================== Extension Methods ====================

    /**
     * Gets capability instance by alias.
     *
     * <p>Alias mappings are automatically established during registration via {@link CapabilityDescriptor#getAliases()}.
     * Useful for scenarios requiring lookup by string identifier.</p>
     *
     * @param alias capability alias
     * @param <T> capability type parameter
     * @return Optional wrapper of capability instance
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getByAlias(String alias) {
        Class<?> type = aliases.get(alias);
        if (type == null) {
            return Optional.empty();
        }
        return (Optional<T>) get(type);
    }

    /**
     * Auto-registers from instance with {@link Capability @Capability} annotation.
     *
     * <p>Infers capability type and descriptor info from annotation, simplifying registration.
     * Works with Spring's auto-discovery mechanism.</p>
     *
     * @param instance instance with @Capability annotation
     * @throws IllegalArgumentException when instance has no @Capability annotation
     */
    public void registerFromAnnotation(Object instance) {
        Class<?> clazz = instance.getClass();
        Capability annotation = clazz.getAnnotation(Capability.class);

        if (annotation == null) {
            throw new IllegalArgumentException("Instance has no @Capability annotation: " + clazz.getName());
        }

        CapabilityDescriptor descriptor = CapabilityDescriptor.fromAnnotation(annotation, clazz);

        @SuppressWarnings("unchecked")
        Class<Object> capabilityType = (Class<Object>) descriptor.getType();
        register(capabilityType, instance, descriptor);
    }

    /**
     * Clears all registered capabilities (for testing only).
     *
     * @throws IllegalStateException when registry is frozen
     */
    public void clear() {
        checkNotFrozen();
        capabilities.clear();
        descriptors.clear();
        aliases.clear();
        log.debug("Capability registry cleared");
    }

    // ==================== Internal Methods ====================

    /**
     * Creates default descriptor for capability instance.
     *
     * <p>Prefers inferring from @Capability annotation, creates minimal descriptor if no annotation.</p>
     */
    private <T> CapabilityDescriptor createDefaultDescriptor(Class<T> type, T instance) {
        Capability annotation = instance.getClass().getAnnotation(Capability.class);
        if (annotation != null) {
            return CapabilityDescriptor.fromAnnotation(annotation, instance.getClass());
        }

        return CapabilityDescriptor.builder(type)
                .name(type.getSimpleName())
                .implementationClass(instance.getClass().getName())
                .build();
    }

    /**
     * Checks if registry is frozen, write operations prohibited after freezing.
     *
     * @throws IllegalStateException when registry is frozen
     */
    private void checkNotFrozen() {
        if (frozen) {
            throw new IllegalStateException("Capability registry is frozen, modifications not allowed");
        }
    }

    @Override
    public String toString() {
        return "DefaultCapabilityRegistry{"
                + "frozen=" + frozen
                + ", capabilities=" + capabilities.keySet().stream()
                    .map(Class::getSimpleName)
                    .collect(Collectors.joining(", ", "[", "]"))
                + '}';
    }
}
