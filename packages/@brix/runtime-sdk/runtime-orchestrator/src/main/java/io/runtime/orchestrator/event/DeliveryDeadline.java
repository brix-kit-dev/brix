/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;

/**
 * Binds an event or command delivery to its deadline (root BRX-RS-DATA-011, DATA-028, RESILIENCE-001; standard-link
 * plan point 3).
 *
 * <p>The owner's unit of work lasts no longer than the time left before the deadline, so its statements stop, and
 * its waits for a pooled connection end, when the deadline passes. A handler that has not finished by then is told
 * through its cancellation signal; so is one that finishes after it, as a synchronous handler that ran past it does.
 * Either way the delivery fails before the commit: the unit of work rolls back, the attempt counts, and the message is
 * tried again.</p>
 */
public final class DeliveryDeadline {

    private DeliveryDeadline() {
    }

    /**
     * Runs a delivery's work in the owner's read-write unit of work, which lasts no longer than the time left before
     * the deadline. An owner without persistence runs the work as it is.
     *
     * @param owners the owners' persistence
     * @param ownerId the subscriber or provider that handles the message
     * @param clock the delivery clock
     * @param deadline the invocation's deadline
     * @param work the inbox record and the handler
     * @param <T> result type
     * @return the work's result
     * @throws IllegalStateException when the deadline has passed already: no unit of work begins
     */
    public static <T> T inUnitOfWork(OwnerPersistenceProvider owners, String ownerId, Clock clock, Instant deadline,
            Supplier<T> work) {
        Duration left = Duration.between(clock.instant(), deadline);
        if (left.isZero() || left.isNegative()) {
            throw new IllegalStateException(
                "the deadline " + deadline + " passed before the handler's unit of work began");
        }
        return owners.readWrite(ownerId, left, work);
    }

    /**
     * Waits for the handler until the deadline (Kafka design §14 K10). A handler still running then, or one that
     * finished after it, is told through its cancellation signal and fails the delivery.
     *
     * @param handled what the handler returned
     * @param clock the delivery clock
     * @param deadline the invocation's deadline
     * @param cancelled the invocation's cancellation signal
     */
    public static void await(CompletionStage<Void> handled, Clock clock, Instant deadline, AtomicBoolean cancelled) {
        CompletableFuture<Void> future = handled.toCompletableFuture();
        long wait = Math.max(0L, Duration.between(clock.instant(), deadline).toMillis());
        try {
            future.get(wait, TimeUnit.MILLISECONDS);
        } catch (TimeoutException late) {
            cancelled.set(true);
            future.cancel(true);
            throw new IllegalStateException("the handler did not finish by its deadline " + deadline, late);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            cancelled.set(true);
            throw new IllegalStateException("the delivery was interrupted", interrupted);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw new CompletionException(failure.getCause());
        }
        if (clock.instant().isAfter(deadline)) {
            // A synchronous handler ran past the deadline before it returned: its work must not commit.
            cancelled.set(true);
            throw new IllegalStateException("the handler finished after its deadline " + deadline);
        }
    }
}
