/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.tenant.support;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.runtime.sdk.capability.UnitOfWork;

/**
 * Runs the work directly and records which kind of unit of work was asked for.
 */
public final class RecordingUnitOfWork implements UnitOfWork {

    private final List<String> modes = new ArrayList<>();

    @Override
    public <T> T required(Supplier<T> work) {
        modes.add("required");
        return work.get();
    }

    @Override
    public <T> T readOnly(Supplier<T> work) {
        modes.add("readOnly");
        return work.get();
    }

    public List<String> modes() {
        return List.copyOf(modes);
    }
}
