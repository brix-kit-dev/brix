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
package io.brix.platform.auth;

/**
 * Platform-level Role Code Constants.
 *
 * <h3>Architectural Position</h3>
 * <p>Layer 2C ({@code platform-auth}). These codes match the enum names defined by
 * {@code io.brix.platform.tenant.enums.PlatformAdminRole} and are written into the
 * {@code platform_role} JWT claim and {@code sys_platform_admin.role} column.</p>
 *
 * <h3>Design Rules (SSOT §11 Red-Line R-3)</h3>
 * <ul>
 *   <li>Application code MUST reference these constants — bare strings like
 *       {@code "SUPER_ADMIN"} are prohibited outside of this class,
 *       RBAC seed SQL, and unit-test assertions.</li>
 *   <li>Codes are immutable once the first admin account exists; renaming requires
 *       a coordinated DB migration + token rotation.</li>
 * </ul>
 *
 * <h3>Role Hierarchy</h3>
 * <pre>
 * PLATFORM_SUPER_ADMIN  (highest — full system access)
 *       │
 * PLATFORM_ADMIN        (day-to-day platform operations)
 *       │
 * SUPPORT_ADMIN         (customer support, limited access)
 *       │
 * PLATFORM_AUDITOR      (read-only compliance monitoring)
 * </pre>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @Frozen — role codes are stored in JWTs; changing them requires coordinated token rotation.
 */
public final class RoleCode {

    /**
     * Super Administrator — full system access.
     * <p>Matches {@code PlatformAdminRole.SUPER_ADMIN.name()}.
     */
    public static final String PLATFORM_SUPER_ADMIN = "SUPER_ADMIN";

    /**
     * Platform Administrator — manages tenants and other admin accounts.
     * <p>Matches {@code PlatformAdminRole.PLATFORM_ADMIN.name()}.
     */
    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    /**
     * Support Administrator — customer support operations.
     * <p>Matches {@code PlatformAdminRole.SUPPORT_ADMIN.name()}.
     */
    public static final String SUPPORT_ADMIN = "SUPPORT_ADMIN";

    /**
     * Platform Auditor — read-only compliance and monitoring.
     * <p>Matches {@code PlatformAdminRole.AUDITOR.name()}.
     */
    public static final String PLATFORM_AUDITOR = "AUDITOR";

    // Utility class — no instances.
    private RoleCode() {}
}
