/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.external;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * The platform's policies for calls to external services (root BRX-RS-MANIFEST-026, RESILIENCE-001; standard-link
 * plan L8.2, L8.4). A declared service names one of each; a name the runtime does not know stops the plugin at
 * startup.
 * <ul>
 *   <li>auth {@value #AUTH_NONE} (or blank): the runtime adds no credentials;</li>
 *   <li>timeout {@value #TIMEOUT_DEFAULT}: the host's external call timeout, 5 seconds unless the host sets another;
 *   a call made while handling another call also ends at that call's deadline;</li>
 *   <li>retry {@value #RETRY_NONE}, or {@value #RETRY_IDEMPOTENT}: a {@code GET}, {@code HEAD}, {@code PUT} or
 *   {@code DELETE} that fails or answers 502, 503 or 504 is sent again, up to the host's attempts, after a backoff
 *   that doubles, while the deadline allows;</li>
 *   <li>concurrency {@value #CONCURRENCY_STANDARD}: a plugin makes at most the host's limit of calls to a service at
 *   once; one more fails at once with {@code external_http.rejected}.</li>
 * </ul>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class ExternalHttpPolicies {

    /** No credentials. */
    public static final String AUTH_NONE = "none";

    /** The host's external call timeout. */
    public static final String TIMEOUT_DEFAULT = "external-default";

    /** A failed call is not sent again. */
    public static final String RETRY_NONE = "external-no-retry";

    /** A failed idempotent call is sent again. */
    public static final String RETRY_IDEMPOTENT = "external-idempotent-retry";

    /** The host's limit of calls at once, per plugin and service. */
    public static final String CONCURRENCY_STANDARD = "external-standard";

    private static final Set<String> RETRIES = Set.of(RETRY_NONE, RETRY_IDEMPOTENT);

    private ExternalHttpPolicies() {
    }

    /**
     * Checks a declared service's policies.
     *
     * @param authPolicyRef the declared auth policy, blank for {@value #AUTH_NONE}
     * @param timeoutPolicyRef the declared timeout policy
     * @param retryPolicyRef the declared retry policy
     * @param concurrencyPolicyRef the declared concurrency policy
     * @throws IllegalArgumentException when the runtime does not know one of them
     */
    public static void check(String authPolicyRef, String timeoutPolicyRef, String retryPolicyRef,
            String concurrencyPolicyRef) {
        if (authPolicyRef != null && !authPolicyRef.isBlank() && !AUTH_NONE.equals(authPolicyRef)) {
            throw new IllegalArgumentException("unknown external auth policy '" + authPolicyRef + "'; this release knows "
                + AUTH_NONE + " only");
        }
        require(TIMEOUT_DEFAULT.equals(timeoutPolicyRef), "timeout", timeoutPolicyRef, Set.of(TIMEOUT_DEFAULT));
        require(RETRIES.contains(retryPolicyRef), "retry", retryPolicyRef, RETRIES);
        require(CONCURRENCY_STANDARD.equals(concurrencyPolicyRef), "concurrency", concurrencyPolicyRef,
            Set.of(CONCURRENCY_STANDARD));
    }

    private static void require(boolean known, String kind, String value, Set<String> names) {
        if (!known) {
            throw new IllegalArgumentException("unknown external " + kind + " policy '" + value + "'; the runtime knows "
                + names);
        }
    }

    /**
     * The host's limits on external calls ({@code brix.external-http.*}).
     *
     * @param timeout the deadline of {@value #TIMEOUT_DEFAULT}
     * @param concurrency the calls at once of {@value #CONCURRENCY_STANDARD}, per plugin and service
     * @param attempts the attempts of {@value #RETRY_IDEMPOTENT}
     * @param backoff the wait before the second attempt; it doubles for each later one
     * @param maxResponseBytes the largest answer body taken; a larger one fails the call
     */
    public record Limits(Duration timeout, int concurrency, int attempts, Duration backoff, long maxResponseBytes) {

        /**
         * Checks the limits.
         */
        public Limits {
            Objects.requireNonNull(timeout, "timeout must not be null");
            Objects.requireNonNull(backoff, "backoff must not be null");
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("timeout must be positive");
            }
            if (backoff.isNegative()) {
                throw new IllegalArgumentException("backoff must not be negative");
            }
            if (concurrency < 1 || attempts < 1 || maxResponseBytes < 1) {
                throw new IllegalArgumentException("concurrency, attempts and maxResponseBytes must be at least 1");
            }
        }

        /**
         * Returns the defaults of {@code brix.external-http.*}.
         *
         * @return 5 s, 16 calls at once, 3 attempts 100 ms apart, answers up to 1 MiB
         */
        public static Limits defaults() {
            return new Limits(Duration.ofSeconds(5), 16, 3, Duration.ofMillis(100), 1024 * 1024);
        }
    }
}
