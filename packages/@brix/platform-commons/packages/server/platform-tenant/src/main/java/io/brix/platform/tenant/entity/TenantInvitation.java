/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.entity;

import java.time.OffsetDateTime;
import java.util.Objects;

import io.brix.platform.tenant.enums.InvitationInviterType;
import io.brix.platform.tenant.enums.InvitationPurpose;
import io.brix.platform.tenant.enums.InvitationStatus;
import io.brix.platform.tenant.enums.TenantMemberType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Owner entity for {@code sys_tenant_invitation}.
 *
 * <p>Only token hashes are stored. Raw invitation tokens are used inside the
 * Data Owner transaction path only long enough to render the managed
 * notification request.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@Entity
@Table(
    name = "sys_tenant_invitation",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_invite_token", columnNames = "token_hash")
    },
    indexes = {
        @Index(name = "idx_invite_tenant", columnList = "tenant_id"),
        @Index(name = "idx_invite_status", columnList = "tenant_id, status"),
        @Index(name = "idx_invite_purpose_status", columnList = "tenant_id, invitation_purpose, status")
    }
)
public class TenantInvitation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private InvitationTargetType targetType = InvitationTargetType.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_role", nullable = false, length = 20)
    private TenantMemberType targetRole = TenantMemberType.OWNER;

    @Enumerated(EnumType.STRING)
    @Column(name = "invitation_purpose", nullable = false, length = 32)
    private InvitationPurpose invitationPurpose = InvitationPurpose.FIRST_OWNER;

    @Enumerated(EnumType.STRING)
    @Column(name = "inviter_type", nullable = false, length = 32)
    private InvitationInviterType inviterType = InvitationInviterType.PLATFORM_ADMIN;

    @Column(name = "platform_admin_id")
    private Long platformAdminId;

    @Column(name = "invitee_email", length = 255)
    private String inviteeEmail;

    @Column(name = "invitee_phone", length = 50)
    private String inviteePhone;

    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    @Column(name = "invited_by_member_id")
    private Long invitedByMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public boolean isPendingNow(OffsetDateTime now) {
        return status == InvitationStatus.PENDING && expiresAt.isAfter(now);
    }

    public void accept(OffsetDateTime now) {
        this.status = InvitationStatus.ACCEPTED;
        this.acceptedAt = now;
    }

    public void revoke(OffsetDateTime now) {
        this.status = InvitationStatus.REVOKED;
        this.revokedAt = now;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public InvitationTargetType getTargetType() {
        return targetType;
    }

    public void setTargetType(InvitationTargetType targetType) {
        this.targetType = targetType;
    }

    public TenantMemberType getTargetRole() {
        return targetRole;
    }

    public void setTargetRole(TenantMemberType targetRole) {
        this.targetRole = targetRole;
    }

    public InvitationPurpose getInvitationPurpose() {
        return invitationPurpose;
    }

    public void setInvitationPurpose(InvitationPurpose invitationPurpose) {
        this.invitationPurpose = invitationPurpose;
    }

    public InvitationInviterType getInviterType() {
        return inviterType;
    }

    public void setInviterType(InvitationInviterType inviterType) {
        this.inviterType = inviterType;
    }

    public Long getPlatformAdminId() {
        return platformAdminId;
    }

    public void setPlatformAdminId(Long platformAdminId) {
        this.platformAdminId = platformAdminId;
    }

    public String getInviteeEmail() {
        return inviteeEmail;
    }

    public void setInviteeEmail(String inviteeEmail) {
        this.inviteeEmail = inviteeEmail;
    }

    public String getInviteePhone() {
        return inviteePhone;
    }

    public void setInviteePhone(String inviteePhone) {
        this.inviteePhone = inviteePhone;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Long getInvitedByMemberId() {
        return invitedByMemberId;
    }

    public void setInvitedByMemberId(Long invitedByMemberId) {
        this.invitedByMemberId = invitedByMemberId;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public void setStatus(InvitationStatus status) {
        this.status = status;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public OffsetDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(OffsetDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(OffsetDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        TenantInvitation that = (TenantInvitation) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /** Invitation target table family. */
    public enum InvitationTargetType {
        MEMBER,
        PRINCIPAL
    }
}
