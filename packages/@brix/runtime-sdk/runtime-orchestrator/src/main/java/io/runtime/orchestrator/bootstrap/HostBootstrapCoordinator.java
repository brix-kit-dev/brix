/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.bootstrap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import io.runtime.orchestrator.endpoint.EndpointRoute;
import io.runtime.orchestrator.endpoint.EndpointRouteDeclaration;
import io.runtime.orchestrator.endpoint.EndpointRouteValidator;
import io.runtime.orchestrator.endpoint.PluginEndpointDispatcher;
import io.runtime.orchestrator.operational.OperationalModuleRuntimeManager;
import io.runtime.orchestrator.operational.OperationalModuleRuntimeState;
import io.runtime.orchestrator.plugin.PluginRuntimeManager;
import io.runtime.orchestrator.plugin.PluginRuntimeState;

/**
 * Owns H0-H4, B0-B3, and the single Plugin/Operational Host entry commit.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class HostBootstrapCoordinator {

    private final PluginRuntimeManager plugins;
    private final OperationalModuleRuntimeManager operationalModules;
    private final PluginEndpointDispatcher endpointDispatcher;
    private final HostRuntimeOperationalView runtimeView;
    private final RuntimeShellFatalAction processFatalAction;
    private RuntimeShellBootstrapHandle handle;

    /**
     * Creates a Host bootstrap coordinator.
     *
     * @param plugins plugin manager
     * @param operationalModules operational module manager
     * @param endpointDispatcher single L2B HTTP dispatcher
     * @param runtimeView Host Runtime view
     * @param processFatalAction Standalone/Local process-wrapper fatal action;
     *                           Embedded callers pass a no-op and observe the fatal future
     */
    public HostBootstrapCoordinator(
            PluginRuntimeManager plugins,
            OperationalModuleRuntimeManager operationalModules,
            PluginEndpointDispatcher endpointDispatcher,
            HostRuntimeOperationalView runtimeView,
            RuntimeShellFatalAction processFatalAction) {
        this.plugins = Objects.requireNonNull(plugins, "plugins must not be null");
        this.operationalModules = Objects.requireNonNull(
            operationalModules,
            "operationalModules must not be null");
        this.endpointDispatcher = Objects.requireNonNull(
            endpointDispatcher,
            "endpointDispatcher must not be null");
        this.runtimeView = Objects.requireNonNull(runtimeView, "runtimeView must not be null");
        this.processFatalAction = Objects.requireNonNull(
            processFatalAction,
            "processFatalAction must not be null");
    }

    /**
     * Executes bootstrap and atomically publishes one deterministic HTTP entry snapshot.
     *
     * @return one-shot bootstrap handle
     */
    public synchronized RuntimeShellBootstrapHandle start() {
        if (handle != null) {
            throw new IllegalStateException("Host bootstrap has already been started");
        }
        RuntimeShellBootstrapHandle candidate = new RuntimeShellBootstrapHandle(this::ready, this::stop);
        handle = candidate;
        try {
            plugins.prepare();
            operationalModules.prepare();
            List<EndpointRouteDeclaration> declarations =
                new ArrayList<>(plugins.preparedRouteDeclarations());
            declarations.addAll(operationalModules.preparedRouteDeclarations());
            declarations.sort(Comparator
                .comparing(EndpointRouteDeclaration::method)
                .thenComparing(EndpointRouteDeclaration::path)
                .thenComparing(EndpointRouteDeclaration::ownerId)
                .thenComparing(EndpointRouteDeclaration::endpointId));
            EndpointRouteValidator.validate(List.copyOf(declarations));
            plugins.startPrepared();
            operationalModules.startPrepared();
            List<EndpointRoute> routes = new ArrayList<>(plugins.preparedRoutes());
            routes.addAll(operationalModules.preparedRoutes());
            routes.sort(Comparator
                .comparing(EndpointRoute::method)
                .thenComparing(EndpointRoute::path)
                .thenComparing(EndpointRoute::pluginId)
                .thenComparing(EndpointRoute::endpointId));
            endpointDispatcher.replaceSnapshot(List.copyOf(routes));
            plugins.markPublished();
            operationalModules.markPublished();
            runtimeView.published();
            runtimeView.ready(ready());
            if (!ready()) {
                throw new IllegalStateException("Required Runtime Shell modules did not reach readiness");
            }
            candidate.completeReady();
            return candidate;
        } catch (RuntimeException e) {
            endpointDispatcher.clear();
            runtimeView.ready(false);
            operationalModules.stop();
            plugins.stop();
            throw e;
        }
    }

    /**
     * Reports a required module terminal failure exactly once.
     *
     * <p>All new HTTP entry admission is closed before module drain and before
     * the process-wrapper callback is invoked.</p>
     *
     * @param errorCode stable fatal code
     * @param moduleId failed module id
     */
    public synchronized void requiredModuleFailed(String errorCode, String moduleId) {
        if (handle == null) {
            throw new IllegalStateException("Host bootstrap has not started");
        }
        RuntimeShellBootstrapHandle.FatalReason reason =
            new RuntimeShellBootstrapHandle.FatalReason(errorCode, moduleId);
        if (!handle.completeFatal(reason)) {
            return;
        }
        endpointDispatcher.clear();
        runtimeView.ready(false);
        operationalModules.stop();
        plugins.stop();
        processFatalAction.onFatal(reason);
    }

    /**
     * Returns derived Host readiness.
     *
     * @return readiness
     */
    public synchronized boolean ready() {
        return plugins.ready() && operationalModules.ready();
    }

    /**
     * Returns immutable plugin states.
     *
     * @return plugin states
     */
    public synchronized List<PluginRuntimeState> pluginStates() {
        return plugins.states();
    }

    /**
     * Returns immutable operational module states.
     *
     * @return operational states
     */
    public synchronized List<OperationalModuleRuntimeState> operationalStates() {
        return operationalModules.states();
    }

    private synchronized void stop() {
        endpointDispatcher.clear();
        runtimeView.ready(false);
        operationalModules.stop();
        plugins.stop();
    }
}
