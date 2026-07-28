/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.operational;

/**
 * Runtime-owned factory for an operational handler or task.
 *
 * @param <H> handler type
 * @author Runtime SDK Team
 * @since 3.0.10
 */
@FunctionalInterface
public interface OperationalHandlerFactory<H> {

    /**
     * Creates a handler after dependency resolution and global entry reservation.
     *
     * @param context module-scoped operational context
     * @return non-null handler
     */
    H create(OperationalContext context);
}
