/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import io.runtime.orchestrator.outbox.OutboxRelayAutoConfiguration;
import io.runtime.orchestrator.outbox.OutboxRelayPolicy;
import io.runtime.orchestrator.outbox.OutboxRelayProperties;

/**
 * Consumes every declared subscription from the broker the relay sends to (Kafka design §4.2, §11.1).
 * <p>The group starts with the host and waits on its own thread until the plugins have started and the
 * broker can be reached: no subscription takes an event before every required module started (root
 * BRX-RS-A-5). It then prepares the broker for the declared event types and subscriptions and starts one
 * consumption per subscription (root BRX-RS-MANIFEST-029). Stopping stops the waiting, gives running
 * handlers their cancellation signal, and lets each consumption finish the record in hand within the
 * relay's lease. With the in-process transport the relay delivers every event itself, so the group does
 * nothing. A broker transport without an adapter that consumes from it stops the host: its events would
 * be sent and never received.</p>
 */
public final class EventSubscriptionGroup implements SmartLifecycle {

    /** How often the parked events of each subscription are counted again. */
    static final Duration PARKED_REFRESH = Duration.ofSeconds(15);
    /** How long to wait before preparing a broker that refused again. */
    static final Duration PREPARE_RETRY = Duration.ofSeconds(5);
    /** Parked events counted per subscription at most. */
    static final int PARKED_COUNT_LIMIT = 10_000;

    private static final Logger log = LoggerFactory.getLogger(EventSubscriptionGroup.class);

    private final OutboxRelayProperties properties;
    private final OutboxRelayPolicy policy;
    private final Supplier<EventDelivery> deliveries;
    private final Supplier<EventSubscriptionTransport> brokers;
    private final EventSubscriptionMetricsFactory metrics;
    private final List<EventSubscriptionTransport.Consumption> consumptions = new ArrayList<>();
    private final Map<String, EventSubscriptionMetrics> subscriptionMetrics = new ConcurrentHashMap<>();
    private EventDelivery delivery;
    private EventSubscriptionTransport broker;
    private Thread supervisor;
    private volatile boolean stopping;
    private volatile boolean consuming;
    private volatile boolean running;
    private volatile String refusal;

