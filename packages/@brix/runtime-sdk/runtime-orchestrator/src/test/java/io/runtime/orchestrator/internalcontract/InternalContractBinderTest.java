/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.internalcontract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.capability.DefaultCapabilityRegistry;
import io.runtime.orchestrator.invocation.RuntimeInvocation;
import io.runtime.orchestrator.invocation.RuntimeInvocations;
import io.runtime.orchestrator.operational.OperationalModuleDescriptor;
import io.runtime.orchestrator.operational.OperationalModuleIdentity;
import io.runtime.orchestrator.operational.OperationalRuntimeException;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.persistence.RecordingPersistence;
import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.orchestrator.query.QuerySideEffects;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.IntegrationEvent;
import io.runtime.sdk.internalcontract.InternalContractProvider;
import io.runtime.sdk.plugin.AccessDecision;
import io.runtime.sdk.plugin.CallerIdentity;
import io.runtime.sdk.plugin.CancellationSignal;
import io.runtime.sdk.plugin.InvocationScope;
import io.runtime.sdk.query.BrixQueryException;

class InternalContractBinderTest {

    @Test
    void bindsAndResolvesOneDescriptorDeclaredProvider() {
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        InternalContractBinder binder =
            new InternalContractBinder(registry, registry, getClass().getClassLoader());
        binder.bind(providerDescriptor(), provider(), Set.of());
        binder.activateAndFreeze();

        Runnable contract = binder.require(requirement(">=1.0.0 <2.0.0"), Runnable.class);

        contract.run();
        assertEquals(
            "owner-module",
            registry.getInternalContract("test.contract", Runnable.class)
                .orElseThrow()
                .ownerIdentity()
                .moduleId());
    }

    @Test
    void bindsAndResolvesPluginDeclaredProvider() {
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        Runnable ownerCapability = () -> { };
        registry.register(Runnable.class, ownerCapability);
        InternalContractBinder binder =
            new InternalContractBinder(registry, registry, getClass().getClassLoader());
        binder.bindPlugin(pluginDescriptor(), ownerCapabilityProvider(), Set.of(Runnable.class));
        binder.activateAndFreeze();

        Runnable contract = binder.require(requirement(">=1.0.0 <2.0.0"), Runnable.class);

        assertEquals(ownerCapability, contract);
        assertEquals(
            "owner-plugin",
            registry.getInternalContract("test.contract", Runnable.class)
                .orElseThrow()
                .ownerIdentity()
                .moduleId());
    }

    @Test
    void missingBindingFailsBidirectionalValidation() {
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        InternalContractBinder binder =
            new InternalContractBinder(registry, registry, getClass().getClassLoader());

        OperationalRuntimeException failure = assertThrows(
            OperationalRuntimeException.class,
            () -> binder.bind(providerDescriptor(), bootstrap -> { }, Set.of()));

        assertEquals("internal_contract.binding_mismatch", failure.diagnosticCode());
    }

    @Test
    void versionMismatchFailsClosed() {
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        InternalContractBinder binder =
            new InternalContractBinder(registry, registry, getClass().getClassLoader());
        binder.bind(providerDescriptor(), provider(), Set.of());
        binder.activateAndFreeze();

        OperationalRuntimeException failure = assertThrows(
            OperationalRuntimeException.class,
            () -> binder.require(requirement(">=2.0.0 <3.0.0"), Runnable.class));

        assertEquals("internal_contract.version_mismatch", failure.diagnosticCode());
    }

    @Test
    void h3FreezeRejectsLateProviderMutation() {
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        InternalContractBinder binder =
            new InternalContractBinder(registry, registry, getClass().getClassLoader());
        binder.activateAndFreeze();

        assertThrows(
            IllegalStateException.class,
            () -> binder.bind(providerDescriptor(), provider(), Set.of()));
    }

    @Test
    void ownerContractsRunInsideTheOwnerUnitOfWork() {
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        RecordingPersistence persistence = new RecordingPersistence();
        List<Boolean> insideUnitOfWork = new ArrayList<>();
        InternalContractBinder binder = new InternalContractBinder(
            registry, registry, getClass().getClassLoader(), ownerPersistence(persistence));

        binder.bindPlugin(pluginDescriptor(), bootstrap -> bootstrap.bind(
            "test.contract",
            Runnable.class,
            context -> () -> insideUnitOfWork.add(persistence.active())), Set.of());
        binder.activateAndFreeze();
        binder.require(requirement(">=1.0.0 <2.0.0"), Runnable.class).run();

        assertEquals(List.of(true), insideUnitOfWork);
        assertEquals(List.of("required"), persistence.modes());
    }

