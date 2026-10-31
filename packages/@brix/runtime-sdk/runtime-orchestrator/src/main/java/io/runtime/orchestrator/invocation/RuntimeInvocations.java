/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.invocation;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The call the current thread's handler is running in.
 *
 * <p>L2B private carrier (multi-tenant §11.1 allows one): dispatchers bind a {@link RuntimeInvocation} around each
 * handler and restore the previous one afterwards. Plugins cannot reach it; they get their context through the
 * typed invocation their handler receives.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class RuntimeInvocations {

    private static final ThreadLocal<RuntimeInvocation> CURRENT = new ThreadLocal<>();

    private RuntimeInvocations() {
    }

    /**
     * Runs work in a call, restoring the previous call afterwards.
     *
     * @param invocation the call
     * @param work the handler work
     * @param <T> result type
     * @return the work's result
     */
    public static <T> T within(RuntimeInvocation invocation, Supplier<T> work) {
        Objects.requireNonNull(invocation, "invocation must not be null");
        Objects.requireNonNull(work, "work must not be null");
        RuntimeInvocation previous = CURRENT.get();
        CURRENT.set(invocation);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /**
     * Returns the call of the current thread.
     *
     * @return the call, or empty outside any admitted call
     */
    public static Optional<RuntimeInvocation> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * Returns whether the current thread runs a query's provider, where side effects are refused
     * (root BRX-RS-MESSAGE-002).
     *
     * @return {@code true} inside a query
     */
    public static boolean inQuery() {
        RuntimeInvocation current = CURRENT.get();
        return current != null && current.kind() == RuntimeInvocation.Kind.QUERY;
    }
}
