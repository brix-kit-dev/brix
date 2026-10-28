/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.outbox;

import java.time.OffsetDateTime;

/**
 * An outbox message the relay parked, without its payload.
 *
 * @param eventId the id a replay keeps
 * @param messageId the outbox row
 * @param messageType event or command type
 * @param schemaVersion payload schema version
 * @param producerPluginId publishing plugin
 * @param tenantId the envelope's tenant, or {@code null} for a PLATFORM message
 * @param attemptCount delivery attempts before it was parked
 * @param errorCode the stable code of the last failure
 * @param occurredAt when the fact occurred
 * @param parkedAt when the relay parked it
 */
public record ParkedOutboxMessage(
        String eventId,
        String messageId,
        String messageType,
        String schemaVersion,
        String producerPluginId,
        Long tenantId,
        int attemptCount,
        String errorCode,
        OffsetDateTime occurredAt,
        OffsetDateTime parkedAt) {
}
