/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.query;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Host Profile settings of typed queries, {@code brix.query.*} (root BRX-RS-MESSAGE-005; query design §5.3).
 *
 * <p>{@code max-deadline} has no default on purpose: the Host Profile states the platform's hard cap, and a host
 * whose plugins provide or call queries does not start without it. The other settings default to query design
 * §5.3 and may only be shorter than the cap.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@ConfigurationProperties(prefix = "brix.query")
public class QueryProperties {

    private Duration maxDeadline;
    private Duration interactiveTimeout;
    private Duration backgroundTimeout;
    private Integer standardConcurrency;
    private Integer maxDepth;
    private Duration retryBackoff;
    private final Breaker breaker = new Breaker();

    /**
     * Returns the settings these properties describe.
     *
     * @return the settings, or {@code null} when {@code max-deadline} is not set
     */
    public QuerySettings toSettings() {
        if (maxDeadline == null) {
            return null;
        }
        QuerySettings defaults = QuerySettings.defaults(maxDeadline);
        QuerySettings.Breaker defaultBreaker = defaults.breaker();
        return new QuerySettings(
            maxDeadline,
            interactiveTimeout != null ? interactiveTimeout : defaults.interactiveTimeout(),
            backgroundTimeout != null ? backgroundTimeout : defaults.backgroundTimeout(),
            standardConcurrency != null ? standardConcurrency : defaults.standardConcurrency(),
            maxDepth != null ? maxDepth : defaults.maxDepth(),
            new QuerySettings.Breaker(
                breaker.window != null ? breaker.window : defaultBreaker.window(),
                breaker.minimumCalls != null ? breaker.minimumCalls : defaultBreaker.minimumCalls(),
                breaker.failureRatePercent != null ? breaker.failureRatePercent : defaultBreaker.failureRatePercent(),
                breaker.openFor != null ? breaker.openFor : defaultBreaker.openFor(),
                breaker.halfOpenCalls != null ? breaker.halfOpenCalls : defaultBreaker.halfOpenCalls()),
            retryBackoff != null ? retryBackoff : defaults.retryBackoff());
    }

    /** @return the hard cap on a query's deadline */
    public Duration getMaxDeadline() {
        return maxDeadline;
    }

    /** @param maxDeadline the hard cap on a query's deadline */
    public void setMaxDeadline(Duration maxDeadline) {
        this.maxDeadline = maxDeadline;
    }

    /** @return the timeout of policy query-interactive */
    public Duration getInteractiveTimeout() {
        return interactiveTimeout;
    }

    /** @param interactiveTimeout the timeout of policy query-interactive */
    public void setInteractiveTimeout(Duration interactiveTimeout) {
        this.interactiveTimeout = interactiveTimeout;
    }

    /** @return the timeout of policy query-background */
    public Duration getBackgroundTimeout() {
        return backgroundTimeout;
    }

    /** @param backgroundTimeout the timeout of policy query-background */
    public void setBackgroundTimeout(Duration backgroundTimeout) {
        this.backgroundTimeout = backgroundTimeout;
    }

    /** @return the concurrency of policy query-standard */
    public Integer getStandardConcurrency() {
        return standardConcurrency;
    }

    /** @param standardConcurrency the concurrency of policy query-standard */
    public void setStandardConcurrency(Integer standardConcurrency) {
        this.standardConcurrency = standardConcurrency;
    }

    /** @return how deeply queries may call queries */
    public Integer getMaxDepth() {
        return maxDepth;
    }

    /** @param maxDepth how deeply queries may call queries */
    public void setMaxDepth(Integer maxDepth) {
        this.maxDepth = maxDepth;
    }

    /** @return the base backoff of policy query-transient-retry */
    public Duration getRetryBackoff() {
        return retryBackoff;
    }

    /** @param retryBackoff the base backoff of policy query-transient-retry */
    public void setRetryBackoff(Duration retryBackoff) {
        this.retryBackoff = retryBackoff;
    }

    /** @return the circuit breaker settings */
    public Breaker getBreaker() {
        return breaker;
    }

    /**
     * The circuit breaker of each caller and query, {@code brix.query.breaker.*}.
     */
    public static class Breaker {

        private Integer window;
        private Integer minimumCalls;
        private Float failureRatePercent;
        private Duration openFor;
        private Integer halfOpenCalls;

        /** @return how many recent calls count */
        public Integer getWindow() {
            return window;
        }

        /** @param window how many recent calls count */
        public void setWindow(Integer window) {
            this.window = window;
        }

        /** @return how many calls must be seen before the breaker may open */
        public Integer getMinimumCalls() {
            return minimumCalls;
        }

        /** @param minimumCalls how many calls must be seen before the breaker may open */
        public void setMinimumCalls(Integer minimumCalls) {
            this.minimumCalls = minimumCalls;
        }

        /** @return the failure rate that opens the breaker */
        public Float getFailureRatePercent() {
            return failureRatePercent;
        }

        /** @param failureRatePercent the failure rate that opens the breaker */
        public void setFailureRatePercent(Float failureRatePercent) {
            this.failureRatePercent = failureRatePercent;
        }

        /** @return how long the breaker stays open */
        public Duration getOpenFor() {
            return openFor;
        }

        /** @param openFor how long the breaker stays open */
        public void setOpenFor(Duration openFor) {
            this.openFor = openFor;
        }

        /** @return how many trial calls a half-open breaker lets through */
        public Integer getHalfOpenCalls() {
            return halfOpenCalls;
        }

        /** @param halfOpenCalls how many trial calls a half-open breaker lets through */
        public void setHalfOpenCalls(Integer halfOpenCalls) {
            this.halfOpenCalls = halfOpenCalls;
        }
    }
}
