/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.event;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;

/**
 * The subscriber owner's persistent inbox (root BRX-RS-MESSAGE-025, multi-tenant BRX-TENANT-EVENT-004).
 *
 * <p>The record of {@code (handlerId, eventId)} is written in the owner's current local transaction, so it
 * commits or rolls back together with what the handler does.</p>
 */
public interface EventInboxStore {

    /**
     * Records that an event reached a handler.
     *
     * @param ownerId owner whose inbox it is, the subscriber plugin
     * @param handlerId the subscription's handler id
     * @param message the delivered event
     * @return {@code true} for a first delivery, {@code false} when the handler already committed this event
     */
    boolean insertProcessed(String ownerId, String handlerId, CanonicalOutboxMessage message);
}
