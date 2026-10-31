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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.capability.DefaultCapabilityRegistry;
import io.runtime.orchestrator.endpoint.DefaultPluginEndpointDispatcher;
import io.runtime.orchestrator.endpoint.EndpointAccessDeniedException;
import io.runtime.orchestrator.endpoint.EndpointDispatchException;
import io.runtime.orchestrator.endpoint.PluginEndpointDispatcher;
import io.runtime.orchestrator.endpoint.TenantGrantTable;
import io.runtime.orchestrator.endpoint.TestEndpointAccessPolicies;
import io.runtime.orchestrator.event.PluginEventSubscribers;
import io.runtime.orchestrator.internalcontract.InternalContractBinder;
import io.runtime.orchestrator.operational.OperationalModuleDescriptor;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.task.TaskProvider;
import io.runtime.orchestrator.task.TaskProviders;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.EventReliability;
import io.runtime.sdk.event.IntegrationEvent;
import io.runtime.sdk.internalcontract.InternalContractProvider;
import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.BrixPlugin;
import io.runtime.sdk.plugin.CommandHandler;
import io.runtime.sdk.plugin.CommandInvocation;
import io.runtime.sdk.plugin.EventHandler;
import io.runtime.sdk.plugin.EventInvocation;
import io.runtime.sdk.plugin.HandlerFactory;
import io.runtime.sdk.plugin.ManagedTask;
import io.runtime.sdk.plugin.PluginBootstrapContext;
import io.runtime.sdk.plugin.PluginContext;
import io.runtime.sdk.plugin.PluginLifecycleState;
import io.runtime.sdk.plugin.TaskInvocation;

class PluginRuntimeManagerTest {

    @Test
    void noPluginsStartsAndReportsEmptyRuntimeCollection() {
        PluginRuntimeManager manager = manager(List.of(), Map.of(), new DefaultCapabilityRegistry(), List.of());

        assertEquals(List.of(), manager.start());
        assertTrue(manager.ready());
        assertEquals(List.of(), manager.states());
    }

    @Test
    void requiredPluginProviderMissingFailsFast() {
        PluginRuntimeManager manager = manager(
            List.of(),
            Map.of(),
            new DefaultCapabilityRegistry(),
            List.of("required-plugin"));

        assertThrows(PluginRuntimeException.class, manager::start);
        assertFalse(manager.ready());
    }

    @Test
    void manifestMissingFailsFastForDiscoveredProvider() {
        RecordingPlugin plugin = new RecordingPlugin();
        PluginRuntimeManager manager = manager(
            List.of(plugin),
            Map.of(),
            new DefaultCapabilityRegistry(),
            List.of());

        assertThrows(PluginRuntimeException.class, manager::start);
    }

    @Test
    void duplicatePluginIdFailsFast() {
        RecordingPlugin first = new RecordingPlugin();
        RecordingPlugin second = new RecordingPlugin();
        PluginRuntimeDescriptor descriptor = PluginRuntimeDescriptor.builder("duplicate-plugin").build();
        PluginRuntimeManager manager = manager(
            List.of(first, second),
            Map.of(first, descriptor, second, descriptor),
            new DefaultCapabilityRegistry(),
            List.of());

        assertThrows(PluginRuntimeException.class, manager::start);
    }

    @Test
    void requiredCapabilityMissingPreventsRequiredPluginReady() {
        RecordingPlugin plugin = new RecordingPlugin();
        PluginRuntimeManager manager = manager(
            List.of(plugin),
            Map.of(plugin, PluginRuntimeDescriptor.builder("plugin-a")
                .requiredCapabilities(List.of(TestCapability.class.getSimpleName()))
                .build()),
            new DefaultCapabilityRegistry(),
            List.of("plugin-a"));

        assertThrows(PluginRuntimeException.class, manager::start);
        assertFalse(manager.ready());
        assertEquals(PluginLifecycleState.STOPPED, manager.states().get(0).lifecycleState());
    }

    @Test
    void reliableEventPublisherWithoutOutboxFailsBeforeStart() {
        RecordingPlugin plugin = new RecordingPlugin();
        DefaultCapabilityRegistry registry = eventBusRegistry();
        PluginRuntimeManager manager = manager(
            List.of(plugin),
            Map.of(plugin, PluginRuntimeDescriptor.builder("plugin-a")
                .requiredCapabilities(List.of(EventBusCapability.class.getName()))
                .eventPublication("TenantFirstOwnerAccepted", "1.0.0", EventReliability.CRITICAL)
                .build()),
            registry,
            List.of("plugin-a"));

        assertThrows(PluginRuntimeException.class, manager::start);
        assertFalse(plugin.started);
        assertFalse(manager.ready());
    }

    @Test
    void reliableEventPublisherRequiresEventBusCapabilityDeclaration() {
        RecordingPlugin plugin = new RecordingPlugin();
        DefaultCapabilityRegistry registry = eventBusRegistry();
        PluginRuntimeManager manager = manager(
            List.of(plugin),
            Map.of(plugin, PluginRuntimeDescriptor.builder("plugin-a")
                .data("platform_tenant", "platform_tenant_outbox", "platform_tenant_inbox")
                .eventPublication("TenantFirstOwnerAccepted", "1.0.0", EventReliability.CRITICAL)
                .build()),
            registry,
            List.of("plugin-a"));

        assertThrows(PluginRuntimeException.class, manager::start);
        assertFalse(plugin.started);
        assertFalse(manager.ready());
    }

