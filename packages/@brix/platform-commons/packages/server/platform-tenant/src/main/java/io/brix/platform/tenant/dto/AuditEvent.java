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
package io.brix.platform.tenant.dto;

import java.util.Objects;

/**
 * Data Transfer Object for audit event logging.
 *
 * <p>This DTO encapsulates all information needed to record an audit log entry
 * through {@link io.brix.platform.tenant.service.AuditService#log}.
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer - Platform Commons DTO</p>
 *
 * <h3>Audit Event Components</h3>
 * <ul>
 *   <li><b>Actor Context:</b> Who performed the action (createdBy, tenantId, ownerMemberId, ownerOrgId)</li>
 *   <li><b>Action Description:</b> What was done (action, resourceType, resourceId, description)</li>
 *   <li><b>Request Context:</b> Where it came from (clientIp, userAgent, requestId)</li>
 *   <li><b>Result:</b> Success/failure status and error details</li>
 *   <li><b>Additional Context:</b> JSON context for before/after values</li>
 * </ul>
 *
 * <h3>Common Action Constants</h3>
 * <p>Use these constants for standardized action names:
 * <ul>
 *   <li>{@link #ACTION_LOGIN} - User login</li>
 *   <li>{@link #ACTION_LOGOUT} - User logout</li>
 *   <li>{@link #ACTION_CREATE} - Entity creation</li>
 *   <li>{@link #ACTION_VIEW} - Entity read/view</li>
 *   <li>{@link #ACTION_UPDATE} - Entity update</li>
 *   <li>{@link #ACTION_DELETE} - Entity deletion</li>
 *   <li>{@link #ACTION_EXPORT} - Data export</li>
 *   <li>{@link #ACTION_PERMISSION_CHANGE} - Permission modification</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * AuditEvent event = AuditEvent.builder()
 *     .createdBy(userId)
 *     .tenantId(tenantId)
 *     .action(AuditEvent.ACTION_CREATE)
 *     .resourceType("TENANT")
 *     .resourceId(newTenant.getId().toString())
 *     .description("Created new tenant: " + newTenant.getName())
 *     .clientIp(request.getRemoteAddr())
 *     .success(true)
 *     .build();
 *
 * auditService.log(event);
 * }</pre>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see io.brix.platform.tenant.service.AuditService
 * @see io.brix.platform.tenant.entity.AuditLog
 */
public class AuditEvent {

    // ========================================================================
    // Action Constants - Standardized action names for consistency
    // ========================================================================

    /** Login action - user authentication */
    public static final String ACTION_LOGIN = "LOGIN";

    /** Logout action - user session termination */
    public static final String ACTION_LOGOUT = "LOGOUT";

    /** Create action - entity creation */
    public static final String ACTION_CREATE = "CREATE";

    /** View/Read action - entity access */
    public static final String ACTION_VIEW = "VIEW";

    /** Update action - entity modification */
    public static final String ACTION_UPDATE = "UPDATE";

    /** Delete action - entity removal */
    public static final String ACTION_DELETE = "DELETE";

    /** Export action - data export */
    public static final String ACTION_EXPORT = "EXPORT";

    /** Permission change action - authorization modification */
    public static final String ACTION_PERMISSION_CHANGE = "PERMISSION_CHANGE";

    // ========================================================================
    // Actor Context Fields
    // ========================================================================

    /**
     * Identity ID of the user who performed the action.
     *
     * <p>Required for most events. Can be null only for failed login attempts
     * where the identity is unknown.
     */
    private Long createdBy;

    /**
     * Tenant ID for tenant-scoped events.
     *
     * <p>Null for system-level events (platform admin actions).
     */
    private Long tenantId;

    /**
     * Owner member ID - the tenant member who performed the action.
     *
     * <p>Optional. References sys_tenant_member.id.
     */
    private Long ownerMemberId;

    /**
     * Owner organization ID - the org context of the action.
     *
     * <p>Optional. References sys_organization.id.
     */
    private Long ownerOrgId;

    // ========================================================================
    // Action Description Fields
    // ========================================================================

    /**
     * The action performed.
     *
     * <p>Required. Use constants like {@link #ACTION_CREATE}, {@link #ACTION_UPDATE}, etc.
     */
    private String action;

    /**
     * Type of resource being acted upon.
     *
     * <p>Required. Examples: TENANT, USER, CASE, CONTRACT, PERMISSION
     */
    private String resourceType;

    /**
     * ID of the specific resource affected.
     *
     * <p>Optional. May be null for list/search operations.
     */
    private String resourceId;

    /**
     * Human-readable description of the action.
     *
     * <p>Optional but recommended for clarity in audit reports.
     */
    private String description;

    // ========================================================================
    // Request Context Fields
    // ========================================================================

    /**
     * Client IP address.
     *
     * <p>Optional. Supports both IPv4 and IPv6.
     */
    private String clientIp;

    /**
     * User agent string from the client.
     *
     * <p>Optional. Useful for identifying client type (browser, mobile app, API client).
     */
    private String userAgent;

    /**
     * Request correlation ID for distributed tracing.
     *
     * <p>Optional. Used to correlate audit logs with application logs.
     */
    private String requestId;

    /**
     * Additional context as JSON string.
     *
     * <p>Optional. Can store before/after values, related IDs, metadata, etc.
     */
    private String context;

    // ========================================================================
    // Result Fields
    // ========================================================================

