/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.bootstrap.HostRuntimeOperationalView;
import io.runtime.orchestrator.capability.DefaultCapabilityRegistry;
import io.runtime.orchestrator.internalcontract.InternalContractBinder;
import io.runtime.sdk.internalcontract.InternalContractProvider;
import io.runtime.sdk.plugin.PluginLifecycleState;

class OperationalModuleRuntimeManagerTest {

    @Test
    void requiredModuleRemainsUnpublishedUntilO8() {
        OperationalModuleRuntimeManager manager = manager(
            new ServiceLoaderOperationalModuleDiscovery()::discover,
            List.of("runtime-test-operational"));

        var prepared = manager.prepare();

        assertEquals(PluginLifecycleState.RESOLVED, prepared.get(0).lifecycleState());
        assertFalse(prepared.get(0).entriesPublished());
        assertFalse(manager.ready());
        assertEquals(1, manager.preparedRouteDeclarations().size());
        assertTrue(manager.preparedRoutes().isEmpty());

        manager.startPrepared();

        assertEquals(PluginLifecycleState.STARTED, manager.states().get(0).lifecycleState());
        assertEquals(1, manager.preparedRoutes().size());

        manager.markPublished();

        assertTrue(manager.ready());
        assertTrue(manager.states().get(0).entriesPublished());
    }

    @Test
    void missingRequiredOperationalProviderFailsFast() {
        OperationalModuleRuntimeManager manager = manager(List::of, List.of("required-operational"));

        OperationalRuntimeException failure =
            assertThrows(OperationalRuntimeException.class, manager::prepare);

        assertEquals("operational.required_provider_missing", failure.diagnosticCode());
        assertFalse(manager.ready());
    }

    @Test
    void missingDescriptorFactoryFailsBidirectionalValidation() {
        PlatformOperationalModule provider = new PlatformOperationalModule() {
            @Override
            public void configure(OperationalBootstrapContext bootstrap) {
            }

            @Override
            public void onStart(OperationalContext context) {
            }

            @Override
            public void onStop() {
            }

            @Override
            public io.runtime.sdk.plugin.BrixHealth health() {
                return io.runtime.sdk.plugin.BrixHealth.up();
            }
        };
        OperationalModuleDescriptor descriptor = descriptor("factory-test");
        var discovered = new ServiceLoaderOperationalModuleDiscovery.DiscoveredOperationalModule(
            provider,
            descriptor,
            provider.getClass().getProtectionDomain().getCodeSource().getLocation());
        OperationalModuleRuntimeManager manager = manager(
            () -> List.of(discovered),
            List.of("factory-test"));

        OperationalRuntimeException failure =
            assertThrows(OperationalRuntimeException.class, manager::prepare);

        assertEquals("operational.factory.binding_mismatch", failure.diagnosticCode());
        assertEquals(PluginLifecycleState.STOPPED, manager.states().get(0).lifecycleState());
    }

    @Test
    void endpointFactoryIsNotInvokedBeforeHostB2() {
        AtomicBoolean factoryInvoked = new AtomicBoolean();
        PlatformOperationalModule provider = new PlatformOperationalModule() {
            @Override
            public void configure(OperationalBootstrapContext bootstrap) {
                bootstrap.bindEndpointHandlerFactory("runtime.status", context -> {
                    factoryInvoked.set(true);
                    return request -> "ok";
                });
            }

            @Override
            public void onStart(OperationalContext context) {
            }

            @Override
            public void onStop() {
            }

            @Override
            public io.runtime.sdk.plugin.BrixHealth health() {
                return io.runtime.sdk.plugin.BrixHealth.up();
            }
        };
        var discovered = new ServiceLoaderOperationalModuleDiscovery.DiscoveredOperationalModule(
            provider,
            descriptor("barrier-test"),
            provider.getClass().getProtectionDomain().getCodeSource().getLocation());
        OperationalModuleRuntimeManager manager = manager(
            () -> List.of(discovered),
            List.of("barrier-test"));

        manager.prepare();
        assertFalse(factoryInvoked.get());

        manager.startPrepared();
        assertTrue(factoryInvoked.get());
    }

    @Test
    void duplicateSameArtifactInternalContractProvidersFailFast() {
        PlatformOperationalModule module = operationalModuleWithoutBindings();
        var discovered = new ServiceLoaderOperationalModuleDiscovery.DiscoveredOperationalModule(
            module,
            providerDescriptor("provider-duplicate"),
            module.getClass().getProtectionDomain().getCodeSource().getLocation());
        InternalContractProvider first =
            bootstrap -> bootstrap.bind("test.contract", Runnable.class, context -> () -> { });
        InternalContractProvider second =
            bootstrap -> bootstrap.bind("test.contract", Runnable.class, context -> () -> { });
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        OperationalModuleRuntimeManager manager = new OperationalModuleRuntimeManager(
            () -> List.of(discovered),
            () -> List.of(first, second),
            new InternalContractBinder(registry, registry, getClass().getClassLoader()),
            new HostRuntimeOperationalView(List.of("provider-duplicate")),
            List.of("provider-duplicate"),
            "3.2.0");

        OperationalRuntimeException failure =
            assertThrows(OperationalRuntimeException.class, manager::prepare);

        assertEquals("internal_contract.provider_duplicate", failure.diagnosticCode());
    }

    private OperationalModuleRuntimeManager manager(
            java.util.function.Supplier<List<ServiceLoaderOperationalModuleDiscovery.DiscoveredOperationalModule>>
                discovery,
            List<String> required) {
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        return new OperationalModuleRuntimeManager(
            discovery,
            List::of,
            new InternalContractBinder(registry, registry, getClass().getClassLoader()),
            new HostRuntimeOperationalView(required),
            required,
            "3.2.0");
    }

    private OperationalModuleDescriptor descriptor(String id) {
        var endpoint = new OperationalModuleDescriptor.EndpointDeclaration(
            "runtime.status.v1",
            "GET",
            "/api/platform/runtime/status",
            "runtime.status",
            "runtime-status-read");
        return new OperationalModuleDescriptor(
            new OperationalModuleIdentity(id, "3.2.0", "runtime-tests"),
            ">=3.2.0 <4.0.0",
            List.of(),
            List.of(),
            java.util.Set.of(),
            java.util.Map.of(endpoint.endpointId(), endpoint),
            java.util.Map.of());
    }

    private OperationalModuleDescriptor providerDescriptor(String id) {
        return new OperationalModuleDescriptor(
            new OperationalModuleIdentity(id, "3.2.0", "runtime-tests"),
            ">=3.2.0 <4.0.0",
            List.of(new OperationalModuleDescriptor.ProvidedInternalContract(
                "test.contract",
                Runnable.class.getName(),
                "1.0.0",
                "test.provider",
                "runtime-tests")),
            List.of(),
            java.util.Set.of(),
            java.util.Map.of(),
            java.util.Map.of());
    }

    private PlatformOperationalModule operationalModuleWithoutBindings() {
        return new PlatformOperationalModule() {
            @Override
            public void configure(OperationalBootstrapContext bootstrap) {
            }

            @Override
            public void onStart(OperationalContext context) {
            }

            @Override
            public void onStop() {
            }

            @Override
            public io.runtime.sdk.plugin.BrixHealth health() {
                return io.runtime.sdk.plugin.BrixHealth.up();
            }
        };
    }
}
