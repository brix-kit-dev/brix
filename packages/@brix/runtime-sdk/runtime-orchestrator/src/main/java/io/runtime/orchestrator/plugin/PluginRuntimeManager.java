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

import java.net.URL;
import java.security.CodeSource;
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

import io.runtime.orchestrator.internalcontract.InternalContractBinder;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.registry.CapabilityDescriptor;
import io.runtime.sdk.capability.registry.CapabilityRegistry;
import io.runtime.sdk.internalcontract.InternalContractProvider;
import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.BrixPlugin;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.PluginLifecycleState;
import io.runtime.orchestrator.endpoint.EndpointRoute;
import io.runtime.orchestrator.endpoint.EndpointRouteDeclaration;
import io.runtime.orchestrator.endpoint.EndpointRouteValidator;
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
    private final Supplier<List<InternalContractProvider>> internalProviderDiscovery;
    private final InternalContractBinder internalContracts;
    private final Map<String, ManagedPlugin> plugins = new LinkedHashMap<>();
    private boolean prepared;
    private boolean started;
    private boolean entriesPublished;

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
        this(
            discovery,
            descriptorResolver,
            capabilityRegistry,
            requiredPluginIds,
            endpointDispatcher,
            pluginInitializer,
            List::of,
            null);
    }

    /**
     * Creates a runtime manager with plugin-owned internal contract binding.
     *
     * @param discovery plugin discovery supplier
     * @param descriptorResolver descriptor resolver
     * @param capabilityRegistry capability registry
     * @param requiredPluginIds composition-required plugin ids
     * @param endpointDispatcher Runtime Shell endpoint dispatcher
     * @param pluginInitializer Runtime-owned provider initializer
     * @param internalProviderDiscovery internal contract provider discovery
     * @param internalContracts internal contract binder
     */
    public PluginRuntimeManager(
            Supplier<List<BrixPlugin>> discovery,
            PluginRuntimeDescriptorResolver descriptorResolver,
            CapabilityRegistry capabilityRegistry,
            Collection<String> requiredPluginIds,
            PluginEndpointDispatcher endpointDispatcher,
            Consumer<BrixPlugin> pluginInitializer,
            Supplier<List<InternalContractProvider>> internalProviderDiscovery,
            InternalContractBinder internalContracts) {
        this.discovery = Objects.requireNonNull(discovery, "discovery must not be null");
        this.descriptorResolver = Objects.requireNonNull(descriptorResolver, "descriptorResolver must not be null");
        this.capabilityRegistry = Objects.requireNonNull(capabilityRegistry, "capabilityRegistry must not be null");
        this.requiredPluginIds = normalize(requiredPluginIds);
        this.endpointDispatcher = Objects.requireNonNull(endpointDispatcher, "endpointDispatcher must not be null");
        this.pluginInitializer = Objects.requireNonNull(pluginInitializer, "pluginInitializer must not be null");
        this.internalProviderDiscovery = Objects.requireNonNull(
            internalProviderDiscovery,
            "internalProviderDiscovery must not be null");
        this.internalContracts = internalContracts;
    }

    /**
     * Discovers, resolves, wires, and starts all visible plugins.
     *
     * @return immutable runtime states after bootstrap
     * @throws PluginRuntimeException if required plugin startup cannot reach readiness
     */
    public synchronized List<PluginRuntimeState> start() {
        prepare();
        EndpointRouteValidator.validate(preparedRouteDeclarations());
        startPrepared();
        endpointDispatcher.replaceSnapshot(preparedRoutes());
        markPublished();
        return states();
    }

    /**
     * Executes plugin discovery and resolution while keeping Handler bindings uncreated.
     *
     * <p>This is the Host coordinator entry point used to preserve the B3
     * Plugin/Operational atomic publication boundary.</p>
     *
     * @return immutable runtime states before publication
     */
    public synchronized List<PluginRuntimeState> prepare() {
        if (prepared) {
            throw new PluginRuntimeException("Runtime Shell bootstrap has already been started in this Host process");
        }
        prepared = true;

        List<BrixPlugin> discovered = discovery.get();
        if (discovered == null) {
            throw new PluginRuntimeException("Plugin discovery returned null");
        }
        log.info("Runtime Shell discovered {} BrixPlugin provider(s)", discovered.size());

        resolveDiscoveredPlugins(discovered);
        verifyRequiredProvidersPresent();

        for (ManagedPlugin plugin : new ArrayList<>(plugins.values())) {
            resolvePlugin(plugin);
        }
        bindProvidedInternalContracts(internalProviderDiscovery.get());
        return states();
    }

    /**
     * Performs wiring and startup after the Host B2 barrier accepts all declarations.
     *
     * @return immutable runtime states before publication
     */
    public synchronized List<PluginRuntimeState> startPrepared() {
        if (!prepared || started) {
            throw new PluginRuntimeException("Plugin Runtime is not in the prepared state");
        }
        started = true;
        for (ManagedPlugin plugin : new ArrayList<>(plugins.values())) {
            if (plugin.state.lifecycleState() == PluginLifecycleState.RESOLVED) {
                startPlugin(plugin);
            }
        }
        return states();
    }

    /**
     * Returns prepared plugin routes for global Host entry reservation.
     *
     * @return immutable routes
     */
    public synchronized List<EndpointRoute> preparedRoutes() {
        return endpointRoutes();
    }

    /**
     * Returns handler-free route declarations for the Host B2 barrier.
     *
     * @return immutable route declarations
     */
    public synchronized List<EndpointRouteDeclaration> preparedRouteDeclarations() {
        List<EndpointRouteDeclaration> declarations = new ArrayList<>();
        for (ManagedPlugin plugin : plugins.values()) {
            if (plugin.state.lifecycleState() != PluginLifecycleState.RESOLVED) {
                continue;
            }
            for (PluginRuntimeDescriptor.EndpointDeclaration endpoint
                    : plugin.descriptor.endpointDeclarations().values()) {
                declarations.add(new EndpointRouteDeclaration(
                    plugin.id(),
                    endpoint.id(),
                    endpoint.method(),
                    endpoint.path()));
            }
        }
        return List.copyOf(declarations);
    }

    /**
     * Marks the complete plugin entry set published after a Host snapshot commit.
     */
    public synchronized void markPublished() {
        if (!started || entriesPublished) {
            throw new PluginRuntimeException("Plugin Runtime publication state is invalid");
        }
        for (ManagedPlugin plugin : plugins.values()) {
            if (plugin.state.lifecycleState() != PluginLifecycleState.STARTED) {
                continue;
            }
            BrixHealth health = pluginHealth(plugin);
            plugin.state = state(plugin, PluginLifecycleState.STARTED, health.isReadyStatus(), health, health.message());
        }
        entriesPublished = true;
    }

    /**
     * Drains and stops started plugins.
     */
    public synchronized void stop() {
        endpointDispatcher.clear();
        entriesPublished = false;
        List<ManagedPlugin> ordered = new ArrayList<>(plugins.values());
        for (int i = ordered.size() - 1; i >= 0; i--) {
            ManagedPlugin plugin = ordered.get(i);
            if (plugin.state.lifecycleState() == PluginLifecycleState.STOPPED) {
                continue;
            }
            boolean runtimeResourcesCreated =
                plugin.state.lifecycleState() == PluginLifecycleState.WIRED
                    || plugin.state.lifecycleState() == PluginLifecycleState.STARTED
                    || plugin.state.lifecycleState() == PluginLifecycleState.DRAINING;
            plugin.state = state(plugin, PluginLifecycleState.DRAINING, false, plugin.state.health(), "Draining plugin");
            if (runtimeResourcesCreated) {
                safeStop(plugin);
            }
            plugin.state = state(plugin, PluginLifecycleState.STOPPED, false, plugin.state.health(), "Plugin stopped");
        }
    }

    /**
     * Returns runtime readiness.
     *
     * @return true when every required plugin is ready
     */
    public synchronized boolean ready() {
        if (!entriesPublished) {
            return requiredPluginIds.isEmpty() && plugins.isEmpty();
        }
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

    private void resolvePlugin(ManagedPlugin plugin) {
        try {
            validateRequiredCapabilities(plugin);
            validateReliableEventDeclarations(plugin);
            plugin.state = state(plugin, PluginLifecycleState.RESOLVED, false,
                BrixHealth.unknown("Plugin resolved"), "Plugin manifest resolved");
        } catch (RuntimeException e) {
            handleStartupFailure(plugin, e);
        }
    }

    private void startPlugin(ManagedPlugin plugin) {
        try {
            DefaultPluginBootstrapContext bootstrapContext = new DefaultPluginBootstrapContext(plugin.descriptor);
            plugin.provider.configure(bootstrapContext);
            plugin.endpointHandlers = bootstrapContext.endpoints();
            plugin.state = state(plugin, PluginLifecycleState.WIRED, false,
                BrixHealth.unknown("Plugin wired"), "Plugin bindings wired");

            plugin.provider.onStart(new DefaultPluginContext(plugin.descriptor, capabilityRegistry));
            BrixHealth health = pluginHealth(plugin);
            plugin.state = state(plugin, PluginLifecycleState.STARTED, false, health, health.message());
            if (isRequired(plugin) && !health.isReadyStatus()) {
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

    private void validateReliableEventDeclarations(ManagedPlugin plugin) {
        boolean persistentPublisher = false;
        for (PluginRuntimeDescriptor.EventPublication publication
                : plugin.descriptor.eventPublications().values()) {
            if (publication.id().isBlank()) {
                throw new PluginRuntimeException("Plugin '" + plugin.id()
                    + "' declares a blank published event id");
            }
            if (publication.version().isBlank()) {
                throw new PluginRuntimeException("Plugin '" + plugin.id()
                    + "' declares event '" + publication.id() + "' without version");
            }
            persistentPublisher = persistentPublisher || publication.requiresPersistentDelivery();
        }

        PluginRuntimeDescriptor.DataDeclaration data = plugin.descriptor.data();
        if (persistentPublisher) {
            if (data.storageId().isBlank() || data.outbox().isBlank()) {
                throw new PluginRuntimeException("Plugin '" + plugin.id()
                    + "' publishes CRITICAL/STANDARD events without data.storageId/data.outbox");
            }
            if (!plugin.descriptor.isRequiredCapability(EventBusCapability.class, capabilityRegistry)) {
                throw new PluginRuntimeException("Plugin '" + plugin.id()
                    + "' publishes CRITICAL/STANDARD events without required EventBusCapability declaration");
            }
            if (!capabilityAvailable(EventBusCapability.class.getName())) {
                throw new PluginRuntimeException("Plugin '" + plugin.id()
                    + "' publishes CRITICAL/STANDARD events but EventBusCapability is not registered");
            }
        }

        if (!plugin.descriptor.eventSubscriptions().isEmpty()
                && (data.storageId().isBlank() || data.inbox().isBlank())) {
            throw new PluginRuntimeException("Plugin '" + plugin.id()
                + "' subscribes to events without data.storageId/data.inbox");
        }
        for (PluginRuntimeDescriptor.EventSubscription subscription
                : plugin.descriptor.eventSubscriptions().values()) {
            if (subscription.subscriptionId().isBlank()
                    || subscription.eventType().isBlank()
                    || subscription.schemaRange().isBlank()
                    || subscription.handlerId().isBlank()
                    || subscription.retryPolicyRef().isBlank()
                    || subscription.idempotencyPolicyRef().isBlank()) {
                throw new PluginRuntimeException("Plugin '" + plugin.id()
                    + "' declares incomplete event subscription policy");
            }
        }
    }

    private void bindProvidedInternalContracts(List<InternalContractProvider> providers) {
        List<InternalContractProvider> available = providers == null ? List.of() : List.copyOf(providers);
        for (ManagedPlugin plugin : plugins.values()) {
            boolean provides = !plugin.descriptor.providedInternalContracts().isEmpty();
            List<InternalContractProvider> sameArtifact = available.stream()
                .filter(provider -> codeSource(plugin.provider.getClass()).equals(codeSource(provider.getClass())))
                .toList();
            if (provides && internalContracts == null) {
                throw new PluginRuntimeException(
                    "Plugin '" + plugin.id() + "' provides internal contracts without an L2B binder");
            }
            if (provides && sameArtifact.size() != 1) {
                throw new PluginRuntimeException(sameArtifact.isEmpty()
                    ? "Providing plugin artifact must publish exactly one InternalContractProvider"
                    : "Providing plugin artifact published duplicate InternalContractProvider instances");
            }
            if (!provides && !sameArtifact.isEmpty()) {
                throw new PluginRuntimeException("Non-providing plugin artifact published an InternalContractProvider");
            }
            if (provides) {
                internalContracts.bindPlugin(
                    plugin.descriptor,
                    sameArtifact.get(0),
                    providedInternalContractTypes(plugin));
            }
        }
    }

    private Set<Class<?>> providedInternalContractTypes(ManagedPlugin plugin) {
        Set<Class<?>> types = new LinkedHashSet<>();
        ClassLoader loader = plugin.provider.getClass().getClassLoader();
        for (PluginRuntimeDescriptor.ProvidedInternalContract declaration
                : plugin.descriptor.providedInternalContracts().values()) {
            try {
                types.add(Class.forName(declaration.contractType(), false, loader));
            } catch (ClassNotFoundException e) {
                throw new PluginRuntimeException(
                    "Plugin '" + plugin.id() + "' internal contract type is unavailable: "
                        + declaration.contractType(),
                    e);
            }
        }
        return Set.copyOf(types);
    }

    private static URL codeSource(Class<?> providerType) {
        CodeSource source = providerType.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            throw new PluginRuntimeException("Provider has no code source: " + providerType.getName());
        }
        return source.getLocation();
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
        boolean providerStarted = plugin.state.lifecycleState() == PluginLifecycleState.WIRED
            || plugin.state.lifecycleState() == PluginLifecycleState.STARTED
            || plugin.state.lifecycleState() == PluginLifecycleState.DRAINING;
        plugin.state = state(plugin, PluginLifecycleState.FAILED, false,
            BrixHealth.down(cause.getMessage()), cause.getMessage());
        if (providerStarted) {
            safeStop(plugin);
        }
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
