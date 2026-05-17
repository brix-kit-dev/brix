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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * AuditLog Entity for recording all sensitive operations.
 *
 * <p>This entity stores an immutable record of actions performed in the system.
 * Audit logs are critical for security, compliance, and troubleshooting.
 *
 * <h3>Audit Log Characteristics</h3>
 * <ul>
 *   <li><b>Immutable:</b> Records cannot be updated or deleted via application</li>
 *   <li><b>Complete:</b> Contains full context (who, what, when, where)</li>
 *   <li><b>Tenant-aware:</b> Supports both tenant-scoped and system events</li>
 *   <li><b>Queryable:</b> Indexed for efficient searching and filtering</li>
 * </ul>
 *
 * <h3>Event Categories</h3>
 * <ul>
 *   <li>AUTHENTICATION: Login, logout, password change</li>
 *   <li>AUTHORIZATION: Permission changes, role assignments</li>
 *   <li>DATA_ACCESS: Read sensitive data</li>
 *   <li>DATA_MUTATION: Create, update, delete operations</li>
 *   <li>CONFIGURATION: System/tenant configuration changes</li>
 *   <li>SECURITY: Security events, violations</li>
 * </ul>
 *
 * <h3>Ownership Model</h3>
 * <p>Implements the full ownership model:
 * <ul>
 *   <li>tenant_id: NULL for system events, set for tenant events</li>
 *   <li>owner_member_id: The tenant member who performed the action</li>
 *   <li>owner_org_id: The org context of the action</li>
 *   <li>created_by: Identity who performed the action (always required)</li>
 * </ul>
 *
 * <h3>Retention Policy</h3>
 * <p>Audit logs should be retained according to compliance requirements.
 * Typical retention: 7 years for financial, 3 years for general.
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see TenantOwnedEntity
 */
@Entity
@Table(
    name = "biz_audit_log",
    indexes = {
        @Index(name = "idx_biz_audit_log_tenant", columnList = "tenant_id"),
        @Index(name = "idx_biz_audit_log_resource", columnList = "resource_type, resource_id"),
        @Index(name = "idx_biz_audit_log_created_by", columnList = "created_by"),
        @Index(name = "idx_biz_audit_log_created_at", columnList = "created_at DESC"),
        @Index(name = "idx_biz_audit_log_tenant_time", columnList = "tenant_id, created_at DESC"),
        @Index(name = "idx_biz_audit_log_action", columnList = "tenant_id, action"),
        @Index(name = "idx_biz_audit_log_request_id", columnList = "request_id"),
        @Index(name = "idx_biz_audit_log_owner_member", columnList = "owner_member_id"),
        @Index(name = "idx_biz_audit_log_owner_org", columnList = "owner_org_id")
    }
)
public class AuditLog {

    // ========================================================================
    // Common Action Constants
    // ========================================================================

    /** Login action */
    public static final String ACTION_LOGIN = "LOGIN";

    /** Logout action */
    public static final String ACTION_LOGOUT = "LOGOUT";

    /** Create action */
    public static final String ACTION_CREATE = "CREATE";

    /** Read/View action */
    public static final String ACTION_VIEW = "VIEW";

    /** Update action */
    public static final String ACTION_UPDATE = "UPDATE";

    /** Delete action */
    public static final String ACTION_DELETE = "DELETE";

    /** Export action */
    public static final String ACTION_EXPORT = "EXPORT";

    /** Permission change action */
    public static final String ACTION_PERMISSION_CHANGE = "PERMISSION_CHANGE";

    /**
     * Sentinel actor ID for unauthenticated / pre-authentication audit events.
     *
     * <p>Used when an event must be recorded but no authenticated identity is
     * available — for example, a failed platform-admin login attempt where
     * the supplied credentials could not be resolved to a {@code sys_identity}
     * row. {@code created_by} is declared {@code NOT NULL} in the schema and
     * has no foreign key to {@code sys_identity}, so a reserved sentinel
     * preserves immutability and completeness of the audit trail.</p>
     */
    public static final long ANONYMOUS_ACTOR_ID = 0L;

    // ========================================================================
    // Entity Fields
    // ========================================================================

    /**
     * Primary key - Snowflake-generated unique identifier.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    // ========================================================================
    // Ownership Fields
    // ========================================================================

    /**
     * Tenant ID for tenant-scoped events.
     *
     * <p>NULL for system-level events (platform admin actions).
     */
    @Column(name = "tenant_id")
    private Long tenantId;

    /**
     * Owner member ID - the tenant member who performed the action.
     */
    @Column(name = "owner_member_id")
    private Long ownerMemberId;

    /**
     * Owner organization ID - the org context of the action.
     */
    @Column(name = "owner_org_id")
    private Long ownerOrgId;

    /**
     * Identity who performed the action.
     *
     * <p>Always required. References sys_identity.id.
     */
    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    // ========================================================================
    // Audit Event Fields
    // ========================================================================

