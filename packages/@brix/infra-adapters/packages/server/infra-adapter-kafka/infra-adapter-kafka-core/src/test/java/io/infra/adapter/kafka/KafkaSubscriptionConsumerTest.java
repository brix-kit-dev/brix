/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.mock.MockProducerFactory;

import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import io.runtime.orchestrator.event.ConsumedSubscription;
import io.runtime.orchestrator.event.EventDelivery;
import io.runtime.orchestrator.event.EventSubscriptionMetrics;
import io.runtime.orchestrator.event.ParkedEvent;
import io.runtime.orchestrator.event.SubscriptionDelivery;
import io.runtime.orchestrator.event.SubscriptionDelivery.Kind;
import io.runtime.orchestrator.event.SubscriptionDelivery.Outcome;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;

/**
 * One subscription's consumer (Kafka design §4.2, §14 K10, K11; root BRX-RS-MESSAGE-025, MESSAGE-027, MESSAGE-028):
 * an offset is committed once the subscriber took its record, a failed record holds its partition back until its retry
 * is due, a record the subscription cannot take is parked and passed, and whatever stops the loop, it starts again.
 */
class KafkaSubscriptionConsumerTest {

    private static final ConsumedSubscription SUBSCRIPTION =
        new ConsumedSubscription("billing", "invoice-on-order.v1", "OrderPlaced");
    private static final String TOPIC = "brix.event.OrderPlaced";
    private static final String PARKING_TOPIC = "brix.parked.billing.invoice-on-order.v1";
    private static final Duration POLL = Duration.ofMillis(10);

    private final MutableClock clock = new MutableClock();
    private final CanonicalEnvelopeCodec envelopes = new CanonicalEnvelopeCodec(JsonMapper.builder()
        .configureForJackson2()
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build());
    private final EventTopic topic = new EventTopic(TOPIC, 2);
    private final ParkingTopic parkingTopic = new ParkingTopic();
    private final KafkaParkingLot parking = new KafkaParkingLot(parkingTopic.template(), new KafkaEventTopics(""),
        () -> {
            throw new IllegalStateException("the consumer never reads the parking topics");
        },
        envelopes,
        new KafkaEventSettings(2, (short) 1, (short) 1, Duration.ofDays(7), 10, POLL, Duration.ofSeconds(5),
            Duration.ofSeconds(1), Duration.ofMillis(100)),
        clock);

    {
        // This host reads no parking topics: it knows what its own consumer parked.
        parking.watch(List.of());
    }

    private final RecordingMetrics metrics = new RecordingMetrics();
    private final Subscriber subscriber = new Subscriber();
    private KafkaSubscriptionConsumer consumer;

    @AfterEach
    void stopConsumer() {
        subscriber.releaseAll();
        if (consumer != null) {
            consumer.stop(Duration.ofSeconds(10));
        }
    }

    @Test
    void aRecordsOffsetIsCommittedOnlyOnceTheSubscriberTookIt() {
        Hold handler = subscriber.hold("evt-1");
        topic.append(0, envelope("evt-1"));
        start();
        await("evt-1 in the handler", handler::entered);

        assertThat(topic.commits()).isEmpty();

        handler.release();
        await("evt-1 committed", () -> topic.committed(0).isPresent());
        assertThat(topic.commits()).containsExactly(Map.of(partition(0), 1L));
        assertThat(subscriber.received()).containsExactly(new Received("evt-1", 1));
    }

    @Test
    void aFailedRecordIsDeliveredAgainWithTheNextAttemptOnceItsDelayIsOverAndBeforeAnyLaterRecord() {
        subscriber.replies("evt-1", retryAfter(Duration.ofSeconds(30)));
        topic.append(0, envelope("evt-1"));
        topic.append(0, envelope("evt-2"));
        start();
        await("evt-1 held back", () -> heldBack(0));

        clock.advance(Duration.ofSeconds(29));
        awaitPolls(3);

        assertThat(subscriber.received()).containsExactly(new Received("evt-1", 1));
        assertThat(topic.commits()).isEmpty();
        assertThat(metrics.stalled).as("how long the head of the partition has been failing")
            .isEqualTo(Duration.ofSeconds(29));
        assertThat(metrics.lag).as("both records wait behind the consumer's position").isEqualTo(2);

        clock.advance(Duration.ofSeconds(1));
        await("evt-2 committed", () -> topic.committed(0).equals(Optional.of(2L)));

        assertThat(subscriber.received())
            .containsExactly(new Received("evt-1", 1), new Received("evt-1", 2), new Received("evt-2", 1));
        assertThat(subscriber.parked()).isEmpty();
        await("nothing stalled any more", () -> metrics.stalled.isZero());
    }

