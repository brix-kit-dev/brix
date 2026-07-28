/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.sdk.command.CommandEnvelope;

/**
 * Encodes and decodes command envelopes inside the canonical outbox payload.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface CommandPayloadCodec {

    /**
     * Encodes a command envelope for canonical outbox storage.
     *
     * @param envelope command envelope
     * @return serialized envelope payload
     */
    String encode(CommandEnvelope<?> envelope);

    /**
     * Reconstructs a command envelope from a claimed canonical outbox message.
     *
     * @param message canonical outbox message
     * @return command envelope with JSON payload
     */
    CommandEnvelope<String> decode(CanonicalOutboxMessage message);
}
