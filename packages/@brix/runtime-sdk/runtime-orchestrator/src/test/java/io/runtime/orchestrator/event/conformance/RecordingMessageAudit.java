/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event.conformance;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.runtime.orchestrator.outbox.MessageOperationAudit;

/**
 * A platform audit that keeps what it records, and refuses to record while told to (Kafka design D33).
 */
public final class RecordingMessageAudit implements MessageOperationAudit {

    private final List<MessageOperation> recorded = new ArrayList<>();
    private final Consumer<MessageOperation> onRecord;
    private boolean refusing;

    /** Creates an audit that only records. */
    public RecordingMessageAudit() {
        this(operation -> { });
    }

    /**
     * Creates an audit that also tells {@code onRecord} about each record, to check what happened before it.
     *
     * @param onRecord called with each record, after it was kept
     */
    public RecordingMessageAudit(Consumer<MessageOperation> onRecord) {
        this.onRecord = onRecord;
    }

    @Override
    public synchronized void record(MessageOperation operation) {
        if (refusing) {
            throw new IllegalStateException("The platform audit is down");
        }
        recorded.add(operation);
        onRecord.accept(operation);
    }

    /**
     * Makes the audit refuse every record, or accept them again.
     *
     * @param refusing whether to refuse
     */
    public synchronized void refusing(boolean refusing) {
        this.refusing = refusing;
    }

    /**
     * Returns what was recorded, in order.
     *
     * @return the records
     */
    public synchronized List<MessageOperation> recorded() {
        return List.copyOf(recorded);
    }
}
