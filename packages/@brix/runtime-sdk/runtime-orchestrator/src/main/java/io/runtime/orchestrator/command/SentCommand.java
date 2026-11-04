/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * A command's row in its sender's outbox (standard-link plan point 7).
 *
 * @param commandId the command id
 * @param commandType the command type
 * @param status {@code PENDING}, {@code IN_FLIGHT}, {@code PUBLISHED}, {@code PARKED} or {@code DISCARDED}
 * @param tenantId the command's tenant, or {@code null} for a platform command
 * @param errorCode the last failure of a parked or discarded command; otherwise {@code null}
 * @param at when the command was queued, published, parked or discarded, by its status
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public record SentCommand(
        String commandId,
        String commandType,
        String status,
        Long tenantId,
        String errorCode,
        OffsetDateTime at) {

    /**
     * Creates a row.
     */
    public SentCommand {
        Objects.requireNonNull(commandId, "commandId must not be null");
        Objects.requireNonNull(commandType, "commandType must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
