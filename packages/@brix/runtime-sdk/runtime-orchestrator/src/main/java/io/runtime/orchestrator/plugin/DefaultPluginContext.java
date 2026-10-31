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
import io.runtime.orchestrator.query.QueryRuntime;
import io.runtime.orchestrator.query.QuerySideEffects;
import io.runtime.sdk.capability.CommandCapability;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.QueryCapability;
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
 * (root blueprint BRX-RS-DATA-026). So is {@link EventBusCapability} for a
 * plugin that publishes through its own outbox: its CRITICAL and STANDARD
 * events go there in its own transaction (root BRX-RS-MESSAGE-017,
 * MESSAGE-022); other plugins get the Host's event bus.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class DefaultPluginContext implements PluginContext {

    private final PluginRuntimeDescriptor descriptor;
    private final CapabilityRegistry capabilityRegistry;
    private final OwnerPersistenceProvider ownerPersistence;
    private final QueryRuntime queries;

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
        this(descriptor, capabilityRegistry, ownerPersistence, QueryRuntime.unconfigured());
    }

    /**
     * Creates a plugin context whose plugin may call typed queries.
     *
     * @param descriptor runtime descriptor resolved from the plugin manifest
     * @param capabilityRegistry runtime capability registry
     * @param ownerPersistence source of the plugin's owner-scoped persistence
     * @param queries the typed queries of this host
     */
    public DefaultPluginContext(
            PluginRuntimeDescriptor descriptor,
            CapabilityRegistry capabilityRegistry,
            OwnerPersistenceProvider ownerPersistence,
            QueryRuntime queries) {
        this.queries = Objects.requireNonNull(queries, "queries must not be null");
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
        if (capabilityType == QueryCapability.class) {
            return capabilityType.cast(queries.capabilityFor(descriptor).orElseThrow(() -> new PluginRuntimeException(
                "Plugin '" + descriptor.identity().pluginId() + "' requires queries, but the Host sets no "
                    + "brix.query.max-deadline")));
        }
        if (capabilityType == EventBusCapability.class) {
            Optional<EventBusCapability> outbox = ownerEventBus();
            if (outbox.isPresent()) {
                return QuerySideEffects.guard(capabilityType, capabilityType.cast(outbox.get()));
            }
        }
        if (capabilityType == CommandCapability.class) {
            return QuerySideEffects.guard(capabilityType, capabilityType.cast(
                ownerPersistence.commandBusFor(descriptor.identity().pluginId())
                    .orElseThrow(() -> new PluginRuntimeException("Plugin '" + descriptor.identity().pluginId()
                        + "' has no command capability: it needs data.outbox and commands.consumes"))));
        }
        return QuerySideEffects.guard(capabilityType, capabilityRegistry.getRequired(capabilityType));
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
        if (capabilityType == QueryCapability.class) {
            return queries.capabilityFor(descriptor).map(capabilityType::cast);
        }
        if (capabilityType == EventBusCapability.class) {
            Optional<EventBusCapability> outbox = ownerEventBus();
            if (outbox.isPresent()) {
                return outbox.map(capabilityType::cast).map(bus -> QuerySideEffects.guard(capabilityType, bus));
            }
        }
        if (capabilityType == CommandCapability.class) {
            return ownerPersistence.commandBusFor(descriptor.identity().pluginId()).map(capabilityType::cast)
                .map(bus -> QuerySideEffects.guard(capabilityType, bus));
        }
        return capabilityRegistry.get(capabilityType).map(capability -> QuerySideEffects.guard(capabilityType, capability));
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

    private Optional<EventBusCapability> ownerEventBus() {
        return ownerPersistence.eventBusFor(descriptor.identity().pluginId());
    }
}