    @Test
    void reliableEventPublisherStartsWhenPolicyAndCapabilityAreReady() {
        RecordingPlugin plugin = new RecordingPlugin();
        DefaultCapabilityRegistry registry = eventBusRegistry();
        PluginRuntimeManager manager = manager(
            List.of(plugin),
            Map.of(plugin, PluginRuntimeDescriptor.builder("plugin-a")
                .requiredCapabilities(List.of(EventBusCapability.class.getName()))
                .data("platform_tenant", "platform_tenant_outbox", "platform_tenant_inbox")
                .eventPublication("TenantFirstOwnerAccepted", "1.0.0", EventReliability.CRITICAL)
                .build()),
            registry,
            List.of("plugin-a"));

        manager.start();

        assertTrue(plugin.started);
        assertTrue(manager.ready());
    }

    @Test
    void aPluginWhoseMessagingTablesAreRefusedFailsBeforeItStarts() {
        // Kafka design §14 K19: a missing or incomplete outbox fails the plugin at start, not at its first event.
        RecordingPlugin plugin = new RecordingPlugin();
        DefaultCapabilityRegistry registry = eventBusRegistry();
        List<String> checked = new ArrayList<>();
        OwnerPersistenceProvider persistence = new OwnerPersistenceProvider() {
            @Override
            public Optional<io.runtime.sdk.capability.PersistenceCapability> forOwner(String ownerModuleId) {
                return Optional.empty();
            }

            @Override
            public void requireMessagingTables(String ownerModuleId) {
                checked.add(ownerModuleId);
                throw new IllegalStateException("plugin_a_outbox has no column seq");
            }
        };
        PluginRuntimeManager manager = new PluginRuntimeManager(
            () -> List.of(plugin),
            candidate -> Optional.of(PluginRuntimeDescriptor.builder("plugin-a")
                .requiredCapabilities(List.of(EventBusCapability.class.getName()))
                .data("plugin_a", "plugin_a_outbox", "")
                .eventPublication("OrderPlaced", "1.0.0", EventReliability.CRITICAL)
                .build()),
            registry,
            List.of("plugin-a"),
            PluginEndpointDispatcher.none(),
            ignored -> {
            },
            List::of,
            new InternalContractBinder(registry, registry, getClass().getClassLoader()),
            persistence);

        assertThrows(PluginRuntimeException.class, manager::start);
        assertEquals(List.of("plugin-a"), checked);
        assertFalse(plugin.started);
        assertFalse(manager.ready());
    }

    @Test
    void eventSubscriberWithoutInboxFailsBeforeStart() {
        RecordingPlugin plugin = new RecordingPlugin();
        PluginRuntimeManager manager = manager(
            List.of(plugin),
            Map.of(plugin, PluginRuntimeDescriptor.builder("plugin-a")
                .data("platform_tenant", "platform_tenant_outbox", "")
                .eventSubscription(
                    "tenant-first-owner-projection",
                    "TenantFirstOwnerAccepted",
                    ">=1.0.0 <2.0.0",
                    "tenant-first-owner-projection.v1",
                    "event-standard",
                    "persistent-inbox")
                .build()),
            new DefaultCapabilityRegistry(),
            List.of("plugin-a"));

        assertThrows(PluginRuntimeException.class, manager::start);
        assertFalse(plugin.started);
        assertFalse(manager.ready());
    }

    @Test
    void aSubscriptionWithoutAnInboxToABestEffortEventTypeNeedsNoInboxTable() {
        // Root BRX-RS-MANIFEST-025: a BEST_EFFORT subscription may name the explicit no-durable-idempotency policy.
        RecordingPlugin plugin = new RecordingPlugin();
        plugin.eventHandlers.put("seen.v1", context -> new AcceptedHandler());
        PluginRuntimeManager manager = manager(
            List.of(plugin),
            Map.of(plugin, PluginRuntimeDescriptor.builder("plugin-a")
                .eventPublication("MemberSeen", "1.0.0", EventReliability.BEST_EFFORT)
                .eventSubscription("seen", "MemberSeen", ">=1.0.0 <2.0.0", "seen.v1", "event-standard",
                    PluginRuntimeDescriptor.EventSubscription.NO_DURABLE_IDEMPOTENCY)
                .build()),
            new DefaultCapabilityRegistry(),
            List.of("plugin-a"));

        manager.start();

        assertTrue(plugin.started);
        assertEquals(1, manager.eventSubscribers("MemberSeen").size());
    }

    @Test
    void aSubscriptionWithoutAnInboxToACriticalEventTypeStopsThePluginBeforeItStarts() {
        RecordingPlugin plugin = new RecordingPlugin();
        plugin.eventHandlers.put("projection.v1", context -> new AcceptedHandler());
        PluginRuntimeManager manager = manager(
            List.of(plugin),
            Map.of(plugin, PluginRuntimeDescriptor.builder("plugin-a")
                .requiredCapabilities(List.of(EventBusCapability.class.getName()))
                .data("platform_tenant", "platform_tenant_outbox", "platform_tenant_inbox")
                .eventPublication("TenantFirstOwnerAccepted", "1.0.0", EventReliability.CRITICAL)
                .eventSubscription("projection", "TenantFirstOwnerAccepted", ">=1.0.0 <2.0.0", "projection.v1",
                    "event-standard", PluginRuntimeDescriptor.EventSubscription.NO_DURABLE_IDEMPOTENCY)
                .build()),
            eventBusRegistry(),
            List.of("plugin-a"));

        PluginRuntimeException refused = assertThrows(PluginRuntimeException.class, manager::start);
        assertTrue(refused.getMessage().contains("keeps no inbox"), refused.getMessage());
        assertFalse(plugin.started);
    }