    @Test
    void pluginProviderReceivesItsOwnerPersistence() {
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        RecordingPersistence persistence = new RecordingPersistence();
        List<PersistenceCapability> received = new ArrayList<>();
        InternalContractBinder binder = new InternalContractBinder(
            registry, registry, getClass().getClassLoader(), ownerPersistence(persistence));

        binder.bindPlugin(pluginDescriptor(), bootstrap -> bootstrap.bind(
            "test.contract",
            Runnable.class,
            context -> {
                received.add(context.requireOwnerCapability(PersistenceCapability.class));
                return () -> { };
            }), Set.of(PersistenceCapability.class));
        binder.activateAndFreeze();

        assertSame(persistence, received.get(0));
    }

    @Test
    void undeclaredOwnerPersistenceFailsClosed() {
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        InternalContractBinder binder = new InternalContractBinder(
            registry, registry, getClass().getClassLoader(), ownerPersistence(new RecordingPersistence()));

        binder.bindPlugin(pluginDescriptor(), bootstrap -> bootstrap.bind(
            "test.contract",
            Runnable.class,
            context -> {
                context.requireOwnerCapability(PersistenceCapability.class);
                return () -> { };
            }), Set.of());

        OperationalRuntimeException failure =
            assertThrows(OperationalRuntimeException.class, binder::activateAndFreeze);
        assertEquals("internal_contract.factory_failed", failure.diagnosticCode());
        assertEquals("internal_contract.owner_capability_undeclared",
            ((OperationalRuntimeException) failure.getCause()).diagnosticCode());
    }

    @Test
    void aPluginContractPublishesThroughItsOwnersEventBusInsideItsUnitOfWork() {
        // D125: the bus the plugin's PluginContext gives its handlers, used in the contract call's unit of work.
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        RecordingEventBus hostBus = new RecordingEventBus(null);
        registry.register(EventBusCapability.class, hostBus);
        RecordingPersistence persistence = new RecordingPersistence();
        RecordingEventBus ownerBus = new RecordingEventBus(persistence);
        List<EventBusCapability> received = new ArrayList<>();
        InternalContractBinder binder = new InternalContractBinder(
            registry, registry, getClass().getClassLoader(), ownerPersistence(persistence, ownerBus));

        binder.bindPlugin(pluginDescriptor(), bootstrap -> bootstrap.bind(
            "test.contract",
            Runnable.class,
            context -> {
                EventBusCapability events = context.requireOwnerCapability(EventBusCapability.class);
                received.add(events);
                return () -> events.publishIntegration(event());
            }), Set.of(EventBusCapability.class));
        binder.activateAndFreeze();
        binder.require(requirement(">=1.0.0 <2.0.0"), Runnable.class).run();

        assertSame(ownerBus, QuerySideEffects.unwrap(received.get(0)));
        assertEquals(List.of(true), ownerBus.publishedInsideUnitOfWork);
        assertEquals(List.of(), hostBus.publishedInsideUnitOfWork);
    }

    @Test
    void aQueryCannotPublishThroughAContractsEventBus() {
        // The same guard as the plugin's own bus: a query has no side effects (root BRX-RS-MESSAGE-002).
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        RecordingPersistence persistence = new RecordingPersistence();
        RecordingEventBus ownerBus = new RecordingEventBus(persistence);
        List<EventBusCapability> received = new ArrayList<>();
        InternalContractBinder binder = new InternalContractBinder(
            registry, registry, getClass().getClassLoader(), ownerPersistence(persistence, ownerBus));

        binder.bindPlugin(pluginDescriptor(), bootstrap -> bootstrap.bind(
            "test.contract",
            Runnable.class,
            context -> {
                received.add(context.requireOwnerCapability(EventBusCapability.class));
                return () -> { };
            }), Set.of(EventBusCapability.class));
        binder.activateAndFreeze();
        RuntimeInvocation query = new RuntimeInvocation(CallerIdentity.service("owner-plugin"),
            RuntimeInvocation.Kind.QUERY, Set.of(), InvocationScope.platform(), AccessDecision.none(), Optional.empty(),
            Instant.now().plusSeconds(5), CancellationSignal.none(), 1);

        BrixQueryException refused = assertThrows(BrixQueryException.class,
            () -> RuntimeInvocations.within(query, () -> {
                received.get(0).publishIntegration(event());
                return null;
            }));

        assertEquals(BrixQueryException.Code.PROVIDER_FAILED, refused.code());
        assertEquals(List.of(), ownerBus.publishedInsideUnitOfWork);
    }

