/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Canonical Inbox receipt owned by {@code platform-tenant}.
 *
 * <p>The primary key {@code (handler_id, message_id)} is the durable idempotency
 * boundary for reliable event consumption.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@Entity
@Table(name = "platform_tenant_inbox")
public class PlatformTenantInbox {

    @EmbeddedId
    private PlatformTenantInboxId id;

    @Column(name = "message_kind", nullable = false, length = 20)
    private String messageKind = "EVENT";

    @Column(name = "message_type", nullable = false, length = 128)
    private String messageType;

    @Column(name = "schema_version", nullable = false, length = 16)
    private String schemaVersion = "1.0.0";

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PROCESSED";

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected PlatformTenantInbox() {
    }

    /**
     * Creates a processed event Inbox receipt.
     *
     * @param handlerId stable manifest handler id
     * @param messageId canonical message id
     * @param messageType manifest event type
     * @param schemaVersion event schema version
     * @param tenantId tenant id for TENANT scoped events
     * @return receipt entity
     */
    public static PlatformTenantInbox processedEvent(
            String handlerId,
            String messageId,
            String messageType,
            String schemaVersion,
            Long tenantId) {
        PlatformTenantInbox inbox = new PlatformTenantInbox();
        inbox.id = new PlatformTenantInboxId(handlerId, messageId);
        inbox.messageKind = "EVENT";
        inbox.messageType = requireText(messageType, "messageType");
        inbox.schemaVersion = requireText(schemaVersion, "schemaVersion");
        inbox.tenantId = tenantId;
        inbox.status = "PROCESSED";
        OffsetDateTime now = OffsetDateTime.now();
        inbox.processedAt = now;
        inbox.createdAt = now;
        return inbox;
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (processedAt == null) {
            processedAt = now;
        }
        if (createdAt == null) {
            createdAt = processedAt;
        }
    }

    public PlatformTenantInboxId getId() {
        return id;
    }

    public String getMessageKind() {
        return messageKind;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