    @Test
    void aRetryPolicyTheRuntimeDoesNotKnowStopsThePluginBeforeItStarts() {
        // Kafka design §14 K20: a subscription names only policies of the platform's catalog, which take effect.
        RecordingPlugin plugin = new RecordingPlugin();
        plugin.eventHandlers.put("projection.v1", context -> new AcceptedHandler());
        PluginRuntimeManager manager = manager(
            List.of(plugin),
            Map.of(plugin, PluginRuntimeDescriptor.builder("plugin-a")
                .data("platform_tenant", "", "platform_tenant_inbox")
                .eventSubscription("projection", "TenantFirstOwnerAccepted", ">=1.0.0 <2.0.0", "projection.v1",
                    "retry-forever", PluginRuntimeDescriptor.EventSubscription.PERSISTENT_INBOX)
                .build()),
            new DefaultCapabilityRegistry(),
            List.of("plugin-a"));

        PluginRuntimeException refused = assertThrows(PluginRuntimeException.class, manager::start);
        assertTrue(refused.getMessage().contains("unknown retry policy 'retry-forever'"), refused.getMessage());
        assertFalse(plugin.started);
    }

    @Test
    void aConcurrencyPolicyTheRuntimeDoesNotKnowStopsThePluginBeforeItStarts() {
        RecordingPlugin plugin = new RecordingPlugin();
        plugin.eventHandlers.put("projection.v1", context -> new AcceptedHandler());
        PluginRuntimeManager manager = manager(
            List.of(plugin),
            Map.of(plugin, PluginRuntimeDescriptor.builder("plugin-a")
                .data("platform_tenant", "", "platform_tenant_inbox")
                .eventSubscription("projection", "TenantFirstOwnerAccepted", ">=1.0.0 <2.0.0", "projection.v1",
                    "event-standard", PluginRuntimeDescriptor.EventSubscription.PERSISTENT_INBOX)
                .subscriptionPolicies("projection.v1", "event-standard", "ten-at-once", "")
                .build()),
            new DefaultCapabilityRegistry(),
            List.of("plugin-a"));

        PluginRuntimeException refused = assertThrows(PluginRuntimeException.class, manager::start);
        assertTrue(refused.getMessage().contains("unknown concurrency policy 'ten-at-once'"), refused.getMessage());
    }

    @Test
    void aHandlerIdThatCannotNameAConsumerGroupStopsThePluginBeforeItStarts() {
        // Kafka design §14 K15: handler ids become consumer group names.
        RecordingPlugin plugin = new RecordingPlugin();
        plugin.eventHandlers.put("projection v1", context -> new AcceptedHandler());
        PluginRuntimeManager manager = manager(
            List.of(plugin),
            Map.of(plugin, PluginRuntimeDescriptor.builder("plugin-a")
                .data("platform_tenant", "", "platform_tenant_inbox")
                .eventSubscription("projection", "TenantFirstOwnerAccepted", ">=1.0.0 <2.0.0", "projection v1",
                    "event-standard", PluginRuntimeDescriptor.EventSubscription.PERSISTENT_INBOX)
                .build()),
            new DefaultCapabilityRegistry(),
            List.of("plugin-a"));

        PluginRuntimeException refused = assertThrows(PluginRuntimeException.class, manager::start);
        assertTrue(refused.getMessage().contains("is not a legal name"), refused.getMessage());
    }

    @Test
    void twoPluginsThatPublishOneEventTypeAreBothRefused() {
        // Root BRX-RS-BOOT-005: an event type has one publisher; an ambiguous one fails every plugin it concerns.
        RecordingPlugin first = new RecordingPlugin();
        RecordingPlugin second = new RecordingPlugin();
        PluginRuntimeManager manager = manager(
            List.of(first, second),
            Map.of(first, publisher("plugin-a", "1.0.0"), second, publisher("plugin-b", "1.0.0")),
            eventBusRegistry(),
            List.of());

        manager.start();

        assertFalse(first.started);
        assertFalse(second.started);
    }

    @Test
    void aSubscriptionWhoseRangeDoesNotTakeThePublishedVersionStopsTheSubscriber() {
        RecordingPlugin publisher = new RecordingPlugin();
        RecordingPlugin subscriber = new RecordingPlugin();
        subscriber.eventHandlers.put("projection.v1", context -> new AcceptedHandler());
        PluginRuntimeManager manager = manager(
            List.of(publisher, subscriber),
            Map.of(publisher, publisher("plugin-a", "2.0.0"), subscriber, subscriber("plugin-a-owner")),
            eventBusRegistry(),
            List.of());

        manager.start();

        assertTrue(publisher.started);
        assertFalse(subscriber.started, "accepts >=1.0.0 <2.0.0 of an event published as 2.0.0");
    }

    @Test
    void aSubscriptionThatTrustsAnotherPublisherStopsTheSubscriber() {
        RecordingPlugin publisher = new RecordingPlugin();
        RecordingPlugin subscriber = new RecordingPlugin();
        subscriber.eventHandlers.put("projection.v1", context -> new AcceptedHandler());
        PluginRuntimeManager manager = manager(
            List.of(publisher, subscriber),
            Map.of(publisher, publisher("plugin-a", "1.0.0"), subscriber, subscriber("plugin-x-owner")),
            eventBusRegistry(),
            List.of());

        manager.start();

        assertTrue(publisher.started);
        assertFalse(subscriber.started, "trusts plugin-x, but plugin-a publishes the event type");
    }

