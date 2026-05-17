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
package io.brix.platform.tenant.enums;

import java.util.List;

import io.brix.platform.auth.PlatformPermissions;

/**
 * Platform Administrator Role Enumeration.
 *
 * <p>Defines the roles for platform-level administrators who manage the
 * entire Brix Platform instance, not individual tenants. Platform admins
 * operate across all tenants and have system-wide responsibilities.
 *
 * <h3>Role Hierarchy</h3>
 * <pre>
 * SUPER_ADMIN (highest - system maintenance)
 *      │
 *      ▼
 * PLATFORM_ADMIN (platform management)
 *      │
 *      ▼
 * SUPPORT_ADMIN (customer support)
 *      │
 *      ▼
 * AUDITOR (read-only monitoring)
 * </pre>
 *
 * <h3>Database Storage</h3>
 * <p>Stored as VARCHAR(32) in sys_platform_admin.role column.
 *
 * <h3>Security Note</h3>
 * <p>Platform admin accounts require additional security measures:
 * <ul>
 *   <li>Multi-factor authentication mandatory</li>
 *   <li>IP whitelist restrictions recommended</li>
 *   <li>All actions are audit logged</li>
 *   <li>Session timeout enforced</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @Frozen - DO NOT modify enum values without database migration
 */
// @Frozen - This enum is frozen. Adding/removing/renaming values requires database migration.
public enum PlatformAdminRole {

    /**
     * Super Administrator - highest level system access.
     *
     * <p>Reserved for system maintenance and emergency operations:
     * <ul>
     *   <li>Full system configuration access</li>
     *   <li>Database migration and maintenance</li>
     *   <li>System health and infrastructure management</li>
     *   <li>Create/manage platform admin accounts</li>
     *   <li>Access to all tenants for troubleshooting</li>
     *   <li>Can perform data recovery operations</li>
     * </ul>
     *
     * <p><b>Security:</b> Should be limited to 1-2 accounts maximum.
     * All actions require audit logging.
     */
    SUPER_ADMIN("Super Administrator", "Full system access for maintenance", 100),

    /**
     * Platform Administrator - day-to-day platform management.
     *
     * <p>Responsible for platform operations:
     * <ul>
     *   <li>Tenant provisioning and management</li>
     *   <li>Platform configuration (non-critical)</li>
     *   <li>User support escalation handling</li>
     *   <li>Billing and subscription management</li>
     *   <li>System monitoring and alerting</li>
     * </ul>
     */
    PLATFORM_ADMIN("Platform Administrator", "Platform management and operations", 80),

    /**
     * Support Administrator - customer support access.
     *
     * <p>Limited access for support operations:
     * <ul>
     *   <li>View tenant information (read-only)</li>
     *   <li>Reset user passwords</li>
     *   <li>Unlock accounts</li>
     *   <li>View audit logs</li>
     *   <li>Cannot modify tenant data</li>
     * </ul>
     */
    SUPPORT_ADMIN("Support Administrator", "Customer support with limited access", 50),

    /**
     * Auditor - read-only monitoring access.
     *
     * <p>Restricted to monitoring and compliance:
     * <ul>
     *   <li>View system logs and audit trails</li>
     *   <li>Generate compliance reports</li>
     *   <li>Monitor system health</li>
     *   <li>No modification capabilities</li>
     *   <li>No access to sensitive data (PII masked)</li>
     * </ul>
     */
    AUDITOR("Auditor", "Read-only access for compliance monitoring", 20);

    /**
     * Human-readable display name for UI presentation.
     */
    private final String displayName;

    /**
     * Detailed description for documentation and tooltips.
     */
    private final String description;

    /**
     * Numeric privilege level for permission comparison.
     * Higher values indicate higher privilege levels.
     */
    private final int privilegeLevel;

    /**
     * Constructor for PlatformAdminRole enum.
     *
     * @param displayName human-readable display name
     * @param description detailed role description
     * @param privilegeLevel numeric privilege level (higher = more privileges)
     */
    PlatformAdminRole(String displayName, String description, int privilegeLevel) {
        this.displayName = displayName;
        this.description = description;
        this.privilegeLevel = privilegeLevel;
    }

    /**
     * Returns the human-readable display name.
     *
     * @return display name for UI presentation
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the detailed description.
     *
     * @return description for documentation
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the numeric privilege level.
     *
     * @return privilege level (higher values = more privileges)
     */
    public int getPrivilegeLevel() {
        return privilegeLevel;
    }

    /**
     * Checks if this admin role has higher or equal privileges than another.
     *
     * @param other the admin role to compare against
     * @return true if this role has higher or equal privileges
     */
    public boolean hasPrivilegeOver(PlatformAdminRole other) {
        return this.privilegeLevel >= other.privilegeLevel;
    }

    /**
     * Checks if this role can manage (create/modify/delete) admin accounts
     * of the target role.
     *
     * <p>Management rules:
     * <ul>
     *   <li>SUPER_ADMIN can manage all roles</li>
     *   <li>PLATFORM_ADMIN can manage SUPPORT_ADMIN and AUDITOR</li>
     *   <li>SUPPORT_ADMIN and AUDITOR cannot manage anyone</li>
     * </ul>
     *
     * @param targetRole the admin role to be managed
     * @return true if this role can manage the target role
     */
    public boolean canManageRole(PlatformAdminRole targetRole) {
        if (this == SUPER_ADMIN) {
            return true;
        }
        if (this == PLATFORM_ADMIN) {
            return targetRole == SUPPORT_ADMIN || targetRole == AUDITOR;
        }
        return false;
    }

    /**
     * Checks if this role has tenant data modification capabilities.
     *
     * <p>Only SUPER_ADMIN and PLATFORM_ADMIN can modify tenant data.
     *
     * @return true if this role can modify tenant data
     */
    public boolean canModifyTenantData() {
        return this == SUPER_ADMIN || this == PLATFORM_ADMIN;
    }

    /**
     * Checks if this role requires multi-factor authentication.
     *
     * <p>All platform admin roles should require MFA, but this method
     * can be used to enforce stricter requirements for higher privilege roles.
     *
     * @return true (all platform admin roles require MFA)
     */
    public boolean requiresMfa() {
        return true; // All platform admin roles require MFA
    }

    /**
     * Checks if this is a super administrator role.
     *
     * @return true if this is SUPER_ADMIN
     */
    public boolean isSuperAdmin() {
        return this == SUPER_ADMIN;
    }

    /**
     * Returns the default permission codes for this platform admin role.
     *
     * <p>These permissions are written into the Platform Admin JWT at login time
     * by {@link io.brix.platform.auth.flow.AuthFlowCapabilityImpl} and validated
     * by {@link io.brix.platform.auth.aspect.PermissionAspect}.
     *
     * <p>The mapping is authoritative in
     * {@link io.brix.platform.auth.PlatformPermissions#defaultPermissionsFor(String)}.
     *
     * @return immutable, non-null list of permission codes
     */
    public List<String> getDefaultPermissions() {
        return PlatformPermissions.defaultPermissionsFor(this.name());
    }
}
