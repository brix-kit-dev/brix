/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import java.util.Map;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.runtime.sdk.capability.CommandSubmitException;

/**
 * Spring transaction synchronization for durable command sender receipts.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class SpringCommandTransactionBoundary implements CommandTransactionBoundary {

    @Override
    public void register(Runnable afterCommit, Runnable afterRollback) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new CommandSubmitException(
                CommandSubmitException.Code.NO_ACTIVE_TRANSACTION,
                Map.of("boundary", "spring"),
                null);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                afterCommit.run();
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    afterRollback.run();
                }
            }
        });
    }
}