    @Test
    void aProvidedCommandWithoutAHandlerStopsThePluginBeforeItStarts() {
        // Root BRX-RS-MANIFEST-013: a provided command needs its handler factory.
        RecordingPlugin plugin = new RecordingPlugin();
        PluginRuntimeManager manager = manager(
            List.of(plugin),
            Map.of(plugin, PluginRuntimeDescriptor.builder("plugin-a")
                .data("plugin_a", "", "plugin_a_inbox")
                .providedCommand(new PluginRuntimeDescriptor.ProvidedCommand("ReserveStock", "reserve-stock.v1", "1.0.0",
                    "event-standard", "event-standard", "partition-serial", "command-envelope", "declared-consumers", "persistent-inbox"))
                .build()),
            new DefaultCapabilityRegistry(),
            List.of("plugin-a"));

        PluginRuntimeException refused = assertThrows(PluginRuntimeException.class, manager::start);
        assertTrue(refused.getMessage().contains("provided command"), refused.getMessage());
        assertFalse(plugin.started);
    }

    @Test
    void aPluginThatRequiresACommandNoPluginProvidesIsRefused() {
        // Root BRX-RS-MESSAGE-010: every command resolves to one owner at startup.
        RecordingPlugin plugin = new RecordingPlugin();
        PluginRuntimeManager manager = manager(
            List.of(plugin),
            Map.of(plugin, PluginRuntimeDescriptor.builder("plugin-a")
                .data("plugin_a", "plugin_a_outbox", "")
                .consumedCommand(new PluginRuntimeDescriptor.ConsumedCommand("ReserveStock", ">=1.0.0 <2.0.0", true,
                    "event-standard", "event-standard", "persistent-inbox"))
                .build()),
            new DefaultCapabilityRegistry(),
            List.of("plugin-a"));

        PluginRuntimeException refused = assertThrows(PluginRuntimeException.class, manager::start);
        assertTrue(refused.getMessage().contains("no plugin on this host provides"), refused.getMessage());
    }

    @Test
    void aConsumerThatSendsAnotherVersionThanTheProviderTakesIsRefused() {
        RecordingPlugin provider = new RecordingPlugin();
        provider.commandHandlers.put("reserve-stock.v1", context -> new ReserveHandler());
        RecordingPlugin consumer = new RecordingPlugin();
        PluginRuntimeManager manager = manager(
            List.of(provider, consumer),
            Map.of(provider, PluginRuntimeDescriptor.builder("plugin-p")
                    .data("plugin_p", "", "plugin_p_inbox")
                    .providedCommand(new PluginRuntimeDescriptor.ProvidedCommand("ReserveStock", "reserve-stock.v1", "2.0.0",
                    "event-standard", "event-standard", "partition-serial", "command-envelope", "declared-consumers", "persistent-inbox"))
                    .build(),
                consumer, PluginRuntimeDescriptor.builder("plugin-c")
                    .data("plugin_c", "plugin_c_outbox", "")
                    .consumedCommand(new PluginRuntimeDescriptor.ConsumedCommand("ReserveStock", ">=1.0.0 <2.0.0", false,
                    "event-standard", "event-standard", "persistent-inbox"))
                    .build()),
            new DefaultCapabilityRegistry(),
            List.of());

        manager.start();

        assertTrue(provider.started);
        assertFalse(consumer.started);
        assertEquals("2.0.0", manager.commandProvider("ReserveStock").orElseThrow().command().version());
        assertEquals(ReserveHandler.Reserve.class, manager.commandProvider("ReserveStock").orElseThrow().payloadType());
    }

    @Test
    void anIdempotencyPolicyTheRuntimeDoesNotKnowStopsThePluginBeforeItStarts() {
        // Root BRX-RS-MANIFEST-026: a policy reference must resolve.
        RecordingPlugin plugin = new RecordingPlugin();
        plugin.eventHandlers.put("projection.v1", context -> new AcceptedHandler());
        PluginRuntimeManager manager = manager(
            List.of(plugin),
            Map.of(plugin, PluginRuntimeDescriptor.builder("plugin-a")
                .data("platform_tenant", "platform_tenant_outbox", "platform_tenant_inbox")
                .eventSubscription("projection", "TenantFirstOwnerAccepted", ">=1.0.0 <2.0.0", "projection.v1",
                    "event-standard", "at-most-once")
                .build()),
            new DefaultCapabilityRegistry(),
            List.of("plugin-a"));

        PluginRuntimeException refused = assertThrows(PluginRuntimeException.class, manager::start);
        assertTrue(refused.getMessage().contains("at-most-once"), refused.getMessage());
        assertFalse(plugin.started);
    }

    @Test
    void aSubscriptionIsServedByTheHandlerItsFactoryCreatedWithThePayloadTypeItNames() {
        RecordingPlugin plugin = new RecordingPlugin();
        plugin.eventHandlers.put("projection.v1", context -> new AcceptedHandler());
        PluginRuntimeManager manager = manager(
            List.of(plugin), Map.of(plugin, subscriber()), new DefaultCapabilityRegistry(), List.of("plugin-a"));
        PluginEventSubscribers delivery = new PluginEventSubscribers(manager);
        assertFalse(delivery.ready(), "the relay delivers nothing before the plugins start");

        manager.start();

        assertTrue(delivery.ready());
        List<PluginRuntimeManager.EventSubscriber> subscribers = manager.eventSubscribers("TenantFirstOwnerAccepted");
        assertEquals(1, subscribers.size());
        assertEquals("plugin-a", subscribers.get(0).pluginId());
        assertTrue(subscribers.get(0).handler().handler() instanceof AcceptedHandler);
        assertEquals(Accepted.class, subscribers.get(0).handler().payloadType());
    }