    @Test
    void anOwnerWithoutAnEventBusOfItsOwnGetsNoneFromTheHost() {
        // D125: never the Host's direct bus, which takes no part in the owner's transaction.
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        registry.register(EventBusCapability.class, new RecordingEventBus(null));
        InternalContractBinder binder = new InternalContractBinder(
            registry, registry, getClass().getClassLoader(), ownerPersistence(new RecordingPersistence()));

        binder.bindPlugin(pluginDescriptor(), bootstrap -> bootstrap.bind(
            "test.contract",
            Runnable.class,
            context -> {
                context.requireOwnerCapability(EventBusCapability.class);
                return () -> { };
            }), Set.of(EventBusCapability.class));

        OperationalRuntimeException failure =
            assertThrows(OperationalRuntimeException.class, binder::activateAndFreeze);
        assertEquals("internal_contract.factory_failed", failure.diagnosticCode());
        assertEquals("internal_contract.owner_event_bus_missing",
            ((OperationalRuntimeException) failure.getCause()).diagnosticCode());
    }

    @Test
    void anUndeclaredEventBusFailsClosed() {
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        registry.register(EventBusCapability.class, new RecordingEventBus(null));
        RecordingPersistence persistence = new RecordingPersistence();
        InternalContractBinder binder = new InternalContractBinder(registry, registry, getClass().getClassLoader(),
            ownerPersistence(persistence, new RecordingEventBus(persistence)));

        binder.bindPlugin(pluginDescriptor(), bootstrap -> bootstrap.bind(
            "test.contract",
            Runnable.class,
            context -> {
                context.requireOwnerCapability(EventBusCapability.class);
                return () -> { };
            }), Set.of(PersistenceCapability.class));

        OperationalRuntimeException failure =
            assertThrows(OperationalRuntimeException.class, binder::activateAndFreeze);
        assertEquals("internal_contract.factory_failed", failure.diagnosticCode());
        assertEquals("internal_contract.owner_capability_undeclared",
            ((OperationalRuntimeException) failure.getCause()).diagnosticCode());
    }

    @Test
    void theOwnerOfReplayableWritesIsKnownOnceTheyAreBound() {
        // Idempotency design D79: an operational endpoint's record goes to this owner's table.
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        InternalContractBinder binder = new InternalContractBinder(
            registry, registry, getClass().getClassLoader(), ownerPersistence(new RecordingPersistence()));

        binder.bindPlugin(pluginDescriptor(Set.of("run")), provider(), Set.of());

        assertEquals(Optional.of("owner-plugin"), binder.replayableWritesOwner("test.contract"));
    }

    @Test
    void aContractWithoutReplayableWritesKeepsNoResults() {
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        InternalContractBinder binder = new InternalContractBinder(
            registry, registry, getClass().getClassLoader(), ownerPersistence(new RecordingPersistence()));

        binder.bindPlugin(pluginDescriptor(), provider(), Set.of());

        assertEquals(Optional.empty(), binder.replayableWritesOwner("test.contract"));
        assertEquals(Optional.empty(), binder.replayableWritesOwner("no.such.contract"));
    }

    @Test
    void aReplayableWriteIsAMethodOfTheContract() {
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        InternalContractBinder binder = new InternalContractBinder(
            registry, registry, getClass().getClassLoader(), ownerPersistence(new RecordingPersistence()));

        OperationalRuntimeException failure = assertThrows(OperationalRuntimeException.class,
            () -> binder.bindPlugin(pluginDescriptor(Set.of("walk")), provider(), Set.of()));

        assertEquals("internal_contract.replayable_write_invalid", failure.diagnosticCode());
    }

    @Test
    void aReplayableWriteIsNotAnOverloadedMethod() {
        // Its result is read back as the method's type, so the name has to mean one method.
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        InternalContractBinder binder = new InternalContractBinder(
            registry, registry, getClass().getClassLoader(), ownerPersistence(new RecordingPersistence()));
        PluginRuntimeDescriptor descriptor = PluginRuntimeDescriptor.builder("owner-plugin")
            .version("3.2.0")
            .providedInternalContract("test.contract", Overloaded.class.getName(), "1.0.0", "test.provider", "owner-plugin",
                Set.of("save"))
            .build();
        Overloaded contract = new Overloaded() {
            @Override
            public void save(String name) {
            }

            @Override
            public void save(int number) {
            }
        };

        OperationalRuntimeException failure = assertThrows(OperationalRuntimeException.class,
            () -> binder.bindPlugin(descriptor, bootstrap -> bootstrap.bind("test.contract", Overloaded.class,
                context -> contract), Set.of()));

        assertEquals("internal_contract.replayable_write_invalid", failure.diagnosticCode());
    }

