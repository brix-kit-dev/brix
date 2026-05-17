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

/**
 * Tenant Member Type Enumeration.
 *
 * <p>Defines the role/type of a member within a specific tenant context.
 * A single identity (sys_identity) can have different member types across
 * different tenants through the sys_tenant_member association.
 *
 * <h3>Role Hierarchy</h3>
 * <pre>
 * OWNER (highest privileges)
 *   │
 *   ▼
 * ADMIN (administrative access)
 *   │
 *   ▼
 * MEMBER (standard access)
 *   │
 *   ▼
 * GUEST (limited access)
 * </pre>
 *
 * <h3>Database Storage</h3>
 * <p>Stored as VARCHAR(32) in sys_tenant_member.member_type column.
 *
 * <h3>Permission Model</h3>
 * <p>Member types are used for coarse-grained access control. Fine-grained
 * permissions should be implemented through a separate permission system.
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @Frozen - DO NOT modify enum values without database migration
 */
// @Frozen - This enum is frozen. Adding/removing/renaming values requires database migration.
public enum TenantMemberType {

    /**
     * Tenant Owner - highest level of access within a tenant.
     *
     * <p>Characteristics:
     * <ul>
     *   <li>Full control over tenant settings and configuration</li>
     *   <li>Can manage all other members including admins</li>
     *   <li>Can transfer ownership to another member</li>
     *   <li>Can delete/terminate the tenant</li>
     *   <li>Cannot be removed by admins</li>
     * </ul>
     *
     * <p><b>Constraint:</b> Each tenant must have exactly one owner.
     * Ownership transfer requires explicit action.
     */
    OWNER("Owner", "Tenant owner with full privileges", 100),

    /**
     * Tenant Administrator - high level of access for day-to-day management.
     *
     * <p>Characteristics:
     * <ul>
     *   <li>Can manage tenant configuration (except ownership)</li>
     *   <li>Can manage members (add, remove, modify roles)</li>
     *   <li>Cannot manage other admins or owner</li>
     *   <li>Can view audit logs</li>
     *   <li>Can manage integrations and API keys</li>
     * </ul>
     */
    ADMIN("Administrator", "Tenant administrator with management privileges", 80),

    /**
     * Standard Member - normal operational access.
     *
     * <p>Characteristics:
     * <ul>
     *   <li>Full access to business features based on permissions</li>
     *   <li>Cannot modify tenant settings</li>
     *   <li>Cannot manage other members</li>
     *   <li>Standard billing/quota allocations</li>
     * </ul>
     */
    MEMBER("Member", "Standard tenant member", 50),

    /**
     * Guest - limited read-only or restricted access.
     *
     * <p>Characteristics:
     * <ul>
     *   <li>Read-only access to specific resources</li>
     *   <li>May have time-limited access</li>
     *   <li>Cannot create or modify data</li>
     *   <li>Used for external collaborators or auditors</li>
     * </ul>
     */
    GUEST("Guest", "Guest with limited access", 10);

    /**
     * Human-readable display name for UI presentation.
     */
    private final String displayName;

    /**
     * Detailed description for documentation and tooltips.
     */
    private final String description;

    /**
     * Numeric priority level for permission comparison.
     * Higher values indicate higher privilege levels.
     */
    private final int privilegeLevel;

    /**
     * Constructor for TenantMemberType enum.
     *
     * @param displayName human-readable display name
     * @param description detailed type description
     * @param privilegeLevel numeric privilege level (higher = more privileges)
     */
    TenantMemberType(String displayName, String description, int privilegeLevel) {
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
     * Checks if this member type has higher or equal privileges than another.
     *
     * <p>Used for permission checks where higher privilege levels
     * can perform actions on lower privilege members.
     *
     * @param other the member type to compare against
     * @return true if this type has higher or equal privileges
     */
    public boolean hasPrivilegeOver(TenantMemberType other) {
        return this.privilegeLevel >= other.privilegeLevel;
    }

    /**
     * Checks if this member type can manage (add/remove/modify) the target type.
     *
     * <p>Management rules:
     * <ul>
     *   <li>OWNER can manage all types</li>
     *   <li>ADMIN can manage MEMBER and GUEST</li>
     *   <li>MEMBER and GUEST cannot manage anyone</li>
     * </ul>
     *
     * @param targetType the member type to be managed
     * @return true if this type can manage the target type
     */
    public boolean canManage(TenantMemberType targetType) {
        if (this == OWNER) {
            return true; // Owner can manage everyone
        }
        if (this == ADMIN) {
            return targetType == MEMBER || targetType == GUEST;
        }
        return false; // MEMBER and GUEST cannot manage others
    }

    /**
     * Checks if this is an administrative role (OWNER or ADMIN).
     *
     * @return true if this is an administrative role
     */
    public boolean isAdministrative() {
        return this == OWNER || this == ADMIN;
    }

    /**
     * Checks if this is the tenant owner.
     *
     * @return true if this is OWNER type
     */
    public boolean isOwner() {
        return this == OWNER;
    }
}
