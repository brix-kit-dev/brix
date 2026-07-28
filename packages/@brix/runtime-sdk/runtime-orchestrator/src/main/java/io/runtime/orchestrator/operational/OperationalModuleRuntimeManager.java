/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.operational;

import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.orchestrator.endpoint.EndpointRoute;
import io.runtime.orchestrator.endpoint.EndpointRouteDeclaration;
import io.runtime.orchestrator.internalcontract.BrixRange;
import io.runtime.orchestrator.internalcontract.InternalContractBinder;
import io.runtime.sdk.internalcontract.InternalContractProvider;
import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.ManagedTask;
import io.runtime.sdk.plugin.PluginLifecycleState;

/**
 * Drives O0-O8 for platform operational modules without Spring business wiring.
 *
 * <p>{@link #prepare()} stops at O7 with all entries unpublished. The Host
 * coordinator owns B3 and calls {@link #markPublished()} only after atomically
 * installing the complete Plugin/Operational Host entry snapshot.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class OperationalModuleRuntimeManager {

    private static final Logger log = LoggerFactory.getLogger(OperationalModuleRuntimeManager.class);

    private final Supplier<List<ServiceLoaderOperationalModuleDiscovery.DiscoveredOperationalModule>> discovery;
    private final Supplier<List<InternalContractProvider>> internalProviderDiscovery;
    private final InternalContractBinder internalContracts;
    private final RuntimeOperationalView runtimeView;
    private final Set<String> requiredModuleIds;
    private final String runtimeVersion;
    private final Map<String, ManagedModule> modules = new LinkedHashMap<>();
    private boolean prepared;
    private boolean started;
    private boolean published;

    /**
     * Creates an operational Runtime manager.
     *
     * @param discovery operational module discovery
     * @param internalProviderDiscovery internal contract provider discovery
     * @param internalContracts internal contract binder
     * @param runtimeView read-only Host Runtime view
     * @param requiredModuleIds Composition-required operational module ids
     * @param runtimeVersion exact L2B Runtime version
     */
    public OperationalModuleRuntimeManager(
            Supplier<List<ServiceLoaderOperationalModuleDiscovery.DiscoveredOperationalModule>> discovery,
            Supplier<List<InternalContractProvider>> internalProviderDiscovery,
            InternalContractBinder internalContracts,
            RuntimeOperationalView runtimeView,
            Collection<String> requiredModuleIds,
            String runtimeVersion) {
        this.discovery = Objects.requireNonNull(discovery, "discovery must not be null");
        this.internalProviderDiscovery = Objects.requireNonNull(
            internalProviderDiscovery,
            "internalProviderDiscovery must not be null");
        this.internalContracts = Objects.requireNonNull(internalContracts, "internalContracts must not be null");
        this.runtimeView = Objects.requireNonNull(runtimeView, "runtimeView must not be null");
        this.requiredModuleIds = normalize(requiredModuleIds);
        this.runtimeVersion = requireText(runtimeVersion, "runtimeVersion");
    }

    /**
     * Executes O0-O3 while keeping every Handler uncreated and every entry unpublished.
     *
     * @return immutable states
     */
    public synchronized List<OperationalModuleRuntimeState> prepare() {
        if (prepared) {
            throw new OperationalRuntimeException(
                "operational.bootstrap.repeated",
                "Operational Runtime bootstrap has already been prepared");
        }
        prepared = true;
        List<ServiceLoaderOperationalModuleDiscovery.DiscoveredOperationalModule> discovered =
            Objects.requireNonNull(discovery.get(), "Operational discovery returned null").stream()
                .sorted(java.util.Comparator.comparing(item -> item.descriptor().identity().moduleId()))
                .toList();
        for (ServiceLoaderOperationalModuleDiscovery.DiscoveredOperationalModule item : discovered) {
            String id = item.descriptor().identity().moduleId();
            if (modules.putIfAbsent(id, new ManagedModule(item)) != null) {
                throw failure("operational.module.duplicate", "Duplicate operational module id: " + id);
            }
        }
        verifyRequiredProviders();
        for (ServiceLoaderOperationalModuleDiscovery.DiscoveredOperationalModule module : discovered) {
            validateRuntimeRange(module.descriptor());
        }
        bindProvidedContracts(discovered, internalProviderDiscovery.get());
        internalContracts.activateAndFreeze();
        for (ManagedModule module : new ArrayList<>(modules.values())) {
            try {
                prepareModule(module);
            } catch (RuntimeException e) {
                failModuleDuringStartup(module, e);
            }
        }
        return states();
    }

    /**
     * Executes O4-O7 after the Host B2 barrier has accepted the complete candidate set.
     *
     * @return immutable states
     */
    public synchronized List<OperationalModuleRuntimeState> startPrepared() {
        if (!prepared || started) {
            throw failure(
                "operational.start.invalid_state",
                "Operational Runtime modules are not in the prepared state");
        }
        started = true;
        for (ManagedModule module : new ArrayList<>(modules.values())) {
            try {
                startModule(module);
            } catch (RuntimeException e) {
                failModuleDuringStartup(module, e);
            }
        }
        return states();
    }

    /**
     * Returns handler-free route declarations for the Host B2 barrier.
     *
     * @return immutable route declarations
     */
    public synchronized List<EndpointRouteDeclaration> preparedRouteDeclarations() {
        if (!prepared) {
            throw failure("operational.routes.invalid_state", "Operational modules are not prepared");
        }
        List<EndpointRouteDeclaration> declarations = new ArrayList<>();
        for (ManagedModule module : modules.values()) {
            for (OperationalModuleDescriptor.EndpointDeclaration endpoint
                    : module.descriptor.endpoints().values()) {
                declarations.add(new EndpointRouteDeclaration(
                    module.id(),
                    endpoint.endpointId(),
                    endpoint.method(),
                    endpoint.path()));
            }
        }
        return List.copyOf(declarations);
    }

    /**
     * Returns prepared HTTP entries for global B2 reservation.
     *
     * @return immutable routes
     */
    public synchronized List<EndpointRoute> preparedRoutes() {
        List<EndpointRoute> routes = new ArrayList<>();
        for (ManagedModule module : modules.values()) {
            if (module.state.lifecycleState() == PluginLifecycleState.STARTED) {
                routes.addAll(module.routes);
            }
        }
        return List.copyOf(routes);
    }

    /**
     * Completes O8 after the Host coordinator commits the global entry snapshot.
     */
    public synchronized void markPublished() {
        if (!started || published) {
            throw failure("operational.publication.invalid_state", "Operational publication state is invalid");
        }
        for (ManagedModule module : modules.values()) {
            if (module.state.lifecycleState() == PluginLifecycleState.STARTED) {
                BrixHealth health = health(module);
                module.state = state(module, true, health.isReadyStatus(), health, "operational.started");
            }
        }
        published = true;
    }

    /**
     * Returns derived readiness for required operational modules.
     *
     * @return readiness
     */
    public synchronized boolean ready() {
        if (!published) {
            return requiredModuleIds.isEmpty() && modules.isEmpty();
        }
        for (String id : requiredModuleIds) {
            ManagedModule module = modules.get(id);
            if (module == null || !module.state.ready()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Drains and stops modules in deterministic reverse order.
     */
    public synchronized void stop() {
        published = false;
        List<ManagedModule> ordered = new ArrayList<>(modules.values());
        for (int index = ordered.size() - 1; index >= 0; index--) {
            ManagedModule module = ordered.get(index);
            if (module.state.lifecycleState() == PluginLifecycleState.STOPPED) {
                continue;
            }
            boolean providerStarted = shouldStopProvider(module.state.lifecycleState());
            module.state = state(
                module,
                false,
                false,
                module.state.health(),
                "operational.draining",
                PluginLifecycleState.DRAINING);
            if (providerStarted) {
                safeStop(module);
            }
            module.state = state(
                module,
                false,
                false,
                module.state.health(),
                "operational.stopped",
                PluginLifecycleState.STOPPED);
        }
    }

    /**
     * Returns immutable module states.
     *
     * @return states
     */
    public synchronized List<OperationalModuleRuntimeState> states() {
        return modules.values().stream().map(module -> module.state).toList();
    }

    private void prepareModule(ManagedModule module) {
        module.state = state(
            module,
            false,
            false,
            BrixHealth.unknown("Resolved"),
            "operational.resolved",
            PluginLifecycleState.RESOLVED);

        DefaultOperationalBootstrapContext bootstrap = new DefaultOperationalBootstrapContext(module.descriptor);
        module.provider.configure(bootstrap);
        bootstrap.validateBidirectionalConsistency();
        module.bootstrap = bootstrap;

        DefaultOperationalContext context = new DefaultOperationalContext(
            module.descriptor,
            runtimeView,
            internalContracts);
        for (OperationalModuleDescriptor.RequiredInternalContract requirement
                : module.descriptor.requiredContracts()) {
            Class<?> type = loadType(requirement.contractType(), module.provider.getClass().getClassLoader());
            requireUntyped(requirement, type);
        }
        module.context = context;
    }

    private void validateRuntimeRange(OperationalModuleDescriptor descriptor) {
        if (!BrixRange.contains(runtimeVersion, descriptor.runtimeRange())) {
            throw failure(
                "operational.runtime.version_mismatch",
                "Operational module Runtime range does not include the Host version: "
                    + descriptor.identity().moduleId());
        }
    }

    private void startModule(ManagedModule module) {
        createEntries(module, module.context);
        module.state = state(
            module,
            false,
            false,
            BrixHealth.unknown("Wired"),
            "operational.wired",
            PluginLifecycleState.WIRED);
        module.provider.onStart(module.context);
        BrixHealth health = health(module);
        module.state = state(
            module,
            false,
            false,
            health,
            "operational.started",
            PluginLifecycleState.STARTED);
        if (isRequired(module) && !health.isReadyStatus()) {
            throw failure("operational.health.not_ready", "Required operational module is not healthy");
        }
    }

    private void createEntries(ManagedModule module, OperationalContext context) {
        Map<String, EndpointHandler<?, ?>> handlers = new LinkedHashMap<>();
        for (Map.Entry<String, OperationalHandlerFactory<? extends EndpointHandler<?, ?>>> entry
                : module.bootstrap.endpointFactories().entrySet()) {
            EndpointHandler<?, ?> handler = Objects.requireNonNull(
                entry.getValue().create(context),
                "Operational endpoint factory returned null");
            handlers.put(entry.getKey(), handler);
        }
        Map<String, ManagedTask> tasks = new LinkedHashMap<>();
        for (Map.Entry<String, OperationalHandlerFactory<? extends ManagedTask>> entry
                : module.bootstrap.taskFactories().entrySet()) {
            tasks.put(entry.getKey(), Objects.requireNonNull(
                entry.getValue().create(context),
                "Operational task factory returned null"));
        }
        List<EndpointRoute> routes = new ArrayList<>();
        for (OperationalModuleDescriptor.EndpointDeclaration declaration
                : module.descriptor.endpoints().values()) {
            routes.add(new EndpointRoute(
                module.id(),
                declaration.endpointId(),
                declaration.method(),
                declaration.path(),
                declaration.accessPolicy(),
                handlers.get(declaration.handlerId())));
        }
        module.routes = List.copyOf(routes);
        module.tasks = Map.copyOf(tasks);
    }

    private void bindProvidedContracts(
            List<ServiceLoaderOperationalModuleDiscovery.DiscoveredOperationalModule> discovered,
            List<InternalContractProvider> providers) {
        List<InternalContractProvider> available = providers == null ? List.of() : List.copyOf(providers);
        for (ServiceLoaderOperationalModuleDiscovery.DiscoveredOperationalModule module : discovered) {
            List<InternalContractProvider> sameArtifact = available.stream()
                .filter(provider -> module.artifactCodeSource().equals(codeSource(provider.getClass())))
                .toList();
            boolean provides = !module.descriptor().providedContracts().isEmpty();
            if (provides && sameArtifact.size() != 1) {
                throw failure(
                    sameArtifact.isEmpty()
                        ? "internal_contract.provider_missing"
                        : "internal_contract.provider_duplicate",
                    "Providing operational artifact must publish exactly one InternalContractProvider");
            }
            if (!provides && !sameArtifact.isEmpty()) {
                throw failure(
                    "internal_contract.provider_undeclared",
                    "Non-providing operational artifact published an InternalContractProvider");
            }
            if (provides) {
                internalContracts.bind(module.descriptor(), sameArtifact.get(0), Set.of());
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void requireUntyped(
            OperationalModuleDescriptor.RequiredInternalContract requirement,
            Class<?> type) {
        internalContracts.require(requirement, (Class) type);
    }

    private void failModuleDuringStartup(ManagedModule module, RuntimeException cause) {
        boolean providerStarted = shouldStopProvider(module.state.lifecycleState());
        module.state = state(
            module,
            false,
            false,
            BrixHealth.down("Startup failed"),
            cause instanceof OperationalRuntimeException operational
                ? operational.diagnosticCode()
                : "operational.start_failed",
            PluginLifecycleState.FAILED);
        if (providerStarted) {
            safeStop(module);
        }
        module.state = state(
            module,
            false,
            false,
            BrixHealth.down("Startup failed"),
            module.state.diagnosticCode(),
            PluginLifecycleState.STOPPED);
        if (isRequired(module)) {
            throw cause instanceof OperationalRuntimeException
                ? cause
                : failure("operational.start_failed", "Required operational module failed to start", cause);
        }
        modules.remove(module.id());
    }

    private void verifyRequiredProviders() {
        Set<String> missing = new LinkedHashSet<>(requiredModuleIds);
        missing.removeAll(modules.keySet());
        if (!missing.isEmpty()) {
            throw failure(
                "operational.required_provider_missing",
                "Required operational provider(s) missing: " + missing);
        }
    }

    private BrixHealth health(ManagedModule module) {
        BrixHealth health = module.provider.health();
        if (health == null) {
            throw failure("operational.health.null", "Operational module returned null health");
        }
        return health;
    }

    private void safeStop(ManagedModule module) {
        try {
            module.provider.onStop();
        } catch (RuntimeException e) {
            log.warn("Operational module '{}' failed during stop: {}", module.id(), e.getMessage(), e);
        }
    }

    private boolean isRequired(ManagedModule module) {
        return requiredModuleIds.contains(module.id());
    }

    private boolean shouldStopProvider(PluginLifecycleState lifecycleState) {
        return lifecycleState == PluginLifecycleState.WIRED
            || lifecycleState == PluginLifecycleState.STARTED
            || lifecycleState == PluginLifecycleState.DRAINING;
    }

    private OperationalModuleRuntimeState state(
            ManagedModule module,
            boolean entriesPublished,
            boolean ready,
            BrixHealth health,
            String diagnosticCode) {
        return state(
            module,
            entriesPublished,
            ready,
            health,
            diagnosticCode,
            module.state.lifecycleState());
    }

    private OperationalModuleRuntimeState state(
            ManagedModule module,
            boolean entriesPublished,
            boolean ready,
            BrixHealth health,
            String diagnosticCode,
            PluginLifecycleState lifecycleState) {
        return new OperationalModuleRuntimeState(
            module.descriptor.identity(),
            lifecycleState,
            entriesPublished,
            ready,
            health,
            diagnosticCode);
    }

    private static Class<?> loadType(String name, ClassLoader classLoader) {
        try {
            return Class.forName(name, false, classLoader);
        } catch (ClassNotFoundException e) {
            throw failure(
                "internal_contract.type_missing",
                "Internal contract type is unavailable: " + name,
                e);
        }
    }

    private static URL codeSource(Class<?> type) {
        CodeSource source = type.getProtectionDomain().getCodeSource();
        return source != null ? source.getLocation() : null;
    }

    private static Set<String> normalize(Collection<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static OperationalRuntimeException failure(String code, String message) {
        return new OperationalRuntimeException(code, message);
    }

    private static OperationalRuntimeException failure(String code, String message, Throwable cause) {
        return new OperationalRuntimeException(code, message, cause);
    }

    private static final class ManagedModule {
        private final PlatformOperationalModule provider;
        private final OperationalModuleDescriptor descriptor;
        private DefaultOperationalBootstrapContext bootstrap;
        private OperationalContext context;
        private List<EndpointRoute> routes = List.of();
        private Map<String, ManagedTask> tasks = Map.of();
        private OperationalModuleRuntimeState state;

        private ManagedModule(
                ServiceLoaderOperationalModuleDiscovery.DiscoveredOperationalModule discovered) {
            this.provider = discovered.provider();
            this.descriptor = discovered.descriptor();
            this.state = new OperationalModuleRuntimeState(
                descriptor.identity(),
                PluginLifecycleState.DISCOVERED,
                false,
                false,
                BrixHealth.unknown("Discovered"),
                "operational.discovered");
        }

        private String id() {
            return descriptor.identity().moduleId();
        }
    }
}
