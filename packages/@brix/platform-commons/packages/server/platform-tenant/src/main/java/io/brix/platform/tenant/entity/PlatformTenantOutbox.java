/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.entity;

import java.time.OffsetDateTime;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Canonical outbox record owned by {@code platform-tenant}.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@Entity
@Table(name = "platform_tenant_outbox")
public class PlatformTenantOutbox {

    @Id
    @Column(name = "message_id", nullable = false, updatable = false, length = 64)
    private String messageId;

    @Column(name = "event_id", nullable = false, updatable = false, length = 64)
    private String eventId;

    @Column(name = "message_kind", nullable = false, length = 20)
    private String messageKind = "EVENT";

    @Column(name = "message_type", nullable = false, length = 128)
    private String messageType;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "schema_version", nullable = false, length = 16)
    private String schemaVersion = "1.0.0";

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "partition_key", nullable = false, length = 128)
    private String partitionKey;

    @Column(name = "payload", nullable = false, columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(name = "reliability", nullable = false, length = 20)
    private String reliability = "CRITICAL";

    @Column(name = "producer_plugin_id", nullable = false, length = 128)
    private String producerPluginId;

    @Column(name = "scope", nullable = false, length = 20)
    private String scope = "TENANT";

    @Column(name = "correlation_id", nullable = false, length = 128)
    private String correlationId;

    @Column(name = "causation_id", length = 128)
    private String causationId;

    @Column(name = "traceparent", length = 128)
    private String traceparent;

    @Column(name = "tracestate", length = 512)
    private String tracestate;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "available_at", nullable = false)
    private OffsetDateTime availableAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "claim_owner", length = 128)
    private String claimOwner;

    @Column(name = "claim_until")
    private OffsetDateTime claimUntil;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "last_error_code", length = 128)
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) {
            occurredAt = OffsetDateTime.now();
        }
        if (availableAt == null) {
            availableAt = occurredAt;
        }
        if (createdAt == null) {
            createdAt = occurredAt;
        }
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getMessageKind() {
        return messageKind;
    }

    public void setMessageKind(String messageKind) {
        this.messageKind = messageKind;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public void setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getReliability() {
        return reliability;
    }

    public void setReliability(String reliability) {
        this.reliability = reliability;
    }

    public String getProducerPluginId() {
        return producerPluginId;
    }

    public void setProducerPluginId(String producerPluginId) {
        this.producerPluginId = producerPluginId;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getCausationId() {
        return causationId;
    }

    public void setCausationId(String causationId) {
        this.causationId = causationId;
    }

    public String getTraceparent() {
        return traceparent;
    }

    public void setTraceparent(String traceparent) {
        this.traceparent = traceparent;
    }

    public String getTracestate() {
        return tracestate;
    }

    public void setTracestate(String tracestate) {
        this.tracestate = tracestate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public OffsetDateTime getAvailableAt() {
        return availableAt;
    }

    public void setAvailableAt(OffsetDateTime availableAt) {
        this.availableAt = availableAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getClaimOwner() {
        return claimOwner;
    }

    public void setClaimOwner(String claimOwner) {
        this.claimOwner = claimOwner;
    }

    public OffsetDateTime getClaimUntil() {
        return claimUntil;
    }

    public void setClaimUntil(OffsetDateTime claimUntil) {
        this.claimUntil = claimUntil;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        PlatformTenantOutbox that = (PlatformTenantOutbox) other;
        return Objects.equals(messageId, that.messageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId);
    }
}
