/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.persistence.RecordingPersistence;
import io.runtime.sdk.capability.PersistenceCapability;

/**
 * Standard-link plan point 3 (root BRX-RS-DATA-011, DATA-028): a delivery's unit of work lasts no longer than the time
 * left before its deadline, and a handler that finishes after the deadline fails the delivery before the commit.
 */
class DeliveryDeadlineTest {

    private static final Instant NOW = Instant.parse("2026-11-02T08:00:00Z");

    private final List<Duration> timeouts = new ArrayList<>();
    private final RecordingPersistence persistence = new RecordingPersistence();
    private final OwnerPersistenceProvider owners = new OwnerPersistenceProvider() {
        @Override
        public Optional<PersistenceCapability> forOwner(String ownerModuleId) {
            return Optional.of(persistence);
        }

        @Override
        public <T> T readWrite(String ownerModuleId, Duration timeout, Supplier<T> work) {
            timeouts.add(timeout);
            return persistence.required(work);
        }
    };

    @Test
    void theUnitOfWorkLastsNoLongerThanTheTimeLeftBeforeTheDeadline() {
        String result = DeliveryDeadline.inUnitOfWork(owners, "app-stock", Clock.fixed(NOW, ZoneOffset.UTC),
            NOW.plusSeconds(15), () -> persistence.active() ? "in its unit of work" : "outside");

        assertEquals("in its unit of work", result);
        assertEquals(List.of(Duration.ofSeconds(15)), timeouts);
    }

    @Test
    void noUnitOfWorkBeginsOnceTheDeadlineHasPassed() {
        IllegalStateException late = assertThrows(IllegalStateException.class, () -> DeliveryDeadline.inUnitOfWork(
            owners, "app-stock", Clock.fixed(NOW, ZoneOffset.UTC), NOW, () -> "ran"));

        assertTrue(late.getMessage().contains("passed before the handler's unit of work began"), late.getMessage());
        assertTrue(timeouts.isEmpty());
        assertTrue(persistence.modes().isEmpty());
    }

    @Test
    void aHandlerThatFinishesByItsDeadlinePasses() {
        AtomicBoolean cancelled = new AtomicBoolean();

        DeliveryDeadline.await(CompletableFuture.completedFuture(null), Clock.fixed(NOW, ZoneOffset.UTC),
            NOW.plusSeconds(15), cancelled);

        assertFalse(cancelled.get());
    }

    @Test
    void aSynchronousHandlerThatFinishesAfterItsDeadlineFailsAndIsToldSo() {
        // It did its work before it returned, so its stage is already complete; the clock says it took too long.
        MutableClock clock = new MutableClock(NOW);
        Instant deadline = NOW.plusSeconds(15);
        AtomicBoolean cancelled = new AtomicBoolean();
        clock.advance(Duration.ofSeconds(16));

        IllegalStateException late = assertThrows(IllegalStateException.class,
            () -> DeliveryDeadline.await(CompletableFuture.completedFuture(null), clock, deadline, cancelled));

        assertTrue(late.getMessage().contains("finished after its deadline"), late.getMessage());
        assertTrue(cancelled.get(), "the invocation's cancellation signal is set");
    }

    @Test
    void aHandlerStillRunningAtItsDeadlineIsCancelled() {
        CompletableFuture<Void> running = new CompletableFuture<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        Clock clock = Clock.systemUTC();

        IllegalStateException late = assertThrows(IllegalStateException.class,
            () -> DeliveryDeadline.await(running, clock, clock.instant().plusMillis(50), cancelled));

        assertTrue(late.getMessage().contains("did not finish by its deadline"), late.getMessage());
        assertTrue(cancelled.get());
        assertTrue(running.isCancelled());
    }

    @Test
    void aHandlerThatFailsByItsDeadlineFailsWithItsOwnException() {
        IllegalArgumentException refused = new IllegalArgumentException("stock service refused");
        AtomicBoolean cancelled = new AtomicBoolean();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> DeliveryDeadline.await(
            CompletableFuture.failedFuture(refused), Clock.fixed(NOW, ZoneOffset.UTC), NOW.plusSeconds(15), cancelled));

        assertEquals(refused, thrown);
        assertFalse(cancelled.get());
    }

    /** A clock the test moves forward, as time passes while a synchronous handler runs. */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

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
