/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.command;

import java.util.Objects;

/**
 * What a plugin submits (root BRX-RS-MESSAGE-010): the command type its manifest declares under
 * {@code commands.consumes}, the payload, and the business idempotency scope and key. The provider runs one
 * command per {@code (idempotencyScope, idempotencyKey)}; a second submission with the same key is acknowledged
 * without running again (MESSAGE-014).
 *
 * <p>A command for a tenant names it with {@link #forTenant(String)}, a tenant the submitter's unit of work
 * reaches; otherwise it is a platform command. Commands of one partition key are delivered in order; the key
 * defaults to the idempotency key.</p>
 *
 * @param commandType the declared command type
 * @param payload the typed payload
 * @param idempotencyScope what the idempotency key is unique within, such as an aggregate type
 * @param idempotencyKey the business key
 * @param tenantId the tenant, or {@code null} for a platform command
 * @param partitionKey the ordering key, or {@code null} for the idempotency key
 * @param <C> payload type
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public record CommandRequest<C>(
        String commandType,
        C payload,
        String idempotencyScope,
        String idempotencyKey,
        String tenantId,
        String partitionKey) {

    /**
     * Creates a request.
     */
    public CommandRequest {
        commandType = requireText(commandType, "commandType");
        payload = Objects.requireNonNull(payload, "payload must not be null");
        idempotencyScope = requireText(idempotencyScope, "idempotencyScope");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        tenantId = tenantId == null || tenantId.isBlank() ? null : tenantId;
        partitionKey = partitionKey == null || partitionKey.isBlank() ? null : partitionKey;
    }

    /**
     * Creates a platform command request.
     *
     * @param commandType the declared command type
     * @param payload the typed payload
     * @param idempotencyScope what the key is unique within
     * @param idempotencyKey the business key
     * @param <C> payload type
     * @return the request
     */
    public static <C> CommandRequest<C> of(String commandType, C payload, String idempotencyScope, String idempotencyKey) {
        return new CommandRequest<>(commandType, payload, idempotencyScope, idempotencyKey, null, null);
    }

    /**
     * Returns this request for a tenant.
     *
     * @param tenant the tenant id
     * @return the request
     */
    public CommandRequest<C> forTenant(String tenant) {
        return new CommandRequest<>(commandType, payload, idempotencyScope, idempotencyKey, tenant, partitionKey);
    }

    /**
     * Returns this request with an ordering key of its own.
     *
     * @param key the partition key
     * @return the request
     */
    public CommandRequest<C> withPartitionKey(String key) {
        return new CommandRequest<>(commandType, payload, idempotencyScope, idempotencyKey, tenantId, key);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
