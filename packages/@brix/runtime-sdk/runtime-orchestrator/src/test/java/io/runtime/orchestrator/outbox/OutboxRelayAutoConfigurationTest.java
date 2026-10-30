/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import tools.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.event.ConsumedSubscription;
import io.runtime.orchestrator.event.EventDelivery;
import io.runtime.orchestrator.event.EventDeliveryProvider;
import io.runtime.orchestrator.event.EventDeliveryScopes;
import io.runtime.orchestrator.event.EventSubscribers;
import io.runtime.orchestrator.event.EventSubscriptionGroup;
import io.runtime.orchestrator.event.EventSubscriptionMetricsFactory;
import io.runtime.orchestrator.event.EventSubscriptionTransport;
import io.runtime.orchestrator.event.EventsEndpoint;
import io.runtime.orchestrator.event.ParkedEvent;
import io.runtime.orchestrator.event.SubscriptionDelivery;

/**
 * Contract tests for the Spring Host path that assembles the L2B relay.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
class OutboxRelayAutoConfigurationTest {

    @Test
    void oneRelayRunsForEachOwnerOutbox() {
        OutboxRelayProperties properties = properties();
        OutboxRelayPolicy policy = new OutboxRelayAutoConfiguration().outboxRelayPolicy(properties);
        List<String> metered = new ArrayList<>();
        OutboxRelayGroup group = new OutboxRelayGroup(properties, policy,
            () -> () -> java.util.Map.of("platform_tenant_outbox", new EmptyOutboxMessageStore(),
                "platform_identity_outbox", new EmptyOutboxMessageStore()),
            () -> ignored -> {
            },
            outbox -> {
                metered.add(outbox);
                return OutboxRelayMetrics.NOOP;
            });

        assertEquals(25, policy.batchSize());
        assertDoesNotThrow(group::start);
        assertTrue(group.isRunning());
        assertEquals(Set.of("platform_tenant_outbox", "platform_identity_outbox"), Set.copyOf(metered));
        group.stop();
        assertFalse(group.isRunning());
    }

    @Test
    void stoppingTheGroupTellsRunningDeliveriesToStop() {
        AtomicBoolean stopped = new AtomicBoolean();
        OutboxTransport transport = new OutboxTransport() {
            @Override
            public void publish(CanonicalOutboxMessage message) {
            }

            @Override
            public void stop() {
                stopped.set(true);
            }
        };
        OutboxRelayGroup group = new OutboxRelayGroup(properties(), OutboxRelayPolicy.defaults(),
            () -> () -> java.util.Map.of("platform_tenant_outbox", new EmptyOutboxMessageStore()), () -> transport,
            OutboxRelayMetricsFactory.NOOP);
        group.start();

        group.stop();

        assertTrue(stopped.get());
    }

    @Test
    void aHostWithMicrometerAndActuatorGetsTheRelayMetersAndTheManagementEndpoints() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("relay", Map.of(
                "brix.outbox.relay.enabled", "true",
                "brix.outbox.relay.owner", "enterprise-shell",
                "brix.outbox.relay.transport", "kafka")));
            context.registerBean(MeterRegistry.class, SimpleMeterRegistry::new);
            context.registerBean(OutboxStoreRegistry.class,
                () -> () -> Map.of("platform_tenant_outbox", new EmptyOutboxMessageStore()));
            context.registerBean(OutboxTransport.class, () -> message -> {
            });
            context.registerBean(EventSubscriptionTransport.class, UnreachableBroker::new);
            context.registerBean(EventDeliveryProvider.class, () -> new EventDeliveryProvider(
                OutboxRelayAutoConfigurationTest::pluginsNotStarted));
            context.register(OutboxRelayAutoConfiguration.class);
            context.refresh();

            assertTrue(context.getBean(OutboxRelayGroup.class).isRunning());
            assertTrue(context.getBean(EventSubscriptionGroup.class).isRunning());
            assertFalse(context.getBean(EventSubscriptionGroup.class).isReady(), "nothing is consumed before the plugins start");
            assertNotNull(context.getBean(OutboxEndpoint.class));
            assertNotNull(context.getBean(EventsEndpoint.class));
            assertTrue(context.getBean(MessageRetention.class).isRunning(), "the messaging tables are cleaned");
            assertNotNull(context.getBean(EventSubscriptionMetricsFactory.class));
            assertNotNull(context.getBean(MeterRegistry.class).find(MicrometerOutboxRelayMetrics.MESSAGES)
                .tags("outbox", "platform_tenant_outbox", "status", "parked").gauge());
            assertNotNull(context.getBean(MeterRegistry.class).find(MicrometerOutboxRelayMetrics.WAITING)
                .tags("outbox", "platform_tenant_outbox").gauge());
        }
    }

    @Test
    void anInboxRetentionShorterThanTheOutboxsPlusTheBrokersStopsTheHost() {
        // Kafka design D34: a message republished late could otherwise arrive after its inbox record is gone.
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("relay", Map.of(
                "brix.outbox.relay.enabled", "true",
                "brix.outbox.relay.owner", "enterprise-shell",
                "brix.outbox.relay.transport", "kafka",
                "brix.outbox.relay.retention.inbox", "20d")));
            context.registerBean(OutboxStoreRegistry.class,
                () -> () -> Map.of("platform_tenant_outbox", new EmptyOutboxMessageStore()));
            context.registerBean(OutboxTransport.class, () -> message -> {
            });
            context.registerBean(EventSubscriptionTransport.class, () -> new UnreachableBroker() {
                @Override
                public Duration retention() {
                    return Duration.ofDays(7);
                }
            });
            context.registerBean(EventDeliveryProvider.class, () -> new EventDeliveryProvider(
                OutboxRelayAutoConfigurationTest::pluginsNotStarted));
            context.register(OutboxRelayAutoConfiguration.class);

            Throwable refused = assertThrows(Exception.class, context::refresh);
            while (refused.getCause() != null) {
                refused = refused.getCause();
            }
            assertTrue(refused.getMessage().contains("brix.outbox.relay.retention.inbox"), refused.getMessage());
        }
    }

    @Test
    void aBrokerTransportWithoutAnAdapterThatConsumesStopsTheHost() {
        // Kafka design §4.3: events sent to a broker nobody reads from would wait there unseen.
        OutboxRelayProperties properties = properties();
        properties.setTransport("kafka");
        EventSubscriptionGroup group = new EventSubscriptionGroup(properties, OutboxRelayPolicy.defaults(),
            OutboxRelayAutoConfigurationTest::pluginsNotStarted, () -> null, EventSubscriptionMetricsFactory.NOOP);

        assertTrue(assertThrows(IllegalStateException.class, group::start).getMessage()
            .contains("no adapter consumes them"));
    }

    @Test
    void withTheInProcessTransportTheRelayDeliversAndNoSubscriptionIsConsumed() {
        EventSubscriptionGroup group = new EventSubscriptionGroup(properties(), OutboxRelayPolicy.defaults(),
            () -> null, () -> null, EventSubscriptionMetricsFactory.NOOP);

        assertDoesNotThrow(group::start);
        assertFalse(group.isRunning());
    }

    @Test
    void enabledRelayRequiresStableOwnerIdentity() {
        OutboxRelayGroup group = new OutboxRelayGroup(new OutboxRelayProperties(), OutboxRelayPolicy.defaults(),
            () -> java.util.Map::of, () -> ignored -> {
            }, OutboxRelayMetricsFactory.NOOP);

        IllegalStateException failure = assertThrows(IllegalStateException.class, group::start);

        assertEquals("brix.outbox.relay.owner must be configured when the relay is enabled", failure.getMessage());
    }

    @Test
    void anEnabledRelayWithoutOutboxesOrTransportStopsTheHost() {
        OutboxRelayGroup withoutStores = new OutboxRelayGroup(properties(), OutboxRelayPolicy.defaults(), () -> null,
            () -> ignored -> {
            }, OutboxRelayMetricsFactory.NOOP);
        OutboxRelayGroup withoutTransport = new OutboxRelayGroup(properties(), OutboxRelayPolicy.defaults(),
            () -> java.util.Map::of, () -> null, OutboxRelayMetricsFactory.NOOP);

        assertTrue(assertThrows(IllegalStateException.class, withoutStores::start).getMessage()
            .contains("brix.outbox.relay.persistence"));
        assertTrue(assertThrows(IllegalStateException.class, withoutTransport::start).getMessage()
            .contains("in-process"));
    }

    private static OutboxRelayProperties properties() {
        OutboxRelayProperties properties = new OutboxRelayProperties();
        properties.setOwner("enterprise-shell");
        properties.setPollDelay(Duration.ofMillis(100));
        return properties;
    }

    private static EventDelivery pluginsNotStarted() {
        EventSubscribers subscribers = new EventSubscribers() {
            @Override
            public boolean ready() {
                return false;
            }

            @Override
            public Optional<String> publisherOf(String eventType) {
                return Optional.empty();
            }

            @Override
            public List<DeclaredSubscriber> subscribersOf(String eventType) {
                return List.of();
            }

            @Override
            public List<DeclaredSubscriber> subscriptions() {
                return List.of();
            }

            @Override
            public Set<String> publishedEventTypes() {
                return Set.of();
            }
        };
        return new EventDelivery(subscribers, owner -> Optional.empty(), (owner, handlerId, message) -> true,
            new ObjectMapper(), Clock.systemUTC(), Duration.ofSeconds(15), EventDeliveryScopes.ENVELOPE);
    }

    /** A broker that cannot be reached, so the subscription group keeps waiting. */
    private static class UnreachableBroker implements EventSubscriptionTransport {

        @Override
        public boolean ready() {
            return false;
        }

        @Override
        public void prepare(Set<String> eventTypes, List<ConsumedSubscription> subscriptions) {
            throw new IllegalStateException("unreachable");
        }

        @Override
        public Consumption consume(ConsumedSubscription subscription, SubscriptionDelivery delivery) {
            throw new IllegalStateException("unreachable");
        }

        @Override
        public Map<String, List<ParkedEvent>> parked(int limit) {
            return Map.of();
        }

        @Override
        public Optional<ParkedEvent> parkedEvent(ConsumedSubscription subscription, String eventId) {
            return Optional.empty();
        }

        @Override
        public Optional<CanonicalOutboxMessage> parkedMessage(ConsumedSubscription subscription, String eventId) {
            return Optional.empty();
        }

        @Override
        public void release(ConsumedSubscription subscription, String eventId) {
        }
    }

    private static final class EmptyOutboxMessageStore implements OutboxMessageStore {

        private final List<CanonicalOutboxMessage> appended = new ArrayList<>();

        @Override
        public void append(CanonicalOutboxMessage message) {
            appended.add(message);
        }

        @Override
        public List<CanonicalOutboxMessage> claimDue(
                String relayOwner,
                OffsetDateTime now,
                Duration leaseDuration,
                int batchSize) {
            return List.of();
        }

        @Override
        public void markPublished(String relayOwner, String messageId, OffsetDateTime publishedAt) {
        }

        @Override
        public void releaseForRetry(String relayOwner, String messageId, OffsetDateTime availableAt, String errorCode) {
        }

        @Override

        public void releaseUntried(String relayOwner, String messageId) {

        }


        @Override
        public void park(String relayOwner, String messageId, String errorCode, OffsetDateTime parkedAt) {
        }

        @Override
        public OutboxBacklogSnapshot readBacklog(OffsetDateTime now) {
            return new OutboxBacklogSnapshot(appended.size(), 0L, 0L, Duration.ZERO);
        }

        @Override
        public List<ParkedOutboxMessage> listParked(int limit) {
            return List.of();
        }

        @Override
        public boolean replayParked(String eventId, OffsetDateTime now) {
            return false;
        }
    }
}
