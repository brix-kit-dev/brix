/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import java.time.Instant;

/**
 * What the messaging tables' cleaning reports (Kafka design D34, §14 K16).
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public interface MessageRetentionMetrics {

    /** Records nothing. */
    MessageRetentionMetrics NOOP = new MessageRetentionMetrics() {
        @Override
        public void deleted(String table, int rows) {
        }

        @Override
        public void failed(String table) {
        }

        @Override
        public void passed(Instant at) {
        }
    };

    /**
     * Records rows deleted from a table.
     *
     * @param table the table
     * @param rows how many
     */
    void deleted(String table, int rows);

    /**
     * Records that cleaning a table failed; the pass goes on with the others.
     *
     * @param table the table
     */
    void failed(String table);

    /**
     * Records that a pass ended.
     *
     * @param at when
     */
    void passed(Instant at);
}
