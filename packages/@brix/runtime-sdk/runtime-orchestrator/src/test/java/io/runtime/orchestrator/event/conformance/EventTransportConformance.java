/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.event.EventDelivery;
import io.runtime.orchestrator.persistence.TenantScope;

/**
 * What every event transport must do, from the publisher's committed outbox row to the subscriber's
 * committed inbox record (Kafka design §4.6; root §6.5 item 8). The in-process transport and every broker
 * adapter run these same tests; a new adapter is done when it passes them.
 * <p>A subclass gives the {@link Harness}: how to start and stop delivering, how to make the transport
 * unable to deliver, and how to list and replay what it parked.</p>
 */
public abstract class EventTransportConformance {

    /** How long a test waits for the transport. */
    protected static final Duration WAIT = Duration.ofSeconds(60);

    protected ConformanceWorld world;
    private Harness harness;

    /**
     * Delivers the world's outbox to its subscription through the transport under test.
     */
    protected interface Harness extends AutoCloseable {

        /** Starts delivering, as a host that starts does: with a new delivery core. */
        void start();

        /** Stops delivering, as a host that shuts down does. */
        void stop();

        /** Makes the transport able or unable to deliver, such as a broker that cannot be reached. */
        void reachable(boolean reachable);

        /** What the transport parked, the earliest first. */
        List<Parked> parked();

        /** Replays a parked event; true when the transport took the replay. */
        boolean replay(String eventId);

        @Override
        void close();
    }

    /**
     * A parked event.
     *
     * @param eventId the event id
     * @param errorCode why it was parked
     */
    public record Parked(String eventId, String errorCode) {
    }

    /**
     * Creates the harness of the transport under test.
     *
     * @param world what the transport delivers from and into
     * @return the harness
     */
    protected abstract Harness harness(ConformanceWorld world);

    /** How long to watch for a delivery that should not come. */
    protected Duration settleTime() {
        return Duration.ofMillis(500);
    }

    @BeforeEach
    void createHarness() {
        world = new ConformanceWorld();
        harness = harness(world);
    }

    @AfterEach
    void closeHarness() {
        harness.close();
    }

    @Test
    void aCommittedEventReachesItsSubscriberOnce() {
        world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1);

        harness.start();