    @Test
    void aPartitionHeldBackOnAFailedRecordDoesNotHoldBackTheOthers() {
        subscriber.replies("evt-1", retryAfter(Duration.ofSeconds(30)));
        topic.append(0, envelope("evt-1"));
        topic.append(1, envelope("evt-2"));
        topic.append(1, envelope("evt-3"));
        start();

        await("evt-3 committed", () -> topic.committed(1).equals(Optional.of(2L)));

        assertThat(heldBack(0)).isTrue();
        assertThat(topic.committed(0)).isEmpty();
        assertThat(subscriber.received())
            .containsExactlyInAnyOrder(new Received("evt-1", 1), new Received("evt-2", 1), new Received("evt-3", 1));
    }

    @Test
    void aSubscriberThatIsNotThereIsWaitedForWithoutSpendingAnAttempt() {
        // Kafka design §14 K11: the record waits in place, as the relay waits for the broker (D21).
        subscriber.replies("evt-1", waitFor(Duration.ofSeconds(1)), waitFor(Duration.ofSeconds(1)));
        topic.append(0, envelope("evt-1"));
        start();
        await("the first wait", () -> subscriber.received().size() == 1 && heldBack(0));
        awaitPolls(3);
        assertThat(subscriber.received()).hasSize(1);

        clock.advance(Duration.ofSeconds(1));
        await("the second wait", () -> subscriber.received().size() == 2 && heldBack(0));
        clock.advance(Duration.ofSeconds(1));
        await("evt-1 committed", () -> topic.committed(0).equals(Optional.of(1L)));

        assertThat(subscriber.received())
            .containsExactly(new Received("evt-1", 1), new Received("evt-1", 1), new Received("evt-1", 1));
        assertThat(subscriber.parked()).isEmpty();
        assertThat(parkingTopic.taken()).isEmpty();
    }

    @Test
    void theDeliveryIsToldHowLongTheRecordHasWaitedSoItCanEndTheWait() {
        // Root BRX-RS-RESILIENCE-002 (standard-link plan D91): the wait counts from when the record first failed.
        subscriber.replies("evt-1", waitFor(Duration.ofSeconds(1)), waitFor(Duration.ofSeconds(2)));
        topic.append(0, envelope("evt-1"));
        topic.append(0, envelope("evt-2"));
        start();
        await("the first wait", () -> subscriber.received().size() == 1 && heldBack(0));

        clock.advance(Duration.ofSeconds(1));
        await("the second wait", () -> subscriber.received().size() == 2 && heldBack(0));
        clock.advance(Duration.ofSeconds(2));
        await("both are committed", () -> topic.committed(0).equals(Optional.of(2L)));

        assertThat(subscriber.waited()).containsExactly(SubscriptionDelivery.Waited.NOT_YET,
            new SubscriptionDelivery.Waited(1, Duration.ofSeconds(1)),
            new SubscriptionDelivery.Waited(2, Duration.ofSeconds(3)),
            SubscriptionDelivery.Waited.NOT_YET);
    }

    @Test
    void aFailedAttemptIsNoWaitButItsTimeCounts() {
        subscriber.replies("evt-1", retryAfter(Duration.ofSeconds(10)), waitFor(Duration.ofSeconds(1)));
        topic.append(0, envelope("evt-1"));
        start();
        await("the retry", () -> subscriber.received().size() == 1 && heldBack(0));

        clock.advance(Duration.ofSeconds(10));
        await("the wait", () -> subscriber.received().size() == 2 && heldBack(0));
        clock.advance(Duration.ofSeconds(1));
        await("evt-1 is committed", () -> topic.committed(0).equals(Optional.of(1L)));

        assertThat(subscriber.waited()).containsExactly(SubscriptionDelivery.Waited.NOT_YET,
            new SubscriptionDelivery.Waited(0, Duration.ofSeconds(10)),
            new SubscriptionDelivery.Waited(1, Duration.ofSeconds(11)));
    }

