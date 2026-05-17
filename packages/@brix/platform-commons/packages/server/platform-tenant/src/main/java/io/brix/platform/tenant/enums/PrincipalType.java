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
 * Principal Type Enumeration (Subject/C-side roles).
 *
 * <p>Defines the type of a principal within a tenant context.
 * Principals represent C-side (Subject) relationships in the B2B2C model,
 * as opposed to members which represent B-side (Actor) relationships.
 *
 * <h3>Actor/Subject Separation</h3>
 * <ul>
 *   <li>{@link TenantMemberType} — Actor (B-side): OWNER, ADMIN, MEMBER</li>
 *   <li>{@link PrincipalType} — Subject (C-side): CUSTOMER, GUEST</li>
 * </ul>
 *
 * <h3>Database Storage</h3>
 * <p>Stored as VARCHAR(20) in sys_tenant_principal.principal_type column.
 *
 * <h3>Token Model</h3>
 * <p>Principals receive Subject tokens (pid) which are mutually exclusive
 * with Actor tokens (mid). Subject tokens MUST NOT access admin APIs.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see TenantMemberType
 * @Frozen - DO NOT modify enum values without database migration
 */
// @Frozen - This enum is frozen. Adding/removing/renaming values requires database migration.
public enum PrincipalType {

    /**
     * Customer - primary C-side relationship with a tenant.
     *
     * <p>Characteristics:
     * <ul>
     *   <li>Service consumer (e.g., patient, student, buyer)</li>
     *   <li>Can have active business objects (Case, Order, Enrollment)</li>
     *   <li>Full Subject access based on permissions</li>
     *   <li>Lifecycle independent of any single business object</li>
     * </ul>
     *
     * <p><b>Typical industry mappings:</b>
     * <ul>
     *   <li>Healthcare: Patient</li>
     *   <li>Education: Student</li>
     *   <li>E-commerce: Buyer</li>
     * </ul>
     */
    CUSTOMER("Customer", "Service consumer (patient, student, buyer)"),

    /**
     * Guest - temporary or limited C-side relationship with a tenant.
     *
     * <p>Characteristics:
     * <ul>
     *   <li>Temporary subject access (e.g., trial user, visitor)</li>
     *   <li>May be time-limited or feature-limited</li>
     *   <li>Can be promoted to CUSTOMER upon business action</li>
     *   <li>No persistent business objects expected</li>
     * </ul>
     */
    GUEST("Guest", "Temporary or limited subject access");

    /**
     * Human-readable display name for UI presentation.
     */
    private final String displayName;

    /**
     * Detailed description for documentation and tooltips.
     */
    private final String description;

    /**
     * Constructor for PrincipalType enum.
     *
     * @param displayName human-readable display name
     * @param description detailed type description
     */
    PrincipalType(String displayName, String description) {
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
}
