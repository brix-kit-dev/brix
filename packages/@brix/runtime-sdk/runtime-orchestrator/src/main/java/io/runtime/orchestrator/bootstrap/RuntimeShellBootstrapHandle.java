/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.bootstrap;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * One-shot Host bootstrap result and bounded shutdown handle.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class RuntimeShellBootstrapHandle implements AutoCloseable {

    private final BooleanSupplier readiness;
    private final Runnable shutdown;
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private final CompletableFuture<FatalReason> fatal = new CompletableFuture<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    RuntimeShellBootstrapHandle(BooleanSupplier readiness, Runnable shutdown) {
        this.readiness = Objects.requireNonNull(readiness, "readiness must not be null");
        this.shutdown = Objects.requireNonNull(shutdown, "shutdown must not be null");
    }

    /**
     * Returns current derived Host readiness.
     *
     * @return readiness
     */
    public boolean ready() {
        return !fatal.isDone() && readiness.getAsBoolean();
    }

    /**
     * Completes once when the initial Host reaches Ready.
     *
     * @return ready completion
     */
    public CompletionStage<Void> readyFuture() {
        return ready;
    }

    /**
     * Completes once when a required module reaches terminal failure.
     *
     * @return fatal completion
     */
    public CompletionStage<FatalReason> fatalFuture() {
        return fatal;
    }

    /**
     * Performs idempotent bounded shutdown.
     */
    public void shutdown() {
        if (closed.compareAndSet(false, true)) {
            shutdown.run();
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    void completeReady() {
        ready.complete(null);
    }

    void completeStartupFailure(Throwable cause) {
        ready.completeExceptionally(Objects.requireNonNull(cause, "cause must not be null"));
        completeFatal(new FatalReason("runtime.bootstrap_failed", "host-bootstrap"));
    }

    boolean completeFatal(FatalReason reason) {
        return fatal.complete(Objects.requireNonNull(reason, "reason must not be null"));
    }

    /**
     * Stable fatal result reported to an embedding owner or process wrapper.
     *
     * @param errorCode stable fatal error code
     * @param moduleId failed required module identifier
     */
    public record FatalReason(String errorCode, String moduleId) {

        /**
         * Validates fatal fields.
         */
        public FatalReason {
            if (errorCode == null || errorCode.isBlank()) {
                throw new IllegalArgumentException("errorCode must not be blank");
            }
            if (moduleId == null || moduleId.isBlank()) {
                throw new IllegalArgumentException("moduleId must not be blank");
            }
        }
    }
}
