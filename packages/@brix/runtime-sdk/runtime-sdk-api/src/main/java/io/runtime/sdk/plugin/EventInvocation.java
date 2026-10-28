/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.runtime.sdk.plugin;

import java.time.Instant;
import java.util.Objects;

import io.runtime.sdk.event.EventEnvelope;

/**
 * Immutable context of one event delivery (root BRX-RS-CAP-025, §5).
 *
 * @param event the canonical envelope with the decoded payload
 * @param publisher why the publisher was accepted
 * @param trace trace context of the delivery
 * @param deadline when the runtime stops waiting for the handler
 * @param cancellation set when the runtime stops waiting
 * @param deliveryAttempt 1 for the first delivery; redeliveries count up
 * @param <E> payload type
 * @since 3.2.0
 */
public record EventInvocation<E>(
        EventEnvelope<E> event,
        PublisherDecision publisher,
        TraceContext trace,
        Instant deadline,
        CancellationSignal cancellation,
        int deliveryAttempt) {

    public EventInvocation {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(publisher, "publisher must not be null");
        Objects.requireNonNull(trace, "trace must not be null");
        Objects.requireNonNull(deadline, "deadline must not be null");
        Objects.requireNonNull(cancellation, "cancellation must not be null");
        if (deliveryAttempt <= 0) {
            throw new IllegalArgumentException("deliveryAttempt must be positive");
        }
    }
}
