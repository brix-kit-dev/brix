/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.capability.DefaultCapabilityRegistry;
import io.runtime.orchestrator.endpoint.DefaultPluginEndpointDispatcher;
import io.runtime.orchestrator.internalcontract.InternalContractBinder;
import io.runtime.orchestrator.operational.OperationalModuleRuntimeManager;
import io.runtime.orchestrator.operational.ServiceLoaderOperationalModuleDiscovery;
import io.runtime.orchestrator.plugin.PluginRuntimeManager;

class HostBootstrapCoordinatorTest {

    @Test
    void publishesOneDeterministicPluginOperationalSnapshotAfterBothManagersStart() {
        Fixture first = fixture();
        Fixture second = fixture();

        RuntimeShellBootstrapHandle firstHandle = first.coordinator.start();
        RuntimeShellBootstrapHandle secondHandle = second.coordinator.start();

        assertTrue(firstHandle.ready());
        assertTrue(secondHandle.ready());
        assertEquals(
            first.dispatcher.routes().stream().map(route ->
                route.method() + " " + route.path() + " " + route.pluginId() + " " + route.endpointId()).toList(),
            second.dispatcher.routes().stream().map(route ->
                route.method() + " " + route.path() + " " + route.pluginId() + " " + route.endpointId()).toList());
        assertEquals(1, first.runtimeView.entryGeneration());
        assertEquals(1, first.dispatcher.routes().size());
    }

    @Test
    void requiredTerminalFailureClosesAdmissionBeforeOneShotFatalCallback() {
        Fixture fixture = fixture();
        List<RuntimeShellBootstrapHandle.FatalReason> fatalActions = new ArrayList<>();
        fixture.coordinator = new HostBootstrapCoordinator(
            fixture.plugins,
            fixture.operational,
            fixture.dispatcher,
            fixture.runtimeView,
            fatalActions::add);
        RuntimeShellBootstrapHandle handle = fixture.coordinator.start();

        fixture.coordinator.requiredModuleFailed("runtime.required_module_failed", "runtime-test-operational");
        fixture.coordinator.requiredModuleFailed("runtime.required_module_failed", "runtime-test-operational");

        assertFalse(handle.ready());
        assertTrue(fixture.dispatcher.routes().isEmpty());
        assertEquals(1, fatalActions.size());
        assertTrue(handle.fatalFuture().toCompletableFuture().isDone());
    }

    private Fixture fixture() {
        DefaultPluginEndpointDispatcher dispatcher =
            new DefaultPluginEndpointDispatcher(Duration.ofSeconds(5));
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        PluginRuntimeManager plugins = new PluginRuntimeManager(
            List::of,
            ignored -> Optional.empty(),
            registry,
            List.of(),
            dispatcher);
        HostRuntimeOperationalView runtimeView =
            new HostRuntimeOperationalView(List.of("runtime-test-operational"));
        OperationalModuleRuntimeManager operational = new OperationalModuleRuntimeManager(
            new ServiceLoaderOperationalModuleDiscovery()::discover,
            List::of,
            new InternalContractBinder(registry, registry, getClass().getClassLoader()),
            runtimeView,
            List.of("runtime-test-operational"),
            "3.2.0");
        HostBootstrapCoordinator coordinator = new HostBootstrapCoordinator(
            plugins,
            operational,
            dispatcher,
            runtimeView,
            ignored -> { });
        return new Fixture(dispatcher, plugins, operational, runtimeView, coordinator);
    }

    private static final class Fixture {
        private final DefaultPluginEndpointDispatcher dispatcher;
        private final PluginRuntimeManager plugins;
        private final OperationalModuleRuntimeManager operational;
        private final HostRuntimeOperationalView runtimeView;
        private HostBootstrapCoordinator coordinator;

        private Fixture(
                DefaultPluginEndpointDispatcher dispatcher,
                PluginRuntimeManager plugins,
                OperationalModuleRuntimeManager operational,
                HostRuntimeOperationalView runtimeView,
                HostBootstrapCoordinator coordinator) {
            this.dispatcher = dispatcher;
            this.plugins = plugins;
            this.operational = operational;
            this.runtimeView = runtimeView;
            this.coordinator = coordinator;
        }
    }
}
