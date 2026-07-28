/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

/**
 * L2B port for observing the sender Owner transaction outcome.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface CommandTransactionBoundary {

    /**
     * Registers callbacks for the active sender transaction.
     *
     * @param afterCommit callback invoked only after local transaction commit
     * @param afterRollback callback invoked when the local transaction rolls back
     */
    void register(Runnable afterCommit, Runnable afterRollback);
}
