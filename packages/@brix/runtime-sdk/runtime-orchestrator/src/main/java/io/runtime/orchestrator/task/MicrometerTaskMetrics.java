/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.task;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * The scheduler's meters (standard-link plan L6.6), labelled by {@code module} and {@code task}:
 * <ul>
 *   <li>{@value #RUNS}: runs and how long they took, by {@code outcome};</li>
 *   <li>{@value #SKIPPED}: triggers this host did not run, by {@code reason};</li>
 *   <li>{@value #RUNNING}: runs going on now;</li>
 *   <li>{@value #LAST_SUCCESS}: when a run last succeeded here, in seconds since the epoch; the publication until
 *   then;</li>
 *   <li>{@value #INTERVAL}: the shortest interval of the trigger, in seconds;</li>
 *   <li>{@value #CONSECUTIVE_FAILURES}: runs that failed here since the last success.</li>
 * </ul>
 */
public final class MicrometerTaskMetrics implements TaskMetrics {

    /** Runs, by outcome. */
    public static final String RUNS = "brix.task.runs";
    /** Triggers not run here, by reason. */
    public static final String SKIPPED = "brix.task.skipped";
    /** Runs going on. */
    public static final String RUNNING = "brix.task.running";
    /** When a run last succeeded here. */
    public static final String LAST_SUCCESS = "brix.task.last.success";
    /** The trigger's shortest interval. */
    public static final String INTERVAL = "brix.task.interval";
    /** Failed runs since the last success. */
    public static final String CONSECUTIVE_FAILURES = "brix.task.consecutive.failures";

    private final Supplier<MeterRegistry> registry;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    /**
     * Creates the meters.
     *
     * @param registry supplies the meter registry; records nothing while it supplies {@code null}
     */
    public MicrometerTaskMetrics(Supplier<MeterRegistry> registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public void published(String moduleId, String taskId, Duration interval, Instant at) {
        State state = state(moduleId, taskId);
        state.interval.set(interval.getSeconds());
        state.lastSuccess.compareAndSet(0, at.getEpochSecond());
    }

    @Override
    public void started(String moduleId, String taskId) {
        state(moduleId, taskId).running.incrementAndGet();
    }

    @Override
    public void finished(String moduleId, String taskId, String outcome, Duration took, Instant at) {
        State state = state(moduleId, taskId);
        state.running.decrementAndGet();
        if ("succeeded".equals(outcome)) {
            state.lastSuccess.set(at.getEpochSecond());
            state.consecutiveFailures.set(0);
        } else if ("failed".equals(outcome)) {
            state.consecutiveFailures.incrementAndGet();
        }
        MeterRegistry meters = registry.get();
        if (meters != null) {
            Timer.builder(RUNS).description("Task runs and how long they took")
                .tag("module", moduleId).tag("task", taskId).tag("outcome", outcome)
                .register(meters).record(took);
        }
    }

    @Override
    public void skipped(String moduleId, String taskId, String reason) {
        state(moduleId, taskId);
        MeterRegistry meters = registry.get();
        if (meters != null) {
            Counter.builder(SKIPPED).description("Task triggers this host did not run")
                .tag("module", moduleId).tag("task", taskId).tag("reason", reason)
                .register(meters).increment();
        }
    }

    private State state(String moduleId, String taskId) {
        State state = states.computeIfAbsent(moduleId + "/" + taskId, ignored -> new State());
        MeterRegistry meters = registry.get();
        if (meters != null && !state.registered) {
            synchronized (state) {
                if (!state.registered) {
                    gauge(meters, RUNNING, "Task runs going on", moduleId, taskId, state.running, null);
                    gauge(meters, LAST_SUCCESS, "When a task run last succeeded on this host", moduleId, taskId,
                        state.lastSuccess, "seconds");
                    gauge(meters, INTERVAL, "The shortest interval of a task's trigger", moduleId, taskId,
                        state.interval, "seconds");
                    gauge(meters, CONSECUTIVE_FAILURES, "Task runs that failed on this host since the last success",
                        moduleId, taskId, state.consecutiveFailures, null);
                    state.registered = true;
                }
            }
        }
        return state;
    }

    private static void gauge(MeterRegistry meters, String name, String description, String moduleId, String taskId,
            Number value, String unit) {
        Gauge.Builder<Number> builder = Gauge.builder(name, value, Number::doubleValue).description(description)
            .tag("module", moduleId).tag("task", taskId);
        if (unit != null) {
            builder.baseUnit(unit);
        }
        builder.register(meters);
    }

    private static final class State {
        final AtomicInteger running = new AtomicInteger();
        final AtomicLong lastSuccess = new AtomicLong();
        final AtomicLong interval = new AtomicLong();
        final AtomicInteger consecutiveFailures = new AtomicInteger();
        volatile boolean registered;
    }
}
