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
package io.brix.platform.tenant.entity;

import java.time.OffsetDateTime;
import java.util.Objects;

import io.brix.platform.tenant.enums.MemberStatus;
import io.brix.platform.tenant.enums.PlatformAdminRole;
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
 *   <li>IP whitelist recommended for SUPER_ADMIN</li>
 *   <li>Session timeout strictly enforced</li>
 * </ul>
 *
 * <h3>Role Hierarchy</h3>
 * <ul>
 *   <li>SUPER_ADMIN - Full system access, infrastructure management</li>
 *   <li>PLATFORM_ADMIN - Tenant management, day-to-day operations</li>
 *   <li>SUPPORT_ADMIN - Customer support, limited access</li>
 *   <li>AUDITOR - Read-only compliance monitoring</li>
 * </ul>
 *
 * <h3>Best Practices</h3>
 * <ul>
 *   <li>Limit SUPER_ADMIN accounts to 1-2 maximum</li>
 *   <li>Regular access reviews for all admin accounts</li>
 *   <li>Document reason for admin access in notes field</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see PlatformAdminRole
 */
@Entity
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
     * Admin account status.
     *
     * @see MemberStatus
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MemberStatus status = MemberStatus.ACTIVE;

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
     * <p>NULL for the first SUPER_ADMIN bootstrapped by {@code SuperAdminBootstrapRunner}
     * because no operator exists at that point.
     *
     * @since 3.2.0 (V015)
     */
    @Column(name = "created_by")
    private Long createdBy;

    /**
     * Timestamp when this account was disabled.
     *
     * <p>NULL while the account is ACTIVE.
     *
     * @since 3.2.0 (V015)
     */
    @Column(name = "disabled_at")
    private OffsetDateTime disabledAt;

    /**
     * identity_id of the operator who disabled this account.
     *
     * <p>NULL while the account is ACTIVE.
     *
     * @since 3.2.0 (V015)
     */
    @Column(name = "disabled_by")
    private Long disabledBy;

    /**
     * Optional textual reason for disabling the account.
     *
     * <p><b>Security (R-10):</b> MUST NOT contain passwords, tokens, or any other secret material.
     *
     * @since 3.2.0 (V015)
     */
    @Column(name = "disable_reason", length = 512)
    private String disableReason;

    /**
     * Expiry timestamp for a temporary (one-time) password.
     *
     * <p>Set by the {@code reset-password} flow. The service MUST verify
     * {@code now() < tempPasswordExpiresAt} on first login and immediately
     * set this field to {@code null} after the password is changed.
     *
     * @since 3.2.0 (V015)
     */
    @Column(name = "temp_password_expires_at")
    private OffsetDateTime tempPasswordExpiresAt;

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
        this.status = MemberStatus.ACTIVE;
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
        return status == MemberStatus.ACTIVE;
    }

    /**
     * Checks if this is a super administrator.
     *
     * @return true if role is SUPER_ADMIN
     */
    public boolean isSuperAdmin() {
        return role == PlatformAdminRole.SUPER_ADMIN;
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
     * Suspends this admin account.
     */
    public void suspend() {
        this.status = MemberStatus.SUSPENDED;
    }

    /**
     * Activates this admin account.
     *
     * @throws IllegalStateException if account cannot be activated
     */
    public void activate() {
        if (status == MemberStatus.DELETED) {
            throw new IllegalStateException("Cannot activate deleted admin account");
        }
        this.status = MemberStatus.ACTIVE;
    }

    /**
     * Disables this admin account, recording who did it and why.
     *
     * <p>Sets status to SUSPENDED and captures the lifecycle metadata required by SSOT §5.
     *
     * <p><b>Security (R-10):</b> {@code reason} MUST NOT contain passwords, tokens, or secrets.
     *
     * @param operatorIdentityId identity_id of the admin performing the disable operation
     * @param reason             optional human-readable reason (max 512 chars)
     * @since 3.2.0
     */
    public void disable(Long operatorIdentityId, String reason) {
        if (status == MemberStatus.DELETED) {
            throw new IllegalStateException("Cannot disable a deleted admin account");
        }
        this.status = MemberStatus.SUSPENDED;
        this.disabledAt = OffsetDateTime.now();
        this.disabledBy = operatorIdentityId;
        this.disableReason = reason;
    }

    /**
     * Marks that a temporary password was issued for this admin account.
     *
     * <p>The caller MUST NOT pass the actual password — only the expiry timestamp.
     *
     * @param expiresAt the timestamp after which the temporary password is no longer valid
     * @since 3.2.0
     */
    public void markTempPasswordIssued(OffsetDateTime expiresAt) {
        this.tempPasswordExpiresAt = expiresAt;
    }

    /**
     * Clears the temporary-password-expiry marker once the admin has changed their password.
     *
     * @since 3.2.0
     */
    public void clearTempPassword() {
        this.tempPasswordExpiresAt = null;
    }

    /**
     * Returns {@code true} if the temporary password has expired.
     *
     * <p>An absent (null) expiry means no temporary password is in effect — returns {@code false}.
     *
     * @since 3.2.0
     */
    public boolean isTempPasswordExpired() {
        return tempPasswordExpiresAt != null && OffsetDateTime.now().isAfter(tempPasswordExpiresAt);
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

    public MemberStatus getStatus() {
        return status;
    }

    public void setStatus(MemberStatus status) {
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

    public OffsetDateTime getDisabledAt() {
        return disabledAt;
    }

    public void setDisabledAt(OffsetDateTime disabledAt) {
        this.disabledAt = disabledAt;
    }

    public Long getDisabledBy() {
        return disabledBy;
    }

    public void setDisabledBy(Long disabledBy) {
        this.disabledBy = disabledBy;
    }

    public String getDisableReason() {
        return disableReason;
    }

    public void setDisableReason(String disableReason) {
        this.disableReason = disableReason;
    }

    public OffsetDateTime getTempPasswordExpiresAt() {
        return tempPasswordExpiresAt;
    }

    public void setTempPasswordExpiresAt(OffsetDateTime tempPasswordExpiresAt) {
        this.tempPasswordExpiresAt = tempPasswordExpiresAt;
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
