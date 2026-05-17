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
 * MFA (Multi-Factor Authentication) Policy Enumeration.
 *
 * <p>Defines the MFA enforcement policy for a tenant.
 *
 * <h3>Database Storage</h3>
 * <p>Stored as VARCHAR(20) in sys_tenant.mfa_policy column.
 *
 * @author Brix Platform Team
 * @since 3.1.0
 */
public enum MfaPolicy {

    DISABLED("Disabled", "MFA is not available for tenant members"),
    OPTIONAL("Optional", "MFA is available but not required"),
    REQUIRED("Required", "All tenant members must enable MFA");

    private final String displayName;
    private final String description;

    MfaPolicy(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
