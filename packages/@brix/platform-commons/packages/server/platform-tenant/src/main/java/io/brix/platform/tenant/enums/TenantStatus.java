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
 * Tenant Status Enumeration.
 *
 * <p>Defines the lifecycle states of a tenant in the multi-tenant system.
 * This enum is frozen and should not be modified without careful consideration
 * of database migration and backward compatibility.
 *
 * <h3>State Transition Diagram</h3>
 * <pre>
 * PENDING_ACTIVATION ──► ACTIVE ──► SUSPENDED ──► TERMINATED
 *                           │            │
 *                           │            ▼
 *                           └───────► SUSPENDED
 *                                        │
 *                                        ▼
 *                                   TERMINATED
 * </pre>
 *
 * <h3>Database Storage</h3>
 * <p>Stored as VARCHAR(32) in sys_tenant.status column.
 * Use {@link #name()} for database persistence.
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * Tenant tenant = new Tenant();
 * tenant.setStatus(TenantStatus.PENDING_ACTIVATION);
 *
 * // Check tenant access
 * if (tenant.getStatus() != TenantStatus.ACTIVE) {
 *     throw new TenantNotActiveException("Tenant is not active");
 * }
 * }</pre>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @Frozen - DO NOT modify enum values without database migration
 */
// @Frozen - This enum is frozen. Adding/removing/renaming values requires database migration.
public enum TenantStatus {

    /**
     * Tenant registration is pending activation.
     *
     * <p>Initial state after tenant creation. The tenant exists in the system
     * but is not yet operational. This state is used for:
     * <ul>
     *   <li>Email verification pending</li>
     *   <li>Manual approval pending</li>
     *   <li>Initial setup in progress</li>
     * </ul>
     *
     * <p><b>Access Rights:</b> No data access, no user login allowed.
     */
    PENDING_ACTIVATION("Pending Activation", "Tenant is pending activation"),

    /**
     * Tenant is fully operational.
     *
     * <p>Normal operational state. All tenant features are available:
     * <ul>
     *   <li>User authentication and authorization</li>
     *   <li>Data read/write operations</li>
     *   <li>API access</li>
     *   <li>Billing active (if applicable)</li>
     * </ul>
     *
     * <p><b>Access Rights:</b> Full access according to user permissions.
     */
    ACTIVE("Active", "Tenant is active and operational"),

    /**
     * Tenant is temporarily suspended.
     *
     * <p>Suspended state due to policy violations, payment issues, or
     * administrative action. Characteristics:
     * <ul>
     *   <li>User logins are blocked</li>
     *   <li>API access is denied</li>
     *   <li>Data is preserved (read-only for admins)</li>
     *   <li>Can be reactivated to ACTIVE state</li>
     * </ul>
     *
     * <p><b>Access Rights:</b> Platform admin read-only access only.
     */
    SUSPENDED("Suspended", "Tenant is suspended temporarily"),

    /**
     * Tenant is permanently terminated.
     *
     * <p>Final state indicating tenant deletion/termination:
     * <ul>
     *   <li>All access is permanently denied</li>
     *   <li>Data may be scheduled for deletion</li>
     *   <li>This state is irreversible</li>
     *   <li>Audit records are preserved</li>
     * </ul>
     *
     * <p><b>Access Rights:</b> No access. Data retention per policy.
     */
    TERMINATED("Terminated", "Tenant is permanently terminated");

    /**
     * Human-readable display name for UI presentation.
     */
    private final String displayName;

    /**
     * Detailed description for documentation and tooltips.
     */
    private final String description;

    /**
     * Constructor for TenantStatus enum.
     *
     * @param displayName human-readable display name
     * @param description detailed status description
     */
    TenantStatus(String displayName, String description) {
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
     * Checks if the tenant can perform normal operations.
     *
     * <p>Only ACTIVE status allows full operational access.
     *
     * @return true if tenant is in operational state
     */
    public boolean isOperational() {
        return this == ACTIVE;
    }

    /**
     * Checks if the tenant can be reactivated.
     *
     * <p>PENDING_ACTIVATION and SUSPENDED can transition to ACTIVE.
     * TERMINATED is a final state and cannot be reactivated.
     *
     * @return true if tenant can be transitioned to ACTIVE
     */
    public boolean canBeActivated() {
        return this == PENDING_ACTIVATION || this == SUSPENDED;
    }

    /**
     * Checks if the tenant is in a final state.
     *
     * <p>TERMINATED is the only final state that cannot be changed.
     *
     * @return true if tenant is in a terminal state
     */
    public boolean isFinalState() {
        return this == TERMINATED;
    }

    /**
     * Validates if transition to target status is allowed.
     *
     * <p>Valid transitions:
     * <ul>
     *   <li>PENDING_ACTIVATION → ACTIVE, TERMINATED</li>
     *   <li>ACTIVE → SUSPENDED, TERMINATED</li>
     *   <li>SUSPENDED → ACTIVE, TERMINATED</li>
     *   <li>TERMINATED → (none, final state)</li>
     * </ul>
     *
     * @param target target status to transition to
     * @return true if transition is valid
     */
    public boolean canTransitionTo(TenantStatus target) {
        if (this == target) {
            return true; // No-op transition is always valid
        }
        
        return switch (this) {
            case PENDING_ACTIVATION -> target == ACTIVE || target == TERMINATED;
            case ACTIVE -> target == SUSPENDED || target == TERMINATED;
            case SUSPENDED -> target == ACTIVE || target == TERMINATED;
            case TERMINATED -> false; // Final state, no transitions allowed
        };
    }
}