    @Test
    void anOwnerOfReplayableWritesHasItsPersistence() {
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        InternalContractBinder binder = new InternalContractBinder(registry, registry, getClass().getClassLoader());
        binder.bindPlugin(pluginDescriptor(Set.of("run")), provider(), Set.of());

        OperationalRuntimeException failure = assertThrows(OperationalRuntimeException.class, binder::activateAndFreeze);

        assertEquals("internal_contract.owner_persistence_missing", failure.diagnosticCode());
    }

    /** A contract whose method has two shapes. */
    public interface Overloaded {
        void save(String name);

        void save(int number);
    }

    private static OwnerPersistenceProvider ownerPersistence(RecordingPersistence persistence) {
        return owner -> "owner-plugin".equals(owner) ? Optional.of(persistence) : Optional.empty();
    }

    /** The owner's persistence, and its own event bus as the persistence adapter gives it. */
    private static OwnerPersistenceProvider ownerPersistence(RecordingPersistence persistence, EventBusCapability bus) {
        return new OwnerPersistenceProvider() {
            @Override
            public Optional<PersistenceCapability> forOwner(String owner) {
                return "owner-plugin".equals(owner) ? Optional.of(persistence) : Optional.empty();
            }

            @Override
            public Optional<EventBusCapability> eventBusFor(String owner) {
                return "owner-plugin".equals(owner) ? Optional.of(bus) : Optional.empty();
            }
        };
    }

    private static IntegrationEvent event() {
        return new IntegrationEvent("owner-plugin") {
            private static final long serialVersionUID = 1L;

            @Override
            public String getRoutingKey() {
                return "test.1";
            }
        };
    }

    /** Records whether each integration event arrived inside the owner's unit of work. */
    private static final class RecordingEventBus implements EventBusCapability {

        private final RecordingPersistence persistence;
        private final List<Boolean> publishedInsideUnitOfWork = new ArrayList<>();

        private RecordingEventBus(RecordingPersistence persistence) {
            this.persistence = persistence;
        }

        @Override
        public void publish(DomainEvent event) {
            throw new UnsupportedOperationException("integration events only");
        }

        @Override
        public void publishIntegration(IntegrationEvent event) {
            publishedInsideUnitOfWork.add(persistence != null && persistence.active());
        }
    }

    private InternalContractProvider provider() {
        return bootstrap -> bootstrap.bind("test.contract", Runnable.class, context -> () -> { });
    }

    private InternalContractProvider ownerCapabilityProvider() {
        return bootstrap -> bootstrap.bind(
            "test.contract",
            Runnable.class,
            context -> context.requireOwnerCapability(Runnable.class));
    }

    private PluginRuntimeDescriptor pluginDescriptor() {
        return pluginDescriptor(Set.of());
    }

    private PluginRuntimeDescriptor pluginDescriptor(Set<String> replayableWrites) {
        return PluginRuntimeDescriptor.builder("owner-plugin")
            .version("3.2.0")
            .providedInternalContract(
                "test.contract",
                Runnable.class.getName(),
                "1.1.0",
                "test.provider",
                "owner-plugin",
                replayableWrites)
            .build();
    }

    private OperationalModuleDescriptor providerDescriptor() {
        return new OperationalModuleDescriptor(
            new OperationalModuleIdentity("owner-module", "3.2.0", "runtime-tests"),
            ">=3.2.0 <4.0.0",
            List.of(new OperationalModuleDescriptor.ProvidedInternalContract(
                "test.contract",
                Runnable.class.getName(),
                "1.1.0",
                "test.provider",
                "runtime-tests")),
            List.of(),
            Set.of(),
            Map.of(),
            Map.of());
    }

    private OperationalModuleDescriptor.RequiredInternalContract requirement(String range) {
        return new OperationalModuleDescriptor.RequiredInternalContract(
            "test.contract",
            Runnable.class.getName(),
            range,
            true,
            "contract.test");
    }
}
