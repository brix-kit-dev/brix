/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

/**
 * A broker transport refuses to carry messages until an operator fixes its configuration, such as topics with fewer
 * partitions than the production profile needs (standard-link plan point 5). The runtime waits out an outage with the
 * host healthy (Kafka design D21); a refusal shows in the host's health until it is fixed, and the runtime tries again
 * as it does after an outage.
 */
public class EventTransportRefusedException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what is refused, and what the operator does about it
     */
    public EventTransportRefusedException(String message) {
        super(message);
    }
}
