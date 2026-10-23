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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Platform audit trail entry written by the identity data owner.
 *
 * <p>The row is written in the same unit of work as the change it records, so the trail only
 * contains changes that were committed. JSON columns are mapped through the ORM dialect rather
 * than database-specific SQL.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@Entity(name = "PlatformIdentityAuditLogEntry")
@Table(name = "sys_platform_audit_log")
public class PlatformAuditLogEntry {

    private static final String NO_AFFECTED_TENANTS = "[]";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "operator_identity_id", updatable = false)
    private Long operatorIdentityId;

    @Column(name = "action", nullable = false, length = 64, updatable = false)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 64, updatable = false)
    private String resourceType;

    @Column(name = "resource_id", length = 128, updatable = false)
    private String resourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "affected_tenants", nullable = false, updatable = false)
    private String affectedTenants = NO_AFFECTED_TENANTS;

    @Column(name = "description", columnDefinition = "TEXT", updatable = false)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context", updatable = false)
    private String context;

    @Column(name = "success", nullable = false, updatable = false)
    private boolean success;

    @Column(name = "error_code", length = 64, updatable = false)
    private String errorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected PlatformAuditLogEntry() {
    }

    /**
     * Creates an audit entry.
     *
     * @param id application-assigned identifier
     * @param operatorIdentityId identity that performed the action, or {@code null} for the platform
     * @param action audit action code
     * @param resourceType audited resource type
     * @param resourceId audited resource id, if any
     * @param description human-readable description without secrets
     * @param success whether the action succeeded
     */
    public PlatformAuditLogEntry(
            Long id,
            Long operatorIdentityId,
            String action,
            String resourceType,
            String resourceId,
            String description,
            boolean success) {
        this.id = id;
        this.operatorIdentityId = operatorIdentityId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.description = description;
        this.success = success;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOperatorIdentityId() {
        return operatorIdentityId;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getAffectedTenants() {
        return affectedTenants;
    }

    public String getDescription() {
        return description;
    }

    public String getContext() {
        return context;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
