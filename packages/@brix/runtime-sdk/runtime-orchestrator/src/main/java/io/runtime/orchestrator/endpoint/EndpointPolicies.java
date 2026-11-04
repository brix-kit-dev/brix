/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * The platform's deadline and isolation policies for HTTP endpoints (root BRX-RS-MANIFEST-018, MANIFEST-022,
 * RESILIENCE-001; standard-link plan L7.5, D60). An endpoint names its deadline in {@code timeout.policyRef}; a name
 * the runtime does not know stops the module at startup.
 * <ul>
 *   <li>{@value #TIMEOUT_DEFAULT}: the host's endpoint deadline, which also caps the other two;</li>
 *   <li>{@value #TIMEOUT_INTERACTIVE_READ}: a read a person waits for, 10 seconds unless the host sets another;</li>
 *   <li>{@value #TIMEOUT_INTERACTIVE_WRITE}: a write a person waits for, 15 seconds unless the host sets another.</li>
 * </ul>
 * <p>Each endpoint runs at most {@link Limits#concurrency()} calls at once, 64 unless the host sets another; one more
 * is answered 503 {@code endpoint.rejected} at once. Quotas per tenant or per caller belong to the entry gateway.</p>
 * <p>An endpoint whose results the runtime replays may name how long they are kept in
 * {@code idempotency.retentionPolicyRef}: {@value #RETENTION_DEFAULT}, the default and for now the only name (standard-link
 * plan point 10a, idempotency design D73). A name the runtime does not know stops the module at startup.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class EndpointPolicies {

    /** The host's endpoint deadline. */
    public static final String TIMEOUT_DEFAULT = "endpoint-default";

    /** A read a person waits for. */
    public static final String TIMEOUT_INTERACTIVE_READ = "interactive-read";

    /** A write a person waits for. */
    public static final String TIMEOUT_INTERACTIVE_WRITE = "interactive-write";

    /** Keeps an endpoint's result for a day. */
    public static final String RETENTION_DEFAULT = "idempotency-24h";

    private static final Duration RETENTION = Duration.ofHours(24);

    private static final Set<String> TIMEOUTS =
        Set.of(TIMEOUT_DEFAULT, TIMEOUT_INTERACTIVE_READ, TIMEOUT_INTERACTIVE_WRITE);

    private EndpointPolicies() {
    }

    /**
     * Checks a declared timeout policy.
     *
     * @param policyRef the declared {@code timeout.policyRef}, blank for {@value #TIMEOUT_DEFAULT}
     * @return the policy
     * @throws IllegalArgumentException when the runtime does not know it
     */
    public static String timeout(String policyRef) {
        String policy = policyRef == null || policyRef.isBlank() ? TIMEOUT_DEFAULT : policyRef.trim();
        if (!TIMEOUTS.contains(policy)) {
            throw new IllegalArgumentException("unknown endpoint timeout policy '" + policy + "'; the runtime knows "
                + TIMEOUT_DEFAULT + ", " + TIMEOUT_INTERACTIVE_READ + " and " + TIMEOUT_INTERACTIVE_WRITE);
        }
        return policy;
    }

    /**
     * Returns how long a declared retention policy keeps an endpoint's results.
     *
     * @param policyRef the declared {@code idempotency.retentionPolicyRef}, blank for {@value #RETENTION_DEFAULT}
     * @return how long
     * @throws IllegalArgumentException when the runtime does not know it
     */
    public static Duration retention(String policyRef) {
        String policy = policyRef == null || policyRef.isBlank() ? RETENTION_DEFAULT : policyRef.trim();
        if (!RETENTION_DEFAULT.equals(policy)) {
            throw new IllegalArgumentException("unknown endpoint retention policy '" + policy + "'; the runtime knows "
                + RETENTION_DEFAULT);
        }
        return RETENTION;
    }

    /**
     * The host's limits on endpoints.
     *
     * @param maxDeadline the deadline of {@value #TIMEOUT_DEFAULT}, and the cap on the others
     * @param interactiveRead the deadline of {@value #TIMEOUT_INTERACTIVE_READ}
     * @param interactiveWrite the deadline of {@value #TIMEOUT_INTERACTIVE_WRITE}
     * @param concurrency how many calls each endpoint runs at once
     */
    public record Limits(Duration maxDeadline, Duration interactiveRead, Duration interactiveWrite, int concurrency) {

        /**
         * Checks the limits.
         */
        public Limits {
            requirePositive(maxDeadline, "maxDeadline");
            requirePositive(interactiveRead, "interactiveRead");
            requirePositive(interactiveWrite, "interactiveWrite");
            requireWithin(interactiveRead, maxDeadline, "interactiveRead");
            requireWithin(interactiveWrite, maxDeadline, "interactiveWrite");
            if (concurrency < 1) {
                throw new IllegalArgumentException("concurrency must be at least 1");
            }
        }

        /**
         * Returns the defaults under the host's endpoint deadline.
         *
         * @param maxDeadline the host's endpoint deadline
         * @return interactive reads 10 s and writes 15 s, each no longer than the deadline, and 64 calls at once
         */
        public static Limits defaults(Duration maxDeadline) {
            requirePositive(maxDeadline, "maxDeadline");
            return new Limits(maxDeadline, atMost(Duration.ofSeconds(10), maxDeadline),
                atMost(Duration.ofSeconds(15), maxDeadline), 64);
        }

        /**
         * Returns the deadline of a policy.
         *
         * @param policy a policy {@link EndpointPolicies#timeout(String)} accepted
         * @return how long a call may take
         */
        public Duration deadline(String policy) {
            return switch (timeout(policy)) {
                case TIMEOUT_INTERACTIVE_READ -> interactiveRead;
                case TIMEOUT_INTERACTIVE_WRITE -> interactiveWrite;
                default -> maxDeadline;
            };
        }

        private static Duration atMost(Duration value, Duration cap) {
            return value.compareTo(cap) <= 0 ? value : cap;
        }

        private static void requirePositive(Duration value, String name) {
            Objects.requireNonNull(value, name + " must not be null");
            if (value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive");
            }
        }

        private static void requireWithin(Duration value, Duration cap, String name) {
            if (value.compareTo(cap) > 0) {
                throw new IllegalArgumentException(name + " must not exceed the endpoint deadline " + cap);
            }
        }
    }
}