    @Test
    void aBrokerLearnsEveryDeclaredSubscriptionAndPublishedEventType() {
        // Kafka design §4.2, §4.4: the runtime creates one consumer per subscription and a topic per event type.
        RecordingPlugin plugin = new RecordingPlugin();
        plugin.eventHandlers.put("projection.v1", context -> new AcceptedHandler());
        PluginRuntimeDescriptor descriptor = PluginRuntimeDescriptor.builder("plugin-a")
            .requiredCapabilities(List.of(EventBusCapability.class.getName()))
            .data("platform_tenant", "platform_tenant_outbox", "platform_tenant_inbox")
            .eventPublication("TenantFirstOwnerAccepted", "1.0.0", EventReliability.CRITICAL)
            .eventSubscription(
                "projection", "TenantFirstOwnerAccepted", ">=1.0.0 <2.0.0", "projection.v1", "event-standard",
                "persistent-inbox")
            .build();
        PluginRuntimeManager manager = manager(
            List.of(plugin), Map.of(plugin, descriptor), eventBusRegistry(), List.of("plugin-a"));
        PluginEventSubscribers declared = new PluginEventSubscribers(manager);

        manager.start();

        assertEquals(Set.of("TenantFirstOwnerAccepted"), declared.publishedEventTypes());
        assertEquals(1, declared.subscriptions().size());
        assertEquals("plugin-a", declared.subscriptions().get(0).pluginId());
        assertEquals("projection.v1", declared.subscriptions().get(0).subscription().handlerId());
        assertTrue(declared.subscriptions().get(0).handler().handler() instanceof AcceptedHandler);
    }

    @Test
    void aHandlerThatDoesNotNameItsPayloadTypeStopsThePlugin() {
        RecordingPlugin plugin = new RecordingPlugin();
        plugin.eventHandlers.put("projection.v1", context -> invocation -> CompletableFuture.completedFuture(null));
        PluginRuntimeManager manager = manager(
            List.of(plugin), Map.of(plugin, subscriber()), new DefaultCapabilityRegistry(), List.of("plugin-a"));

        assertThrows(PluginRuntimeException.class, manager::start);
        assertFalse(manager.ready());
    }

    @Test
    void aSubscriptionWithoutAHandlerFactoryStopsThePluginBeforeItStarts() {
        RecordingPlugin plugin = new RecordingPlugin();
        PluginRuntimeManager manager = manager(
            List.of(plugin), Map.of(plugin, subscriber()), new DefaultCapabilityRegistry(), List.of("plugin-a"));

        assertThrows(PluginRuntimeException.class, manager::start);
        assertTrue(plugin.configured);
        assertFalse(plugin.started);
        assertFalse(manager.ready());
    }

    @Test
    void startsRequiredPluginWhenCapabilitiesArePresent() {
        RecordingPlugin plugin = new RecordingPlugin();
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        registry.register(TestCapability.class, new TestCapability() {
        });
        PluginRuntimeManager manager = manager(
            List.of(plugin),
            Map.of(plugin, PluginRuntimeDescriptor.builder("plugin-a")
                .requiredCapabilities(List.of(TestCapability.class.getSimpleName()))
                .build()),
            registry,
            List.of("plugin-a"));

        List<PluginRuntimeState> states = manager.start();

        assertTrue(plugin.started);
        assertTrue(manager.ready());
        assertEquals(PluginLifecycleState.STARTED, states.get(0).lifecycleState());
        assertTrue(states.get(0).ready());
    }

    @Test
    void requiredPluginStartFailureFailsFastAndStopsPlugin() {
        RecordingPlugin plugin = new RecordingPlugin();
        plugin.failOnStart = true;
        PluginRuntimeManager manager = manager(
            List.of(plugin),
            Map.of(plugin, PluginRuntimeDescriptor.builder("plugin-a").build()),
            new DefaultCapabilityRegistry(),
            List.of("plugin-a"));

        assertThrows(PluginRuntimeException.class, manager::start);
        assertTrue(plugin.stopped);
        assertEquals(PluginLifecycleState.STOPPED, manager.states().get(0).lifecycleState());
    }

    @Test
    void requiredPluginDownHealthBlocksReadiness() {
        RecordingPlugin plugin = new RecordingPlugin();
        plugin.health = BrixHealth.down("down");
        PluginRuntimeManager manager = manager(
            List.of(plugin),
            Map.of(plugin, PluginRuntimeDescriptor.builder("plugin-a").build()),
            new DefaultCapabilityRegistry(),
            List.of("plugin-a"));

        assertThrows(PluginRuntimeException.class, manager::start);
        assertFalse(manager.ready());
        assertEquals(PluginLifecycleState.STOPPED, manager.states().get(0).lifecycleState());
    }

    @Test
    void optionalPluginFailureDoesNotBlockHostReadiness() {
        RecordingPlugin plugin = new RecordingPlugin();
        plugin.failOnStart = true;
        PluginRuntimeManager manager = manager(
            List.of(plugin),
            Map.of(plugin, PluginRuntimeDescriptor.builder("optional-plugin").build()),
            new DefaultCapabilityRegistry(),
            List.of());

        manager.start();

        assertTrue(manager.ready());
        assertEquals(PluginLifecycleState.STOPPED, manager.states().get(0).lifecycleState());
    }

