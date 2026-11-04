/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.springframework.boot.actuate.endpoint.SecurityContext;

import io.runtime.orchestrator.command.CommandDelivery;
import io.runtime.orchestrator.event.EventDelivery;
import io.runtime.orchestrator.event.EventSubscriptionTransport;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxEndpoint;
import io.runtime.orchestrator.persistence.PolicyUseRecord;
import io.runtime.orchestrator.persistence.TenantAccessPolicy;
import io.runtime.orchestrator.persistence.TenantScope;
import io.runtime.sdk.command.CommandStatus;

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
    /** Who replays, discards and republishes in these scenarios. */
    protected static final String OPERATOR = "conformance-operator";

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

        /**
         * Replays a parked event through the transport's management endpoint, recording in the world's audit; true
         * when the transport took the replay (Kafka design D33).
         */
        boolean replay(String eventId);

        /**
         * Discards a parked event through the transport's management endpoint, recording in the world's audit; true
         * when the event will never be delivered (Kafka design D33).
         */
        boolean discard(String eventId);

        /**
         * Sends a published event again through the outbox endpoint, recording in the world's audit; true when the
         * outbox took it (Kafka design D34, §14 K16).
         */
        default boolean republish(String eventId) {
            return world().outboxEndpoint().republish(eventId, OutboxEndpoint.REPUBLISH, OPERATOR,
                "conformance republish", SecurityContext.NONE).getStatus() == 200;
        }

        /** The world the harness delivers. */
        ConformanceWorld world();

        /**
         * The broker transport whose parked commands a command's status counts, or {@code null} when the transport
         * parks in the publisher's outbox, as the in-process one does (standard-link plan point 7).
         */
        default EventSubscriptionTransport subscriptions() {
            return null;
        }

        /**
         * Sends copies of one message through the transport at the same moment, as two relays that both claimed it
         * would, or a retry racing a slow first send. Sends that fail because another copy won are ignored, as the
         * relay that made them would retry.
         */
        void sendAtOnce(CanonicalOutboxMessage message, int copies);

        /** Starts a second relay on the same outbox and transport, as a second host would. */
        void startAnotherRelay();

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
    void aKeyTakenInOneTenantIsFreeInAnother() {
        // Standard-link plan L4.5 (D54): the owner keeps a key unique for its sender and tenant only.
        world.submit("command-1", "order-7", ConformanceWorld.TENANT, null);
        world.submit("command-2", "order-7", ConformanceWorld.FANOUT_TENANTS.get(1), null);
        world.submit("command-3", "order-7", ConformanceWorld.TENANT, null);

        harness.start();

        await("command-2 is handled", () -> world.handledCommands().contains("command-2"));
        await("command-3 is acknowledged", () -> world.inboxed("command-3"));
        settle();
        assertEquals(java.util.Set.of("command-1", "command-2"), java.util.Set.copyOf(world.handledCommands()));
        assertEquals(2, world.handledCommands().size());
        assertTrue(harness.parked().isEmpty());
    }

    @Test
    void aCommandTheOwnerTakesOnlyForAUserIsParkedWithoutOne() {
        // Standard-link plan L4.4 (D54): the owner checks its access policy before the handler, and a command it can
        // never take is parked at once.
        world.commandsOnlyOnBehalfOf("stock:reserve");
        world.submit("command-1", "order-7", ConformanceWorld.TENANT, null);
        world.submit("command-2", "order-8", ConformanceWorld.TENANT, "user-1");

        harness.start();

        await("command-2 is handled", () -> world.handledCommands().contains("command-2"));
        await("command-1 is parked", () -> !harness.parked().isEmpty());
        settle();
        assertEquals(List.of(new Parked("command-1", CommandDelivery.ACCESS_DENIED)), harness.parked());
        assertEquals(List.of("command-2"), world.handledCommands());
    }

    @Test
    void aSenderReadsItsCommandQueuedThenSucceededAndASecondWithTheKeyAsADuplicateOfIt() {
        // Standard-link plan point 7 (root BRX-RS-MESSAGE-013, MESSAGE-014): the receipt only says queued; the status
        // says what the owner did, and only to the sender in its tenant.
        world.submit("command-1", "order-7", ConformanceWorld.TENANT, null);
        world.submit("command-2", "order-7", ConformanceWorld.TENANT, null);
        assertEquals(CommandStatus.State.QUEUED, status("command-1").state());

        harness.start();

        await("command-1 is handled", () -> world.handledCommands().contains("command-1"));
        await("command-2 is acknowledged", () -> world.inboxed("command-2"));
        assertEquals(CommandStatus.State.SUCCEEDED, status("command-1").state());
        CommandStatus duplicate = status("command-2");
        assertEquals(CommandStatus.State.DUPLICATE, duplicate.state());
        assertEquals("command-1", duplicate.originalCommandId());
        assertEquals(CommandStatus.unknown("command-1"), world.commandStatus("another-sender", ConformanceWorld.TENANT,
            "command-1", harness.subscriptions()), "another plugin's command");
        assertEquals(CommandStatus.unknown("command-1"), world.commandStatus(ConformanceWorld.PUBLISHER,
            ConformanceWorld.FANOUT_TENANTS.get(1), "command-1", harness.subscriptions()), "another tenant's command");
        assertEquals(CommandStatus.unknown("command-3"), status("command-3"));
    }

    @Test
    void aParkedCommandReadsParkedUntilAnOperatorDiscardsIt() {
        // Standard-link plan point 7: the in-process transport parks in the sender's outbox, a broker at the provider;
        // the sender reads the same either way.
        world.commandsOnlyOnBehalfOf("stock:reserve");
        world.submit("command-1", "order-7", ConformanceWorld.TENANT, null);

        harness.start();

        await("command-1 is parked", () -> !harness.parked().isEmpty());
        CommandStatus parked = status("command-1");
        assertEquals(CommandStatus.State.PARKED, parked.state());
        assertEquals(CommandDelivery.ACCESS_DENIED, parked.errorCode());

        assertTrue(harness.discard("command-1"));

        await("command-1 is discarded", () -> status("command-1").state() == CommandStatus.State.DISCARDED);
        assertEquals(CommandDelivery.ACCESS_DENIED, status("command-1").errorCode(), "why it had been parked");
        assertTrue(world.handledCommands().isEmpty());
    }

    /** The publisher's view of one of its commands in its usual tenant. */
    private CommandStatus status(String commandId) {
        return world.commandStatus(ConformanceWorld.PUBLISHER, ConformanceWorld.TENANT, commandId,
            harness.subscriptions());
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
    void aParkedEventIsReplayedOrDiscardedOnlyOnceTheAuditRecordedIt() {
        // Root BRX-RS-MESSAGE-028, Kafka design D33: replays and discards are recorded, with the operator and the
        // reason, before they take effect; without the record nothing happens.
        world.failing(true);
        world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1);
        world.publish("event-2", "member-2", ConformanceWorld.TENANT, 2);
        harness.start();
        await("both are parked", () -> harness.parked().size() == 2);
        world.failing(false);

        world.audit().refusing(true);
        assertFalse(harness.replay("event-1"), "a replay the audit cannot record");
        assertFalse(harness.discard("event-2"), "a discard the audit cannot record");
        world.audit().refusing(false);
        assertEquals(2, harness.parked().size());

        assertTrue(harness.replay("event-1"));
        assertTrue(harness.discard("event-2"));

        await("event-1 is handled", () -> world.handledIds().contains("event-1"));
        await("nothing is parked", () -> harness.parked().isEmpty());
        settle();
        assertEquals(List.of("event-1"), world.handledIds());
        assertEquals(List.of("MESSAGE_REPLAYED event-1 " + OPERATOR, "MESSAGE_DISCARDED event-2 " + OPERATOR),
            world.audit().recorded().stream()
                .map(operation -> operation.action() + " " + operation.messageId() + " " + operation.operator())
                .toList());
    }

    @Test
    void aRepublishedEventReachesTheSubscriberAgainAndItsInboxSkipsIt() {
        // Kafka design D32, D34, §14 K16: a published event is sent again with its id while the outbox keeps it; the
        // subscriber's inbox recognises it, so the handler does not run twice.
        world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1);
        harness.start();
        await("event-1 is handled", () -> world.handledIds().contains("event-1"));
        await("event-1 is published", () -> world.outbox().find("event-1")
            .filter(stored -> "PUBLISHED".equals(stored.status())).isPresent());
        int duplicates = world.duplicates();

        assertTrue(harness.republish("event-1"));

        await("the inbox recognises event-1", () -> world.duplicates() > duplicates);
        settle();
        assertEquals(List.of("event-1"), world.handledIds());
        assertEquals("MESSAGE_REPUBLISHED", world.audit().recorded().get(0).action());
    }

    @Test
    void copiesOfAnEventSentAtOnceTakeEffectOnce() {
        // Design §4.6: concurrent duplicate delivery takes effect once; the inbox's key decides which copy wins.
        harness.start();
        CanonicalOutboxMessage message = world.message("event-copies", "member-1", ConformanceWorld.TENANT, 1);

        harness.sendAtOnce(message, 4);

        await("event-copies is handled", () -> world.handledIds().contains("event-copies"));
        settle();
        assertEquals(List.of("event-copies"), world.handledIds());
        assertTrue(world.inboxed("event-copies"));
    }

    @Test
    void aPolicySubscriptionRunsInItsPolicyScopeAndEachUseIsAudited() {
        // Design §4.6, multi-tenant §6.4 (P-3): a PLATFORM fact taken under a registered cross-tenant policy is handled
        // in that policy's scope, keyed by what the runtime binds (here the invitee's e-mail), and each use is in the
        // policy audit with the tenants it reached. A redelivery the inbox skips is no use and leaves no record.
        world.publishSetupCompleted("setup-1", 77L);
        harness.start();

        await("the fan-out ran", () -> world.handledIds().contains("setup-1"));
        await("the use is audited", () -> !world.policyUses().isEmpty());
        settle();
        ConformanceWorld.Handled handled = world.handled().get(0);
        assertEquals(TenantScope.policy(TenantAccessPolicy.FIRST_OWNER_INVITEE_SETUP_FANOUT, ConformanceWorld.INVITEE_EMAIL),
            handled.scope());
        assertEquals(1, world.policyUses().size());
        PolicyUseRecord use = world.policyUses().get(0);
        assertEquals(TenantAccessPolicy.FIRST_OWNER_INVITEE_SETUP_FANOUT, use.policy());
        assertEquals(77L, use.actorIdentityId());
        assertEquals(ConformanceWorld.FANOUT_TENANTS, use.affectedTenants());
        assertTrue(use.success());

        int duplicates = world.duplicates();
        assertTrue(harness.republish("setup-1"));
        await("the inbox recognises the redelivery", () -> world.duplicates() > duplicates);
        settle();
        assertEquals(List.of("setup-1"), world.handledIds());
        assertEquals(1, world.policyUses().size(), "a skipped redelivery is no use of the policy");
    }

    @Test
    void aSubscriberWhoseDatabaseIsDownWaitsForItWithoutSpendingAttempts() {
        // Kafka design §14 K11: the subscriber's database being down is no fault of the event. Its events wait, spend
        // no attempt and are not parked; the later events of their key wait behind them; all arrive once it is back.
        world.subscriberDatabaseDown(true);
        world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1);
        world.publish("event-2", "member-1", ConformanceWorld.TENANT, 2);
        harness.start();

        // Three attempts 100 ms apart would have parked both long before this.
        pause(Duration.ofSeconds(3));
        assertEquals(List.of(), harness.parked());
        assertEquals(List.of(), world.handledIds());

        world.subscriberDatabaseDown(false);
        await("both are handled", () -> world.handledIds().size() == 2);
        settle();
        assertEquals(List.of("event-1", "event-2"), world.handledIds());
        assertEquals(List.of(), harness.parked());
    }

    @Test
    void aHandlerThatThrowsAnErrorFailsOnlyThatDelivery() {
        // Kafka design §14 K10: an Error from a handler, such as a failed assertion or a class that cannot load, fails
        // that delivery like an exception. The transport keeps delivering and the event is tried again.
        world.failNextWith(new AssertionError("an invariant the handler checks"));
        world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1);
        world.publish("event-2", "member-2", ConformanceWorld.TENANT, 2);
        harness.start();

        await("both are handled", () -> world.handledIds().size() == 2);
        settle();
        assertEquals(java.util.Set.of("event-1", "event-2"), java.util.Set.copyOf(world.handledIds()));
        assertTrue(world.handled().stream().anyMatch(handled -> handled.attempt() >= 2), "the failed one was tried again");
        assertEquals(List.of(), harness.parked());
    }

    @Test
    void aHandlerPastItsDeadlineIsCancelledAndTriedAgain() {
        // Kafka design §14 K10: the deadline is enforced. A handler that does not finish in time is cancelled, its
        // delivery counts as failed, and the event is tried again instead of holding its key for good.
        world.hangNext(1);
        world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1);
        harness.start();

        await("event-1 is handled", () -> world.handledIds().contains("event-1"));
        settle();
        assertEquals(List.of("event-1"), world.handledIds());
        assertTrue(world.handled().get(0).attempt() >= 2, "handled on a later attempt");
        assertEquals(List.of(), harness.parked());
    }

    @Test
    void aHandlerThatRunsPastItsDeadlineRollsBackAndIsTriedAgain() {
        // Standard-link plan point 3 (root BRX-RS-DATA-011, DATA-028): a synchronous handler that did its work and
        // returned after its deadline does not commit. Its write and its inbox record roll back, and the event takes
        // effect once, on a later attempt.
        world.handlerDeadline(Duration.ofMillis(300));
        world.overrunNext(1);
        world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1);
        harness.start();

        await("event-1 is handled", () -> world.handledIds().contains("event-1"));
        settle();
        assertEquals(List.of("event-1"), world.handledIds());
        assertTrue(world.handled().get(0).attempt() >= 2, "committed on a later attempt");
        assertEquals(2, world.calls(), "the late attempt ran, and its work was undone");
        assertEquals(List.of(), harness.parked());
    }

    @Test
    void aCommandHandlerThatRunsPastItsDeadlineRollsBackAndIsTriedAgain() {
        // Standard-link plan point 3: the late attempt's inbox record, business key and write roll back together, so
        // the command runs again and takes effect once.
        world.handlerDeadline(Duration.ofMillis(300));
        world.overrunNext(1);
        world.submit("command-1", "order-7");
        harness.start();

        await("command-1 is handled", () -> world.handledCommands().contains("command-1"));
        settle();
        assertEquals(List.of("command-1"), world.handledCommands());
        assertEquals(2, world.commandCalls(), "the late attempt ran, and its work was undone");
        assertEquals(List.of(), harness.parked());
    }

    @Test
    void twoRelaysOnOneOutboxDeliverEachEventOnceInItsKeyOrder() {
        // Kafka design §14 K12: hosts share an outbox. A relay claims a key's later messages only together with its
        // earlier ones, so two relays never send one event at once and each key keeps its order.
        for (int i = 1; i <= 24; i++) {
            world.publish("event-" + i, "member-" + (i % 3), ConformanceWorld.TENANT, i);
        }
        harness.start();
        harness.startAnotherRelay();

        await("all are handled", () -> world.handledIds().size() == 24);
        settle();
        assertEquals(24, java.util.Set.copyOf(world.handledIds()).size());
        for (int key = 0; key < 3; key++) {
            int k = key;
            List<Long> order = world.handled().stream().map(ConformanceWorld.Handled::memberId)
                .filter(member -> member % 3 == k).toList();
            assertEquals(order.stream().sorted().toList(), order, "the events of member-" + k + " in order");
        }
    }

    @Test
    void anEventTheRetentionRemovedCannotBeRepublished() {
        // Kafka design D34: an event can be republished only while its outbox keeps it. Once the cleaning removed it,
        // nothing can send it again, so no inbox meets it after its own record has expired.
        world.publish("event-1", "member-1", ConformanceWorld.TENANT, 1);
        harness.start();
        await("event-1 is handled", () -> world.handledIds().contains("event-1"));
        await("event-1 is published", () -> world.outbox().find("event-1")
            .filter(stored -> "PUBLISHED".equals(stored.status())).isPresent());

        assertEquals(1, world.outbox().purge(java.time.OffsetDateTime.now().plusDays(1), 10));

        assertFalse(harness.republish("event-1"));
        settle();
        assertEquals(List.of("event-1"), world.handledIds());
    }

    /**
     * Runs the sends at the same moment and waits for them; what a send throws is ignored.
     *
     * @param copies how many
     * @param send one send
     */
    protected static void atOnce(int copies, Runnable send) {
        java.util.concurrent.CyclicBarrier start = new java.util.concurrent.CyclicBarrier(copies);
        List<Thread> threads = IntStream.range(0, copies).mapToObj(copy -> new Thread(() -> {
            try {
                start.await();
                send.run();
            } catch (Exception lost) {
                // Another copy committed first; a relay would retry this one and its inbox would skip it.
            }
        }, "conformance-copy-" + copy)).toList();
        threads.forEach(Thread::start);
        for (Thread thread : threads) {
            try {
                thread.join(WAIT.toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail("interrupted while sending copies");
            }
        }
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
