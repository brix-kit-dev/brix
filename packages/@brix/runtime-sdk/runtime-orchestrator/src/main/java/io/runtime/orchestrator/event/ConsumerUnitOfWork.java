/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

/**
 * Opens the Consumer Owner local transaction for Inbox and side effects.
 *
 * <p>The Runtime dispatcher owns ordering and durable receipt policy, while the
 * concrete Owner controls its transaction implementation.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
@FunctionalInterface
public interface ConsumerUnitOfWork {

    /**
     * Executes the callback inside one Consumer Owner local transaction.
     *
     * @param callback unit of work callback
     */
    void execute(Runnable callback);
}
