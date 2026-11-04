/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import java.time.OffsetDateTime;
import java.util.Objects;

import io.runtime.sdk.command.CommandStatus;

/**
 * What a provider recorded about one command it received (standard-link plan point 7): the idempotency key the
 * command holds, read as {@link CommandStatus.State#SUCCEEDED}, or what became of a command that took no key.
 *
 * @param commandId the command
 * @param state {@code SUCCEEDED} for the command that holds its key; otherwise {@code DUPLICATE}, {@code CONFLICT},
 *     {@code REJECTED} or {@code DISCARDED}
 * @param idempotencyScope the provider's scope of the key, made of the sender, the tenant and the sender's scope
 *     (plan D54); it tells who may read the record
 * @param idempotencyKey the sender's key
 * @param tenantId the command's tenant, or {@code null} for a platform command
 * @param errorCode why a command was rejected, conflicted or was discarded; otherwise {@code null}
 * @param holderCommandId for a duplicate or a conflict, the command that holds the key; otherwise {@code null}
 * @param at when the record was written
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public record CommandRecord(
        String commandId,
        CommandStatus.State state,
        String idempotencyScope,
        String idempotencyKey,
        Long tenantId,
        String errorCode,
        String holderCommandId,
        OffsetDateTime at) {

    /**
     * Creates a record.
     */
    public CommandRecord {
        Objects.requireNonNull(commandId, "commandId must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(idempotencyScope, "idempotencyScope must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(at, "at must not be null");
    }
}