    @Test
    void publishesEndpointSnapshotAfterPluginStart() {
        RecordingPlugin plugin = new RecordingPlugin();
        plugin.endpointId = "tenant.list";
        DefaultPluginEndpointDispatcher dispatcher = new DefaultPluginEndpointDispatcher(java.time.Duration.ofSeconds(30));
        PluginRuntimeManager manager = new PluginRuntimeManager(
            () -> List.of(plugin),
            candidate -> Optional.of(PluginRuntimeDescriptor.builder("plugin-a")
                .endpoint("tenant.list", "GET", "/api/v1/tenants",
                    TestEndpointAccessPolicies.authenticated("tenant:admin:read"))
                .permission("tenant:admin:read", List.of())
                .build()),
            new DefaultCapabilityRegistry(),
            List.of("plugin-a"),
            dispatcher);

        manager.start();

        assertEquals("plugin-a", plugin.endpointContext.pluginIdentity().pluginId(),
            "the endpoint's factory got the plugin's own context (root BRX-RS-MANIFEST-019)");
        assertEquals(1, dispatcher.routes().size());
        EndpointAccessDeniedException denied = assertThrows(
            EndpointAccessDeniedException.class,
            () -> dispatcher.invoke("GET", "/api/v1/tenants", null, Map.of(), Map.of()));
        assertEquals(401, denied.status());
        assertEquals("ok", dispatcher.invoke("GET", "/api/v1/tenants", null, Map.of(), Map.of(
            "x-actor-id", List.of("1001"),
            "x-auth-permission", List.of("tenant:admin:read"))));
    }

    @Test
    void anEndpointIsBoundByTheHandlerIdItsRouteNames() {
        RecordingPlugin plugin = new RecordingPlugin();
        plugin.endpointId = "tenants.list-handler";
        DefaultPluginEndpointDispatcher dispatcher = new DefaultPluginEndpointDispatcher(java.time.Duration.ofSeconds(30));
        PluginRuntimeManager manager = new PluginRuntimeManager(
            () -> List.of(plugin),
            candidate -> Optional.of(PluginRuntimeDescriptor.builder("plugin-a")
                .endpoint("tenant.list", "GET", "/api/v1/tenants",
                    TestEndpointAccessPolicies.authenticated("tenant:admin:read"))
                .endpointHandler("tenants.list-handler", "tenant.list")
                .permission("tenant:admin:read", List.of())
                .build()),
            new DefaultCapabilityRegistry(),
            List.of("plugin-a"),
            dispatcher);

        manager.start();

        assertEquals("ok", dispatcher.invoke("GET", "/api/v1/tenants", null, Map.of(), Map.of(
            "x-actor-id", List.of("1001"),
            "x-auth-permission", List.of("tenant:admin:read"))));
    }

    @Test
    void invalidTenantGrantStopsStartupBeforePluginStartAndPublication() {
        RecordingPlugin plugin = new RecordingPlugin();
        plugin.endpointId = "tenant.list";
        DefaultPluginEndpointDispatcher dispatcher = new DefaultPluginEndpointDispatcher(java.time.Duration.ofSeconds(30));
        PluginRuntimeManager manager = new PluginRuntimeManager(
            () -> List.of(plugin),
            candidate -> Optional.of(PluginRuntimeDescriptor.builder("plugin-a")
                .endpoint("tenant.list", "GET", "/api/v1/tenants",
                    TestEndpointAccessPolicies.authenticated("tenant:admin:read"))
                .permission("tenant:admin:read", List.of(new TenantGrantTable.Grant("CUSTOMER", "TENANT")))
                .build()),
            new DefaultCapabilityRegistry(),
            List.of("plugin-a"),
            dispatcher);

        EndpointDispatchException invalid = assertThrows(EndpointDispatchException.class, manager::start);

        assertTrue(invalid.getMessage().contains("Subject roles may only receive SELF"), invalid.getMessage());
        assertFalse(plugin.started, "plugins must not start after a failed B2 grant check");
        assertTrue(dispatcher.routes().isEmpty());
    }

    @Test
    void pluginHandlerBindingWaitsUntilAfterHostB2Preparation() {
        RecordingPlugin plugin = new RecordingPlugin();
        plugin.endpointId = "tenant.list";
        PluginRuntimeManager manager = manager(
            List.of(plugin),
            Map.of(plugin, PluginRuntimeDescriptor.builder("plugin-a")
                .endpoint("tenant.list", "GET", "/api/v1/tenants",
                    TestEndpointAccessPolicies.authenticated("tenant:admin:read"))
                .build()),
            new DefaultCapabilityRegistry(),
            List.of("plugin-a"));

        manager.prepare();

        assertFalse(plugin.configured);
        assertEquals(1, manager.preparedRouteDeclarations().size());
        manager.startPrepared();
        assertTrue(plugin.configured);
    }

    @Test
    void prepareBindsPluginProvidedInternalContract() {
        RecordingPlugin plugin = new RecordingPlugin();
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        Runnable ownerCapability = () -> { };
        registry.register(Runnable.class, ownerCapability);
        InternalContractBinder binder =
            new InternalContractBinder(registry, registry, getClass().getClassLoader());
        PluginRuntimeManager manager = new PluginRuntimeManager(
            () -> List.of(plugin),
            candidate -> Optional.of(PluginRuntimeDescriptor.builder("plugin-a")
                .version("3.2.0")
                .providedInternalContract(
                    "test.contract",
                    Runnable.class.getName(),
                    "1.0.0",
                    "test.provider",
                    "plugin-a")
                .build()),
            registry,
            List.of("plugin-a"),
            PluginEndpointDispatcher.none(),
            ignored -> {
            },
            () -> List.of(ownerCapabilityProvider()),
            binder);

        manager.prepare();
        binder.activateAndFreeze();

        assertEquals(ownerCapability, binder.require(requirement(), Runnable.class));
    }

