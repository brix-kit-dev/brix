/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.UnitOfWork;

/**
 * Test double that records which unit-of-work mode wrapped each call and whether a call ran inside
 * a unit of work.
 */
public final class RecordingPersistence implements PersistenceCapability, UnitOfWork {

    private final List<String> modes = new ArrayList<>();
    private int depth;

    @Override
    public <R> R repository(Class<R> repositoryType) {
        throw new UnsupportedOperationException("repositories are not used by runtime tests");
    }

    @Override
    public UnitOfWork unitOfWork() {
        return this;
    }

    @Override
    public <T> T required(Supplier<T> work) {
        return run("required", work);
    }

    @Override
    public <T> T readOnly(Supplier<T> work) {
        return run("readOnly", work);
    }

    /**
     * Returns the recorded unit-of-work modes in call order.
     *
     * @return recorded modes
     */
    public List<String> modes() {
        return List.copyOf(modes);
    }

    /**
     * Returns whether the current call runs inside a unit of work.
     *
     * @return true inside a unit of work
     */
    public boolean active() {
        return depth > 0;
    }

    private <T> T run(String mode, Supplier<T> work) {
        modes.add(mode);
        depth++;
        try {
            return work.get();
        } finally {
            depth--;
        }
    }
}
