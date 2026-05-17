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

import io.brix.platform.tenant.enums.PrincipalStatus;
import io.brix.platform.tenant.enums.PrincipalType;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * TenantPrincipal Entity representing a C-side (Subject) relationship with a tenant.
 *
 * <p>This is the Subject counterpart of {@link TenantMember} (Actor) in the B2B2C model.
 * While members represent B-side operational users (owners, admins, staff),
 * principals represent C-side service consumers (customers, guests).
 *
 * <h3>B2B2C Actor/Subject Model</h3>
 * <pre>
 * sys_identity (1) ──&lt; sys_tenant_member (N)      = Actor  (B-side)
 * sys_identity (1) ──&lt; sys_tenant_principal (N)   = Subject (C-side)
 * </pre>
 *
 * <h3>Token Model</h3>
 * <p>Principals receive Subject tokens ({@code pid}) which are mutually exclusive
 * with Actor tokens ({@code mid}). Subject tokens carry {@code role=subject}
 * and MUST NOT access admin APIs.
 *
 * <h3>Lifecycle Rules</h3>
 * <ul>
 *   <li>Created when a Subject first engages with a tenant (not on passive browsing)</li>
 *   <li>Lifecycle is independent of any single business object (Case/Order)</li>
 *   <li>Only invalidated by explicit exit, admin action, or status change</li>
 * </ul>
 *
 * <h3>Constraints</h3>
 * <ul>
 *   <li>One principal per identity-tenant pair (unique constraint)</li>
 *   <li>principal_type restricted to CUSTOMER or GUEST</li>
 *   <li>Status restricted to ACTIVE, DISABLED, or REVOKED</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see TenantMember
 * @see PrincipalType
 * @see PrincipalStatus
 */
@Entity
@Table(
    name = "sys_tenant_principal",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_principal_tenant_identity",
            columnNames = {"tenant_id", "identity_id"}
        )
    },
    indexes = {
        @Index(name = "idx_principal_tenant", columnList = "tenant_id"),
        @Index(name = "idx_principal_identity", columnList = "identity_id"),
        @Index(name = "idx_principal_status", columnList = "tenant_id, status")
    }
)
public class TenantPrincipal {

    /**
     * Primary key - Snowflake-generated unique identifier.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /**
     * Reference to the tenant.
     *
     * <p>Indicates which tenant this principal relationship belongs to.
     */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    /**
     * Reference to the identity (user).
     *
     * <p>The user who is a principal (Subject) in this tenant.
     */
    @Column(name = "identity_id", nullable = false, updatable = false)
    private Long identityId;

    /**
     * Principal type within this tenant.
     *
     * <p>Determines the Subject role: CUSTOMER or GUEST.
     *
     * @see PrincipalType
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "principal_type", nullable = false, length = 20)
    private PrincipalType principalType;

    /**
     * Principal status.
     *
     * <p>Controls whether Subject tokens can be issued.
     * Only ACTIVE principals can authenticate and access tenant resources.
     *
     * @see PrincipalStatus
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PrincipalStatus status = PrincipalStatus.ACTIVE;

    /**
     * Display name for this principal within the tenant context.
     *
     * <p>May differ from identity username. For example, a patient's
     * preferred name in a clinic context.
     */
    @Column(name = "display_name", length = 100)
    private String displayName;

    /**
     * When the principal first joined this tenant.
     *
     * <p>Represents the moment the Subject relationship was established.
     */
    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

    /**
     * Last time the principal accessed this tenant.
     *
     * <p>Used for sorting in tenant selector (most recently accessed first)
     * and for activity tracking.
     */
    @Column(name = "last_access_at")
    private OffsetDateTime lastAccessAt;

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
    public TenantPrincipal() {
    }

    /**
     * Creates a new tenant principal (Subject relationship).
     *
     * @param tenantId the tenant ID
     * @param identityId the identity ID
     * @param principalType the principal type (CUSTOMER or GUEST)
     */
    public TenantPrincipal(Long tenantId, Long identityId, PrincipalType principalType) {
        this.tenantId = tenantId;
        this.identityId = identityId;
        this.principalType = principalType;
        this.status = PrincipalStatus.ACTIVE;
        this.joinedAt = OffsetDateTime.now();
    }

    // ========================================================================
    // JPA Lifecycle Callbacks
    // ========================================================================

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
        if (this.joinedAt == null) {
            this.joinedAt = this.createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    // ========================================================================
    // Business Methods
    // ========================================================================

    /**
     * Checks if this principal is active.
     *
     * @return true if status is ACTIVE
     */
    public boolean isActive() {
        return status == PrincipalStatus.ACTIVE;
    }

    /**
     * Checks if this is a customer principal.
     *
     * @return true if principal type is CUSTOMER
     */
    public boolean isCustomer() {
        return principalType == PrincipalType.CUSTOMER;
    }

    /**
     * Disables this principal (reversible).
     *
     * <p>Subject tokens will no longer be issued.
     * Can be re-enabled via {@link #enable()}.
     */
    public void disable() {
        this.status = PrincipalStatus.DISABLED;
    }

    /**
     * Re-enables a disabled principal.
     *
     * @throws IllegalStateException if the principal is in a terminal state (REVOKED)
     */
    public void enable() {
        if (!status.canBeReEnabled()) {
            throw new IllegalStateException(
                "Cannot re-enable principal in status: " + status
            );
        }
        this.status = PrincipalStatus.ACTIVE;
    }

    /**
     * Permanently revokes this principal (terminal state).
     *
     * <p>The Subject relationship is permanently terminated.
     * This cannot be reversed. Historical data is retained for audit.
     */
    public void revoke() {
        this.status = PrincipalStatus.REVOKED;
    }

    /**
     * Records an access to this tenant by the principal.
     *
     * <p>Updates {@link #lastAccessAt} for tenant selector sorting.
     */
    public void recordAccess() {
        this.lastAccessAt = OffsetDateTime.now();
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

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getIdentityId() {
        return identityId;
    }

    public void setIdentityId(Long identityId) {
        this.identityId = identityId;
    }

    public PrincipalType getPrincipalType() {
        return principalType;
    }

    public void setPrincipalType(PrincipalType principalType) {
        this.principalType = principalType;
    }

    public PrincipalStatus getStatus() {
        return status;
    }

    public void setStatus(PrincipalStatus status) {
        this.status = status;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(OffsetDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }

    public OffsetDateTime getLastAccessAt() {
        return lastAccessAt;
    }

    public void setLastAccessAt(OffsetDateTime lastAccessAt) {
        this.lastAccessAt = lastAccessAt;
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
        TenantPrincipal that = (TenantPrincipal) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TenantPrincipal{" +
               "id=" + id +
               ", tenantId=" + tenantId +
               ", identityId=" + identityId +
               ", principalType=" + principalType +
               ", status=" + status +
               '}';
    }
}