    @Test
    void aTaskWithAPolicyTheRuntimeDoesNotKnowStopsThePluginBeforeItStarts() {
        // Root BRX-RS-MANIFEST-026; standard-link plan L6.2: every task policy resolves at startup.
        RecordingPlugin plugin = new RecordingPlugin();
        plugin.tasks.put("heartbeat.v1", context -> new HeartbeatTask());
        PluginRuntimeManager manager = manager(List.of(plugin),
            Map.of(plugin, taskDescriptor("task-forever")), new DefaultCapabilityRegistry(), List.of("plugin-a"));

        PluginRuntimeException refused = assertThrows(PluginRuntimeException.class, manager::start);
        assertTrue(refused.getMessage().contains("retry"), refused.getMessage());
        assertFalse(plugin.started);
    }

    @Test
    void aDeclaredTaskWithoutItsFactoryStopsThePlugin() {
        RecordingPlugin plugin = new RecordingPlugin();
        PluginRuntimeManager manager = manager(List.of(plugin),
            Map.of(plugin, taskDescriptor("task-no-retry")), new DefaultCapabilityRegistry(), List.of("plugin-a"));

        PluginRuntimeException refused = assertThrows(PluginRuntimeException.class, manager::start);
        assertTrue(refused.getMessage().contains("does not bind"), refused.getMessage());
    }

    @Test
    void aTaskThatImplementsNeitherExecuteNorRunIsRefused() {
        // Standard-link plan D56: ManagedTask's two methods are default ones, so the runtime checks.
        RecordingPlugin plugin = new RecordingPlugin();
        plugin.tasks.put("heartbeat.v1", context -> new ManagedTask() {
        });
        PluginRuntimeManager manager = manager(List.of(plugin),
            Map.of(plugin, taskDescriptor("task-no-retry")), new DefaultCapabilityRegistry(), List.of("plugin-a"));

        PluginRuntimeException refused = assertThrows(PluginRuntimeException.class, manager::start);
        assertTrue(refused.getMessage().contains("implements neither"), refused.getMessage());
    }

    @Test
    void theTasksOfStartedPluginsAreOfferedToTheSchedulerOnceTheEntriesArePublished() {
        RecordingPlugin plugin = new RecordingPlugin();
        HeartbeatTask heartbeat = new HeartbeatTask();
        plugin.tasks.put("heartbeat.v1", context -> heartbeat);
        PluginRuntimeManager manager = manager(List.of(plugin),
            Map.of(plugin, taskDescriptor("task-no-retry")), new DefaultCapabilityRegistry(), List.of("plugin-a"));

        manager.prepare();
        manager.startPrepared();
        assertFalse(manager.taskProviders().ready(), "no task runs before the host's entries are published");
        manager.markPublished();

        TaskProviders providers = manager.taskProviders();
        assertTrue(providers.ready());
        assertEquals(1, providers.tasks().size());
        TaskProvider provider = providers.tasks().get(0);
        assertEquals("plugin-a", provider.moduleId());
        assertEquals("heartbeat", provider.declaration().taskId());
        assertSame(heartbeat, provider.task());
    }

    private static PluginRuntimeDescriptor taskDescriptor(String retryPolicy) {
        return PluginRuntimeDescriptor.builder("plugin-a")
            .providedTask(new PluginRuntimeDescriptor.ProvidedTask("heartbeat", "heartbeat.v1", "task-platform",
                "plugin-service", "every:PT1H", "task-standard", "task-no-overlap", retryPolicy, "task-instance-local",
                PluginRuntimeDescriptor.ProvidedTask.TRANSACTION_NONE))
            .build();
    }

    static final class HeartbeatTask implements ManagedTask {
        @Override
        public CompletionStage<Void> execute(TaskInvocation invocation) {
            return CompletableFuture.completedFuture(null);
        }
    }

    @Test
    void aTypedEndpointWhoseSchemaIsNotOnTheClassPathStopsThePlugin() {
        // Root BRX-RS-MANIFEST-018; standard-link plan L7.2: the runtime checks typed requests against their schema.
        TypedPlugin missing = new TypedPlugin();
        PluginRuntimeManager refused = manager(List.of(missing), Map.of(missing, typedDescriptor(
            "brix://schemas/test-endpoints/absent/1.0.0")), new DefaultCapabilityRegistry(), List.of("plugin-t"));

        PluginRuntimeException failure = assertThrows(PluginRuntimeException.class, refused::start);
        assertTrue(failure.getMessage().contains("request schema"), failure.getMessage());

        TypedPlugin present = new TypedPlugin();
        PluginRuntimeManager started = manager(List.of(present), Map.of(present, typedDescriptor(
            "brix://schemas/test-endpoints/create-thing-request/1.0.0")), new DefaultCapabilityRegistry(),
            List.of("plugin-t"));
        started.start();
        assertTrue(started.ready());
    }

    private static PluginRuntimeDescriptor typedDescriptor(String requestSchemaRef) {
        return PluginRuntimeDescriptor.builder("plugin-t")
            .endpoint("plugin-t.create.v1", "POST", "/api/plugin-t/things",
                io.runtime.orchestrator.endpoint.TestEndpointAccessPolicies.anonymous("plugin-t-public"),
                new io.runtime.orchestrator.endpoint.EndpointContract(requestSchemaRef,
                    "brix://schemas/test-endpoints/thing/1.0.0", 201))
            .build();
    }

