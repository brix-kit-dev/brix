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
 *   <li>Application code MUST reference these constants instead of bare role strings.</li>
 *   <li>Codes are immutable once the first admin account exists; renaming requires
 *       a coordinated DB migration + token rotation.</li>
 * </ul>
 *
 * <h3>Roles</h3>
 * <pre>
 * PLATFORM_SUPER_ADMIN  formal platform super administrator
 * BOOTSTRAP             passwordless first-admin setup anchor
 * </pre>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @Frozen — role codes are stored in JWTs; changing them requires coordinated token rotation.
 */
public final class RoleCode {

    /**
    * Formal platform super administrator.
    * <p>Matches {@code PlatformAdminRole.PLATFORM_SUPER_ADMIN.name()}.
     */
    public static final String PLATFORM_SUPER_ADMIN = "PLATFORM_SUPER_ADMIN";

    /**
    * Passwordless bootstrap setup anchor.
    * <p>Matches {@code PlatformAdminRole.BOOTSTRAP.name()}.
     */
    public static final String BOOTSTRAP = "BOOTSTRAP";

    // Utility class — no instances.
    private RoleCode() {}
}
