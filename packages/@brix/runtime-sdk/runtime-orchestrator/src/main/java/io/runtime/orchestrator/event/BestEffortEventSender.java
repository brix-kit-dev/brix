/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;

/**
 * Sends a BEST_EFFORT event right away, without an outbox (root BRX-RS-MESSAGE-019, MESSAGE-020; Kafka design
 * §14 K21). The Owner's event bus builds the canonical envelope with the producer and tenant the runtime bound,
 * and hands it here after its unit of work commits.
 *
 * <p>A BEST_EFFORT event may be lost: an implementation logs and counts a failed send and returns, so the
 * business operation that published it is never failed by it.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@FunctionalInterface
public interface BestEffortEventSender {

    /**
     * Sends the event, or drops it.
     *
     * @param message the canonical BEST_EFFORT event
     */
    void send(CanonicalOutboxMessage message);
}