    /** A plugin whose one endpoint has a typed handler. */
    static final class TypedPlugin implements BrixPlugin {

        @Override
        public void configure(PluginBootstrapContext bootstrap) {
            bootstrap.bindEndpointHandlerFactory("plugin-t.create.v1", context ->
                (io.runtime.sdk.plugin.TypedEndpointHandler<Object, Object>) invocation ->
                    CompletableFuture.completedFuture(Map.of("id", "t-1", "name", "x")));
        }

        @Override
        public void onStart(PluginContext context) {
        }

        @Override
        public void onStop() {
        }

        @Override
        public BrixHealth health() {
            return BrixHealth.up();
        }
    }

    private PluginRuntimeManager manager(
            List<BrixPlugin> plugins,
            Map<BrixPlugin, PluginRuntimeDescriptor> descriptors,
            DefaultCapabilityRegistry registry,
            List<String> requiredPlugins) {
        return new PluginRuntimeManager(
            () -> plugins,
            plugin -> Optional.ofNullable(descriptors.get(plugin)),
            registry,
            requiredPlugins);
    }

    private InternalContractProvider ownerCapabilityProvider() {
        return bootstrap -> bootstrap.bind(
            "test.contract",
            Runnable.class,
            context -> context.requireOwnerCapability(Runnable.class));
    }

    private OperationalModuleDescriptor.RequiredInternalContract requirement() {
        return new OperationalModuleDescriptor.RequiredInternalContract(
            "test.contract",
            Runnable.class.getName(),
            ">=1.0.0 <2.0.0",
            true,
            "contract.test");
    }

    interface TestCapability {
    }

    private static PluginRuntimeDescriptor publisher(String pluginId, String version) {
        return PluginRuntimeDescriptor.builder(pluginId)
            .requiredCapabilities(List.of(EventBusCapability.class.getName()))
            .data(pluginId.replace('-', '_'), pluginId.replace('-', '_') + "_outbox", "")
            .eventPublication("OrderPlaced", version, EventReliability.CRITICAL)
            .build();
    }

    private static PluginRuntimeDescriptor subscriber(String publisherPolicyRef) {
        return PluginRuntimeDescriptor.builder("plugin-s")
            .data("plugin_s", "", "plugin_s_inbox")
            .eventSubscription("projection", "OrderPlaced", ">=1.0.0 <2.0.0", "projection.v1",
                "event-standard", PluginRuntimeDescriptor.EventSubscription.PERSISTENT_INBOX)
            .subscriptionPolicies("projection.v1", "event-standard", "partition-serial", publisherPolicyRef)
            .build();
    }

    private static DefaultCapabilityRegistry eventBusRegistry() {
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        registry.register(EventBusCapability.class, new EventBusCapability() {
            @Override
            public void publish(DomainEvent event) {
            }

            @Override
            public void publishIntegration(IntegrationEvent event) {
            }
        });
        return registry;
    }

    private static PluginRuntimeDescriptor subscriber() {
        return PluginRuntimeDescriptor.builder("plugin-a")
            .data("platform_tenant", "platform_tenant_outbox", "platform_tenant_inbox")
            .eventSubscription(
                "projection", "TenantFirstOwnerAccepted", ">=1.0.0 <2.0.0", "projection.v1", "event-standard",
                "persistent-inbox")
            .build();
    }

    record Accepted(Long tenantId) {
    }

    static final class AcceptedHandler implements EventHandler<Accepted> {
        @Override
        public CompletionStage<Void> handle(EventInvocation<Accepted> invocation) {
            return CompletableFuture.completedFuture(null);
        }
    }

    static final class ReserveHandler implements CommandHandler<ReserveHandler.Reserve> {

        record Reserve(String sku) {
        }

        @Override
        public CompletionStage<Void> handle(CommandInvocation<Reserve> invocation) {
            return CompletableFuture.completedFuture(null);
        }
    }

    static final class RecordingPlugin implements BrixPlugin {

        private final Map<String, HandlerFactory<? extends EventHandler<?>>> eventHandlers = new LinkedHashMap<>();
        private final Map<String, HandlerFactory<? extends CommandHandler<?>>> commandHandlers = new LinkedHashMap<>();
        private final Map<String, HandlerFactory<? extends ManagedTask>> tasks = new LinkedHashMap<>();

        private boolean started;
        private boolean configured;
        private boolean stopped;
        private boolean failOnStart;
        private BrixHealth health = BrixHealth.up();
        private String endpointId;
        private PluginContext endpointContext;

        @Override
        public void configure(PluginBootstrapContext bootstrap) {
            configured = true;
            if (endpointId != null) {
                bootstrap.bindEndpointHandlerFactory(endpointId, context -> {
                    endpointContext = context;
                    return request -> "ok";
                });
            }
            eventHandlers.forEach(bootstrap::bindEventHandlerFactory);
            commandHandlers.forEach(bootstrap::bindCommandHandlerFactory);
            tasks.forEach(bootstrap::bindTaskFactory);
        }

        @Override
        public void onStart(PluginContext context) {
            if (failOnStart) {
                throw new IllegalStateException("start failed");
            }
            started = true;
        }

        @Override
        public void onStop() {
            stopped = true;
        }

        @Override
        public BrixHealth health() {
            return health;
        }
    }
}
