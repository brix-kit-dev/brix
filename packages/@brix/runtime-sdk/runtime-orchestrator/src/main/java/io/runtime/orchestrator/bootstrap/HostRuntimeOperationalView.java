/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.bootstrap;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import io.runtime.orchestrator.operational.RuntimeOperationalView;

/**
 * Read-only operational projection of Host bootstrap state.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class HostRuntimeOperationalView implements RuntimeOperationalView {

    private final AtomicLong entryGeneration = new AtomicLong();
    private final AtomicBoolean ready = new AtomicBoolean();
    private final Set<String> requiredModuleIds;

    /**
     * Creates a Host Runtime view.
     *
     * @param requiredModuleIds required Plugin and Operational module ids
     */
    public HostRuntimeOperationalView(Collection<String> requiredModuleIds) {
        this.requiredModuleIds = requiredModuleIds == null
            ? Set.of()
            : requiredModuleIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public long entryGeneration() {
        return entryGeneration.get();
    }

    @Override
    public boolean ready() {
        return ready.get();
    }

    @Override
    public Set<String> requiredModuleIds() {
        return requiredModuleIds;
    }

    void published() {
        entryGeneration.incrementAndGet();
    }

    void ready(boolean value) {
        ready.set(value);
    }
}
