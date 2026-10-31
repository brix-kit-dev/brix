/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.external;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The host's external call limits, {@code brix.external-http.*} (standard-link plan L8.3, L8.4).
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@ConfigurationProperties(prefix = "brix.external-http")
public class ExternalHttpProperties {

    private Duration timeout = Duration.ofSeconds(5);
    private int concurrency = 16;
    private int attempts = 3;
    private Duration backoff = Duration.ofMillis(100);
    private long maxResponseBytes = 1024 * 1024;
    private List<String> selfAddresses = new ArrayList<>();

    /**
     * Returns the limits.
     *
     * @return the limits these properties describe
     */
    public ExternalHttpPolicies.Limits limits() {
        return new ExternalHttpPolicies.Limits(timeout, concurrency, attempts, backoff, maxResponseBytes);
    }

    /** @return the deadline of {@code external-default} */
    public Duration getTimeout() {
        return timeout;
    }

    /** @param timeout the deadline of {@code external-default} */
    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    /** @return the calls a module makes to a service at once */
    public int getConcurrency() {
        return concurrency;
    }

    /** @param concurrency the calls a module makes to a service at once */
    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    /** @return the attempts of {@code external-idempotent-retry} */
    public int getAttempts() {
        return attempts;
    }

    /** @param attempts the attempts of {@code external-idempotent-retry} */
    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    /** @return the wait before the second attempt */
    public Duration getBackoff() {
        return backoff;
    }

    /** @param backoff the wait before the second attempt */
    public void setBackoff(Duration backoff) {
        this.backoff = backoff;
    }

    /** @return the largest answer body taken */
    public long getMaxResponseBytes() {
        return maxResponseBytes;
    }

    /** @param maxResponseBytes the largest answer body taken */
    public void setMaxResponseBytes(long maxResponseBytes) {
        this.maxResponseBytes = maxResponseBytes;
    }

    /** @return the names and addresses the host is reached at from outside */
    public List<String> getSelfAddresses() {
        return selfAddresses;
    }

    /** @param selfAddresses the names and addresses the host is reached at from outside */
    public void setSelfAddresses(List<String> selfAddresses) {
        this.selfAddresses = selfAddresses != null ? selfAddresses : new ArrayList<>();
    }

    /**
     * The host's directory of external services, {@code brix.external-services.<baseUrlRef>.base-url}.
     */
    @ConfigurationProperties(prefix = "brix")
    public static class Directory {

        private Map<String, Service> externalServices = new LinkedHashMap<>();

        /** @return the services, by {@code baseUrlRef} */
        public Map<String, Service> getExternalServices() {
            return externalServices;
        }

        /** @param externalServices the services, by {@code baseUrlRef} */
        public void setExternalServices(Map<String, Service> externalServices) {
            this.externalServices = externalServices != null ? externalServices : new LinkedHashMap<>();
        }

        /**
         * Returns the configured address of each entry.
         *
         * @return the base URLs, by {@code baseUrlRef}
         */
        public Map<String, String> baseUrls() {
            Map<String, String> urls = new LinkedHashMap<>();
            externalServices.forEach((ref, service) -> urls.put(ref, service == null ? null : service.getBaseUrl()));
            return urls;
        }
    }

    /**
     * One directory entry.
     */
    public static class Service {

        private String baseUrl;

        /** @return the service's address */
        public String getBaseUrl() {
            return baseUrl;
        }

        /** @param baseUrl the service's address */
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}