    /**
     * Whether the action was successful.
     *
     * <p>Defaults to true. Set to false for failed operations.
     */
    private boolean success = true;

    /**
     * Error message if the action failed.
     *
     * <p>Should be set when success is false.
     */
    private String errorMessage;

    /**
     * Error code if the action failed.
     *
     * <p>Optional machine-readable error code.
     */
    private String errorCode;

    // ========================================================================
    // Constructors
    // ========================================================================

    /**
     * Default constructor for framework use.
     */
    public AuditEvent() {
    }

    /**
     * Creates a basic audit event with required fields.
     *
     * @param createdBy identity who performed the action
     * @param action the action performed
     * @param resourceType type of resource
     */
    public AuditEvent(Long createdBy, String action, String resourceType) {
        this.createdBy = createdBy;
        this.action = action;
        this.resourceType = resourceType;
        this.success = true;
    }

    // ========================================================================
    // Builder Pattern
    // ========================================================================

    /**
     * Creates a new builder for constructing AuditEvent.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for fluent AuditEvent construction.
     */
    public static class Builder {
        private final AuditEvent event = new AuditEvent();

        private Builder() {
        }

        public Builder createdBy(Long createdBy) {
            event.createdBy = createdBy;
            return this;
        }

        public Builder tenantId(Long tenantId) {
            event.tenantId = tenantId;
            return this;
        }

        public Builder ownerMemberId(Long ownerMemberId) {
            event.ownerMemberId = ownerMemberId;
            return this;
        }

        public Builder ownerOrgId(Long ownerOrgId) {
            event.ownerOrgId = ownerOrgId;
            return this;
        }

        public Builder action(String action) {
            event.action = action;
            return this;
        }

        public Builder resourceType(String resourceType) {
            event.resourceType = resourceType;
            return this;
        }

        public Builder resourceId(String resourceId) {
            event.resourceId = resourceId;
            return this;
        }

        public Builder description(String description) {
            event.description = description;
            return this;
        }

        public Builder clientIp(String clientIp) {
            event.clientIp = clientIp;
            return this;
        }

        public Builder userAgent(String userAgent) {
            event.userAgent = userAgent;
            return this;
        }

        public Builder requestId(String requestId) {
            event.requestId = requestId;
            return this;
        }

        public Builder context(String context) {
            event.context = context;
            return this;
        }

        public Builder success(boolean success) {
            event.success = success;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            event.errorMessage = errorMessage;
            return this;
        }

        public Builder errorCode(String errorCode) {
            event.errorCode = errorCode;
            return this;
        }

        /**
         * Builds the AuditEvent instance.
         *
         * @return a new AuditEvent with the configured values
         */
        public AuditEvent build() {
            return event;
        }
    }

    // ========================================================================
    // Factory Methods for Common Scenarios
    // ========================================================================

    /**
     * Creates a login audit event.
     *
     * @param identityId the identity who logged in
     * @param tenantId the tenant context (null for first-time login)
     * @param clientIp client IP address
     * @param userAgent client user agent
     * @return configured audit event
     */
    public static AuditEvent login(Long identityId, Long tenantId, String clientIp, String userAgent) {
        return builder()
            .createdBy(identityId)
            .tenantId(tenantId)
            .action(ACTION_LOGIN)
            .resourceType("SESSION")
            .clientIp(clientIp)
            .userAgent(userAgent)
            .description("User logged in")
            .success(true)
            .build();
    }

    /**
     * Creates a failed login audit event.
     *
     * @param email the email that attempted login
     * @param clientIp client IP address
     * @param errorMessage failure reason
     * @return configured audit event
     */
    public static AuditEvent failedLogin(String email, String clientIp, String errorMessage) {
        return builder()
            .action(ACTION_LOGIN)
            .resourceType("SESSION")
            .resourceId(email)
            .clientIp(clientIp)
            .success(false)
            .errorMessage(errorMessage)
            .description("Failed login attempt for: " + email)
            .build();
    }

    /**
     * Creates a data mutation audit event (CREATE, UPDATE, DELETE).
     *
     * @param createdBy identity who performed the action
     * @param tenantId tenant context
     * @param action CREATE, UPDATE, or DELETE
     * @param resourceType type of entity
     * @param resourceId ID of the entity
     * @return configured audit event
     */
    public static AuditEvent mutation(Long createdBy, Long tenantId, String action,
                                       String resourceType, String resourceId) {
        return builder()
            .createdBy(createdBy)
            .tenantId(tenantId)
            .action(action)
            .resourceType(resourceType)
            .resourceId(resourceId)
            .success(true)
            .build();
    }

    // ========================================================================
    // Getters and Setters
    // ========================================================================

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
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

    // ========================================================================
    // Object Methods
    // ========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditEvent that = (AuditEvent) o;
        return success == that.success &&
               Objects.equals(createdBy, that.createdBy) &&
               Objects.equals(tenantId, that.tenantId) &&
               Objects.equals(action, that.action) &&
               Objects.equals(resourceType, that.resourceType) &&
               Objects.equals(resourceId, that.resourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(createdBy, tenantId, action, resourceType, resourceId, success);
    }

    @Override
    public String toString() {
        return "AuditEvent{" +
               "action='" + action + '\'' +
               ", resourceType='" + resourceType + '\'' +
               ", resourceId='" + resourceId + '\'' +
               ", createdBy=" + createdBy +
               ", tenantId=" + tenantId +
               ", success=" + success +
               '}';
    }
}
