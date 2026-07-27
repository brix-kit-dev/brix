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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.capability.registry.CapabilityDescriptor;
import io.runtime.sdk.capability.registry.CapabilityRegistry;
import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.BrixPlugin;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.PluginLifecycleState;
import io.runtime.orchestrator.endpoint.EndpointRoute;
import io.runtime.orchestrator.endpoint.PluginEndpointDispatcher;

/**
 * L2B runtime manager for v3.0.10 backend plugins.
 *
 * <p>The manager drives the Runtime Shell plugin chain:
 * ServiceLoader discovery, manifest descriptor resolution, required capability
 * validation, configure-time wiring, startup, readiness aggregation, drain, and
 * stop. It never exposes {@link CapabilityRegistry} to plugin code; plugins see
 * only a scoped {@link io.runtime.sdk.plugin.PluginContext}.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class PluginRuntimeManager {

    private static final Logger log = LoggerFactory.getLogger(PluginRuntimeManager.class);

    private final Supplier<List<BrixPlugin>> discovery;
    private final PluginRuntimeDescriptorResolver descriptorResolver;
    private final CapabilityRegistry capabilityRegistry;
    private final Set<String> requiredPluginIds;
    private final PluginEndpointDispatcher endpointDispatcher;
    private final Consumer<BrixPlugin> pluginInitializer;
    private final Map<String, ManagedPlugin> plugins = new LinkedHashMap<>();
    private boolean started;

    /**
     * Creates a runtime manager.
     *
     * @param discovery plugin discovery supplier
     * @param descriptorResolver descriptor resolver
     * @param capabilityRegistry capability registry
     * @param requiredPluginIds composition-required plugin ids
     */
    public PluginRuntimeManager(
            Supplier<List<BrixPlugin>> discovery,
            PluginRuntimeDescriptorResolver descriptorResolver,
            CapabilityRegistry capabilityRegistry,
            Collection<String> requiredPluginIds) {
        this(discovery, descriptorResolver, capabilityRegistry, requiredPluginIds, PluginEndpointDispatcher.none());
    }

    /**
     * Creates a runtime manager.
     *
     * @param discovery plugin discovery supplier
     * @param descriptorResolver descriptor resolver
     * @param capabilityRegistry capability registry
     * @param requiredPluginIds composition-required plugin ids
     * @param endpointDispatcher Runtime Shell endpoint dispatcher
     */
    public PluginRuntimeManager(
            Supplier<List<BrixPlugin>> discovery,
            PluginRuntimeDescriptorResolver descriptorResolver,
            CapabilityRegistry capabilityRegistry,
            Collection<String> requiredPluginIds,
            PluginEndpointDispatcher endpointDispatcher) {
        this(discovery, descriptorResolver, capabilityRegistry, requiredPluginIds, endpointDispatcher, plugin -> {
        });
    }

    /**
     * Creates a runtime manager.
     *
     * @param discovery plugin discovery supplier
     * @param descriptorResolver descriptor resolver
     * @param capabilityRegistry capability registry
     * @param requiredPluginIds composition-required plugin ids
     * @param endpointDispatcher Runtime Shell endpoint dispatcher
     * @param pluginInitializer Runtime-owned provider initializer
     */
    public PluginRuntimeManager(
            Supplier<List<BrixPlugin>> discovery,
            PluginRuntimeDescriptorResolver descriptorResolver,
            CapabilityRegistry capabilityRegistry,
            Collection<String> requiredPluginIds,
            PluginEndpointDispatcher endpointDispatcher,
            Consumer<BrixPlugin> pluginInitializer) {
        this.discovery = Objects.requireNonNull(discovery, "discovery must not be null");
        this.descriptorResolver = Objects.requireNonNull(descriptorResolver, "descriptorResolver must not be null");
        this.capabilityRegistry = Objects.requireNonNull(capabilityRegistry, "capabilityRegistry must not be null");
        this.requiredPluginIds = normalize(requiredPluginIds);
        this.endpointDispatcher = Objects.requireNonNull(endpointDispatcher, "endpointDispatcher must not be null");
        this.pluginInitializer = Objects.requireNonNull(pluginInitializer, "pluginInitializer must not be null");
    }

    /**
     * Discovers, resolves, wires, and starts all visible plugins.
     *
     * @return immutable runtime states after bootstrap
     * @throws PluginRuntimeException if required plugin startup cannot reach readiness
     */
    public synchronized List<PluginRuntimeState> start() {
        if (started) {
            throw new PluginRuntimeException("Runtime Shell bootstrap has already been started in this Host process");
        }
        started = true;

        List<BrixPlugin> discovered = discovery.get();
        if (discovered == null) {
            throw new PluginRuntimeException("Plugin discovery returned null");
        }
        log.info("Runtime Shell discovered {} BrixPlugin provider(s)", discovered.size());

        resolveDiscoveredPlugins(discovered);
        verifyRequiredProvidersPresent();

        for (ManagedPlugin plugin : plugins.values()) {
            startPlugin(plugin);
        }
        endpointDispatcher.replaceSnapshot(endpointRoutes());
        return states();
    }

    /**
     * Drains and stops started plugins.
     */
    public synchronized void stop() {
        List<ManagedPlugin> ordered = new ArrayList<>(plugins.values());
        for (int i = ordered.size() - 1; i >= 0; i--) {
            ManagedPlugin plugin = ordered.get(i);
            if (plugin.state.lifecycleState() == PluginLifecycleState.STOPPED) {
                continue;
            }
            plugin.state = state(plugin, PluginLifecycleState.DRAINING, false, plugin.state.health(), "Draining plugin");
            safeStop(plugin);
            plugin.state = state(plugin, PluginLifecycleState.STOPPED, false, plugin.state.health(), "Plugin stopped");
        }
        endpointDispatcher.clear();
    }

    /**
     * Returns runtime readiness.
     *
     * @return true when every required plugin is ready
     */
    public synchronized boolean ready() {
        for (String requiredPluginId : requiredPluginIds) {
            ManagedPlugin plugin = plugins.get(requiredPluginId);
            if (plugin == null || !plugin.state.ready()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns plugin runtime states.
     *
     * @return immutable runtime state list
     */
    public synchronized List<PluginRuntimeState> states() {
        return plugins.values().stream()
            .map(plugin -> plugin.state)
            .toList();
    }

    private void resolveDiscoveredPlugins(List<BrixPlugin> discovered) {
        for (BrixPlugin plugin : discovered) {
            Objects.requireNonNull(plugin, "Discovered plugin must not be null");
            pluginInitializer.accept(plugin);
            PluginRuntimeDescriptor descriptor = descriptorResolver.resolve(plugin)
                .orElseThrow(() -> new PluginRuntimeException("BrixPlugin provider "
                    + plugin.getClass().getName()
                    + " has no same-artifact META-INF/plugin-manifest.yaml/yml/json"));
            String pluginId = descriptor.identity().pluginId();
            if (plugins.containsKey(pluginId)) {
                throw new PluginRuntimeException("Duplicate BrixPlugin pluginId discovered: " + pluginId);
            }
            ManagedPlugin managed = new ManagedPlugin(plugin, descriptor);
            managed.state = state(managed, PluginLifecycleState.DISCOVERED, false,
                BrixHealth.unknown("Plugin discovered"), "Plugin discovered");
            plugins.put(pluginId, managed);
        }
    }

    private void verifyRequiredProvidersPresent() {
        Set<String> missing = new LinkedHashSet<>(requiredPluginIds);
        missing.removeAll(plugins.keySet());
        if (!missing.isEmpty()) {
            throw new PluginRuntimeException("Required BrixPlugin provider(s) missing from ServiceLoader discovery: "
                + missing);
        }
    }

    private void startPlugin(ManagedPlugin plugin) {
        try {
            validateRequiredCapabilities(plugin);
            plugin.state = state(plugin, PluginLifecycleState.RESOLVED, false,
                BrixHealth.unknown("Plugin resolved"), "Plugin manifest resolved");

            DefaultPluginBootstrapContext bootstrapContext = new DefaultPluginBootstrapContext(plugin.descriptor);
            plugin.provider.configure(bootstrapContext);
            plugin.endpointHandlers = bootstrapContext.endpoints();
            plugin.state = state(plugin, PluginLifecycleState.WIRED, false,
                BrixHealth.unknown("Plugin wired"), "Plugin bindings wired");

            plugin.provider.onStart(new DefaultPluginContext(plugin.descriptor, capabilityRegistry));
            BrixHealth health = pluginHealth(plugin);
            boolean ready = health.isReadyStatus();
            plugin.state = state(plugin, PluginLifecycleState.STARTED, ready, health, health.message());
            if (isRequired(plugin) && !ready) {
                throw new PluginRuntimeException("Required plugin '" + plugin.id()
                    + "' started but is not ready: " + health.status());
            }
        } catch (RuntimeException e) {
            handleStartupFailure(plugin, e);
        }
    }

    private void validateRequiredCapabilities(ManagedPlugin plugin) {
        List<String> missing = new ArrayList<>();
        for (String declaration : plugin.descriptor.requiredCapabilities()) {
            if (!capabilityAvailable(declaration)) {
                missing.add(declaration);
            }
        }
        if (!missing.isEmpty()) {
            throw new PluginRuntimeException("Plugin '" + plugin.id()
                + "' is missing required capabilities: " + missing);
        }
    }

    private boolean capabilityAvailable(String declaration) {
        for (Class<?> type : capabilityRegistry.getRegisteredTypes()) {
            if (declaration.equals(type.getName()) || declaration.equals(type.getSimpleName())) {
                return true;
            }
        }
        for (CapabilityDescriptor descriptor : capabilityRegistry.getAllDescriptors()) {
            if (declaration.equals(descriptor.getName()) || descriptor.getAliases().contains(declaration)) {
                return true;
            }
        }
        return false;
    }

    private BrixHealth pluginHealth(ManagedPlugin plugin) {
        BrixHealth health = plugin.provider.health();
        if (health == null) {
            throw new PluginRuntimeException("Plugin '" + plugin.id() + "' returned null health");
        }
        return health;
    }

    private void handleStartupFailure(ManagedPlugin plugin, RuntimeException cause) {
        plugin.state = state(plugin, PluginLifecycleState.FAILED, false,
            BrixHealth.down(cause.getMessage()), cause.getMessage());
        safeStop(plugin);
        plugin.state = state(plugin, PluginLifecycleState.STOPPED, false,
            BrixHealth.down(cause.getMessage()), "Plugin stopped after startup failure");
        if (isRequired(plugin)) {
            throw cause instanceof PluginRuntimeException
                ? cause
                : new PluginRuntimeException("Required plugin '" + plugin.id() + "' failed to start", cause);
        }
        log.warn("Optional plugin '{}' failed to start and was not loaded: {}", plugin.id(), cause.getMessage());
    }

    private List<EndpointRoute> endpointRoutes() {
        List<EndpointRoute> routes = new ArrayList<>();
        for (ManagedPlugin plugin : plugins.values()) {
            if (plugin.state.lifecycleState() != PluginLifecycleState.STARTED) {
                continue;
            }
            for (Map.Entry<String, EndpointHandler<?, ?>> binding : plugin.endpointHandlers.entrySet()) {
                PluginRuntimeDescriptor.EndpointDeclaration declaration =
                    plugin.descriptor.endpointDeclarations().get(binding.getKey());
                if (declaration == null) {
                    throw new PluginRuntimeException("Plugin '" + plugin.id()
                        + "' has endpoint binding without manifest declaration: " + binding.getKey());
                }
                routes.add(new EndpointRoute(
                    plugin.id(),
                    binding.getKey(),
                    declaration.method(),
                    declaration.path(),
                    declaration.accessPolicy(),
                    binding.getValue()));
            }
        }
        return List.copyOf(routes);
    }

    private void safeStop(ManagedPlugin plugin) {
        try {
            plugin.provider.onStop();
        } catch (RuntimeException e) {
            log.warn("Plugin '{}' failed during stop: {}", plugin.id(), e.getMessage());
        }
    }

    private boolean isRequired(ManagedPlugin plugin) {
        return requiredPluginIds.contains(plugin.id());
    }

    private PluginRuntimeState state(
            ManagedPlugin plugin,
            PluginLifecycleState lifecycleState,
            boolean ready,
            BrixHealth health,
            String detail) {
        return new PluginRuntimeState(plugin.descriptor.identity(), lifecycleState, ready, health, detail);
    }

    private static Set<String> normalize(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                normalized.add(id);
            }
        }
        return Set.copyOf(normalized);
    }

    /**
     * Resolves a manifest-backed runtime descriptor for a discovered plugin.
     */
    @FunctionalInterface
    public interface PluginRuntimeDescriptorResolver {

        /**
         * Resolves descriptor for the provider.
         *
         * @param plugin discovered plugin provider
         * @return descriptor when the provider has an associated manifest
         */
        Optional<PluginRuntimeDescriptor> resolve(BrixPlugin plugin);
    }

    private static final class ManagedPlugin {

        private final BrixPlugin provider;
        private final PluginRuntimeDescriptor descriptor;
        private Map<String, EndpointHandler<?, ?>> endpointHandlers = Map.of();
        private PluginRuntimeState state;

        private ManagedPlugin(BrixPlugin provider, PluginRuntimeDescriptor descriptor) {
            this.provider = provider;
            this.descriptor = descriptor;
        }

        private String id() {
            return descriptor.identity().pluginId();
        }
    }
}