    @Test
    void aRecordTheSubscriptionParksIsWrittenToItsParkingTopicAndPassed() {
        subscriber.replies("evt-1", retryAfter(Duration.ofSeconds(30)), park(EventDelivery.HANDLER_FAILED));
        ConsumerRecord<String, String> failing = topic.append(0, envelope("evt-1"));
        topic.append(0, envelope("evt-2"));
        start();
        await("evt-1 held back", () -> heldBack(0));

        clock.advance(Duration.ofSeconds(30));

        await("evt-2 committed", () -> topic.committed(0).equals(Optional.of(2L)));
        assertThat(subscriber.received())
            .containsExactly(new Received("evt-1", 1), new Received("evt-1", 2), new Received("evt-2", 1));
        assertThat(parkingTopic.taken()).singleElement().satisfies(parked -> {
            assertThat(parked.topic()).isEqualTo(PARKING_TOPIC);
            assertThat(parked.key()).isEqualTo("evt-1");
            assertThat(parked.value()).isEqualTo(failing.value());
            assertThat(header(parked, KafkaParkingLot.CODE)).isEqualTo(EventDelivery.HANDLER_FAILED);
            assertThat(header(parked, KafkaParkingLot.ATTEMPTS)).isEqualTo("2");
            assertThat(header(parked, KafkaParkingLot.SOURCE)).isEqualTo(TOPIC + "-0@0");
            assertThat(header(parked, KafkaParkingLot.EVENT_TYPE)).isEqualTo("OrderPlaced");
        });
        assertThat(parking.find(SUBSCRIPTION, "evt-1")).map(ParkedEvent::attempts).contains(2);
        assertThat(subscriber.parked())
            .containsExactly(new ParkReport("OrderPlaced", "evt-1", EventDelivery.HANDLER_FAILED, 2));
    }

    @Test
    void aRecordThatIsNotAnEventEnvelopeIsParkedAtOnceUnderWhereTheBrokerHeldIt() {
        // Kafka design §14 K15: nothing can deliver it, so no attempt is spent on it.
        topic.append(0, "{\"eventId\":\"evt-1\"}");
        topic.append(0, envelope("evt-2"));
        start();

        await("evt-2 committed", () -> topic.committed(0).equals(Optional.of(2L)));

        String source = TOPIC + "-0@0";
        assertThat(subscriber.received()).containsExactly(new Received("evt-2", 1));
        assertThat(parkingTopic.taken()).singleElement().satisfies(parked -> {
            assertThat(parked.key()).isEqualTo(source);
            assertThat(parked.value()).isEqualTo("{\"eventId\":\"evt-1\"}");
            assertThat(header(parked, KafkaParkingLot.CODE)).isEqualTo(SubscriptionDelivery.ENVELOPE_INVALID);
            assertThat(header(parked, KafkaParkingLot.ATTEMPTS)).isEqualTo("1");
            assertThat(header(parked, KafkaParkingLot.EVENT_TYPE)).isNull();
        });
        assertThat(parking.find(SUBSCRIPTION, source)).isPresent();
        assertThat(subscriber.parked())
            .containsExactly(new ParkReport(null, source, SubscriptionDelivery.ENVELOPE_INVALID, 1));
    }

