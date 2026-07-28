/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

/**
 * L2B internal transport boundary for canonical outbox messages.
 *
 * <p>Adapters map the canonical envelope to a concrete broker record and
 * return only after the broker accepts or rejects the publish attempt.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface OutboxTransport {

    /**
     * Publishes a canonical outbox message to the configured transport.
     *
     * @param message canonical outbox message
     * @throws OutboxTransportException when the broker publish fails
     */
    void publish(CanonicalOutboxMessage message);
}
