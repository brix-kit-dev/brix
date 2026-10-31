/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.task;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The host's task settings, {@code brix.tasks.*} (standard-link plan L6.3, L6.4).
 */
@ConfigurationProperties(prefix = "brix.tasks")
public class TaskProperties {

    private boolean enabled = true;
    private int threads = 4;
    private Duration minInterval = Duration.ofMinutes(1);
    private Duration timeout = Duration.ofMinutes(5);
    private int attempts = 3;
    private Duration backoff = Duration.ofSeconds(1);
    private Duration drainTimeout = Duration.ofSeconds(30);
    private Duration runRetention = Duration.ofDays(30);
    private Duration retentionInterval = Duration.ofHours(1);
    private String instanceId = "";

    /**
     * Returns the limits plugins' tasks are checked against.
     *
     * @return the limits
     */
    public TaskPolicies.Limits limits() {
        return new TaskPolicies.Limits(minInterval, timeout, attempts, backoff);
    }

    /** @return whether the host runs tasks */
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return how many runs may go on at once */
    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    /** @return the shortest interval a trigger may have */
    public Duration getMinInterval() {
        return minInterval;
    }

    public void setMinInterval(Duration minInterval) {
        this.minInterval = minInterval;
    }

    /** @return the deadline of {@code task-standard} */
    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    /** @return the attempts of {@code task-transient-retry} */
    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    /** @return the wait before a second attempt; it doubles for each later one */
    public Duration getBackoff() {
        return backoff;
    }

    public void setBackoff(Duration backoff) {
        this.backoff = backoff;
    }

    /** @return how long stopping waits for runs in flight before it cancels them */
    public Duration getDrainTimeout() {
        return drainTimeout;
    }

    public void setDrainTimeout(Duration drainTimeout) {
        this.drainTimeout = drainTimeout;
    }

    /** @return how long finished runs stay in the task-run tables */
    public Duration getRunRetention() {
        return runRetention;
    }

    public void setRunRetention(Duration runRetention) {
        this.runRetention = runRetention;
    }

    /** @return how often the task-run tables are cleaned */
    public Duration getRetentionInterval() {
        return retentionInterval;
    }

    public void setRetentionInterval(Duration retentionInterval) {
        this.retentionInterval = retentionInterval;
    }

    /** @return this host's name in the task-run tables; blank for one made at startup */
    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }
}
