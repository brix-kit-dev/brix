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
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Organization Entity representing hierarchical organizational structure within a tenant.
 *
 * <p>Organizations provide a tree structure for grouping data and users
 * within a tenant. This supports permission inheritance and data scoping.
 *
 * <h3>Hierarchy Example</h3>
 * <pre>
 * Company (root, parent_id = null)
 * ├── Engineering (parent_id = Company.id)
 * │   ├── Backend Team
 * │   └── Frontend Team
 * ├── Sales
 * │   ├── NA Region
 * │   └── EMEA Region
 * └── HR
 * </pre>
 *
 * <h3>Ownership Model</h3>
 * <p>Business entities can be owned by organizations through owner_org_id,
 * allowing data visibility based on the org hierarchy.
 *
 * <h3>Tenant Isolation</h3>
 * <p>This entity HAS tenant_id because organizations are tenant-scoped.
 * All queries on this table MUST include tenant_id filter.
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see TenantOwnedEntity
 */
@Entity
@Table(
    name = "sys_organization",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_sys_organization_tenant_code",
            columnNames = {"tenant_id", "code"}
        )
    },
    indexes = {
        @Index(name = "idx_sys_organization_tenant", columnList = "tenant_id"),
        @Index(name = "idx_sys_organization_parent", columnList = "parent_id"),
        @Index(name = "idx_sys_organization_tenant_parent", columnList = "tenant_id, parent_id"),
        @Index(name = "idx_sys_organization_tenant_status", columnList = "tenant_id, status"),
        @Index(name = "idx_sys_organization_sort", columnList = "tenant_id, parent_id, sort_order")
    }
)
public class Organization {

    /**
     * Primary key - Snowflake-generated unique identifier.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /**
     * Tenant ID for isolation.
     *
     * <p>Organizations belong to a specific tenant and cannot be shared.
     */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    /**
     * Parent organization ID for hierarchy.
     *
     * <p>NULL for root-level organizations.
     * Must reference an organization within the same tenant.
     */
    @Column(name = "parent_id")
    private Long parentId;

    /**
     * Unique organization code within tenant.
     *
     * <p>Used for API access and external integrations.
     * Typically lowercase alphanumeric with hyphens.
     */
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    /**
     * Human-readable display name.
     */
    @Column(name = "name", nullable = false, length = 256)
    private String name;

    /**
     * Organization description.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Organization type for categorization.
     *
     * <p>Examples: DEPARTMENT, TEAM, DIVISION, REGION, BRANCH
     */
    @Column(name = "org_type", length = 32)
    private String orgType;

    /**
     * Sort order for display within same parent.
     *
     * <p>Lower values appear first.
     */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    /**
     * Organization status.
     *
     * @see MemberStatus
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MemberStatus status = MemberStatus.ACTIVE;

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
    public Organization() {
    }

    /**
     * Creates a new organization.
     *
     * @param tenantId the tenant ID
     * @param code unique code within tenant
     * @param name display name
     */
    public Organization(Long tenantId, String code, String name) {
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.status = MemberStatus.ACTIVE;
    }

    /**
     * Creates a new child organization.
     *
     * @param tenantId the tenant ID
     * @param parentId the parent organization ID
     * @param code unique code within tenant
     * @param name display name
     */
    public Organization(Long tenantId, Long parentId, String code, String name) {
        this(tenantId, code, name);
        this.parentId = parentId;
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
     * Checks if this is a root organization (no parent).
     *
     * @return true if parent_id is null
     */
    public boolean isRoot() {
        return parentId == null;
    }

    /**
     * Checks if this organization is active.
     *
     * @return true if status is ACTIVE
     */
    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    /**
     * Deactivates this organization.
     *
     * <p><b>Note:</b> Consider what happens to child organizations
     * and data owned by this org before deactivating.
     */
    public void deactivate() {
        this.status = MemberStatus.INACTIVE;
    }

    /**
     * Activates this organization.
     */
    public void activate() {
        this.status = MemberStatus.ACTIVE;
    }

    /**
     * Moves this organization to a new parent.
     *
     * <p><b>Warning:</b> Ensure the new parent is from the same tenant.
     * Circular references should be validated at the service layer.
     *
     * @param newParentId the new parent organization ID (null for root)
     */
    public void moveTo(Long newParentId) {
        this.parentId = newParentId;
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

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOrgType() {
        return orgType;
    }

    public void setOrgType(String orgType) {
        this.orgType = orgType;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public void setStatus(MemberStatus status) {
        this.status = status;
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
        Organization that = (Organization) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Organization{" +
               "id=" + id +
               ", tenantId=" + tenantId +
               ", code='" + code + '\'' +
               ", name='" + name + '\'' +
               ", parentId=" + parentId +
               ", status=" + status +
               '}';
    }
}
