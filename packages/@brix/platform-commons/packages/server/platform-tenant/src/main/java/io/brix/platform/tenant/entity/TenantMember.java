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

import io.brix.platform.tenant.enums.MemberStatus;
import io.brix.platform.tenant.enums.TenantMemberType;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * TenantMember Entity representing membership of an identity in a tenant.
 *
 * <p>This is the association entity linking sys_identity to sys_tenant,
 * establishing the many-to-many relationship with additional attributes
 * like member type and status.
 *
 * <h3>Membership Model</h3>
 * <ul>
 *   <li>One identity can be member of multiple tenants</li>
 *   <li>Each membership has its own role/type</li>
 *   <li>Membership can be independently activated/suspended</li>
 * </ul>
 *
 * <h3>Member Types</h3>
 * <ul>
 *   <li>OWNER - Full control, can delete tenant (exactly one per tenant)</li>
 *   <li>ADMIN - Management privileges</li>
 *   <li>MEMBER - Standard access</li>
 *   <li>GUEST - Limited read-only access</li>
 * </ul>
 *
 * <h3>Constraints</h3>
 * <ul>
 *   <li>Each tenant MUST have exactly one OWNER</li>
 *   <li>An identity cannot have duplicate memberships in same tenant</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see TenantMemberType
 * @see MemberStatus
 */
@Entity
@Table(
    name = "sys_tenant_member",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_sys_tenant_member_tenant_identity",
            columnNames = {"tenant_id", "identity_id"}
        )
    },
    indexes = {
        @Index(name = "idx_sys_tenant_member_tenant", columnList = "tenant_id"),
        @Index(name = "idx_sys_tenant_member_identity", columnList = "identity_id"),
        @Index(name = "idx_sys_tenant_member_type", columnList = "tenant_id, member_type"),
        @Index(name = "idx_sys_tenant_member_status", columnList = "tenant_id, status")
    }
)
public class TenantMember {

    /**
     * Primary key - Snowflake-generated unique identifier.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /**
     * Reference to the tenant.
     *
     * <p>Indicates which tenant this membership belongs to.
     */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    /**
     * Reference to the identity (user).
     *
     * <p>The user who is a member of this tenant.
     */
    @Column(name = "identity_id", nullable = false, updatable = false)
    private Long identityId;

    /**
     * Member type/role within this tenant.
     *
     * <p>Determines the privilege level for this membership.
     *
     * @see TenantMemberType
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "member_type", nullable = false, length = 32)
    private TenantMemberType memberType = TenantMemberType.MEMBER;

    /**
     * Membership status.
     *
     * <p>Can be different from the identity's global status.
     * Allows per-tenant membership control.
     *
     * @see MemberStatus
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MemberStatus status = MemberStatus.ACTIVE;

    /**
     * When the member joined this tenant.
     *
     * <p>Used for membership duration tracking and analytics.
     */
    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

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
    public TenantMember() {
    }

    /**
     * Creates a new tenant membership.
     *
     * @param tenantId the tenant ID
     * @param identityId the identity ID
     * @param memberType the member type/role
     */
    public TenantMember(Long tenantId, Long identityId, TenantMemberType memberType) {
        this.tenantId = tenantId;
        this.identityId = identityId;
        this.memberType = memberType;
        this.status = MemberStatus.ACTIVE;
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
     * Checks if this membership is active.
     *
     * @return true if status is ACTIVE
     */
    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    /**
     * Checks if this is an owner membership.
     *
     * @return true if member type is OWNER
     */
    public boolean isOwner() {
        return memberType == TenantMemberType.OWNER;
    }

    /**
     * Checks if this member has administrative privileges.
     *
     * @return true if member type is OWNER or ADMIN
     */
    public boolean isAdmin() {
        return memberType.isAdministrative();
    }

    /**
     * Checks if this member can manage the target member type.
     *
     * @param targetType the member type to manage
     * @return true if this member can manage that type
     */
    public boolean canManage(TenantMemberType targetType) {
        return memberType.canManage(targetType);
    }

    /**
     * Promotes this member to a higher role.
     *
     * @param newType the new member type
     * @throws IllegalArgumentException if demotion attempted or invalid type
     */
    public void promoteTo(TenantMemberType newType) {
        if (newType.getPrivilegeLevel() < memberType.getPrivilegeLevel()) {
            throw new IllegalArgumentException(
                "Cannot demote using promoteTo. Use demoteTo for demotion."
            );
        }
        this.memberType = newType;
    }

    /**
     * Suspends this membership.
     */
    public void suspend() {
        this.status = MemberStatus.SUSPENDED;
    }

    /**
     * Activates this membership.
     */
    public void activate() {
        if (!status.canBeActivated() && status != MemberStatus.SUSPENDED) {
            throw new IllegalStateException(
                "Cannot activate membership in status: " + status
            );
        }
        this.status = MemberStatus.ACTIVE;
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

    public TenantMemberType getMemberType() {
        return memberType;
    }

    public void setMemberType(TenantMemberType memberType) {
        this.memberType = memberType;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public void setStatus(MemberStatus status) {
        this.status = status;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(OffsetDateTime joinedAt) {
        this.joinedAt = joinedAt;
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
        TenantMember that = (TenantMember) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TenantMember{" +
               "id=" + id +
               ", tenantId=" + tenantId +
               ", identityId=" + identityId +
               ", memberType=" + memberType +
               ", status=" + status +
               '}';
    }
}
