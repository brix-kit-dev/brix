/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A task the runtime's scheduler runs, as {@code tasks.provides} declares it (root BRX-RS-MANIFEST-025, MANIFEST-027;
 * standard-link plan L6). The runtime runs it in its module's unit of work, cancels it at its deadline and records
 * how it ended; a run ends when the returned stage completes.
 *
 * <p>Implement {@link #execute(TaskInvocation)}. A task written before 3.3.0 implements {@link #run()}; the runtime
 * refuses, at startup, a task that implements neither.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface ManagedTask {

    /**
     * Executes one run.
     *
     * @param invocation the run: its trigger, identity, scope, deadline, cancellation and attempt
     * @return completes when the run has finished; completes exceptionally when it failed
     * @since 3.3.0
     */
    default CompletionStage<Void> execute(TaskInvocation invocation) {
        run();
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Executes one run, without its invocation.
     *
     * @deprecated since 3.3.0, for removal in 4.0.0: implement {@link #execute(TaskInvocation)}, which receives the
     *     trigger, deadline and cancellation of the run (root BRX-RS-MANIFEST-027)
     */
    @Deprecated(since = "3.3.0", forRemoval = true)
    default void run() {
        throw new UnsupportedOperationException(getClass().getName()
            + " implements neither execute(TaskInvocation) nor run()");
    }
}
