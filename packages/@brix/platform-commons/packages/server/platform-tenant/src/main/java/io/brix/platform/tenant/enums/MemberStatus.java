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
 * Member Status Enumeration.
 *
 * <p>Status enumeration used for tenant-owned memberships and related
 * tenant-local lifecycle records.
 *
 * <h3>State Transition Diagram</h3>
 * <pre>
 * PENDING ──► ACTIVE ──► INACTIVE
 *    │            │          │
 *    │            ▼          ▼
 *    └───────► SUSPENDED ──► DELETED
 * </pre>
 *
 * <h3>Database Storage</h3>
 * <p>Stored as VARCHAR(32) in tenant-owned status columns:
 * <ul>
 *   <li>sys_tenant_member.status</li>
 * </ul>
 *
 * <h3>Usage Context</h3>
 * <p>Global identity and platform-admin grants use their own enums in
 * {@code platform-identity}.
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @Frozen - DO NOT modify enum values without database migration
 */
// @Frozen - This enum is frozen. Adding/removing/renaming values requires database migration.
public enum MemberStatus {

    /**
     * Pending activation or verification.
     *
     * <p>Initial state after creation, waiting for:
     * <ul>
     *   <li>Email verification</li>
     *   <li>Admin approval</li>
     *   <li>Invitation acceptance</li>
     *   <li>Initial setup completion</li>
     * </ul>
     *
     * <p><b>Access:</b> No login or API access allowed.
     */
    PENDING("Pending", "Awaiting activation or verification"),

    /**
     * Active and operational.
     *
     * <p>Normal operational state with full access according to
     * assigned permissions and roles.
     *
     * <p><b>Access:</b> Full access based on role/permissions.
     */
    ACTIVE("Active", "Active and operational"),

    /**
     * Inactive - voluntarily deactivated.
     *
     * <p>Soft deactivation, typically user-initiated:
     * <ul>
     *   <li>User requested account deactivation</li>
     *   <li>Extended period of inactivity</li>
     *   <li>Temporary leave of absence</li>
     * </ul>
     *
     * <p><b>Access:</b> No login allowed. Can be reactivated.
     */
    INACTIVE("Inactive", "Voluntarily deactivated"),

    /**
     * Suspended - administratively disabled.
     *
     * <p>Forced suspension due to policy or security issues:
     * <ul>
     *   <li>Security policy violation</li>
     *   <li>Suspicious activity detected</li>
     *   <li>Administrative action</li>
     *   <li>Pending investigation</li>
     * </ul>
     *
     * <p><b>Access:</b> No access. Requires admin action to restore.
     */
    SUSPENDED("Suspended", "Administratively suspended"),

    /**
     * Deleted - marked for deletion.
     *
     * <p>Soft delete state before permanent removal:
     * <ul>
     *   <li>Account scheduled for permanent deletion</li>
     *   <li>Grace period for recovery may apply</li>
     *   <li>Data anonymization may be in progress</li>
     * </ul>
     *
     * <p><b>Access:</b> No access. Subject to data retention policy.
     */
    DELETED("Deleted", "Marked for deletion");

    /**
     * Human-readable display name for UI presentation.
     */
    private final String displayName;

    /**
     * Detailed description for documentation and tooltips.
     */
    private final String description;

    /**
     * Constructor for MemberStatus enum.
     *
     * @param displayName human-readable display name
     * @param description detailed status description
     */
    MemberStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
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
     * Checks if this status allows login/operational access.
     *
     * <p>Only ACTIVE status allows full operational access.
     *
     * @return true if login/access is allowed
     */
    public boolean allowsAccess() {
        return this == ACTIVE;
    }

    /**
     * Checks if this status can be transitioned to ACTIVE.
     *
     * <p>PENDING and INACTIVE can be activated.
     * SUSPENDED requires explicit admin action (handled separately).
     * DELETED cannot be reactivated.
     *
     * @return true if can be activated through normal flow
     */
    public boolean canBeActivated() {
        return this == PENDING || this == INACTIVE;
    }

    /**
     * Checks if this is a terminal/final state.
     *
     * <p>DELETED is the only truly terminal state that cannot be recovered.
     *
     * @return true if this is a terminal state
     */
    public boolean isTerminal() {
        return this == DELETED;
    }

    /**
     * Checks if this status indicates a problematic state.
     *
     * <p>SUSPENDED and DELETED are considered problematic states
     * that may require administrative attention.
     *
     * @return true if status indicates a problem
     */
    public boolean isProblematic() {
        return this == SUSPENDED || this == DELETED;
    }

    /**
     * Validates if transition to target status is allowed.
     *
     * <p>Valid transitions:
     * <ul>
     *   <li>PENDING → ACTIVE, DELETED</li>
     *   <li>ACTIVE → INACTIVE, SUSPENDED, DELETED</li>
     *   <li>INACTIVE → ACTIVE, DELETED</li>
     *   <li>SUSPENDED → ACTIVE, DELETED</li>
     *   <li>DELETED → (none, terminal state)</li>
     * </ul>
     *
     * @param target target status to transition to
     * @return true if transition is valid
     */
    public boolean canTransitionTo(MemberStatus target) {
        if (this == target) {
            return true; // No-op transition is always valid
        }
        
        return switch (this) {
            case PENDING -> target == ACTIVE || target == DELETED;
            case ACTIVE -> target == INACTIVE || target == SUSPENDED || target == DELETED;
            case INACTIVE -> target == ACTIVE || target == DELETED;
            case SUSPENDED -> target == ACTIVE || target == DELETED;
            case DELETED -> false; // Terminal state
        };
    }
}
