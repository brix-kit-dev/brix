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
 * Principal Status Enumeration (Subject/C-side lifecycle states).
 *
 * <p>Defines the lifecycle status of a principal within a tenant context.
 * Unlike {@link MemberStatus} which has 5 states for Actor governance,
 * PrincipalStatus uses a simpler 3-state model appropriate for
 * Subject relationships.
 *
 * <h3>State Transition Diagram</h3>
 * <pre>
 * ACTIVE ──► DISABLED ──► REVOKED
 *    ▲           │
 *    └───────────┘ (re-enable)
 * </pre>
 *
 * <h3>Lifecycle Rules</h3>
 * <ul>
 *   <li>Principals are created in ACTIVE state upon entry</li>
 *   <li>DISABLED: reversible suspension (admin or system action)</li>
 *   <li>REVOKED: permanent termination of the Subject relationship</li>
 *   <li>Principal lifecycle is independent of business objects</li>
 * </ul>
 *
 * <h3>Hard Constraints</h3>
 * <p>A principal is only invalidated when:
 * <ul>
 *   <li>Subject explicitly exits the tenant (voluntary unbind)</li>
 *   <li>Tenant admin unbinds/disables the principal</li>
 *   <li>Status set to DISABLED or REVOKED</li>
 * </ul>
 * <p>Business object completion (Case/Order closure) does NOT affect
 * principal status.
 *
 * <h3>Database Storage</h3>
 * <p>Stored as VARCHAR(20) in sys_tenant_principal.status column.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see MemberStatus
 * @Frozen - DO NOT modify enum values without database migration
 */
// @Frozen - This enum is frozen. Adding/removing/renaming values requires database migration.
public enum PrincipalStatus {

    /**
     * Active - the principal has a valid Subject relationship.
     *
     * <p>The principal can:
     * <ul>
     *   <li>Authenticate and receive Subject tokens</li>
     *   <li>Access tenant resources based on Subject permissions</li>
     *   <li>Have business objects created on their behalf</li>
     * </ul>
     */
    ACTIVE("Active", "Active subject relationship"),

    /**
     * Disabled - temporarily suspended Subject relationship.
     *
     * <p>Reversible state indicating:
     * <ul>
     *   <li>Admin-initiated suspension</li>
     *   <li>System-detected policy violation</li>
     *   <li>Pending investigation</li>
     * </ul>
     *
     * <p><b>Access:</b> No token issuance. Can be re-enabled to ACTIVE.
     */
    DISABLED("Disabled", "Temporarily suspended subject relationship"),

    /**
     * Revoked - permanently terminated Subject relationship.
     *
     * <p>Terminal state indicating:
     * <ul>
     *   <li>Subject voluntarily exited the tenant</li>
     *   <li>Admin permanently revoked the relationship</li>
     *   <li>Regulatory or compliance-driven removal</li>
     * </ul>
     *
     * <p><b>Access:</b> No token issuance. Cannot be re-enabled.
     * Historical data and business objects are retained for audit.
     */
    REVOKED("Revoked", "Permanently terminated subject relationship");

    /**
     * Human-readable display name for UI presentation.
     */
    private final String displayName;

    /**
     * Detailed description for documentation and tooltips.
     */
    private final String description;

    /**
     * Constructor for PrincipalStatus enum.
     *
     * @param displayName human-readable display name
     * @param description detailed status description
     */
    PrincipalStatus(String displayName, String description) {
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
     * Checks if this status allows Subject token issuance and access.
     *
     * <p>Only ACTIVE status allows authentication and resource access.
     *
     * @return true if Subject access is allowed
     */
    public boolean allowsAccess() {
        return this == ACTIVE;
    }

    /**
     * Checks if this status can be transitioned back to ACTIVE.
     *
     * <p>Only DISABLED can be re-enabled. REVOKED is terminal.
     *
     * @return true if can be re-enabled
     */
    public boolean canBeReEnabled() {
        return this == DISABLED;
    }

    /**
     * Checks if this is a terminal/final state.
     *
     * <p>REVOKED is the only terminal state.
     *
     * @return true if this is a terminal state
     */
    public boolean isTerminal() {
        return this == REVOKED;
    }
}
