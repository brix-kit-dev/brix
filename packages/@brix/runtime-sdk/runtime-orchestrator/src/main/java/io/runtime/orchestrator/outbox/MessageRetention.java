/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import io.runtime.orchestrator.command.OwnerCommandIdempotency;
import io.runtime.orchestrator.endpoint.OwnerEndpointIdempotency;
import io.runtime.orchestrator.event.EventInboxStore;

/**
 * Deletes what the messaging tables no longer need, a batch at a time (root BRX-RS-MESSAGE-026; Kafka design D34,
 * §14 K16): published and discarded outbox messages after the outbox retention; inbox, command idempotency and command
 * outcome records (standard-link plan point 7) after the inbox retention; the answers kept for endpoints when they
 * expire (point 10a). Parked messages stay until an operator replays or discards them.
 *
 * <p>An inbox must recognise a message for as long as it can still arrive: an operator can republish it while its
 * outbox keeps it, and the broker then keeps it for the broker's retention. The Host therefore refuses to start when
 * the inbox retention is shorter than the outbox retention plus the broker's. A table that cannot be cleaned is
 * reported and skipped until the next pass; the others are cleaned.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class MessageRetention implements SmartLifecycle {

    /** Statements per table and pass, so one pass ends even on a large backlog. */
    static final int MAX_BATCHES = 100;
    /** Delay before the first pass after the Host starts, at most. */
    static final Duration FIRST_PASS = Duration.ofMinutes(5);
    /** What a pass that failed as a whole is reported as: it cleaned no table. */
    static final String ALL_TABLES = "all";

    private static final Logger log = LoggerFactory.getLogger(MessageRetention.class);

    private final Supplier<OutboxStoreRegistry> outboxes;
    private final Supplier<EventInboxStore> inboxes;
    private final Supplier<OwnerCommandIdempotency> idempotency;
    private final Supplier<OwnerEndpointIdempotency> answers;
    private final OutboxRelayProperties.Retention settings;
    private final MessageRetentionMetrics metrics;
    private final Clock clock;
    private ScheduledExecutorService executor;
    private volatile boolean running;

    /**
     * Creates the cleaning and checks the retentions.
     *
     * @param outboxes supplies the owner outboxes; may supply {@code null}
     * @param inboxes supplies the owner inboxes; may supply {@code null}
     * @param idempotency supplies the providers' idempotency records; may supply {@code null}
     * @param settings the retentions and the pace
     * @param brokerRetention how long the broker keeps a published message
     * @param metrics where passes are reported
     * @param clock cleaning clock
     * @throws IllegalStateException when the inbox retention is shorter than the outbox's plus the broker's
     */
    public MessageRetention(
            Supplier<OutboxStoreRegistry> outboxes,
            Supplier<EventInboxStore> inboxes,
            Supplier<OwnerCommandIdempotency> idempotency,
            OutboxRelayProperties.Retention settings,
            Duration brokerRetention,
            MessageRetentionMetrics metrics,
            Clock clock) {
        this(outboxes, inboxes, idempotency, () -> null, settings, brokerRetention, metrics, clock);
    }

    /**
     * Creates the cleaning, with the answers kept for endpoints (standard-link plan point 10a), and checks the
     * retentions.
     *
     * @param outboxes supplies the owner outboxes; may supply {@code null}
     * @param inboxes supplies the owner inboxes; may supply {@code null}
     * @param idempotency supplies the providers' idempotency records; may supply {@code null}
     * @param answers supplies the owners' kept answers, cleaned when they expire; may supply {@code null}
     * @param settings the retentions and the pace
     * @param brokerRetention how long the broker keeps a published message
     * @param metrics where passes are reported
     * @param clock cleaning clock
     * @throws IllegalStateException when the inbox retention is shorter than the outbox's plus the broker's
     */
    public MessageRetention(
            Supplier<OutboxStoreRegistry> outboxes,
            Supplier<EventInboxStore> inboxes,
            Supplier<OwnerCommandIdempotency> idempotency,
            Supplier<OwnerEndpointIdempotency> answers,
            OutboxRelayProperties.Retention settings,
            Duration brokerRetention,
            MessageRetentionMetrics metrics,
            Clock clock) {
        this.outboxes = Objects.requireNonNull(outboxes, "outboxes must not be null");
        this.inboxes = Objects.requireNonNull(inboxes, "inboxes must not be null");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency must not be null");
        this.answers = Objects.requireNonNull(answers, "answers must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        check(settings, Objects.requireNonNull(brokerRetention, "brokerRetention must not be null"));
    }

    /**
     * Checks that the retentions fit together (Kafka design D34).
     *
     * @param settings the retentions and the pace
     * @param brokerRetention how long the broker keeps a published message
     * @throws IllegalStateException when they do not
     */
    public static void check(OutboxRelayProperties.Retention settings, Duration brokerRetention) {
        requirePositive(settings.getOutbox(), "brix.outbox.relay.retention.outbox");
        requirePositive(settings.getInbox(), "brix.outbox.relay.retention.inbox");
        requirePositive(settings.getInterval(), "brix.outbox.relay.retention.interval");
        if (settings.getBatchSize() <= 0) {
            throw new IllegalStateException("brix.outbox.relay.retention.batch-size must be positive");
        }
        Duration needed = settings.getOutbox().plus(brokerRetention);
        if (settings.getInbox().compareTo(needed) < 0) {
            throw new IllegalStateException("brix.outbox.relay.retention.inbox (" + settings.getInbox()
                + ") must be at least the outbox retention (" + settings.getOutbox() + ") plus the broker's ("
                + brokerRetention + "): a republished message could otherwise arrive after its inbox record is gone"
                + " (Kafka design D34)");
        }
    }

    /**
     * Runs one pass: each table a batch at a time, until it has nothing expired or {@value #MAX_BATCHES} batches.
     *
     * @return rows deleted per table; a table that failed is left out
     */
    public Map<String, Integer> purgeOnce() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime outboxCutoff = now.minus(settings.getOutbox());
        OffsetDateTime inboxCutoff = now.minus(settings.getInbox());
        Map<String, Integer> deleted = new TreeMap<>();
        OutboxStoreRegistry stores = outboxes.get();
        if (stores != null) {
            new TreeMap<>(stores.stores()).forEach((table, store) ->
                drain(table, limit -> store.purge(outboxCutoff, limit), deleted));
        }
        EventInboxStore inbox = inboxes.get();
        if (inbox != null) {
            for (String table : tables(inbox::tables, "inbox")) {
                drain(table, limit -> inbox.purge(table, inboxCutoff, limit), deleted);
            }
        }
        OwnerCommandIdempotency commands = idempotency.get();
        if (commands != null) {
            for (String table : tables(commands::tables, "command idempotency")) {
                drain(table, limit -> commands.purge(table, inboxCutoff, limit), deleted);
            }
        }
        OwnerEndpointIdempotency endpoints = answers.get();
        if (endpoints != null) {
            for (String table : tables(endpoints::tables, "endpoint idempotency")) {
                drain(table, limit -> endpoints.purge(table, now, limit), deleted);
            }
        }
        metrics.passed(clock.instant());
        int total = deleted.values().stream().mapToInt(Integer::intValue).sum();
        if (total > 0) {
            log.info("event=messaging.retention_pass deleted={} tables={}", total, deleted);
        }
        return deleted;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "brix-message-retention");
            thread.setDaemon(true);
            return thread;
        });
        Duration first = settings.getInterval().compareTo(FIRST_PASS) < 0 ? settings.getInterval() : FIRST_PASS;
        executor.scheduleWithFixedDelay(this::pass, first.toMillis(), settings.getInterval().toMillis(),
            TimeUnit.MILLISECONDS);
        running = true;
        log.info("event=messaging.retention_started outbox={} inbox={} interval={}", settings.getOutbox(),
            settings.getInbox(), settings.getInterval());
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        executor.shutdownNow();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Runs a scheduled pass. A pass that fails as a whole is counted too, so it alerts even before a first pass. */
    void pass() {
        try {
            purgeOnce();
        } catch (RuntimeException | Error failure) {
            // A pass that throws would end the schedule; report it and try again at the next one.
            metrics.failed(ALL_TABLES);
            log.warn("event=messaging.retention_failed table={} code=RETENTION_PASS_FAILED", ALL_TABLES, failure);
        }
    }

    private void drain(String table, Batch batch, Map<String, Integer> deleted) {
        int total = 0;
        try {
            for (int round = 0; round < MAX_BATCHES && !Thread.currentThread().isInterrupted(); round++) {
                int rows = batch.delete(settings.getBatchSize());
                total += rows;
                if (rows < settings.getBatchSize()) {
                    break;
                }
            }
            deleted.put(table, total);
        } catch (RuntimeException failure) {
            metrics.failed(table);
            log.warn("event=messaging.retention_failed table={} code=RETENTION_TABLE_FAILED", table, failure);
        } finally {
            metrics.deleted(table, total);
        }
    }

    private List<String> tables(Supplier<List<String>> tables, String what) {
        try {
            return tables.get();
        } catch (RuntimeException failure) {
            metrics.failed(what);
            log.warn("event=messaging.retention_failed what={} code=RETENTION_TABLES_UNKNOWN", what, failure);
            return List.of();
        }
    }

    private static void requirePositive(Duration value, String property) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalStateException(property + " must be positive");
        }
    }

    @FunctionalInterface
    private interface Batch {
        int delete(int limit);
    }
}
