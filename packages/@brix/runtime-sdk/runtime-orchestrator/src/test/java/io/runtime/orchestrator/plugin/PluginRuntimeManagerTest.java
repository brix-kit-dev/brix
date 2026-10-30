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
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.EventReliability;
import io.runtime.sdk.event.IntegrationEvent;
import io.runtime.sdk.internalcontract.InternalContractProvider;
import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.BrixPlugin;
import io.runtime.sdk.plugin.EventHandler;
import io.runtime.sdk.plugin.EventInvocation;
import io.runtime.sdk.plugin.HandlerFactory;
import io.runtime.sdk.plugin.PluginBootstrapContext;
import io.runtime.sdk.plugin.PluginContext;
import io.runtime.sdk.plugin.PluginLifecycleState;

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

    static final class RecordingPlugin implements BrixPlugin {

        private final Map<String, HandlerFactory<? extends EventHandler<?>>> eventHandlers = new LinkedHashMap<>();

        private boolean started;
        private boolean configured;
        private boolean stopped;
        private boolean failOnStart;
        private BrixHealth health = BrixHealth.up();
        private String endpointId;

        @Override
        public void configure(PluginBootstrapContext bootstrap) {
            configured = true;
            if (endpointId != null) {
                bootstrap.bindEndpoint(endpointId, request -> "ok");
            }
            eventHandlers.forEach(bootstrap::bindEventHandlerFactory);
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