    /**
     * Action performed.
     *
     * <p>Examples: LOGIN, LOGOUT, CREATE, UPDATE, DELETE, VIEW
     */
    @Column(name = "action", nullable = false, length = 64)
    private String action;

    /**
     * Type of resource being acted upon.
     *
     * <p>Examples: TENANT, USER, CASE, CONTRACT, PERMISSION
     */
    @Column(name = "resource_type", nullable = false, length = 64)
    private String resourceType;

    /**
     * ID of the specific resource affected.
     *
     * <p>May be NULL for list/search operations.
     */
    @Column(name = "resource_id", length = 128)
    private String resourceId;

    /**
     * Human-readable description of the action.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // ========================================================================
    // Context Fields
    // ========================================================================

    /**
     * Client IP address.
     *
     * <p>Supports both IPv4 and IPv6 (45 chars max).
     */
    @Column(name = "client_ip", length = 45)
    private String clientIp;

    /**
     * User agent string from the client.
     */
    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /**
     * Request correlation ID.
     *
     * <p>Used to correlate audit logs with application logs.
     */
    @Column(name = "request_id", length = 128)
    private String requestId;

    /**
     * Additional context as JSON.
     *
     * <p>Can store before/after values, related IDs, etc.
     */
    @Column(name = "context", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String context;

    // ========================================================================
    // Result Fields
    // ========================================================================

    /**
     * Whether the action was successful.
     */
    @Column(name = "success", nullable = false)
    private boolean success = true;

    /**
     * Error message if the action failed.
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Error code if the action failed.
     */
    @Column(name = "error_code", length = 64)
    private String errorCode;

    // ========================================================================
    // Timestamp
    // ========================================================================

    /**
     * When the action occurred.
     *
     * <p>Immutable - set once at creation and never changed.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // ========================================================================
    // Constructors
    // ========================================================================

    /**
     * Default constructor required by JPA.
     */
    public AuditLog() {
    }

    /**
     * Creates a new audit log entry.
     *
     * @param createdBy identity ID of the actor
     * @param action the action performed
     * @param resourceType type of resource
     */
    public AuditLog(Long createdBy, String action, String resourceType) {
        this.createdBy = createdBy;
        this.action = action;
        this.resourceType = resourceType;
        this.success = true;
    }

    // ========================================================================
    // JPA Lifecycle Callbacks
    // ========================================================================

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    // ========================================================================
    // Factory Methods for Common Scenarios
    // ========================================================================

    /**
     * Creates a login audit log entry.
     *
     * @param identityId the identity who logged in
     * @param tenantId the tenant context (null for first-time login)
     * @param clientIp client IP address
     * @param userAgent client user agent
     * @return audit log entry
     */
    public static AuditLog login(Long identityId, Long tenantId, String clientIp, String userAgent) {
        AuditLog log = new AuditLog(identityId, ACTION_LOGIN, "SESSION");
        log.setTenantId(tenantId);
        log.setClientIp(clientIp);
        log.setUserAgent(userAgent);
        log.setDescription("User logged in");
        return log;
    }

    /**
     * Creates a failed login audit log entry.
     *
     * @param email the email that attempted login
     * @param clientIp client IP address
     * @param errorMessage failure reason
     * @return audit log entry
     */
    public static AuditLog failedLogin(String email, String clientIp, String errorMessage) {
        AuditLog log = new AuditLog();
        log.setAction(ACTION_LOGIN);
        log.setResourceType("SESSION");
        log.setResourceId(email);
        log.setClientIp(clientIp);
        log.setSuccess(false);
        log.setErrorMessage(errorMessage);
        log.setDescription("Failed login attempt for: " + email);
        return log;
    }

    /**
     * Creates a data mutation audit log entry.
     *
     * @param createdBy identity who performed the action
     * @param tenantId tenant context
     * @param action CREATE, UPDATE, or DELETE
     * @param resourceType type of entity
     * @param resourceId ID of the entity
     * @return audit log entry
     */
    public static AuditLog mutation(Long createdBy, Long tenantId, String action,
                                     String resourceType, String resourceId) {
        AuditLog log = new AuditLog(createdBy, action, resourceType);
        log.setTenantId(tenantId);
        log.setResourceId(resourceId);
        return log;
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

    public Long getOwnerMemberId() {
        return ownerMemberId;
    }

    public void setOwnerMemberId(Long ownerMemberId) {
        this.ownerMemberId = ownerMemberId;
    }

    public Long getOwnerOrgId() {
        return ownerOrgId;
    }

    public void setOwnerOrgId(Long ownerOrgId) {
        this.ownerOrgId = ownerOrgId;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // ========================================================================
    // Object Methods
    // ========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditLog auditLog = (AuditLog) o;
        return Objects.equals(id, auditLog.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "AuditLog{" +
               "id=" + id +
               ", tenantId=" + tenantId +
               ", action='" + action + '\'' +
               ", resourceType='" + resourceType + '\'' +
               ", resourceId='" + resourceId + '\'' +
               ", success=" + success +
               ", createdAt=" + createdAt +
               '}';
    }
}
