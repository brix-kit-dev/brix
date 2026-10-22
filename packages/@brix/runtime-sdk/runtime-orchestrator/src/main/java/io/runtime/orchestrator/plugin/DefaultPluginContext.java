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
package io.runtime.orchestrator.plugin;

import java.util.Objects;
import java.util.Optional;

import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.registry.CapabilityRegistry;
import io.runtime.sdk.plugin.PluginContext;
import io.runtime.sdk.plugin.PluginIdentity;

/**
 * Registry-backed implementation of the plugin capability context.
 *
 * <p>This class is L2B internal. It protects the internal
 * {@link CapabilityRegistry} by exposing only the typed capability operations
 * defined by the L2A {@link PluginContext} contract and by enforcing the
 * plugin manifest's required/optional capability declarations before each
 * lookup.</p>
 *
 * <p>{@link PersistenceCapability} is owner-scoped rather than global: it is
 * resolved for this plugin through the {@link OwnerPersistenceProvider}
 * (root blueprint BRX-RS-DATA-026).</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class DefaultPluginContext implements PluginContext {

    private final PluginRuntimeDescriptor descriptor;
    private final CapabilityRegistry capabilityRegistry;
    private final OwnerPersistenceProvider ownerPersistence;

    /**
     * Creates a plugin context without owner persistence.
     *
     * @param descriptor runtime descriptor resolved from the plugin manifest
     * @param capabilityRegistry runtime capability registry
     */
    public DefaultPluginContext(PluginRuntimeDescriptor descriptor, CapabilityRegistry capabilityRegistry) {
        this(descriptor, capabilityRegistry, OwnerPersistenceProvider.none());
    }

    /**
     * Creates a plugin context.
     *
     * @param descriptor runtime descriptor resolved from the plugin manifest
     * @param capabilityRegistry runtime capability registry
     * @param ownerPersistence source of the plugin's owner-scoped persistence
     */
    public DefaultPluginContext(
            PluginRuntimeDescriptor descriptor,
            CapabilityRegistry capabilityRegistry,
            OwnerPersistenceProvider ownerPersistence) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
        this.capabilityRegistry = Objects.requireNonNull(capabilityRegistry, "capabilityRegistry must not be null");
        this.ownerPersistence = Objects.requireNonNull(ownerPersistence, "ownerPersistence must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <C> C require(Class<C> capabilityType) {
        Objects.requireNonNull(capabilityType, "capabilityType must not be null");
        if (!descriptor.isRequiredCapability(capabilityType, capabilityRegistry)) {
            throw new PluginRuntimeException("Plugin '" + descriptor.identity().pluginId()
                + "' requested undeclared required capability " + capabilityType.getName());
        }
        if (capabilityType == PersistenceCapability.class) {
            return capabilityType.cast(ownerPersistence().orElseThrow(() -> new PluginRuntimeException(
                "Plugin '" + descriptor.identity().pluginId() + "' has no owner persistence unit")));
        }
        return capabilityRegistry.getRequired(capabilityType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <C> Optional<C> find(Class<C> capabilityType) {
        Objects.requireNonNull(capabilityType, "capabilityType must not be null");
        if (!descriptor.isOptionalCapability(capabilityType, capabilityRegistry)) {
            throw new PluginRuntimeException("Plugin '" + descriptor.identity().pluginId()
                + "' requested undeclared optional capability " + capabilityType.getName());
        }
        if (capabilityType == PersistenceCapability.class) {
            return ownerPersistence().map(capabilityType::cast);
        }
        return capabilityRegistry.get(capabilityType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PluginIdentity pluginIdentity() {
        return descriptor.identity();
    }

    private Optional<PersistenceCapability> ownerPersistence() {
        return ownerPersistence.forOwner(descriptor.identity().pluginId());
    }
}