    /**
     * Creates the group.
     *
     * @param properties relay settings: the transport, the polling delay and the lease
     * @param policy attempts, backoff and jitter of each subscription
     * @param deliveries supplies the delivery core; may supply {@code null}
     * @param brokers supplies the adapter that consumes from the broker; may supply {@code null}
     * @param metrics creates the metrics of each subscription
     */
    public EventSubscriptionGroup(
            OutboxRelayProperties properties,
            OutboxRelayPolicy policy,
            Supplier<EventDelivery> deliveries,
            Supplier<EventSubscriptionTransport> brokers,
            EventSubscriptionMetricsFactory metrics) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.deliveries = Objects.requireNonNull(deliveries, "deliveries must not be null");
        this.brokers = Objects.requireNonNull(brokers, "brokers must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    @Override
    public synchronized void start() {
        if (running || OutboxRelayAutoConfiguration.IN_PROCESS.equals(properties.getTransport())) {
            return;
        }
        broker = brokers.get();
        if (broker == null) {
            throw new IllegalStateException("brix.outbox.relay.transport=" + properties.getTransport()
                + " sends events to a broker, but no adapter consumes them from it");
        }
        delivery = deliveries.get();
        if (delivery == null) {
            throw new IllegalStateException("Consuming events needs the plugin runtime, owner persistence and inboxes");
        }
        stopping = false;
        supervisor = new Thread(this::supervise, "brix-event-subscriptions");
        supervisor.start();
        running = true;
    }

    @Override
    public void stop() {
        Thread waiting;
        List<EventSubscriptionTransport.Consumption> started;
        synchronized (this) {
            if (!running) {
                return;
            }
            stopping = true;
            waiting = supervisor;
            started = List.copyOf(consumptions);
            consumptions.clear();
        }
        waiting.interrupt();
        try {
            waiting.join(properties.getLeaseDuration().toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        // Handlers still running see their cancellation signal before the consumptions wait for them.
        delivery.stop();
        started.forEach(consumption -> consumption.stop(properties.getLeaseDuration()));
        consuming = false;
        running = false;
        log.info("event=event.subscriptions_stopped subscriptions={}", started.size());
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Whether every subscription is being consumed.
     *
     * @return readiness
     */
    public synchronized boolean isReady() {
        return consuming && consumptions.stream().allMatch(EventSubscriptionTransport.Consumption::running);
    }

    /**
     * What the host's health reports about the subscriptions (Kafka design §14 K10).
     *
     * @return the consumers' state
     */
    public synchronized SubscriptionsState state() {
        int running = (int) consumptions.stream().filter(EventSubscriptionTransport.Consumption::running).count();
        Duration longest = consumptions.stream()
            .map(EventSubscriptionTransport.Consumption::handlingFor)
            .max(Duration::compareTo)
            .orElse(Duration.ZERO);
        return new SubscriptionsState(consuming, consumptions.size(), running, longest, refusal);
    }

    /**
     * The consumers' state.
     *
     * @param consuming whether the group has started its consumers; it waits for the plugins and the broker first
     * @param consumers how many consumers it started
     * @param running how many of them are consuming
     * @param longestHandling how long the longest-running record has been in its handler
     * @param refusal why the broker refuses to carry the subscriptions until an operator fixes its configuration
     *     ({@link EventTransportRefusedException}); {@code null} while it does not
     */
    public record SubscriptionsState(boolean consuming, int consumers, int running, Duration longestHandling,
            String refusal) {
    }

    private void supervise() {
        try {
            while (!delivery.ready() || !broker.ready()) {
                pause(properties.getPollDelay());
            }
            List<ConsumedSubscription> subscriptions = new java.util.ArrayList<>(delivery.subscriptions().stream()
                .map(declared -> new ConsumedSubscription(declared.pluginId(), declared.subscription().handlerId(),
                    declared.subscription().eventType()))
                .toList());
            Set<String> eventTypes = new TreeSet<>(delivery.publishedEventTypes());
            subscriptions.forEach(subscription -> eventTypes.add(subscription.eventType()));
            // Kafka design §14 K22: each provided command is consumed like a subscription, from its command's topic.
            subscriptions.addAll(delivery.commandProvisions());
            prepare(eventTypes, subscriptions);
            consume(subscriptions);
            while (true) {
                countParked();
                pause(PARKED_REFRESH);
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }

    private void prepare(Set<String> eventTypes, List<ConsumedSubscription> subscriptions) throws InterruptedException {
        while (true) {
            try {
                broker.prepare(eventTypes, subscriptions);
                refusal = null;
                return;
            } catch (EventTransportRefusedException refused) {
                // A configuration the operator fixes, not an outage: the host's health shows it until then.
                refusal = refused.getMessage();
                log.warn("event=event.subscriptions_waiting reason=refused event_types={} subscriptions={}",
                    eventTypes, subscriptions.size(), refused);
                pause(PREPARE_RETRY);
            } catch (RuntimeException failure) {
                refusal = null;
                log.warn("event=event.subscriptions_waiting reason=prepare_failed event_types={} subscriptions={}",
                    eventTypes, subscriptions.size(), failure);
                pause(PREPARE_RETRY);
            }
        }
    }

    private synchronized void consume(List<ConsumedSubscription> subscriptions) {
        if (stopping) {
            return;
        }
        for (ConsumedSubscription subscription : subscriptions) {
            EventSubscriptionMetrics meters = metrics.forSubscription(subscription.name());
            subscriptionMetrics.put(subscription.name(), meters);
            consumptions.add(broker.consume(subscription, new SubscriptionDelivery(subscription, delivery, policy, meters)));
        }
        consuming = true;
        log.info("event=event.subscriptions_started subscriptions={}",
            subscriptions.stream().map(ConsumedSubscription::name).toList());
    }

    private void countParked() {
        try {
            Map<String, List<ParkedEvent>> parked = broker.parked(PARKED_COUNT_LIMIT);
            subscriptionMetrics.forEach((name, meters) -> meters.recordParkedNow(parked.getOrDefault(name, List.of()).size()));
        } catch (RuntimeException failure) {
            log.debug("event=event.parked_count_failed", failure);
        }
    }

    private void pause(Duration duration) throws InterruptedException {
        if (stopping) {
            throw new InterruptedException("the subscription group is stopping");
        }
        Thread.sleep(duration.toMillis());
    }
}
