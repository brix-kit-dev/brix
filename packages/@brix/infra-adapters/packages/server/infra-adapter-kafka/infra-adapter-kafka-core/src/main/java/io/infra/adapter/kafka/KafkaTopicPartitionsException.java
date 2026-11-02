/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka;

import io.runtime.orchestrator.event.EventTransportRefusedException;

/**
 * Event or command topics exist with fewer partitions than the production profile needs (standard-link plan point
 * 5). Nothing is sent to them or consumed from them until their partitions are raised. The check runs again on the
 * next attempt, so the host resumes by itself once they have enough; until then the host's health shows the refusal.
 */
public final class KafkaTopicPartitionsException extends EventTransportRefusedException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message the topics, their partitions, and how to raise them
     */
    public KafkaTopicPartitionsException(String message) {
        super(message);
    }
}
