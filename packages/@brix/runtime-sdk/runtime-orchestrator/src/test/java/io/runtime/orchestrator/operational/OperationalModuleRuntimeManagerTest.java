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

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.bootstrap.HostRuntimeOperationalView;
import io.runtime.orchestrator.capability.DefaultCapabilityRegistry;
import io.runtime.orchestrator.endpoint.EndpointContract;
import io.runtime.orchestrator.endpoint.TestEndpointAccessPolicies;
import io.runtime.orchestrator.internalcontract.InternalContractBinder;
import io.runtime.orchestrator.persistence.RecordingPersistence;
import io.runtime.sdk.internalcontract.InternalContractProvider;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.ManagedTask;
import io.runtime.sdk.plugin.PluginLifecycleState;
import io.runtime.sdk.plugin.TypedEndpointHandler;

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
    void aTypedHandlerStartsWithItsSchemasAndPublishesItsContract() {
        // Root BRX-RS-HOST-014; idempotency design D78: the dispatcher checks requests and responses against them.
        EndpointContract contract = new EndpointContract("brix://schemas/test-endpoints/create-thing-request/1.0.0",
            "brix://schemas/test-endpoints/thing/1.0.0", 201);
        OperationalModuleRuntimeManager manager = moduleWith(contract, request -> CompletableFuture.completedFuture("ok"));

        manager.prepare();
        manager.startPrepared();

        assertEquals(contract, manager.preparedRoutes().get(0).contract());
    }

    @Test
    void aTypedHandlerDoesNotStartWithoutItsRequestSchema() {
        OperationalRuntimeException failure = startFails(new EndpointContract(
            "brix://schemas/test-endpoints/no-such-request/1.0.0", "brix://schemas/test-endpoints/thing/1.0.0", 201));

        assertEquals("operational.endpoint.invalid", failure.diagnosticCode());
        assertTrue(failure.getMessage().contains("endpoint 'runtime.status.v1' has a typed handler, so its request schema "
            + "'brix://schemas/test-endpoints/no-such-request/1.0.0' must be on its class path"), failure.getMessage());
    }

    @Test
    void aTypedHandlerDoesNotStartWithoutItsResponseSchema() {
        OperationalRuntimeException failure = startFails(new EndpointContract(
            "brix://schemas/test-endpoints/create-thing-request/1.0.0", "brix://schemas/test-endpoints/no-such-answer/1.0.0",
            201));

        assertTrue(failure.getMessage().contains("response schema"), failure.getMessage());
    }

    @Test
    void aTypedHandlerThatAnswersNoBodyNeedsNoResponseSchema() {
        EndpointContract contract = new EndpointContract("brix://schemas/test-endpoints/create-thing-request/1.0.0", "",
            204);
        OperationalModuleRuntimeManager manager = moduleWith(contract, request -> CompletableFuture.completedFuture(null));

        manager.prepare();
        manager.startPrepared();

        assertEquals(204, manager.preparedRoutes().get(0).contract().status());
    }

    @Test
    void aHandlerOfTheOldShapeStartsWithSchemasThatDoNotExistYet() {
        // Idempotency design D78: endpoints change one at a time; until then their descriptors name schemas nobody reads.
        OperationalModuleRuntimeManager manager = moduleWith(new EndpointContract(
            "brix://schemas/test-endpoints/no-such-request/1.0.0", "brix://schemas/test-endpoints/no-such-answer/1.0.0", 200),
            (EndpointHandler<Object, Object>) request -> "ok");

        manager.prepare();
        manager.startPrepared();

        assertEquals(1, manager.preparedRoutes().size());
    }

    @Test
    void anEndpointThatReplaysItsResultsWritesToTheOwnerOfItsContract() {
        // Idempotency design D79: the record goes to the table of the owner that declares the writes.
        OperationalModuleRuntimeManager manager = replayingModule(Set.of("run"), typed());

        manager.prepare();
        manager.startPrepared();

        EndpointContract.Replay replay = manager.preparedRoutes().get(0).contract().replay();
        assertEquals("test.write", replay.writeContract());
        assertEquals("owner-plugin", replay.writeOwner());
    }

    @Test
    void anEndpointCannotReplayThroughAContractThatKeepsNoResults() {
        OperationalModuleRuntimeManager manager = replayingModule(Set.of(), typed());

        manager.prepare();
        OperationalRuntimeException failure = assertThrows(OperationalRuntimeException.class, manager::startPrepared);

        assertEquals("operational.endpoint.invalid", failure.diagnosticCode());
        assertTrue(failure.getMessage().contains("declares no replayableWrites"), failure.getMessage());
    }

    @Test
    void anEndpointThatReplaysItsResultsHasATypedHandler() {
        OperationalModuleRuntimeManager manager = replayingModule(Set.of("run"),
            (EndpointHandler<Object, Object>) request -> "ok");

        manager.prepare();
        OperationalRuntimeException failure = assertThrows(OperationalRuntimeException.class, manager::startPrepared);

        assertTrue(failure.getMessage().contains("must be a TypedEndpointHandler"), failure.getMessage());
    }

    private static TypedEndpointHandler<Object, Object> typed() {
        return request -> CompletableFuture.completedFuture("ok");
    }

    /** A module whose endpoint writes through a contract of a plugin, which declares {@code replayableWrites}. */
    private OperationalModuleRuntimeManager replayingModule(Set<String> replayableWrites, EndpointHandler<?, ?> handler) {
        var endpoint = new OperationalModuleDescriptor.EndpointDeclaration(
            "runtime.status.v1",
            "POST",
            "/api/platform/runtime/status",
            "runtime.status",
            TestEndpointAccessPolicies.platform("platform:runtime:read"),
            new EndpointContract("brix://schemas/test-endpoints/create-thing-request/1.0.0",
                "brix://schemas/test-endpoints/thing/1.0.0", 201, null,
                new EndpointContract.Replay("runtime.status", Duration.ofHours(24), "test.write", null)));
        var descriptor = new OperationalModuleDescriptor(
            new OperationalModuleIdentity("replaying-test", "3.2.0", "runtime-tests"),
            ">=3.2.0 <4.0.0",
            List.of(),
            List.of(new OperationalModuleDescriptor.RequiredInternalContract(
                "test.write", Runnable.class.getName(), ">=1.0.0 <2.0.0", true, "test.write")),
            Set.of("test.write"),
            java.util.Map.of(endpoint.endpointId(), endpoint),
            java.util.Map.of());
        PlatformOperationalModule provider = new PlatformOperationalModule() {
            @Override
            public void configure(OperationalBootstrapContext bootstrap) {
                bootstrap.bindEndpointHandlerFactory("runtime.status", context -> handler);
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
            descriptor,
            provider.getClass().getProtectionDomain().getCodeSource().getLocation());
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        InternalContractBinder contracts = new InternalContractBinder(registry, registry, getClass().getClassLoader(),
            owner -> "owner-plugin".equals(owner) ? java.util.Optional.of(new RecordingPersistence())
                : java.util.Optional.empty());
        contracts.bindPlugin(
            io.runtime.orchestrator.plugin.PluginRuntimeDescriptor.builder("owner-plugin")
                .version("3.2.0")
                .providedInternalContract("test.write", Runnable.class.getName(), "1.0.0", "test.provider", "owner-plugin",
                    replayableWrites)
                .build(),
            bootstrap -> bootstrap.bind("test.write", Runnable.class, context -> () -> { }),
            Set.of());
        return new OperationalModuleRuntimeManager(
            () -> List.of(discovered),
            List::of,
            contracts,
            new HostRuntimeOperationalView(List.of("replaying-test")),
            List.of("replaying-test"),
            "3.2.0");
    }

    private OperationalRuntimeException startFails(EndpointContract contract) {
        OperationalModuleRuntimeManager manager = moduleWith(contract, request -> CompletableFuture.completedFuture("ok"));
        manager.prepare();
        return assertThrows(OperationalRuntimeException.class, manager::startPrepared);
    }

    private OperationalModuleRuntimeManager moduleWith(EndpointContract contract, TypedEndpointHandler<Object, Object> handler) {
        return moduleWith(contract, (EndpointHandler<?, ?>) handler);
    }

    private OperationalModuleRuntimeManager moduleWith(EndpointContract contract, EndpointHandler<?, ?> handler) {
        var endpoint = new OperationalModuleDescriptor.EndpointDeclaration(
            "runtime.status.v1",
            "POST",
            "/api/platform/runtime/status",
            "runtime.status",
            TestEndpointAccessPolicies.platform("platform:runtime:read"),
            contract);
        var descriptor = new OperationalModuleDescriptor(
            new OperationalModuleIdentity("typed-test", "3.2.0", "runtime-tests"),
            ">=3.2.0 <4.0.0",
            List.of(),
            List.of(),
            java.util.Set.of(),
            java.util.Map.of(endpoint.endpointId(), endpoint),
            java.util.Map.of());
        PlatformOperationalModule provider = new PlatformOperationalModule() {
            @Override
            public void configure(OperationalBootstrapContext bootstrap) {
                bootstrap.bindEndpointHandlerFactory("runtime.status", context -> handler);
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
            descriptor,
            provider.getClass().getProtectionDomain().getCodeSource().getLocation());
        return manager(() -> List.of(discovered), List.of("typed-test"));
    }

    @Test
    void startupFailureReleasesCreatedOperationalResourcesInReverseOrder() {
        List<String> closeOrder = new java.util.ArrayList<>();
        PlatformOperationalModule provider = new PlatformOperationalModule() {
            @Override
            public void configure(OperationalBootstrapContext bootstrap) {
                bootstrap.bindEndpointHandlerFactory(
                    "runtime.status",
                    context -> new CloseableEndpoint(closeOrder, "endpoint"));
                bootstrap.bindTaskFactory(
                    "runtime.task",
                    context -> new CloseableTask(closeOrder, "task"));
            }

            @Override
            public void onStart(OperationalContext context) {
                throw new OperationalRuntimeException(
                    "operational.start.injected_failure",
                    "Injected startup failure");
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
            descriptorWithTask("resource-cleanup-test"),
            provider.getClass().getProtectionDomain().getCodeSource().getLocation());
        OperationalModuleRuntimeManager manager = manager(
            () -> List.of(discovered),
            List.of("resource-cleanup-test"));

        manager.prepare();
        OperationalRuntimeException failure =
            assertThrows(OperationalRuntimeException.class, manager::startPrepared);

        assertEquals("operational.start.injected_failure", failure.diagnosticCode());
        assertEquals(PluginLifecycleState.STOPPED, manager.states().get(0).lifecycleState());
        assertFalse(manager.states().get(0).entriesPublished());
        assertTrue(manager.preparedRoutes().isEmpty());
        assertEquals(List.of("task", "endpoint"), closeOrder);
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

    @Test
    void operationalInternalProviderMayRequireDeclaredOwnerCapability() {
        Runnable ownerCapability = () -> { };
        PlatformOperationalModule module = operationalModuleWithoutBindings();
        var descriptor = new OperationalModuleDescriptor(
            new OperationalModuleIdentity("provider-owner-capability", "3.2.0", "runtime-tests"),
            ">=3.2.0 <4.0.0",
            List.of(new OperationalModuleDescriptor.ProvidedInternalContract(
                "test.contract",
                Runnable.class.getName(),
                "1.0.0",
                "test.provider",
                "runtime-tests")),
            List.of(new OperationalModuleDescriptor.RequiredInternalContract(
                "test.contract",
                Runnable.class.getName(),
                ">=1.0.0 <2.0.0",
                true,
                "test.contract")),
            java.util.Set.of("test.contract"),
            java.util.Map.of(),
            java.util.Map.of());
        var discovered = new ServiceLoaderOperationalModuleDiscovery.DiscoveredOperationalModule(
            module,
            descriptor,
            module.getClass().getProtectionDomain().getCodeSource().getLocation());
        InternalContractProvider provider =
            bootstrap -> bootstrap.bind(
                "test.contract",
                Runnable.class,
                context -> context.requireOwnerCapability(Runnable.class));
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        registry.register(Runnable.class, ownerCapability);
        OperationalModuleRuntimeManager manager = new OperationalModuleRuntimeManager(
            () -> List.of(discovered),
            () -> List.of(provider),
            new InternalContractBinder(registry, registry, getClass().getClassLoader()),
            new HostRuntimeOperationalView(List.of("provider-owner-capability")),
            List.of("provider-owner-capability"),
            "3.2.0");

        var states = manager.prepare();

        assertEquals(PluginLifecycleState.RESOLVED, states.get(0).lifecycleState());
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
            TestEndpointAccessPolicies.platform("platform:runtime:read"));
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

    private OperationalModuleDescriptor descriptorWithTask(String id) {
        var endpoint = new OperationalModuleDescriptor.EndpointDeclaration(
            "runtime.status.v1",
            "GET",
            "/api/platform/runtime/status",
            "runtime.status",
            TestEndpointAccessPolicies.platform("platform:runtime:read"));
        var task = new OperationalModuleDescriptor.TaskDeclaration(
            "runtime.task.v1",
            "runtime.task",
            "task-platform",
            "plugin-service",
            "every:PT1H",
            "task-standard",
            "task-no-overlap",
            "task-no-retry",
            "task-instance-local");
        return new OperationalModuleDescriptor(
            new OperationalModuleIdentity(id, "3.2.0", "runtime-tests"),
            ">=3.2.0 <4.0.0",
            List.of(),
            List.of(),
            java.util.Set.of(),
            java.util.Map.of(endpoint.endpointId(), endpoint),
            java.util.Map.of(task.taskId(), task));
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

    private static final class CloseableEndpoint implements EndpointHandler<Object, Object>, AutoCloseable {
        private final List<String> closeOrder;
        private final String name;

        private CloseableEndpoint(List<String> closeOrder, String name) {
            this.closeOrder = closeOrder;
            this.name = name;
        }

        @Override
        public Object handle(Object request) {
            return "ok";
        }

        @Override
        public void close() {
            closeOrder.add(name);
        }
    }

    private static final class CloseableTask implements ManagedTask, AutoCloseable {
        private final List<String> closeOrder;
        private final String name;

        private CloseableTask(List<String> closeOrder, String name) {
            this.closeOrder = closeOrder;
            this.name = name;
        }

        @Override
        public void run() {
        }

        @Override
        public void close() {
            closeOrder.add(name);
        }
    }
}
