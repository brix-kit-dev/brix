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
package io.runtime.sdk.event;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable canonical envelope for cross-plugin integration events.
 *
 * <p>The envelope is an L2A contract. It deliberately contains no broker topic,
 * outbox table, transaction handle, or adapter type. Runtime Shell L2B builds
 * and validates envelopes according to the active manifest; L2C adapters only
 * map an accepted envelope to persistence or transport protocols.</p>
 *
 * @param eventId stable message identity used for relay retry and inbox de-duplication
 * @param eventType versioned event contract id
 * @param schemaVersion event schema version declared by the producer manifest
 * @param occurredAt fact occurrence time
 * @param producerPluginId producer plugin identity supplied by Runtime Shell
 * @param scope platform or tenant scope
 * @param tenantId tenant id, required only for {@link EventScope#TENANT}
 * @param correlationId correlation id propagated by Runtime Shell
 * @param causationId causation event id, when available
 * @param traceparent W3C trace context
 * @param partitionKey ordering key scoped to an aggregate or equivalent partition
 * @param payload immutable event payload
 * @param reliability reliability level declared in the producer manifest
 * @param <T> payload type
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public record EventEnvelope<T>(
        String eventId,
        String eventType,
        String schemaVersion,
        Instant occurredAt,
        String producerPluginId,
        EventScope scope,
        String tenantId,
        String correlationId,
        String causationId,
        String traceparent,
        String partitionKey,
        T payload,
        EventReliability reliability) {

    /**
     * Creates a validated canonical event envelope.
     */
    public EventEnvelope {
        eventId = requireText(eventId, "eventId");
        eventType = requireText(eventType, "eventType");
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        producerPluginId = requireText(producerPluginId, "producerPluginId");
        scope = Objects.requireNonNull(scope, "scope must not be null");
        correlationId = requireText(correlationId, "correlationId");
        traceparent = requireText(traceparent, "traceparent");
        partitionKey = requireText(partitionKey, "partitionKey");
        payload = Objects.requireNonNull(payload, "payload must not be null");
        reliability = Objects.requireNonNull(reliability, "reliability must not be null");

        if (scope == EventScope.TENANT) {
            tenantId = requireText(tenantId, "tenantId");
        } else if (tenantId != null && !tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must be empty for PLATFORM scoped events");
        }
    }

    /**
     * Returns whether this envelope requires durable outbox and inbox handling.
     *
     * @return true for CRITICAL and STANDARD events
     */
    public boolean requiresPersistentDelivery() {
        return reliability == EventReliability.CRITICAL || reliability == EventReliability.STANDARD;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