        await("event-1 is handled", () -> world.handledIds().contains("event-1"));
        settle();
        assertEquals(List.of("event-1"), world.handledIds());
        assertTrue(harness.parked().isEmpty());
    }

    @Test
    void aCommandReachesItsOneProviderOncePerIdempotencyKey() {
        // Kafka design §14 K22 (root BRX-RS-MESSAGE-010, MESSAGE-014): commands travel the same way; a second command
        // with a key already taken is acknowledged without running the handler again.
        world.submit("command-1", "order-7");
        world.submit("command-2", "order-7");
        world.submit("command-3", "order-8");

        harness.start();

        await("command-3 is handled", () -> world.handledCommands().contains("command-3"));
        await("command-2 is acknowledged", () -> world.inboxed("command-2"));
        settle();
        assertEquals(java.util.Set.of("command-1", "command-3"), java.util.Set.copyOf(world.handledCommands()));
        assertEquals(2, world.handledCommands().size());
        assertTrue(harness.parked().isEmpty());
    }

    @Test
    void anEventDeliveredAgainIsHandledOnce() {
        // At least once (root BRX-RS-MESSAGE-020): the subscriber's inbox skips the second delivery (MESSAGE-025).
        world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1);
        world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1);

        harness.start();

        await("the second delivery reaches the inbox", () -> world.duplicates() == 1);
        settle();
        assertEquals(List.of("event-1"), world.handledIds());
    }

    @Test
    void theEventsOfOnePartitionKeyAreHandledInTheirOrder() {
        // Root BRX-RS-MESSAGE-027: order holds per partition key.
        List<String> published = IntStream.rangeClosed(1, 8).mapToObj(i -> "event-" + i).toList();
        published.forEach(eventId -> world.publish(eventId, "member-1", ConformanceWorld.TENANT, 1));

        harness.start();

        await("every event is handled", () -> world.handledIds().size() == published.size());
        assertEquals(published, world.handledIds());
    }

    @Test
    void anEventThatFailsHoldsBackTheLaterEventsOfItsKey() {
        // Root BRX-RS-MESSAGE-027: a retry does not let a later event of the same key overtake the failed one.
        world.failNextOf("event-1", 2);
        world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1);
        world.publish("event-2", "member-1", ConformanceWorld.TENANT, 2);
        world.publish("event-3", "member-2", ConformanceWorld.TENANT, 3);

        harness.start();

        await("every event is handled", () -> world.handledIds().size() == 3);
        List<String> handled = world.handledIds();
        assertTrue(handled.indexOf("event-1") < handled.indexOf("event-2"), "event-2 waited for event-1: " + handled);
        assertEquals(3, world.handled().stream().filter(event -> event.eventId().equals("event-1")).findFirst()
            .orElseThrow().attempt(), "event-1 succeeded on its third attempt");
    }

    @Test
    void aHandlerThatFailsIsRetriedUntilItSucceeds() {
        world.failNext(2);
        world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1);

        harness.start();

        await("event-1 is handled", () -> world.handledIds().contains("event-1"));
        assertEquals(3, world.handled().get(0).attempt(), "the third attempt succeeds");
        assertTrue(harness.parked().isEmpty());
        // Root BRX-RS-OTEL-005: a failed delivery's CONSUMER span carries the error.
        await("the three deliveries' spans have ended",
            () -> world.tracing().spans(RecordingMessageTracing.CONSUMER).size() == 3);
        assertEquals(List.of(true, true, false), world.tracing().spans(RecordingMessageTracing.CONSUMER).stream()
            .map(RecordingMessageTracing.Recorded::failed).toList());
    }

    @Test
    void aSchemaVersionTheSubscriptionRefusesIsParkedAtOnce() {
        world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1, "2.0.0");

        harness.start();

        await("event-1 is parked", () -> !harness.parked().isEmpty());
        assertEquals(List.of(new Parked("event-1", EventDelivery.SCHEMA_UNSUPPORTED)), harness.parked());
        assertEquals(0, world.calls(), "a permanent failure is not retried");
    }

    @Test
    void aHandlerThatKeepsFailingIsParkedAfterItsLastAttempt() {
        world.failing(true);
        world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1);

        harness.start();

        await("event-1 is parked", () -> !harness.parked().isEmpty());
        assertEquals(List.of(new Parked("event-1", EventDelivery.HANDLER_FAILED)), harness.parked());
        assertEquals(world.policy().maxAttempts(), world.calls());
        assertTrue(world.handled().isEmpty());
    }

    @Test
    void aParkedEventHoldsNothingBack() {
        world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1, "2.0.0");
        world.publish("event-2", "member-1", ConformanceWorld.TENANT, 2);

        harness.start();

        await("event-2 is handled", () -> world.handledIds().contains("event-2"));
        await("event-1 is parked", () -> !harness.parked().isEmpty());
        assertEquals(List.of(new Parked("event-1", EventDelivery.SCHEMA_UNSUPPORTED)), harness.parked());
    }

    @Test
    void aReplayedEventKeepsItsIdAndIsHandledOnce() {
        // Root BRX-RS-MESSAGE-028: a replay does not change the event id.
        world.failing(true);
        world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1);
        harness.start();
        await("event-1 is parked", () -> !harness.parked().isEmpty());

        world.failing(false);
        assertTrue(harness.replay("event-1"));

        await("event-1 is handled", () -> world.handledIds().contains("event-1"));
        await("nothing is parked", () -> harness.parked().isEmpty());
        settle();
        assertEquals(List.of("event-1"), world.handledIds());
    }

    @Test
    void aTenantEventIsHandledInItsTenantAndAPlatformEventInNone() {
        // Multi-tenant §10: the delivery binds the envelope's tenant; a PLATFORM event binds none.
        world.publish("tenant-event", "member-1", ConformanceWorld.TENANT, 1);
        world.publish("platform-event", "member-2", null, 2);

        harness.start();

        await("both events are handled", () -> world.handledIds().size() == 2);
        Map<String, TenantScope> scopes = world.handled().stream()
            .collect(Collectors.toMap(ConformanceWorld.Handled::eventId, ConformanceWorld.Handled::scope));
        assertEquals(TenantScope.tenant(ConformanceWorld.TENANT), scopes.get("tenant-event"));
        assertEquals(TenantScope.none(), scopes.get("platform-event"));
    }

    @Test
    void theHandlerRunsInTheConsumerSpanOfTheProducerSpanOfThePublishersContext() {
        // Root BRX-RS-OTEL-002: the W3C trace context crosses the transport. The message is sent in a PRODUCER span,
        // a child of the publisher's context, and handled in a CONSUMER span whose parent is the extracted producer
        // span (Kafka design §13 K8).
        world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1);

        harness.start();

        await("event-1 is handled", () -> world.handledIds().contains("event-1"));
        await("both spans have ended", () -> world.tracing().spans(RecordingMessageTracing.PRODUCER).size() == 1
            && world.tracing().spans(RecordingMessageTracing.CONSUMER).size() == 1);
        RecordingMessageTracing.Recorded producer = world.tracing().spans(RecordingMessageTracing.PRODUCER).get(0);
        RecordingMessageTracing.Recorded consumer = world.tracing().spans(RecordingMessageTracing.CONSUMER).get(0);
        assertEquals(ConformanceWorld.TRACEPARENT, producer.parent());
        assertEquals(producer.traceparent(), consumer.parent());
        assertEquals(ConformanceWorld.SUBSCRIBER + "." + ConformanceWorld.HANDLER, consumer.subscription());
        assertEquals(consumer.traceparent(), world.handled().get(0).traceparent());
        assertTrue(consumer.traceparent().startsWith(ConformanceWorld.TRACEPARENT.substring(0, 36)),
            "the handler stays in the publisher's trace");
    }

    @Test
    void aBestEffortEventReachesASubscriptionWithoutAnInboxEachTimeItIsDelivered() {
        // Root BRX-RS-MANIFEST-025: a BEST_EFFORT subscription may keep no inbox, and then nothing skips an event
        // delivered again (MESSAGE-020 promises BEST_EFFORT no more than that).
        useWorld(ConformanceWorld.withoutInbox());
        world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1);
        world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1);

        harness.start();

        await("both deliveries are handled", () -> world.handledIds().size() == 2);
        settle();
        assertEquals(List.of("event-1", "event-1"), world.handledIds());
        assertEquals(0, world.duplicates());
        assertEquals(TenantScope.tenant(ConformanceWorld.TENANT), world.handled().get(0).scope());
        assertTrue(harness.parked().isEmpty());
    }

    @Test
    void whileTheTransportCannotDeliverNothingIsSpentLostOrDoubled() {
        harness.reachable(false);
        harness.start();
        List<String> published = List.of("event-1", "event-2", "event-3");
        published.forEach(eventId -> world.publish(eventId, "member-1", ConformanceWorld.TENANT, 1));

        pause(Duration.ofSeconds(2));
        assertTrue(world.handled().isEmpty());
        assertTrue(harness.parked().isEmpty());
        assertEquals(0, world.outbox().attempts("event-1"), "waiting spends no attempts");

        harness.reachable(true);

        await("every event is handled", () -> world.handledIds().size() == published.size());
        settle();
        assertEquals(published, world.handledIds());
    }

    @Test
    void eventsPendingAcrossARestartAreHandledOnce() {
        harness.start();
        world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1);
        world.publish("event-2", "member-1", ConformanceWorld.TENANT, 2);
        await("the first two are handled", () -> world.handledIds().size() == 2);

        harness.stop();
        world.publish("event-3", "member-1", ConformanceWorld.TENANT, 3);
        world.publish("event-4", "member-1", ConformanceWorld.TENANT, 4);
        harness.start();

        await("every event is handled", () -> world.handledIds().size() == 4);
        settle();
        assertEquals(List.of("event-1", "event-2", "event-3", "event-4"), world.handledIds());
    }

    /**
     * Continues the test in another world, through a new harness of the transport under test.
     *
     * @param other the world
     */
    protected void useWorld(ConformanceWorld other) {
        harness.close();
        world = other;
        harness = harness(other);
    }

    /**
     * Waits until the condition holds.
     *
     * @param what what is waited for, for the failure message
     * @param condition the condition
     */
    protected static void await(String what, BooleanSupplier condition) {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("timed out waiting until " + what);
            }
            pause(Duration.ofMillis(20));
        }
    }

    /** Watches for deliveries that should not come. */
    protected void settle() {
        pause(settleTime());
    }

    protected static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail("interrupted");
        }
    }
}
