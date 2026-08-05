/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.brix.platform.identity.entity;

import java.time.OffsetDateTime;
import java.util.Objects;

import io.brix.platform.identity.enums.PlatformAdminRole;
import io.brix.platform.identity.enums.PlatformAdminStatus;
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
 * PlatformAdmin Entity representing platform-level administrator accounts.
 *
 * <p>Platform admins have cross-tenant access and are responsible for
 * managing the entire Brix Platform instance, not individual tenants.
 *
 * <h3>Security Requirements</h3>
 * <ul>
 *   <li>MFA should be enforced for all platform admin accounts</li>
 *   <li>All actions should be audit logged</li>
 *   <li>IP whitelist recommended for formal platform super administrators</li>
 *   <li>Session timeout strictly enforced</li>
 * </ul>
 *
 * <h3>Roles</h3>
 * <ul>
 *   <li>PLATFORM_SUPER_ADMIN - formal super administrator</li>
 *   <li>BOOTSTRAP - passwordless first-admin setup anchor</li>
 * </ul>
 *
 * <h3>Best Practices</h3>
 * <ul>
 *   <li>Keep the number of formal super-admin accounts minimal</li>
 *   <li>Regular access reviews for all admin accounts</li>
 *   <li>Document reason for admin access in notes field</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see PlatformAdminRole
 */
@Entity(name = "PlatformIdentityPlatformAdmin")
@Table(
    name = "sys_platform_admin",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_sys_platform_admin_identity", columnNames = "identity_id")
    },
    indexes = {
        @Index(name = "idx_sys_platform_admin_role", columnList = "role"),
        @Index(name = "idx_sys_platform_admin_status", columnList = "status"),
        @Index(name = "idx_sys_platform_admin_mfa", columnList = "mfa_enabled")
    }
)
public class PlatformAdmin {

    /**
     * Primary key - Snowflake-generated unique identifier.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /**
     * Reference to the identity (user account).
     *
     * <p>Links the platform admin role to an existing identity.
     * An identity can only have one platform admin record.
     */
    @Column(name = "identity_id", nullable = false, unique = true)
    private Long identityId;

    /**
     * Platform admin role.
     *
     * <p>Determines the privilege level and capabilities.
     *
     * @see PlatformAdminRole
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private PlatformAdminRole role;

    /**
    * Platform admin grant status.
    *
    * @see PlatformAdminStatus
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PlatformAdminStatus status = PlatformAdminStatus.ACTIVE;

    /**
     * Whether MFA (Multi-Factor Authentication) is enabled.
     *
     * <p><b>Security:</b> Should be TRUE for all admin accounts.
     * Accounts with mfa_enabled=FALSE should be flagged for review.
     */
    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled = false;

    /**
     * Administrative notes about this admin.
     *
     * <p>Document reason for access, approval workflow, etc.
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * identity_id of the operator who created this admin account.
     *
     * <p>NULL when the first formal platform administrator is created through
     * the Runtime-published bootstrap endpoint because no operator exists yet.
     *
     * @since 3.2.0 (V015)
     */
    @Column(name = "created_by")
    private Long createdBy;

    /**
    * Timestamp when this platform admin grant was revoked.
     *
     * <p>NULL while the account is ACTIVE.
     *
     * @since 3.2.0 (V015)
     */
    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    /**
    * identity_id of the operator who revoked this grant.
     *
     * <p>NULL while the account is ACTIVE.
     *
     * @since 3.2.0 (V015)
     */
    @Column(name = "revoked_by")
    private Long revokedBy;

    /**
    * Optional textual reason for revoking the grant.
     *
     * <p><b>Security (R-10):</b> MUST NOT contain passwords, tokens, or any other secret material.
     *
     * @since 3.2.0 (V015)
     */
    @Column(name = "revoke_reason", length = 512)
    private String revokeReason;

    /**
     * Record creation timestamp.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Last update timestamp.
     */
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // ========================================================================
    // Constructors
    // ========================================================================

    /**
     * Default constructor required by JPA.
     */
    public PlatformAdmin() {
    }

