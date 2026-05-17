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
 * Platform-level Audit Action Code Constants.
 *
 * <h3>Architectural Position</h3>
 * <p>Layer 2C ({@code platform-auth}). These codes are written into the
 * {@code action} column of {@code biz_audit_log} by the {@code platform-admin} module
 * via {@code AuditService.log(AuditEvent)}.</p>
 *
 * <h3>Design Rules</h3>
 * <ul>
 *   <li>All action codes MUST use these constants — never bare strings in application code.</li>
 *   <li>Codes are immutable once written to the DB; renaming requires a data-migration.</li>
 *   <li>The {@code reason} field of an {@code AuditEvent} MUST NOT contain any password,
 *       token, or secret value (SSOT §10 red-line R-10).</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @Frozen — action codes are persisted; changing them requires a DB migration.
 */
public final class AuditAction {

    // ===== Authentication =====

    /**
     * Platform super-admin login succeeded.
     * <p>Target type: {@code SELF}. Logged in {@code PlatformAuthController}.
     */
    public static final String SUPER_ADMIN_LOGIN_SUCCESS = "SUPER_ADMIN_LOGIN_SUCCESS";

    /**
     * Platform super-admin login failed (bad password, locked, MFA failure, etc.).
     * <p>Target type: {@code SELF}. Logged in {@code PlatformAuthController}.
     */
    public static final String SUPER_ADMIN_LOGIN_FAILED = "SUPER_ADMIN_LOGIN_FAILED";

    /**
     * Platform super-admin logged out (token revoked via logout endpoint).
     * <p>Target type: {@code SELF}. Logged in {@code PlatformAuthController}.
     */
    public static final String SUPER_ADMIN_LOGOUT = "SUPER_ADMIN_LOGOUT";

    // ===== Admin Lifecycle =====

    /**
     * A new platform administrator account was created.
     * <p>Target type: {@code PLATFORM_ADMIN}. Target ID: new admin's identity_id.
     */
    public static final String SUPER_ADMIN_CREATED = "SUPER_ADMIN_CREATED";

    /**
     * A platform administrator account was disabled.
     * <p>Target type: {@code PLATFORM_ADMIN}. Target ID: disabled admin's identity_id.
     */
    public static final String SUPER_ADMIN_DISABLED = "SUPER_ADMIN_DISABLED";

    // ===== Password =====

    /**
     * An administrator's password was reset by another admin (temporary password issued).
     * <p>Target type: {@code PLATFORM_ADMIN}. Target ID: target admin's identity_id.
     * <p><b>Security:</b> the {@code reason} field MUST only state "Password has been reset";
     * never include the actual password (plaintext or hash).
     */
    public static final String SUPER_ADMIN_PASSWORD_RESET = "SUPER_ADMIN_PASSWORD_RESET";

    /**
     * An administrator changed their own password via the change-password endpoint.
     * <p>Target type: {@code SELF}. Logged in {@code PlatformAdminController}.
     */
    public static final String SUPER_ADMIN_PASSWORD_CHANGED = "SUPER_ADMIN_PASSWORD_CHANGED";

    // ===== Tenant =====

    /**
     * A tenant's lifecycle status was changed by a platform admin.
     * <p>Target type: {@code TENANT}. Target ID: affected tenant's ID.
     * <p>Payload includes before/after status values in the audit context JSON.
     */
    public static final String TENANT_STATUS_CHANGED = "TENANT_STATUS_CHANGED";

    // Utility class — no instances.
    private AuditAction() {}
}
