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
 * Profile Reference Type Enumeration.
 *
 * <p>Polymorphic discriminator for {@code biz_user_profile.ref_type},
 * indicating whether the profile references a sys_tenant_member (Actor)
 * or a sys_tenant_principal (Subject).
 *
 * <h3>Polymorphic Model</h3>
 * <pre>
 * biz_user_profile.ref_type = 'MEMBER'    → ref_id references sys_tenant_member.id
 * biz_user_profile.ref_type = 'PRINCIPAL' → ref_id references sys_tenant_principal.id
 * </pre>
 *
 * <h3>Database Storage</h3>
 * <p>Stored as VARCHAR(20) in biz_user_profile.ref_type column.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @Frozen - DO NOT modify enum values without database migration
 */
// @Frozen - This enum is frozen. Adding/removing/renaming values requires database migration.
public enum ProfileRefType {

    /**
     * Reference to sys_tenant_member (Actor/B-side).
     *
     * <p>The profile belongs to a tenant member (OWNER, ADMIN, or MEMBER).
     * {@code ref_id} points to {@code sys_tenant_member.id}.
     */
    MEMBER("Member Profile", "Profile for a tenant member (Actor/B-side)"),

    /**
     * Reference to sys_tenant_principal (Subject/C-side).
     *
     * <p>The profile belongs to a tenant principal (CUSTOMER or GUEST).
     * {@code ref_id} points to {@code sys_tenant_principal.id}.
     */
    PRINCIPAL("Principal Profile", "Profile for a tenant principal (Subject/C-side)");

    /**
     * Human-readable display name for UI presentation.
     */
    private final String displayName;

    /**
     * Detailed description for documentation and tooltips.
     */
    private final String description;

    /**
     * Constructor for ProfileRefType enum.
     *
     * @param displayName human-readable display name
     * @param description detailed type description
     */
    ProfileRefType(String displayName, String description) {
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