    /**
     * Creates a new platform admin.
     *
     * @param identityId the identity ID
     * @param role the platform admin role
     */
    public PlatformAdmin(Long identityId, PlatformAdminRole role) {
        this.identityId = identityId;
        this.role = role;
        this.status = PlatformAdminStatus.ACTIVE;
    }

    // ========================================================================
    // JPA Lifecycle Callbacks
    // ========================================================================

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    // ========================================================================
    // Business Methods
    // ========================================================================

    /**
     * Checks if the admin account is active.
     *
     * @return true if status is ACTIVE
     */
    public boolean isActive() {
        return status == PlatformAdminStatus.ACTIVE;
    }

    /**
     * Checks if this is a super administrator.
     *
      * @return true if role is PLATFORM_SUPER_ADMIN
     */
    public boolean isSuperAdmin() {
          return role == PlatformAdminRole.PLATFORM_SUPER_ADMIN;
    }

    /**
     * Checks if this admin can actively perform operations.
     *
     * <p>Requires both ACTIVE status and MFA enabled.
     *
     * @return true if admin can operate
     */
    public boolean canOperate() {
        return isActive() && mfaEnabled;
    }

    /**
     * Checks if this admin can modify tenant data.
     *
     * @return true if role allows tenant data modification
     */
    public boolean canModifyTenantData() {
        return role.canModifyTenantData();
    }

    /**
     * Checks if this admin can manage admin accounts of the target role.
     *
     * @param targetRole the role to manage
     * @return true if this admin can manage that role
     */
    public boolean canManageRole(PlatformAdminRole targetRole) {
        return role.canManageRole(targetRole);
    }

    /**
     * Enables MFA for this admin account.
     *
     * <p>Should be called after successful MFA setup.
     */
    public void enableMfa() {
        this.mfaEnabled = true;
    }

    /**
      * Revokes this admin account.
     */
    public void suspend() {
          this.status = PlatformAdminStatus.REVOKED;
    }

    /**
     * Activates this admin account.
     *
     * @throws IllegalStateException if account cannot be activated
     */
    public void activate() {
        if (status == PlatformAdminStatus.ACTIVE) {
            return;
        }
        this.status = PlatformAdminStatus.ACTIVE;
        this.revokedAt = null;
        this.revokedBy = null;
        this.revokeReason = null;
    }

    /**
    * Revokes this admin grant, recording who did it and why.
     *
    * <p>Sets status to REVOKED and captures the lifecycle metadata required by SSOT §5.
     *
     * <p><b>Security (R-10):</b> {@code reason} MUST NOT contain passwords, tokens, or secrets.
     *
     * @param operatorIdentityId identity_id of the admin performing the revoke operation
     * @param reason             optional human-readable reason (max 512 chars)
     * @since 3.2.0
     */
    public void revoke(Long operatorIdentityId, String reason) {
        if (status != PlatformAdminStatus.ACTIVE) {
            throw new IllegalStateException("Cannot revoke admin account in status: " + status);
        }
        this.status = PlatformAdminStatus.REVOKED;
        this.revokedAt = OffsetDateTime.now();
        this.revokedBy = operatorIdentityId;
        this.revokeReason = reason;
    }

    // ========================================================================
    // Getters and Setters
    // ========================================================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getIdentityId() {
        return identityId;
    }

    public void setIdentityId(Long identityId) {
        this.identityId = identityId;
    }

    public PlatformAdminRole getRole() {
        return role;
    }

    public void setRole(PlatformAdminRole role) {
        this.role = role;
    }

    public PlatformAdminStatus getStatus() {
        return status;
    }

    public void setStatus(PlatformAdminStatus status) {
        this.status = status;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public void setMfaEnabled(boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(OffsetDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Long getRevokedBy() {
        return revokedBy;
    }

    public void setRevokedBy(Long revokedBy) {
        this.revokedBy = revokedBy;
    }

    public String getRevokeReason() {
        return revokeReason;
    }

    public void setRevokeReason(String revokeReason) {
        this.revokeReason = revokeReason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ========================================================================
    // Object Methods
    // ========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlatformAdmin that = (PlatformAdmin) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "PlatformAdmin{" +
               "id=" + id +
               ", identityId=" + identityId +
               ", role=" + role +
               ", status=" + status +
               ", mfaEnabled=" + mfaEnabled +
               '}';
    }
}
