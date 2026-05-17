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

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.OffsetDateTime;

/**
 * Base class for all tenant-scoped entities with ownership model.
 *
 * <p>This mapped superclass provides the standard fields required for
 * multi-tenancy with the ownership model. All business entities that
 * require tenant isolation MUST extend this class.
 *
 * <h3>Standard Fields (5 fields as per MVP specification)</h3>
 * <ol>
 *   <li><b>tenantId</b> - Tenant isolation key (required)</li>
 *   <li><b>ownerMemberId</b> - Member who owns this record (optional)</li>
 *   <li><b>ownerOrgId</b> - Organization that owns this record (optional)</li>
 *   <li><b>createdBy</b> - Identity who created the record (required)</li>
 *   <li><b>createdAt</b> - Creation timestamp (auto-set)</li>
 * </ol>
 *
 * <h3>Additional Audit Field</h3>
 * <ul>
 *   <li><b>updatedAt</b> - Last update timestamp (auto-set)</li>
 * </ul>
 *
 * <h3>Ownership Model</h3>
 * <p>The ownership fields support flexible data access patterns:
 * <ul>
 *   <li><b>Member ownership:</b> Data owned by a specific tenant member</li>
 *   <li><b>Org ownership:</b> Data owned by an organization unit</li>
 *   <li><b>Combined:</b> Member within an org context</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Entity
 * @Table(name = "biz_case")
 * public class Case extends TenantOwnedEntity {
 *
 *     @Id
 *     private Long id;
 *
 *     private String title;
 *     private String status;
 *
 *     // business fields...
 * }
 * }</pre>
 *
 * <h3>Index Recommendations</h3>
 * <p>Tables extending this class should include these indexes:
 * <pre>
 * CREATE INDEX idx_xxx_tenant ON biz_xxx(tenant_id);
 * CREATE INDEX idx_xxx_owner_member ON biz_xxx(owner_member_id);
 * CREATE INDEX idx_xxx_owner_org ON biz_xxx(owner_org_id);
 * </pre>
 *
 * <h3>Architecture Compliance</h3>
 * <p>Per architecture design:
 * <ul>
 *   <li>All business tables MUST have tenant_id column</li>
 *   <li>All queries MUST include tenant_id filter</li>
 *   <li>Cross-tenant access MUST be explicitly annotated</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see io.brix.platform.tenant.interceptor.TenantInterceptor
 */
@MappedSuperclass
public abstract class TenantOwnedEntity {

    /**
     * Tenant ID for data isolation.
     *
     * <p>This field is the primary tenant isolation key. All database queries
     * on entities extending this class MUST include a filter on this field.
     *
     * <p><b>Constraints:</b>
     * <ul>
     *   <li>Cannot be null for business entities</li>
     *   <li>Cannot be changed after creation</li>
     *   <li>References sys_tenant.id</li>
     * </ul>
     */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    /**
     * Owner member ID for member-level ownership.
     *
     * <p>References the tenant member who owns this record. Used for:
     * <ul>
     *   <li>Personal data (owned by the member)</li>
     *   <li>Assigned tasks or cases</li>
     *   <li>Member-specific permissions</li>
     * </ul>
     *
     * <p><b>Note:</b> Can be null if ownership is at org level only.
     * References sys_tenant_member.id (not sys_identity.id).
     */
    @Column(name = "owner_member_id")
    private Long ownerMemberId;

    /**
     * Owner organization ID for org-level ownership.
     *
     * <p>References the organization that owns this record. Used for:
     * <ul>
     *   <li>Department/team-scoped data</li>
     *   <li>Hierarchical permission inheritance</li>
     *   <li>Reporting and analytics by org</li>
     * </ul>
     *
     * <p><b>Note:</b> References sys_organization.id.
     * Must belong to the same tenant as tenantId.
     */
    @Column(name = "owner_org_id")
    private Long ownerOrgId;

    /**
     * Identity ID of the record creator.
     *
     * <p>References the identity (user) who created this record.
     * This is for audit purposes and never changes after creation.
     *
     * <p><b>Note:</b> References sys_identity.id.
     * Must be a member of the same tenant.
     */
    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    /**
     * Record creation timestamp.
     *
     * <p>Automatically set when the entity is persisted.
     * Never changes after initial creation.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Last update timestamp.
     *
     * <p>Automatically updated whenever the entity is modified.
     */
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // ========================================================================
    // JPA Lifecycle Callbacks
    // ========================================================================

    /**
     * Sets creation timestamp before persist.
     *
     * <p>Called automatically by JPA before INSERT.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    /**
     * Updates the updated_at timestamp before update.
     *
     * <p>Called automatically by JPA before UPDATE.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    // ========================================================================
    // Getters and Setters
    // ========================================================================

    /**
     * Returns the tenant ID.
     *
     * @return tenant ID for isolation
     */
    public Long getTenantId() {
        return tenantId;
    }

    /**
     * Sets the tenant ID.
     *
     * <p><b>Warning:</b> This should only be set once during creation.
     * Changing tenant ID after creation violates data isolation.
     *
     * @param tenantId the tenant ID
     */
    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * Returns the owner member ID.
     *
     * @return owner member ID, or null if not member-owned
     */
    public Long getOwnerMemberId() {
        return ownerMemberId;
    }

    /**
     * Sets the owner member ID.
     *
     * @param ownerMemberId the owner member ID
     */
    public void setOwnerMemberId(Long ownerMemberId) {
        this.ownerMemberId = ownerMemberId;
    }

    /**
     * Returns the owner organization ID.
     *
     * @return owner org ID, or null if not org-owned
     */
    public Long getOwnerOrgId() {
        return ownerOrgId;
    }

    /**
     * Sets the owner organization ID.
     *
     * @param ownerOrgId the owner organization ID
     */
    public void setOwnerOrgId(Long ownerOrgId) {
        this.ownerOrgId = ownerOrgId;
    }

    /**
     * Returns the ID of the identity who created this record.
     *
     * @return creator's identity ID
     */
    public Long getCreatedBy() {
        return createdBy;
    }

    /**
     * Sets the creator identity ID.
     *
     * <p><b>Warning:</b> This should only be set once during creation.
     *
     * @param createdBy the creator's identity ID
     */
    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * Returns the creation timestamp.
     *
     * @return creation timestamp
     */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the creation timestamp.
     *
     * <p><b>Note:</b> Normally set automatically by @PrePersist.
     *
     * @param createdAt the creation timestamp
     */
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns the last update timestamp.
     *
     * @return last update timestamp, or null if never updated
     */
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Sets the last update timestamp.
     *
     * <p><b>Note:</b> Normally set automatically by @PreUpdate.
     *
     * @param updatedAt the update timestamp
     */
    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
