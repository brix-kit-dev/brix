/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import java.time.OffsetDateTime;

/**
 * An event a subscription parked, listed without its payload (root BRX-RS-MESSAGE-028; Kafka design D20).
 *
 * @param subscription the subscription's name
 * @param eventId the event id, or where the broker held the record when it was not an event envelope
 * @param eventType the event type, or {@code null} when the record was not an event envelope
 * @param errorCode why the event was parked
 * @param attempts deliveries tried
 * @param parkedAt when it was parked
 * @param source where the broker held the record
 * @param producerPluginId who published it, or {@code null} when the record was not an event envelope
 * @param tenantId the event's tenant, or {@code null} when it has none or the record was not an event envelope
 */
public record ParkedEvent(
        String subscription,
        String eventId,
        String eventType,
        String errorCode,
        int attempts,
        OffsetDateTime parkedAt,
        String source,
        String producerPluginId,
        Long tenantId) {
}
