/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Minimal business projection created from {@code TenantFirstOwnerAccepted}.
 *
 * <p>The row is intentionally tenant-owned and gives the first reliable
 * Consumer side effect a durable business meaning without introducing a UI or a
 * management workflow.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@Entity
@Table(name = "platform_tenant_first_owner_projection")
public class PlatformTenantFirstOwnerProjection {

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "message_id", nullable = false, updatable = false, length = 64, unique = true)
    private String messageId;

    @Column(name = "owner_member_id", nullable = false, updatable = false)
    private Long ownerMemberId;

    @Column(name = "profile_id", nullable = false, updatable = false)
    private Long profileId;

    @Column(name = "invitation_id", nullable = false, updatable = false)
    private Long invitationId;

    @Column(name = "projected_at", nullable = false, updatable = false)
    private OffsetDateTime projectedAt;

    protected PlatformTenantFirstOwnerProjection() {
    }

    /**
     * Creates the projection side effect.
     *
     * @param tenantId tenant id
     * @param messageId source message id
     * @param ownerMemberId created owner member id
     * @param profileId created profile id
     * @param invitationId accepted invitation id
     * @return projection entity
     */
    public static PlatformTenantFirstOwnerProjection create(
            Long tenantId,
            String messageId,
            Long ownerMemberId,
            Long profileId,
            Long invitationId) {
        PlatformTenantFirstOwnerProjection projection = new PlatformTenantFirstOwnerProjection();
        projection.tenantId = requirePositive(tenantId, "tenantId");
        projection.messageId = requireText(messageId, "messageId");
        projection.ownerMemberId = requirePositive(ownerMemberId, "ownerMemberId");
        projection.profileId = requirePositive(profileId, "profileId");
        projection.invitationId = requirePositive(invitationId, "invitationId");
        projection.projectedAt = OffsetDateTime.now();
        return projection;
    }

    @PrePersist
    protected void onCreate() {
        if (projectedAt == null) {
            projectedAt = OffsetDateTime.now();
        }
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getMessageId() {
        return messageId;
    }

    public Long getOwnerMemberId() {
        return ownerMemberId;
    }

    public Long getProfileId() {
        return profileId;
    }

    public Long getInvitationId() {
        return invitationId;
    }

    public OffsetDateTime getProjectedAt() {
        return projectedAt;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static Long requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
