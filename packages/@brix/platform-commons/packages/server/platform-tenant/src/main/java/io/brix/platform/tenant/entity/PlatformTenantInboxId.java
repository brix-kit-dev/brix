/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Composite identity for canonical platform-tenant Inbox receipts.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@Embeddable
public class PlatformTenantInboxId implements Serializable {

    @Column(name = "handler_id", nullable = false, length = 128)
    private String handlerId;

    @Column(name = "message_id", nullable = false, length = 64)
    private String messageId;

    protected PlatformTenantInboxId() {
    }

    /**
     * Creates an Inbox identity.
     *
     * @param handlerId stable manifest handler id
     * @param messageId canonical message id
     */
    public PlatformTenantInboxId(String handlerId, String messageId) {
        this.handlerId = requireText(handlerId, "handlerId");
        this.messageId = requireText(messageId, "messageId");
    }

    public String getHandlerId() {
        return handlerId;
    }

    public String getMessageId() {
        return messageId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlatformTenantInboxId that)) {
            return false;
        }
        return Objects.equals(handlerId, that.handlerId)
            && Objects.equals(messageId, that.messageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(handlerId, messageId);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
