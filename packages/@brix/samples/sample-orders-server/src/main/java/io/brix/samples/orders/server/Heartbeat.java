/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.server;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.plugin.ManagedTask;
import io.runtime.sdk.plugin.TaskInvocation;

/**
 * The task {@code sample-orders.heartbeat} (standard-link plan L6.7): it shows how the runtime runs a declared task.
 * The scheduler starts it at each trigger, on one host of all that run sample-orders, which claims the trigger in
 * {@code sample_orders_task_run}; the run gets its trigger, deadline and cancellation signal. It touches no data, so it
 * declares {@code transaction: none}. It remembers the triggers it ran, which the end-to-end test reads.
 */
public final class Heartbeat implements ManagedTask {

    private static final Logger log = LoggerFactory.getLogger(Heartbeat.class);

    private final List<String> triggers = new CopyOnWriteArrayList<>();

    @Override
    public CompletionStage<Void> execute(TaskInvocation invocation) {
        triggers.add(invocation.triggerId());
        log.info("event=sample.heartbeat trigger={} attempt={}", invocation.triggerId(), invocation.attempt());
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Returns the triggers this task ran on this host, in order.
     *
     * @return trigger ids
     */
    public List<String> triggers() {
        return List.copyOf(triggers);
    }
}
