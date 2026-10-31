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
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ResolvableType;

import io.runtime.orchestrator.endpoint.EndpointRoute;
import io.runtime.orchestrator.endpoint.EndpointRouteDeclaration;
import io.runtime.orchestrator.endpoint.EndpointRouteValidator;
import io.runtime.orchestrator.endpoint.PluginEndpointDispatcher;
import io.runtime.orchestrator.endpoint.TenantGrantTable;
import io.runtime.orchestrator.command.CommandPolicies;
import io.runtime.orchestrator.command.CommandProviders;
import io.runtime.orchestrator.event.EventPolicies;
import io.runtime.orchestrator.internalcontract.BrixRange;
import io.runtime.orchestrator.internalcontract.InternalContractBinder;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.query.PublishedQuery;
import io.runtime.orchestrator.query.QueryRuntime;
import io.runtime.orchestrator.task.TaskPolicies;
import io.runtime.orchestrator.task.TaskProvider;
import io.runtime.orchestrator.task.TaskProviders;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.CommandCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.QueryCapability;
import io.runtime.sdk.capability.registry.CapabilityDescriptor;
import io.runtime.sdk.capability.registry.CapabilityRegistry;
import io.runtime.sdk.event.EventReliability;
import io.runtime.sdk.internalcontract.InternalContractProvider;
import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.BrixPlugin;
import io.runtime.sdk.plugin.CommandHandler;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EventHandler;
import io.runtime.sdk.plugin.HandlerFactory;
import io.runtime.sdk.plugin.ManagedTask;
import io.runtime.sdk.plugin.PluginContext;
import io.runtime.sdk.plugin.PluginLifecycleState;
import io.runtime.sdk.plugin.QueryHandler;
import io.runtime.sdk.plugin.TaskInvocation;
import io.runtime.sdk.plugin.TypedEndpointHandler;

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
    private final OwnerPersistenceProvider ownerPersistence;
    private final QueryRuntime queries;
    private final TaskPolicies.Limits taskLimits;

    private static final tools.jackson.databind.ObjectMapper SCHEMA_READER = new tools.jackson.databind.ObjectMapper();
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
        this(
            discovery,
            descriptorResolver,
            capabilityRegistry,
            requiredPluginIds,
            endpointDispatcher,
            pluginInitializer,
            internalProviderDiscovery,
            internalContracts,
            OwnerPersistenceProvider.none());
    }

    /**
     * Creates a runtime manager with internal contract binding and owner persistence.
     *
     * @param discovery plugin discovery supplier
     * @param descriptorResolver descriptor resolver
     * @param capabilityRegistry capability registry
     * @param requiredPluginIds composition-required plugin ids
     * @param endpointDispatcher Runtime Shell endpoint dispatcher
     * @param pluginInitializer Runtime-owned provider initializer
     * @param internalProviderDiscovery internal contract provider discovery
     * @param internalContracts internal contract binder
     * @param ownerPersistence source of owner-scoped persistence capabilities
     */
    public PluginRuntimeManager(
            Supplier<List<BrixPlugin>> discovery,
            PluginRuntimeDescriptorResolver descriptorResolver,
            CapabilityRegistry capabilityRegistry,
            Collection<String> requiredPluginIds,
            PluginEndpointDispatcher endpointDispatcher,
            Consumer<BrixPlugin> pluginInitializer,
            Supplier<List<InternalContractProvider>> internalProviderDiscovery,
            InternalContractBinder internalContracts,
            OwnerPersistenceProvider ownerPersistence) {
        this(discovery, descriptorResolver, capabilityRegistry, requiredPluginIds, endpointDispatcher, pluginInitializer,
            internalProviderDiscovery, internalContracts, ownerPersistence, QueryRuntime.unconfigured());
    }

    /**
     * Creates a runtime manager whose plugins may provide and call typed queries.
     *
     * @param discovery plugin discovery supplier
     * @param descriptorResolver descriptor resolver
     * @param capabilityRegistry capability registry
     * @param requiredPluginIds composition-required plugin ids
     * @param endpointDispatcher Runtime Shell endpoint dispatcher
     * @param pluginInitializer Runtime-owned provider initializer
     * @param internalProviderDiscovery internal contract provider discovery
     * @param internalContracts internal contract binder
     * @param ownerPersistence source of owner-scoped persistence capabilities
     * @param queries the typed queries of this host (root BRX-RS-MESSAGE-002 to MESSAGE-009)
     */
    public PluginRuntimeManager(
            Supplier<List<BrixPlugin>> discovery,
            PluginRuntimeDescriptorResolver descriptorResolver,
            CapabilityRegistry capabilityRegistry,
            Collection<String> requiredPluginIds,
            PluginEndpointDispatcher endpointDispatcher,
            Consumer<BrixPlugin> pluginInitializer,
            Supplier<List<InternalContractProvider>> internalProviderDiscovery,
            InternalContractBinder internalContracts,
            OwnerPersistenceProvider ownerPersistence,
            QueryRuntime queries) {
        this(discovery, descriptorResolver, capabilityRegistry, requiredPluginIds, endpointDispatcher, pluginInitializer,
            internalProviderDiscovery, internalContracts, ownerPersistence, queries, TaskPolicies.Limits.defaults());
    }

    /**
     * Creates a runtime manager whose plugins may provide and call typed queries and declare tasks.
     *
     * @param discovery plugin discovery supplier
     * @param descriptorResolver descriptor resolver
     * @param capabilityRegistry capability registry
     * @param requiredPluginIds composition-required plugin ids
     * @param endpointDispatcher Runtime Shell endpoint dispatcher
     * @param pluginInitializer Runtime-owned provider initializer
     * @param internalProviderDiscovery internal contract provider discovery
     * @param internalContracts internal contract binder
     * @param ownerPersistence source of owner-scoped persistence capabilities
     * @param queries the typed queries of this host (root BRX-RS-MESSAGE-002 to MESSAGE-009)
     * @param taskLimits the host's limits on tasks, which a task's policies are checked against at startup
     */
    public PluginRuntimeManager(
            Supplier<List<BrixPlugin>> discovery,
            PluginRuntimeDescriptorResolver descriptorResolver,
            CapabilityRegistry capabilityRegistry,
            Collection<String> requiredPluginIds,
            PluginEndpointDispatcher endpointDispatcher,
            Consumer<BrixPlugin> pluginInitializer,
            Supplier<List<InternalContractProvider>> internalProviderDiscovery,
            InternalContractBinder internalContracts,
            OwnerPersistenceProvider ownerPersistence,
            QueryRuntime queries,
            TaskPolicies.Limits taskLimits) {
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
        this.ownerPersistence = Objects.requireNonNull(ownerPersistence, "ownerPersistence must not be null");
        this.queries = Objects.requireNonNull(queries, "queries must not be null");
        this.taskLimits = Objects.requireNonNull(taskLimits, "taskLimits must not be null");
    }

    /**
     * Discovers, resolves, wires, and starts all visible plugins.
     *
     * @return immutable runtime states after bootstrap
     * @throws PluginRuntimeException if required plugin startup cannot reach readiness
     */
    public synchronized List<PluginRuntimeState> start() {
        prepare();
        List<EndpointRouteDeclaration> declarations = preparedRouteDeclarations();
        EndpointRouteValidator.validatePluginRoutes(declarations);
        EndpointRouteValidator.validate(declarations);
        TenantGrantTable grants = preparedGrantTable();
        startPrepared();
        endpointDispatcher.replaceSnapshot(preparedRoutes(), grants);
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
        validateEventContractsAcrossPlugins();
        validateCommandContractsAcrossPlugins();
        validateQueryContractsAcrossPlugins();
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
     * Builds the tenant grant table of the resolved plugins for the Host B2 barrier.
     *
     * <p>Collects every permission catalog entry and endpoint access policy of
     * the resolved plugins and validates them together, so an invalid grant or
     * an endpoint that references an undeclared permission stops startup before
     * any entry is published.</p>
     *
     * @return immutable grant table of this entry generation
     */
    public synchronized TenantGrantTable preparedGrantTable() {
        List<TenantGrantTable.PermissionDeclaration> permissions = new ArrayList<>();
        List<TenantGrantTable.EndpointPolicyDeclaration> endpoints = new ArrayList<>();
        for (ManagedPlugin plugin : plugins.values()) {
            if (plugin.state.lifecycleState() != PluginLifecycleState.RESOLVED) {
                continue;
            }
            for (PluginRuntimeDescriptor.PermissionDeclaration permission
                    : plugin.descriptor.permissionDeclarations()) {
                permissions.add(new TenantGrantTable.PermissionDeclaration(
                    plugin.id(), permission.id(), permission.grants()));
            }
            for (PluginRuntimeDescriptor.EndpointDeclaration endpoint
                    : plugin.descriptor.endpointDeclarations().values()) {
                if (endpoint.accessPolicy() != null) {
                    endpoints.add(new TenantGrantTable.EndpointPolicyDeclaration(
                        plugin.id(), endpoint.id(), endpoint.accessPolicy()));
                }
            }
        }
        return TenantGrantTable.build(permissions, endpoints);
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
        queries.publish(publishedQueries());
        entriesPublished = true;
    }

    /**
     * Drains and stops started plugins.
     */
    public synchronized void stop() {
        endpointDispatcher.clear();
        queries.close();
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
            // Root BRX-RS-LIFE-002: a required query provider that is not available makes its consumer not ready.
            if (!queries.requiredProvidersAvailable(plugin.descriptor, this::pluginReady)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the event handlers of started plugins that subscribe to an event type, with the
     * subscription each handler is bound for.
     *
     * @param eventType event contract id
     * @return the bound subscriptions
     */
    public synchronized List<BoundEventHandler> eventHandlers(String eventType) {
        List<BoundEventHandler> bound = new ArrayList<>();
        for (ManagedPlugin plugin : plugins.values()) {
            if (plugin.state.lifecycleState() != PluginLifecycleState.STARTED) {
                continue;
            }
            for (Map.Entry<String, CreatedEventHandler> binding : plugin.eventHandlers.entrySet()) {
                PluginRuntimeDescriptor.EventSubscription subscription =
                    plugin.descriptor.eventSubscriptions().get(binding.getKey());
                if (subscription != null && subscription.eventType().equals(eventType)) {
                    bound.add(new BoundEventHandler(plugin.id(), subscription, binding.getValue().handler(),
                        binding.getValue().payloadType()));
                }
            }
        }
        return List.copyOf(bound);
    }

    /**
     * Returns the plugin whose manifest declares the event type under {@code events.publishes}.
     *
     * @param eventType event type
     * @return the only declaring plugin, or empty when none or several declare it
     */
    public synchronized Optional<String> eventPublisher(String eventType) {
        List<String> publishers = plugins.values().stream()
            .filter(plugin -> plugin.descriptor.eventPublications().containsKey(eventType))
            .map(ManagedPlugin::id)
            .toList();
        return publishers.size() == 1 ? Optional.of(publishers.get(0)) : Optional.empty();
    }

    /**
     * Returns every manifest subscription to the event type, whatever state its plugin is in, with the
     * handler once the plugin has started and bound it.
     *
     * @param eventType event type
     * @return the declared subscriptions
     */
    public synchronized List<EventSubscriber> eventSubscribers(String eventType) {
        return eventSubscribers(subscription -> subscription.eventType().equals(eventType));
    }

    /**
     * Returns every manifest subscription of every event type, whatever state its plugin is in, with the
     * handler once the plugin has started and bound it.
     *
     * @return the declared subscriptions
     */
    public synchronized List<EventSubscriber> eventSubscribers() {
        return eventSubscribers(subscription -> true);
    }

    /**
     * Returns the reliability the plugin that publishes the event type declares for it.
     *
     * @param eventType event type
     * @return the declared reliability, or empty when no plugin, or more than one, declares the type
     */
    public synchronized Optional<EventReliability> eventReliability(String eventType) {
        List<PluginRuntimeDescriptor.EventPublication> declared = plugins.values().stream()
            .map(plugin -> plugin.descriptor.eventPublications().get(eventType))
            .filter(Objects::nonNull)
            .toList();
        return declared.size() == 1 ? Optional.of(declared.get(0).reliability()) : Optional.empty();
    }

    /**
     * Returns the event types the plugins declare under {@code events.publishes}.
     *
     * @return the published event types
     */
    public synchronized Set<String> eventPublications() {
        Set<String> published = new LinkedHashSet<>();
        plugins.values().forEach(plugin -> published.addAll(plugin.descriptor.eventPublications().keySet()));
        return Set.copyOf(published);
    }

    private List<EventSubscriber> eventSubscribers(Predicate<PluginRuntimeDescriptor.EventSubscription> wanted) {
        List<EventSubscriber> declared = new ArrayList<>();
        for (ManagedPlugin plugin : plugins.values()) {
            boolean started = plugin.state.lifecycleState() == PluginLifecycleState.STARTED;
            for (PluginRuntimeDescriptor.EventSubscription subscription : plugin.descriptor.eventSubscriptions().values()) {
                if (!wanted.test(subscription)) {
                    continue;
                }
                CreatedEventHandler created = started ? plugin.eventHandlers.get(subscription.handlerId()) : null;
                declared.add(new EventSubscriber(plugin.id(), subscription, created == null
                    ? null
                    : new BoundEventHandler(plugin.id(), subscription, created.handler(), created.payloadType())));
            }
        }
        return List.copyOf(declared);
    }

    /**
     * A manifest subscription and, once bound, its handler.
     *
     * @param pluginId subscriber plugin
     * @param subscription the manifest subscription
     * @param handler the bound handler, or {@code null} while the plugin is not started or has not bound it
     */
    public record EventSubscriber(
            String pluginId,
            PluginRuntimeDescriptor.EventSubscription subscription,
            BoundEventHandler handler) {
    }

    /**
     * An event handler a plugin bound for one of its manifest subscriptions.
     *
     * @param pluginId owning plugin
     * @param subscription the manifest subscription
     * @param handler the handler the plugin's factory created
     * @param payloadType the payload type the handler declares
     */
    public record BoundEventHandler(
            String pluginId,
            PluginRuntimeDescriptor.EventSubscription subscription,
            EventHandler<?> handler,
            Class<?> payloadType) {
    }

    /**
     * Creates each bound event handler with the plugin's scoped context (root BRX-RS-CAP-025) and reads the
     * payload type it declares. A handler that does not name a concrete payload type stops the plugin.
     */
    private static Map<String, CreatedEventHandler> createEventHandlers(
            String pluginId, Map<String, HandlerFactory<? extends EventHandler<?>>> factories, PluginContext context) {
        Map<String, CreatedEventHandler> created = new LinkedHashMap<>();
        for (Map.Entry<String, HandlerFactory<? extends EventHandler<?>>> factory : factories.entrySet()) {
            EventHandler<?> handler = Objects.requireNonNull(factory.getValue().create(context),
                () -> "Event handler factory '" + factory.getKey() + "' of plugin '" + pluginId + "' created no handler");
            Class<?> payloadType = ResolvableType.forClass(handler.getClass())
                .as(EventHandler.class).getGeneric(0).resolve();
            if (payloadType == null || payloadType == Object.class) {
                throw new PluginRuntimeException("Event handler '" + factory.getKey() + "' of plugin '" + pluginId
                    + "' must be a class that names its payload type, EventHandler<PayloadType>");
            }
            created.put(factory.getKey(), new CreatedEventHandler(handler, payloadType));
        }
        return Map.copyOf(created);
    }

    private record CreatedEventHandler(EventHandler<?> handler, Class<?> payloadType) {
    }

    private record CreatedCommandHandler(CommandHandler<?> handler, Class<?> payloadType) {
    }

    /** Reads the payload type each command handler declares; one that names none stops the plugin. */
    private static Map<String, CreatedCommandHandler> typedCommandHandlers(
            String pluginId, Map<String, CommandHandler<?>> handlers) {
        Map<String, CreatedCommandHandler> typed = new LinkedHashMap<>();
        handlers.forEach((handlerId, handler) -> {
            Class<?> payloadType = ResolvableType.forClass(handler.getClass())
                .as(CommandHandler.class).getGeneric(0).resolve();
            if (payloadType == null || payloadType == Object.class) {
                throw new PluginRuntimeException("Command handler '" + handlerId + "' of plugin '" + pluginId
                    + "' must be a class that names its payload type, CommandHandler<PayloadType>");
            }
            typed.put(handlerId, new CreatedCommandHandler(handler, payloadType));
        });
        return Map.copyOf(typed);
    }

    /** Every provided command needs its handler bound in {@code configure} (root BRX-RS-MANIFEST-013). */
    private static void requireBoundCommandHandlers(ManagedPlugin plugin, DefaultPluginBootstrapContext bootstrap) {
        Set<String> bound = new LinkedHashSet<>(bootstrap.commandHandlers().keySet());
        bound.addAll(bootstrap.commandHandlerFactories().keySet());
        List<String> unbound = plugin.descriptor.providedCommands().values().stream()
            .map(PluginRuntimeDescriptor.ProvidedCommand::handlerId)
            .filter(handlerId -> !bound.contains(handlerId))
            .sorted()
            .toList();
        if (!unbound.isEmpty()) {
            throw new PluginRuntimeException("Plugin '" + plugin.id()
                + "' binds no handler factory for its provided command(s) " + unbound);
        }
    }

    /**
     * Root BRX-RS-MESSAGE-010, BOOT-005: a command type has one provider; a plugin that requires a command finds
     * its provider, and the versions it sends include the one the provider takes.
     */
    private void validateCommandContractsAcrossPlugins() {
        Map<String, List<ManagedPlugin>> providers = new LinkedHashMap<>();
        for (ManagedPlugin plugin : plugins.values()) {
            if (plugin.state.lifecycleState() != PluginLifecycleState.RESOLVED) {
                continue;
            }
            for (String commandType : plugin.descriptor.providedCommands().keySet()) {
                providers.computeIfAbsent(commandType, ignored -> new ArrayList<>()).add(plugin);
            }
        }
        providers.forEach((commandType, declaring) -> {
            if (declaring.size() > 1) {
                List<String> ids = declaring.stream().map(ManagedPlugin::id).sorted().toList();
                for (ManagedPlugin plugin : declaring) {
                    handleStartupFailure(plugin, new PluginRuntimeException("Command type " + commandType
                        + " is provided by more than one plugin " + ids + "; a command has one owner"));
                }
            }
        });
        for (ManagedPlugin consumer : new ArrayList<>(plugins.values())) {
            if (consumer.state.lifecycleState() != PluginLifecycleState.RESOLVED) {
                continue;
            }
            try {
                for (PluginRuntimeDescriptor.ConsumedCommand consumed : consumer.descriptor.consumedCommands().values()) {
                    List<ManagedPlugin> declaring = providers.getOrDefault(consumed.commandType(), List.of());
                    if (declaring.size() != 1) {
                        if (consumed.required()) {
                            throw new PluginRuntimeException("Plugin '" + consumer.id() + "' requires command "
                                + consumed.commandType() + ", which no plugin on this host provides");
                        }
                        continue;
                    }
                    String version = declaring.get(0).descriptor.providedCommands().get(consumed.commandType()).version();
                    if (!version.isBlank() && !consumed.versionRange().isBlank()
                            && !BrixRange.contains(version, consumed.versionRange())) {
                        throw new PluginRuntimeException("Plugin '" + consumer.id() + "' sends " + consumed.commandType()
                            + " " + consumed.versionRange() + ", but '" + declaring.get(0).id() + "' takes " + version);
                    }
                }
            } catch (RuntimeException e) {
                handleStartupFailure(consumer, e);
            }
        }
    }

    /**
     * Root BRX-RS-MESSAGE-005, BOOT-004, BOOT-005, LIFE-011: every query declaration resolves, a query has one
     * provider, a required query finds its provider, and required queries form no cycle.
     */
    private void validateQueryContractsAcrossPlugins() {
        Map<String, PluginRuntimeDescriptor> resolved = new LinkedHashMap<>();
        for (ManagedPlugin plugin : plugins.values()) {
            if (plugin.state.lifecycleState() == PluginLifecycleState.RESOLVED) {
                resolved.put(plugin.id(), plugin.descriptor);
            }
        }
        if (resolved.values().stream().allMatch(descriptor -> descriptor.providedQueries().isEmpty()
                && descriptor.consumedQueries().isEmpty())) {
            return;
        }
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = PluginRuntimeManager.class.getClassLoader();
        }
        queries.resolve(resolved, loader).forEach((pluginId, reason) ->
            handleStartupFailure(plugins.get(pluginId), new PluginRuntimeException(reason)));
    }

    /** The queries of the started plugins, published with the Host entry generation (root A-25). */
    private Map<String, PublishedQuery> publishedQueries() {
        Map<String, PublishedQuery> published = new LinkedHashMap<>();
        for (ManagedPlugin plugin : plugins.values()) {
            if (plugin.state.lifecycleState() == PluginLifecycleState.STARTED) {
                published.putAll(plugin.queries);
            }
        }
        return published;
    }

    private boolean pluginReady(String pluginId) {
        ManagedPlugin plugin = plugins.get(pluginId);
        return plugin != null && plugin.state.lifecycleState() == PluginLifecycleState.STARTED && plugin.state.ready();
    }

    /**
     * Returns the plugin that provides a command type and, once it has started, its handler.
     *
     * @param commandType the command type
     * @return the provider, or empty when none or several provide it
     */
    public synchronized Optional<CommandProvider> commandProvider(String commandType) {
        List<CommandProvider> found = commandProviders().stream()
            .filter(provider -> provider.command().commandType().equals(commandType))
            .toList();
        return found.size() == 1 ? Optional.of(found.get(0)) : Optional.empty();
    }

    /**
     * Returns every provided command, with its handler once its plugin has started.
     *
     * @return the providers
     */
    public synchronized List<CommandProvider> commandProviders() {
        List<CommandProvider> providers = new ArrayList<>();
        for (ManagedPlugin plugin : plugins.values()) {
            boolean started = plugin.state.lifecycleState() == PluginLifecycleState.STARTED;
            for (PluginRuntimeDescriptor.ProvidedCommand command : plugin.descriptor.providedCommands().values()) {
                CreatedCommandHandler created = started ? plugin.commandHandlers.get(command.handlerId()) : null;
                providers.add(new CommandProvider(plugin.id(), command,
                    created == null ? null : created.handler(), created == null ? null : created.payloadType()));
            }
        }
        return List.copyOf(providers);
    }

    /**
     * Returns the declaration under which a plugin submits a command type.
     *
     * @param pluginId the submitting plugin
     * @param commandType the command type
     * @return the declaration, or empty when the plugin does not declare it
     */
    public synchronized Optional<PluginRuntimeDescriptor.ConsumedCommand> consumedCommand(String pluginId, String commandType) {
        ManagedPlugin plugin = plugins.get(pluginId);
        return plugin == null ? Optional.empty() : Optional.ofNullable(plugin.descriptor.consumedCommands().get(commandType));
    }

    /**
     * A provided command and, once its plugin has started, its handler.
     *
     * @param pluginId the providing plugin
     * @param command the manifest declaration
     * @param handler the handler its factory created, or {@code null} while the plugin is not started
     * @param payloadType the payload type the handler declares, or {@code null} with no handler
     */
    public record CommandProvider(
            String pluginId,
            PluginRuntimeDescriptor.ProvidedCommand command,
            CommandHandler<?> handler,
            Class<?> payloadType) {
    }

    private static <H> Map<String, H> create(
            String pluginId, String kind, Map<String, ? extends HandlerFactory<? extends H>> factories,
            PluginContext context) {
        Map<String, H> created = new LinkedHashMap<>();
        for (Map.Entry<String, ? extends HandlerFactory<? extends H>> factory : factories.entrySet()) {
            H handler = factory.getValue().create(context);
            if (handler == null) {
                throw new PluginRuntimeException("The " + kind + " handler factory '" + factory.getKey()
                    + "' of plugin '" + pluginId + "' created no handler");
            }
            created.put(factory.getKey(), handler);
        }
        return created;
    }

    /**
     * Every manifest subscription needs its handler factory bound in {@code configure} (root
     * BRX-RS-MANIFEST-013); an unbound one would leave its events waiting in the publisher's outbox.
     */
    private static void requireBoundEventSubscriptions(ManagedPlugin plugin, Set<String> boundHandlerIds) {
        List<String> unbound = plugin.descriptor.eventSubscriptions().keySet().stream()
            .filter(handlerId -> !boundHandlerIds.contains(handlerId))
            .sorted()
            .toList();
        if (!unbound.isEmpty()) {
            throw new PluginRuntimeException("Plugin '" + plugin.id()
                + "' binds no handler factory for its event subscription(s) " + unbound);
        }
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
            validateTasks(plugin);
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
            requireBoundEventSubscriptions(plugin, bootstrapContext.eventHandlerFactories().keySet());
            requireBoundCommandHandlers(plugin, bootstrapContext);
            requireBoundTasks(plugin, bootstrapContext);
            plugin.state = state(plugin, PluginLifecycleState.WIRED, false,
                BrixHealth.unknown("Plugin wired"), "Plugin bindings wired");

            ownerPersistence.requireMessagingTables(plugin.id());
            PluginContext context = new DefaultPluginContext(plugin.descriptor, capabilityRegistry, ownerPersistence,
                queries, CommandProviders.of(this));
            plugin.provider.onStart(context);
            plugin.eventHandlers = createEventHandlers(plugin.id(), bootstrapContext.eventHandlerFactories(), context);
            // Root BRX-RS-MANIFEST-019: every other handler is created by its factory with the plugin's context.
            Map<String, EndpointHandler<?, ?>> endpointHandlers = new LinkedHashMap<>(bootstrapContext.endpoints());
            endpointHandlers.putAll(create(plugin.id(), "endpoint", bootstrapContext.endpointHandlerFactories(), context));
            plugin.endpointHandlers = Map.copyOf(endpointHandlers);
            requireTypedEndpointSchemas(plugin);
            Map<String, CommandHandler<?>> commandHandlers = new LinkedHashMap<>(bootstrapContext.commandHandlers());
            commandHandlers.putAll(create(plugin.id(), "command", bootstrapContext.commandHandlerFactories(), context));
            plugin.commandHandlers = typedCommandHandlers(plugin.id(), commandHandlers);
            Map<String, QueryHandler<?, ?>> queryHandlers = new LinkedHashMap<>(bootstrapContext.queryHandlers());
            queryHandlers.putAll(create(plugin.id(), "query", bootstrapContext.queryHandlerFactories(), context));
            plugin.queries = queries.bind(plugin.descriptor, queryHandlers);
            Map<String, ManagedTask> tasks = new LinkedHashMap<>(bootstrapContext.tasks());
            tasks.putAll(create(plugin.id(), "task", bootstrapContext.taskFactories(), context));
            plugin.tasks = executableTasks(plugin.id(), tasks);
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
            boolean available = declaresPersistence(declaration)
                ? ownerPersistence.forOwner(plugin.id()).isPresent()
                : declaresQueries(declaration) ? queries.configured()
                : declaresCommands(declaration) ? ownerPersistence.commandBusFor(plugin.id()).isPresent()
                : capabilityAvailable(declaration);
            if (!available) {
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

        boolean keepsInbox = plugin.descriptor.eventSubscriptions().values().stream()
            .anyMatch(PluginRuntimeDescriptor.EventSubscription::persistentInbox);
        if (keepsInbox && (data.storageId().isBlank() || data.inbox().isBlank())) {
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
            validateIdempotencyPolicy(plugin, subscription);
            validateSubscriptionContract(plugin, subscription);
        }
        validatePublicationContracts(plugin);
        for (PluginRuntimeDescriptor.ProvidedCommand command : plugin.descriptor.providedCommands().values()) {
            validateCommandPolicies(plugin, command);
        }
        for (PluginRuntimeDescriptor.ConsumedCommand command : plugin.descriptor.consumedCommands().values()) {
            validateConsumedCommandPolicies(plugin, command);
        }
    }

    /**
     * A task names one platform policy of each kind and is due no more often than this host allows (root
     * BRX-RS-MANIFEST-025, MANIFEST-026; standard-link plan L6.2). One that claims its triggers needs the plugin's
     * task-run table, so its {@code data.storageId}; one that runs in a unit of work needs the plugin's persistence
     * (root BRX-RS-DATA-028).
     */
    private void validateTasks(ManagedPlugin plugin) {
        for (PluginRuntimeDescriptor.ProvidedTask task : plugin.descriptor.providedTasks().values()) {
            String where = "Plugin '" + plugin.id() + "' task '" + task.taskId() + "'";
            TaskPolicies.Resolved resolved;
            try {
                resolved = TaskPolicies.resolve(task, taskLimits);
            } catch (IllegalArgumentException refused) {
                throw new PluginRuntimeException(where + ": " + refused.getMessage(), refused);
            }
            if (resolved.claimed() && plugin.descriptor.data().storageId().isBlank()) {
                throw new PluginRuntimeException(where + " claims each trigger in the plugin's task-run table, "
                    + "which needs data.storageId");
            }
            if (!PluginRuntimeDescriptor.ProvidedTask.TRANSACTION_NONE.equals(resolved.transaction())
                    && ownerPersistence.forOwner(plugin.id()).isEmpty()) {
                throw new PluginRuntimeException(where + " runs in the plugin's unit of work, but the plugin has no "
                    + "persistence unit; a task that touches no data declares transaction: none");
            }
        }
    }

    /**
     * A typed endpoint's request and response schemas are on the plugin's class path (root BRX-RS-MANIFEST-018;
     * standard-link plan L7.2): the runtime checks every request and response of it against them.
     */
    private static void requireTypedEndpointSchemas(ManagedPlugin plugin) {
        ClassLoader classes = plugin.provider.getClass().getClassLoader();
        plugin.endpointHandlers.forEach((endpointId, handler) -> {
            PluginRuntimeDescriptor.EndpointDeclaration declaration =
                plugin.descriptor.endpointDeclarations().get(endpointId);
            if (!(handler instanceof TypedEndpointHandler<?, ?>) || declaration == null) {
                return;
            }
            requireSchema(plugin, endpointId, "request", declaration.contract().requestSchemaRef(), classes);
            if (declaration.contract().status() != 204) {
                requireSchema(plugin, endpointId, "response", declaration.contract().responseSchemaRef(), classes);
            }
        });
    }

    private static void requireSchema(ManagedPlugin plugin, String endpointId, String kind, String schemaRef,
            ClassLoader classes) {
        String resource = EventPolicies.schemaResource(schemaRef);
        if (resource == null || classes.getResource(resource) == null) {
            throw new PluginRuntimeException("Plugin '" + plugin.id() + "' endpoint '" + endpointId
                + "' has a typed handler, so its " + kind + " schema '" + schemaRef + "' must be on its class path");
        }
    }

    /** Every declared task has its factory, and every factory its declaration (root BRX-RS-MANIFEST-026). */
    private static void requireBoundTasks(ManagedPlugin plugin, DefaultPluginBootstrapContext bootstrap) {
        Set<String> bound = new java.util.TreeSet<>(bootstrap.taskFactories().keySet());
        bound.addAll(bootstrap.tasks().keySet());
        List<String> unbound = plugin.descriptor.providedTasks().keySet().stream()
            .filter(handlerId -> !bound.contains(handlerId))
            .sorted()
            .toList();
        if (!unbound.isEmpty()) {
            throw new PluginRuntimeException("Plugin '" + plugin.id() + "' declares tasks whose factories it does not "
                + "bind in configure: " + unbound);
        }
    }

    /**
     * A task implements {@code execute(TaskInvocation)} or, written before 3.3.0, {@code run()}; one that implements
     * neither is refused at startup (standard-link plan D56).
     */
    private static Map<String, ManagedTask> executableTasks(String pluginId, Map<String, ManagedTask> tasks) {
        tasks.forEach((handlerId, task) -> {
            if (!overrides(task.getClass(), "execute", TaskInvocation.class) && !overrides(task.getClass(), "run")) {
                throw new PluginRuntimeException("Task '" + handlerId + "' of plugin '" + pluginId + "' ("
                    + task.getClass().getName() + ") implements neither execute(TaskInvocation) nor run()");
            }
        });
        return Map.copyOf(tasks);
    }

    private static boolean overrides(Class<?> type, String name, Class<?>... parameters) {
        try {
            return type.getMethod(name, parameters).getDeclaringClass() != ManagedTask.class;
        } catch (NoSuchMethodException absent) {
            return false;
        }
    }

    /**
     * Returns the tasks of the started plugins, for the runtime's scheduler (standard-link plan L6.3). The scheduler
     * starts once the host's entries are published, and looks a task up again for each trigger, so a plugin that
     * stopped has no runs.
     *
     * @return the plugins' tasks
     */
    public TaskProviders taskProviders() {
        return new TaskProviders() {
            @Override
            public boolean ready() {
                synchronized (PluginRuntimeManager.this) {
                    return entriesPublished && PluginRuntimeManager.this.ready();
                }
            }

            @Override
            public List<TaskProvider> tasks() {
                synchronized (PluginRuntimeManager.this) {
                    List<TaskProvider> tasks = new ArrayList<>();
                    for (ManagedPlugin plugin : plugins.values()) {
                        if (!pluginReady(plugin.id())) {
                            continue;
                        }
                        plugin.tasks.forEach((handlerId, task) -> {
                            PluginRuntimeDescriptor.ProvidedTask declaration =
                                plugin.descriptor.providedTasks().get(handlerId);
                            if (declaration != null) {
                                tasks.add(new TaskProvider(plugin.id(), declaration, task));
                            }
                        });
                    }
                    return List.copyOf(tasks);
                }
            }
        };
    }

    /**
     * A provided command names one policy of each kind, and each is the platform's (root BRX-RS-MANIFEST-025,
     * MANIFEST-026; Kafka design §14 K20, K22; standard-link plan L4.3).
     */
    private static void validateCommandPolicies(ManagedPlugin plugin, PluginRuntimeDescriptor.ProvidedCommand command) {
        String where = "Plugin '" + plugin.id() + "' command '" + command.commandType() + "'";
        requireEventName(where + " type", command.commandType());
        requireEventName(where + " handler id", command.handlerId());
        requirePolicy(where, "retry", command.retryPolicyRef(), EventPolicies.RETRY.contains(command.retryPolicyRef()));
        requirePolicy(where, "timeout", command.timeoutPolicyRef(),
            EventPolicies.TIMEOUT.contains(command.timeoutPolicyRef()));
        requirePolicy(where, "concurrency", command.concurrencyPolicyRef(),
            EventPolicies.CONCURRENCY.contains(command.concurrencyPolicyRef()));
        requirePolicy(where, "scope", command.scopePolicyRef(),
            CommandPolicies.SCOPE.contains(command.scopePolicyRef()));
        requirePolicy(where, "idempotency", command.idempotencyPolicyRef(),
            CommandPolicies.IDEMPOTENCY.contains(command.idempotencyPolicyRef()));
        try {
            CommandPolicies.access(command.accessPolicyRef());
        } catch (IllegalArgumentException unknown) {
            throw new PluginRuntimeException(where + " names unknown access policy '" + command.accessPolicyRef() + "'");
        }
    }

    /** A consumed command names the platform's timeout, retry and idempotency policies (MANIFEST-026). */
    private static void validateConsumedCommandPolicies(ManagedPlugin plugin,
            PluginRuntimeDescriptor.ConsumedCommand command) {
        String where = "Plugin '" + plugin.id() + "' consumed command '" + command.commandType() + "'";
        requirePolicy(where, "timeout", command.timeoutPolicyRef(),
            EventPolicies.TIMEOUT.contains(command.timeoutPolicyRef()));
        requirePolicy(where, "retry", command.retryPolicyRef(), EventPolicies.RETRY.contains(command.retryPolicyRef()));
        requirePolicy(where, "idempotency", command.idempotencyPolicyRef(),
            CommandPolicies.IDEMPOTENCY.contains(command.idempotencyPolicyRef()));
    }

    private static void requirePolicy(String where, String kind, String reference, boolean known) {
        if (reference.isEmpty()) {
            throw new PluginRuntimeException(where + " names no " + kind + " policy");
        }
        if (!known) {
            throw new PluginRuntimeException(where + " names unknown " + kind + " policy '" + reference + "'");
        }
    }

    /**
     * Event types and handler ids become topic and consumer group names, a subscription names only policies the
     * runtime knows (root BRX-RS-BOOT-005, MESSAGE-029). Kafka design §14 K15, K20.
     */
    private void validateSubscriptionContract(ManagedPlugin plugin, PluginRuntimeDescriptor.EventSubscription subscription) {
        String where = "Plugin '" + plugin.id() + "' subscription '" + subscription.subscriptionId() + "'";
        requireEventName(where + " event type", subscription.eventType());
        requireEventName(where + " handler id", subscription.handlerId());
        if (!EventPolicies.RETRY.contains(subscription.retryPolicyRef())) {
            throw new PluginRuntimeException(where + " names unknown retry policy '" + subscription.retryPolicyRef()
                + "'; known: " + EventPolicies.RETRY);
        }
        PluginRuntimeDescriptor.SubscriptionPolicies policies =
            plugin.descriptor.subscriptionPolicies().get(subscription.handlerId());
        if (policies != null) {
            if (!policies.timeoutPolicyRef().isEmpty() && !EventPolicies.TIMEOUT.contains(policies.timeoutPolicyRef())) {
                throw new PluginRuntimeException(where + " names unknown timeout policy '"
                    + policies.timeoutPolicyRef() + "'; known: " + EventPolicies.TIMEOUT);
            }
            if (!policies.concurrencyPolicyRef().isEmpty()
                    && !EventPolicies.CONCURRENCY.contains(policies.concurrencyPolicyRef())) {
                throw new PluginRuntimeException(where + " names unknown concurrency policy '"
                    + policies.concurrencyPolicyRef() + "'; known: " + EventPolicies.CONCURRENCY);
            }
        }
    }

    /**
     * Every published event type has a legal name, and its {@code schemaRef} resolves to a schema file the plugin
     * ships that names itself by that reference and is the schema of the declared version (Kafka design D24, §14 K20).
     */
    private static void validatePublicationContracts(ManagedPlugin plugin) {
        for (PluginRuntimeDescriptor.EventPublication publication : plugin.descriptor.eventPublications().values()) {
            String where = "Plugin '" + plugin.id() + "' publication '" + publication.id() + "'";
            requireEventName(where, publication.id());
            String schemaRef = plugin.descriptor.publicationSchemas().get(publication.id());
            if (schemaRef == null) {
                continue;
            }
            String resource = EventPolicies.schemaResource(schemaRef);
            if (resource == null || !schemaRef.endsWith("/" + publication.version())) {
                throw new PluginRuntimeException(where + " refers to schema " + schemaRef
                    + ", which is not brix://schemas/.../" + publication.version());
            }
            ClassLoader loader = plugin.provider.getClass().getClassLoader();
            try (java.io.InputStream input = loader == null ? null : loader.getResourceAsStream(resource)) {
                if (input == null) {
                    throw new PluginRuntimeException(where + " refers to schema " + schemaRef
                        + ", but the plugin ships no " + resource);
                }
                String id = SCHEMA_READER.readTree(input).path("$id").asString();
                if (!schemaRef.equals(id)) {
                    throw new PluginRuntimeException(where + ": " + resource + " names itself '" + id + "', not "
                        + schemaRef);
                }
            } catch (java.io.IOException | tools.jackson.core.JacksonException unreadable) {
                throw new PluginRuntimeException(where + " has an unreadable schema " + resource, unreadable);
            }
        }
    }

    private static void requireEventName(String what, String name) {
        if (!EventPolicies.NAME.matcher(name).matches()) {
            throw new PluginRuntimeException(what + " '" + name + "' is not a legal name: letters, digits, '.', '-'"
                + " and '_', at most 100, starting with a letter or digit");
        }
    }

    /**
     * Across the plugins: an event type has one publisher, a subscriber's publisher policy names it, and the
     * subscriber's schema range takes the version it publishes (root BRX-RS-BOOT-005). An ambiguous or
     * inconsistent declaration fails the plugins it concerns, by their required/optional rules.
     */
    private void validateEventContractsAcrossPlugins() {
        Map<String, List<ManagedPlugin>> publishers = new LinkedHashMap<>();
        for (ManagedPlugin plugin : plugins.values()) {
            if (plugin.state.lifecycleState() != PluginLifecycleState.RESOLVED) {
                continue;
            }
            for (String eventType : plugin.descriptor.eventPublications().keySet()) {
                publishers.computeIfAbsent(eventType, ignored -> new ArrayList<>()).add(plugin);
            }
        }
        publishers.forEach((eventType, declaring) -> {
            if (declaring.size() > 1) {
                List<String> ids = declaring.stream().map(ManagedPlugin::id).sorted().toList();
                for (ManagedPlugin plugin : declaring) {
                    handleStartupFailure(plugin, new PluginRuntimeException("Event type " + eventType
                        + " is published by more than one plugin " + ids + "; an event type has one publisher"));
                }
            }
        });
        for (ManagedPlugin subscriber : new ArrayList<>(plugins.values())) {
            if (subscriber.state.lifecycleState() != PluginLifecycleState.RESOLVED) {
                continue;
            }
            try {
                for (PluginRuntimeDescriptor.EventSubscription subscription
                        : subscriber.descriptor.eventSubscriptions().values()) {
                    List<ManagedPlugin> declaring = publishers.getOrDefault(subscription.eventType(), List.of());
                    if (declaring.size() != 1) {
                        continue;
                    }
                    ManagedPlugin publisher = declaring.get(0);
                    String where = "Plugin '" + subscriber.id() + "' subscription '" + subscription.subscriptionId() + "'";
                    PluginRuntimeDescriptor.SubscriptionPolicies policies =
                        subscriber.descriptor.subscriptionPolicies().get(subscription.handlerId());
                    if (policies != null && !policies.publisherPolicyRef().isEmpty()
                            && !policies.publisherPolicyRef().equals(EventPolicies.publisherPolicyOf(publisher.id()))) {
                        throw new PluginRuntimeException(where + " trusts publisher policy '"
                            + policies.publisherPolicyRef() + "', but " + subscription.eventType() + " is published by '"
                            + publisher.id() + "' (" + EventPolicies.publisherPolicyOf(publisher.id()) + ")");
                    }
                    String version = publisher.descriptor.eventPublications().get(subscription.eventType()).version();
                    if (!version.isBlank() && !BrixRange.contains(version, subscription.schemaRange())) {
                        throw new PluginRuntimeException(where + " accepts " + subscription.eventType() + " "
                            + subscription.schemaRange() + ", but '" + publisher.id() + "' publishes " + version);
                    }
                }
            } catch (RuntimeException e) {
                handleStartupFailure(subscriber, e);
            }
        }
    }

    /**
     * A subscription records each event in its owner's persistent inbox, or keeps none, which only a BEST_EFFORT
     * event type allows (root BRX-RS-MANIFEST-025, MANIFEST-026). A type no installed plugin publishes is not judged:
     * no event of it can be delivered.
     */
    private void validateIdempotencyPolicy(ManagedPlugin plugin, PluginRuntimeDescriptor.EventSubscription subscription) {
        String policy = subscription.idempotencyPolicyRef();
        if (!PluginRuntimeDescriptor.EventSubscription.IDEMPOTENCY_POLICIES.contains(policy)) {
            throw new PluginRuntimeException("Plugin '" + plugin.id() + "' subscription '"
                + subscription.subscriptionId() + "' names unknown idempotency policy '" + policy + "'");
        }
        if (subscription.persistentInbox()) {
            return;
        }
        Optional<EventReliability> published = eventReliability(subscription.eventType());
        if (published.isPresent() && published.get() != EventReliability.BEST_EFFORT) {
            throw new PluginRuntimeException("Plugin '" + plugin.id() + "' subscription '"
                + subscription.subscriptionId() + "' keeps no inbox, but " + subscription.eventType() + " is "
                + published.get() + ": CRITICAL and STANDARD events need a persistent inbox");
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
        if (plugin.descriptor.isRequiredCapability(PersistenceCapability.class, capabilityRegistry)
                || plugin.descriptor.isOptionalCapability(PersistenceCapability.class, capabilityRegistry)) {
            types.add(PersistenceCapability.class);
        }
        return Set.copyOf(types);
    }

    private static boolean declaresQueries(String declaration) {
        return QueryCapability.class.getName().equals(declaration)
            || QueryCapability.class.getSimpleName().equals(declaration);
    }

    /** Like persistence, the command capability is the owner's own: its outbox bus (standard-link plan L4.6). */
    private static boolean declaresCommands(String declaration) {
        return CommandCapability.class.getName().equals(declaration)
            || CommandCapability.class.getSimpleName().equals(declaration);
    }

    private static boolean declaresPersistence(String declaration) {
        return PersistenceCapability.class.getName().equals(declaration)
            || PersistenceCapability.class.getSimpleName().equals(declaration);
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
                    binding.getValue(),
                    declaration.contract()));
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
        private Map<String, CreatedCommandHandler> commandHandlers = Map.of();
        private Map<String, CreatedEventHandler> eventHandlers = Map.of();
        private Map<String, PublishedQuery> queries = Map.of();
        private Map<String, ManagedTask> tasks = Map.of();
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