    @Test
    void aRecordItsParkingTopicDoesNotTakeHoldsItsPartitionBackAndIsParkedOnALaterPass() {
        subscriber.replies("evt-1", park(EventDelivery.SCHEMA_UNSUPPORTED), park(EventDelivery.SCHEMA_UNSUPPORTED));
        topic.append(0, envelope("evt-1"));
        topic.append(0, envelope("evt-2"));
        parkingTopic.refusing = true;
        start();
        await("evt-1 held back", () -> heldBack(0));
        awaitPolls(3);

        assertThat(topic.commits()).isEmpty();
        assertThat(parking.find(SUBSCRIPTION, "evt-1")).isEmpty();
        assertThat(subscriber.received()).containsExactly(new Received("evt-1", 1));

        parkingTopic.refusing = false;
        clock.advance(KafkaSubscriptionConsumer.PARK_RETRY);

        await("evt-2 committed", () -> topic.committed(0).equals(Optional.of(2L)));
        assertThat(subscriber.received()).as("the same attempt comes back, so it is parked rather than retried")
            .containsExactly(new Received("evt-1", 1), new Received("evt-1", 1), new Received("evt-2", 1));
        assertThat(parkingTopic.taken()).extracting(ProducerRecord::key).containsExactly("evt-1");
        assertThat(parking.find(SUBSCRIPTION, "evt-1")).isPresent();
        assertThat(subscriber.parked())
            .containsExactly(new ParkReport("OrderPlaced", "evt-1", EventDelivery.SCHEMA_UNSUPPORTED, 1));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void whateverEscapesTheDeliveryTheConsumerStartsAgainFromItsCommittedPosition(Throwable failure) {
        // Kafka design §14 K10: the thread survives, closes its consumer and starts a new one after a pause.
        topic.append(0, envelope("evt-1"));
        start();
        await("evt-1 committed", () -> topic.committed(0).isPresent());
        subscriber.replies("evt-2", throwing(failure));
        topic.append(0, envelope("evt-2"));

        await("evt-2 committed after the restart", () -> topic.committed(0).equals(Optional.of(2L)));

        assertThat(subscriber.received()).extracting(Received::eventId).containsExactly("evt-1", "evt-2", "evt-2");
        assertThat(topic.members()).hasSize(2);
        assertThat(topic.members().get(0).closed()).isTrue();
        assertThat(topic.members().get(1).closed()).isFalse();
        assertThat(consumer.running()).isTrue();
    }

    @Test
    void aConsumerWaitingToStartAgainIsNotRunning() {
        // A consumer that fails again on every start, say a broker refusing its credentials, must read as stopped: its
        // gauge and health go down as soon as it closes, not only for the moment between two starts.
        topic.append(0, envelope("evt-1"));
        subscriber.replies("evt-1", throwing(new IllegalStateException("the broker refused the consumer")));
        start();
        await("the first consumer closed", () -> !topic.members().isEmpty() && topic.members().get(0).closed());
        long closed = System.nanoTime();

        await("not running", () -> !consumer.running());

        assertThat(Duration.ofNanos(System.nanoTime() - closed))
            .as("down well before the restart").isLessThan(KafkaSubscriptionConsumer.RESTART_DELAY.dividedBy(2));
        assertThat(metrics.running.getAsBoolean()).as("the gauge reads the same").isFalse();
        assertThat(topic.members()).hasSize(1);
        await("running again after the restart", () -> consumer.running() && topic.members().size() == 2);
        await("evt-1 committed", () -> topic.committed(0).isPresent());
    }

    static Stream<Throwable> failures() {
        return Stream.of(
            new IllegalStateException("the subscriber's unit of work could not begin"),
            new AssertionError("an Error, which the delivery does not turn into a failed attempt"));
    }

    @Test
    void anOffsetTheBrokerDidNotCommitIsCommittedOnTheNextPassWithoutDeliveringTheRecordAgain() {
        topic.refuseNextCommit = true;
        topic.append(0, envelope("evt-1"));
        start();

        await("evt-1 committed", () -> topic.committed(0).isPresent());

        assertThat(topic.commits()).containsExactly(Map.of(partition(0), 1L));
        assertThat(subscriber.received()).containsExactly(new Received("evt-1", 1));
    }

    @Test
    void theHandlingAgeIsHowLongTheRecordInTheHandlerHasBeenThere() {
        Hold handler = subscriber.hold("evt-1");
        topic.append(0, envelope("evt-1"));
        start();
        await("evt-1 in the handler", handler::entered);
        assertThat(consumer.handlingFor()).isZero();

        clock.advance(Duration.ofSeconds(42));

        assertThat(consumer.handlingFor()).isEqualTo(Duration.ofSeconds(42));
        assertThat(metrics.handlingFor.get()).as("the gauge reads the same").isEqualTo(Duration.ofSeconds(42));
        handler.release();
        await("evt-1 committed", () -> topic.committed(0).isPresent());
        assertThat(consumer.handlingFor()).isZero();
    }

    @Test
    void anIdleConsumerStopsWithinItsTimeoutAndNoLongerRuns() {
        start();
        await("the consumer running", consumer::running);
        assertThat(metrics.running.getAsBoolean()).isTrue();

        long started = System.nanoTime();
        consumer.stop(Duration.ofSeconds(5));

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
        assertThat(consumer.running()).isFalse();
        assertThat(metrics.running.getAsBoolean()).isFalse();
        assertThat(topic.members()).singleElement().satisfies(member -> assertThat(member.closed()).isTrue());
    }

    @Test
    void stopLetsTheRecordInHandFinishCommitsItAndFetchesNoMore() throws InterruptedException {
        Hold handler = subscriber.hold("evt-1");
        topic.append(0, envelope("evt-1"));
        topic.append(0, envelope("evt-2"));
        start();
        await("evt-1 in the handler", handler::entered);

        Thread stopping = new Thread(() -> consumer.stop(Duration.ofSeconds(10)), "stopping");
        stopping.start();
        await("stop waiting for the record in hand", () -> stopping.getState() == Thread.State.TIMED_WAITING);
        handler.release();
        stopping.join(TimeUnit.SECONDS.toMillis(15));

        assertThat(stopping.isAlive()).isFalse();
        assertThat(topic.committed(0)).contains(1L);
        assertThat(subscriber.received()).containsExactly(new Received("evt-1", 1));
        assertThat(consumer.running()).isFalse();
    }

    @Test
    void stopGivesUpAfterItsTimeoutWhenTheHandlerDoesNotReturn() {
        Hold handler = subscriber.hold("evt-1");
        topic.append(0, envelope("evt-1"));
        start();
        await("evt-1 in the handler", handler::entered);

        long started = System.nanoTime();
        consumer.stop(Duration.ofMillis(200));

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isBetween(Duration.ofMillis(200), Duration.ofSeconds(5));
        assertThat(consumer.running()).as("the handler still holds the consumer's thread").isTrue();
        handler.release();
        await("the consumer stopped", () -> !consumer.running());
        assertThat(topic.committed(0)).contains(1L);
    }

    private void start() {
        consumer = new KafkaSubscriptionConsumer(SUBSCRIPTION, subscriber.delivery(), topic::join, parking, envelopes,
            TOPIC, POLL, clock);
        consumer.start();
    }

    private boolean heldBack(int partition) {
        List<GroupMember> members = topic.members();
        return !members.isEmpty() && members.get(members.size() - 1).paused().contains(partition(partition));
    }

    /** Waits until the consumer has polled this many more times, so it has passed its loop at least once since. */
    private void awaitPolls(int count) {
        long target = topic.polls.get() + count;
        await(count + " more polls", () -> topic.polls.get() >= target);
    }

    private String envelope(String eventId) {
        return envelopes.write(new CanonicalOutboxMessage("msg-" + eventId, eventId, "EVENT", "OrderPlaced", "1.0.0",
            "STANDARD", "orders", "PLATFORM", null, "order-1", OffsetDateTime.parse("2026-10-30T08:59:00Z"),
            "corr-" + eventId, null, null, null, "{\"orderId\":1}", 1));
    }

    private static TopicPartition partition(int partition) {
        return new TopicPartition(TOPIC, partition);
    }

    private static String header(ProducerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static Reply retryAfter(Duration delay) {
        return () -> new Outcome(Kind.RETRY, EventDelivery.HANDLER_FAILED, delay);
    }

    private static Reply waitFor(Duration delay) {
        return () -> new Outcome(Kind.WAIT, SubscriptionDelivery.OWNER_UNAVAILABLE, delay);
    }

    private static Reply park(String errorCode) {
        return () -> new Outcome(Kind.PARK, errorCode, Duration.ZERO);
    }

    private static Reply throwing(Throwable failure) {
        return () -> {
            throw failure;
        };
    }

    private static void await(String what, BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("timed out waiting for " + what);
            }
            sleep(Duration.ofMillis(5));
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** A delivery as the subscriber received it. */
    private record Received(String eventId, int attempt) {
    }

    /** A record the consumer reported parked. */
    private record ParkReport(String eventType, String eventId, String errorCode, int attempts) {
    }

    /** What the subscriber does with one delivery. */
    @FunctionalInterface
    private interface Reply {

        Outcome outcome() throws Throwable;
    }

    /** A delivery kept in the handler until it is released. */
    private static final class Hold {

        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        Reply reply() {
            return () -> {
                entered.countDown();
                released.await(30, TimeUnit.SECONDS);
                return Outcome.DELIVERED;
            };
        }

        boolean entered() {
            return entered.getCount() == 0;
        }

        void release() {
            released.countDown();
        }
    }

    /** The subscriber behind the delivery: it takes every record, unless replies are queued for its event id. */
    private final class Subscriber {

        private final Map<String, Queue<Reply>> replies = new ConcurrentHashMap<>();
        private final List<Received> received = new CopyOnWriteArrayList<>();
        private final List<SubscriptionDelivery.Waited> waited = new CopyOnWriteArrayList<>();
        private final List<ParkReport> parked = new CopyOnWriteArrayList<>();
        private final List<Hold> holds = new CopyOnWriteArrayList<>();

        void replies(String eventId, Reply... next) {
            replies.computeIfAbsent(eventId, id -> new ConcurrentLinkedQueue<>()).addAll(List.of(next));
        }

        Hold hold(String eventId) {
            Hold hold = new Hold();
            holds.add(hold);
            replies(eventId, hold.reply());
            return hold;
        }

        void releaseAll() {
            holds.forEach(Hold::release);
        }

        List<Received> received() {
            return List.copyOf(received);
        }

        List<SubscriptionDelivery.Waited> waited() {
            return List.copyOf(waited);
        }

        List<ParkReport> parked() {
            return List.copyOf(parked);
        }

        SubscriptionDelivery delivery() {
            SubscriptionDelivery delivery = mock(SubscriptionDelivery.class, withSettings().stubOnly());
            when(delivery.metrics()).thenReturn(metrics);
            when(delivery.receive(any(), anyInt(), any())).thenAnswer(invocation -> {
                waited.add(invocation.getArgument(2));
                return receive(invocation.getArgument(0), invocation.getArgument(1));
            });
            doAnswer(invocation -> {
                parked.add(new ParkReport(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2),
                    invocation.getArgument(3)));
                return null;
            }).when(delivery).parked(any(), any(), any(), anyInt());
            return delivery;
        }

        private Outcome receive(CanonicalOutboxMessage message, int attempt) throws Throwable {
            received.add(new Received(message.eventId(), attempt));
            Queue<Reply> queued = replies.get(message.eventId());
            Reply reply = queued == null ? null : queued.poll();
            return reply == null ? Outcome.DELIVERED : reply.outcome();
        }
    }

    /** The subscription's metrics as the consumer last reported them, and the gauges it registered. */
    private static final class RecordingMetrics implements EventSubscriptionMetrics {

        private volatile long lag = -1;
        private volatile Duration stalled = Duration.ZERO;
        private volatile Supplier<Duration> handlingFor;
        private volatile BooleanSupplier running;

        @Override
        public void recordLag(long records) {
            lag = records;
        }

        @Override
        public void recordStalled(Duration age) {
            stalled = age;
        }

        @Override
        public void watch(Supplier<Duration> handlingFor, BooleanSupplier running) {
            this.handlingFor = handlingFor;
            this.running = running;
        }
    }

    /**
     * The event topic as the broker holds it for the subscription's group: each partition keeps its records, so a
     * consumer that seeks back fetches a record again, and a new member of the group starts at the committed offsets.
     */
    private static final class EventTopic {

        private final String name;
        private final List<List<ConsumerRecord<String, String>>> partitions = new ArrayList<>();
        private final Map<TopicPartition, Long> committed = new ConcurrentHashMap<>();
        private final List<Map<TopicPartition, Long>> commits = new CopyOnWriteArrayList<>();
        private final List<GroupMember> members = new CopyOnWriteArrayList<>();
        private final AtomicLong polls = new AtomicLong();
        private volatile boolean refuseNextCommit;

        EventTopic(String name, int partitionCount) {
            this.name = name;
            for (int partition = 0; partition < partitionCount; partition++) {
                partitions.add(new CopyOnWriteArrayList<>());
            }
        }

        synchronized ConsumerRecord<String, String> append(int partition, String value) {
            List<ConsumerRecord<String, String>> records = partitions.get(partition);
            ConsumerRecord<String, String> record =
                new ConsumerRecord<>(name, partition, records.size(), "order-" + records.size(), value);
            records.add(record);
            return record;
        }

        Consumer<String, String> join() {
            GroupMember member = new GroupMember(this);
            members.add(member);
            return member;
        }

        List<TopicPartition> partitions() {
            List<TopicPartition> all = new ArrayList<>();
            for (int partition = 0; partition < partitions.size(); partition++) {
                all.add(new TopicPartition(name, partition));
            }
            return all;
        }

        List<ConsumerRecord<String, String>> records(TopicPartition partition) {
            return List.copyOf(partitions.get(partition.partition()));
        }

        Optional<Long> committed(int partition) {
            return Optional.ofNullable(committed.get(new TopicPartition(name, partition)));
        }

        List<Map<TopicPartition, Long>> commits() {
            return List.copyOf(commits);
        }

        List<GroupMember> members() {
            return List.copyOf(members);
        }
    }

    /**
     * A member of the subscription's group: it is given every partition at the group's committed offsets, fetches what
     * its partitions hold from their positions, and with nothing to fetch waits out its poll timeout, as a real one does.
     */
    private static final class GroupMember extends MockConsumer<String, String> {

        private final EventTopic topic;

        GroupMember(EventTopic topic) {
            super("earliest");
            this.topic = topic;
            Map<TopicPartition, Long> beginnings = new HashMap<>();
            topic.partitions().forEach(partition -> beginnings.put(partition, 0L));
            updateBeginningOffsets(beginnings);
        }

        @Override
        public ConsumerRecords<String, String> poll(Duration timeout) {
            topic.polls.incrementAndGet();
            Map<TopicPartition, List<ConsumerRecord<String, String>>> fetched = new HashMap<>();
            synchronized (this) {
                if (assignment().isEmpty() && subscription().contains(topic.name)) {
                    rebalance(topic.partitions());
                    topic.committed.forEach((partition, offset) -> seek(partition, offset));
                }
                Map<TopicPartition, Long> ends = new HashMap<>();
                for (TopicPartition partition : assignment()) {
                    List<ConsumerRecord<String, String>> records = topic.records(partition);
                    ends.put(partition, (long) records.size());
                    if (paused().contains(partition)) {
                        continue;
                    }
                    long position = position(partition);
                    List<ConsumerRecord<String, String>> next = records.stream()
                        .filter(record -> record.offset() >= position)
                        .toList();
                    if (!next.isEmpty()) {
                        fetched.put(partition, next);
                        seek(partition, next.get(next.size() - 1).offset() + 1);
                    }
                }
                updateEndOffsets(ends);
            }
            if (fetched.isEmpty()) {
                sleep(timeout);
            }
            return new ConsumerRecords<>(fetched, Map.of());
        }

        @Override
        public void commitSync(Map<TopicPartition, OffsetAndMetadata> offsets, Duration timeout) {
            if (topic.refuseNextCommit) {
                topic.refuseNextCommit = false;
                throw new TimeoutException("the group coordinator did not answer in time");
            }
            super.commitSync(offsets, timeout);
            Map<TopicPartition, Long> committed = new HashMap<>();
            offsets.forEach((partition, offset) -> committed.put(partition, offset.offset()));
            // In this order: a test waits for committed and then reads commits.
            topic.commits.add(Map.copyOf(committed));
            topic.committed.putAll(committed);
        }
    }

    /** The subscription's parking topic as the broker holds it: the records it took; it can refuse writes. */
    private static final class ParkingTopic {

        private final List<MockProducer<String, String>> accepted = new CopyOnWriteArrayList<>();
        private volatile boolean refusing;

        KafkaTemplate<String, String> template() {
            return new KafkaTemplate<>(new MockProducerFactory<String, String>(this::producer));
        }

        List<ProducerRecord<String, String>> taken() {
            return accepted.stream().flatMap(producer -> producer.history().stream()).toList();
        }

        private MockProducer<String, String> producer() {
            // A write the broker refuses is never acknowledged, so the parking lot gives up after its send timeout.
            boolean acknowledging = !refusing;
            MockProducer<String, String> producer =
                new MockProducer<>(acknowledging, null, new StringSerializer(), new StringSerializer());
            if (acknowledging) {
                accepted.add(producer);
            }
            return producer;
        }
    }

    /** A clock that moves only when the test moves it. */
    private static final class MutableClock extends Clock {

        private volatile Instant now = Instant.parse("2026-10-30T09:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
