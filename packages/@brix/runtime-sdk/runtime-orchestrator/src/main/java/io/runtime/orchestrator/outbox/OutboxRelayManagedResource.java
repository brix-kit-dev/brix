/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Spring managed resource that runs the L2B outbox relay.
 *
 * <p>The resource opens no plugin-visible API. Stop first disables admission,
 * then cancels future polling and releases the executor.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class OutboxRelayManagedResource implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayManagedResource.class);

    private final OutboxRelay relay;
    private final Duration pollDelay;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> task;
    private volatile RuntimeException lastFailure;

    /**
     * Creates a managed relay resource.
     *
     * @param relay relay instance
     * @param pollDelay fixed delay between polling passes
     */
    public OutboxRelayManagedResource(OutboxRelay relay, Duration pollDelay) {
        this.relay = Objects.requireNonNull(relay, "relay must not be null");
        if (pollDelay == null || pollDelay.isZero() || pollDelay.isNegative()) {
            throw new IllegalArgumentException("pollDelay must be positive");
        }
        this.pollDelay = pollDelay;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "brix-outbox-relay");
            thread.setDaemon(false);
            return thread;
        });
        task = executor.scheduleWithFixedDelay(
            this::process,
            0L,
            pollDelay.toMillis(),
            TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        relay.drain();
        if (task != null) {
            task.cancel(false);
        }
        if (executor != null) {
            executor.shutdown();
            try {
                long timeoutMillis = Math.max(1000L, pollDelay.toMillis());
                if (!executor.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Reports whether the resource has no latest polling failure.
     *
     * @return true when running without latest failure
     */
    public boolean isReady() {
        return isRunning() && lastFailure == null;
    }

    /**
     * Returns the latest polling failure.
     *
     * @return latest failure or {@code null}
     */
    public RuntimeException getLastFailure() {
        return lastFailure;
    }

    private void process() {
        try {
            relay.processDueBatch();
            lastFailure = null;
        } catch (RuntimeException ex) {
            lastFailure = ex;
            log.error("Outbox relay polling pass failed", ex);
        }
    }
}
