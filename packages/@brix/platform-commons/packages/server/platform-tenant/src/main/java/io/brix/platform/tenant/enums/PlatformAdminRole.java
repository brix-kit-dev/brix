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
 * Platform administrator roles allowed by the v2.0 super-admin model.
 *
 * <p>The MVP intentionally keeps only two roles: the formal platform super
 * administrator and the passwordless bootstrap anchor. Additional historical
 * roles are removed by migration and must not be reintroduced without an SSoT
 * revision.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public enum PlatformAdminRole {

    /** Formal platform super administrator. */
    PLATFORM_SUPER_ADMIN("Platform Super Administrator", "Formal platform super administrator", 100),

    /** Passwordless bootstrap anchor used only during first-admin setup. */
    BOOTSTRAP("Bootstrap", "Passwordless first-admin setup anchor", 10);

    private final String displayName;
    private final String description;
    private final int privilegeLevel;

    PlatformAdminRole(String displayName, String description, int privilegeLevel) {
        this.displayName = displayName;
        this.description = description;
        this.privilegeLevel = privilegeLevel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public int getPrivilegeLevel() {
        return privilegeLevel;
    }

    public boolean hasPrivilegeOver(PlatformAdminRole other) {
        return other != null && this.privilegeLevel >= other.privilegeLevel;
    }

    public boolean canManageRole(PlatformAdminRole targetRole) {
        return this == PLATFORM_SUPER_ADMIN && targetRole == PLATFORM_SUPER_ADMIN;
    }

    public boolean canModifyTenantData() {
        return this == PLATFORM_SUPER_ADMIN;
    }

    public boolean requiresMfa() {
        return this == PLATFORM_SUPER_ADMIN;
    }

    public boolean isSuperAdmin() {
        return this == PLATFORM_SUPER_ADMIN;
    }
}
