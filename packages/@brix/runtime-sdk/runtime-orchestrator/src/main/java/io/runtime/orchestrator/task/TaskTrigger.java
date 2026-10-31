/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.task;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;

import org.springframework.scheduling.support.CronExpression;

/**
 * When a task is due (root BRX-RS-MANIFEST-025; standard-link plan L6.3). Every host computes the same due instants,
 * so a due instant identifies the trigger on all of them.
 *
 * <ul>
 *   <li>{@code every:<ISO-8601 duration>}, such as {@code every:PT15M}: at every multiple of the duration since
 *   1970-01-01T00:00Z, in whole seconds;</li>
 *   <li>{@code cron:<expression>}, such as {@code cron:0 0 3 * * *}: the six fields of Spring's cron expressions
 *   (second, minute, hour, day of month, month, day of week), in UTC.</li>
 * </ul>
 */
public sealed interface TaskTrigger {

    /** Prefix of a fixed-rate trigger. */
    String EVERY = "every:";

    /** Prefix of a cron trigger. */
    String CRON = "cron:";

    /**
     * Returns the first due instant after {@code after}.
     *
     * @param after an instant
     * @return the next due instant, strictly later
     */
    Instant next(Instant after);

    /**
     * Returns the shortest time between two due instants, which the host's minimum interval bounds.
     *
     * @return the shortest interval
     */
    Duration shortestInterval();

    /**
     * Returns the manifest's reference.
     *
     * @return the {@code triggerPolicyRef}
     */
    String reference();

    /**
     * Resolves a trigger policy reference.
     *
     * @param reference the manifest's {@code triggerPolicyRef}
     * @return the trigger
     * @throws IllegalArgumentException for an unknown or malformed reference
     */
    static TaskTrigger parse(String reference) {
        Objects.requireNonNull(reference, "reference must not be null");
        if (reference.startsWith(EVERY)) {
            return Every.of(reference);
        }
        if (reference.startsWith(CRON)) {
            return Cron.of(reference);
        }
        throw new IllegalArgumentException("unknown trigger policy '" + reference + "': expected "
            + EVERY + "<ISO-8601 duration> or " + CRON + "<second minute hour day month weekday>");
    }

    /**
     * Due at every multiple of a period since the epoch.
     *
     * @param reference the manifest's reference
     * @param period the period, whole seconds
     */
    record Every(String reference, Duration period) implements TaskTrigger {

        static Every of(String reference) {
            Duration period;
            try {
                period = Duration.parse(reference.substring(EVERY.length()));
            } catch (DateTimeParseException malformed) {
                throw new IllegalArgumentException("trigger '" + reference + "' does not name an ISO-8601 duration, "
                    + "such as every:PT15M", malformed);
            }
            if (period.compareTo(Duration.ofSeconds(1)) < 0 || period.getNano() != 0) {
                throw new IllegalArgumentException("trigger '" + reference + "' needs a period of whole seconds");
            }
            return new Every(reference, period);
        }

        @Override
        public Instant next(Instant after) {
            long period = this.period.toMillis();
            return Instant.ofEpochMilli(Math.floorDiv(after.toEpochMilli(), period) * period + period);
        }

        @Override
        public Duration shortestInterval() {
            return period;
        }
    }

    /**
     * Due at the instants a cron expression matches, in UTC.
     *
     * @param reference the manifest's reference
     * @param expression the expression
     * @param shortestInterval the shortest time between two matches
     */
    record Cron(String reference, CronExpression expression, Duration shortestInterval) implements TaskTrigger {

        /** Matches compared to find the shortest interval, from a fixed instant so every host finds the same. */
        private static final int SAMPLES = 400;
        private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");

        static Cron of(String reference) {
            CronExpression expression;
            try {
                expression = CronExpression.parse(reference.substring(CRON.length()).trim());
            } catch (IllegalArgumentException malformed) {
                throw new IllegalArgumentException("trigger '" + reference + "' is not a cron expression of six fields "
                    + "(second minute hour day month weekday): " + malformed.getMessage(), malformed);
            }
            Duration shortest = null;
            ZonedDateTime previous = expression.next(FROM.atZone(ZoneOffset.UTC));
            if (previous == null) {
                throw new IllegalArgumentException("trigger '" + reference + "' is never due");
            }
            for (int i = 0; i < SAMPLES; i++) {
                ZonedDateTime following = expression.next(previous);
                if (following == null) {
                    break;
                }
                Duration gap = Duration.between(previous, following);
                shortest = shortest == null || gap.compareTo(shortest) < 0 ? gap : shortest;
                previous = following;
            }
            return new Cron(reference, expression, shortest == null ? Duration.ofDays(365) : shortest);
        }

        @Override
        public Instant next(Instant after) {
            ZonedDateTime next = expression.next(after.atZone(ZoneOffset.UTC));
            if (next == null) {
                throw new IllegalStateException("trigger '" + reference + "' is no longer due");
            }
            return next.toInstant();
        }
    }
}
