/*
 * Copyright 2026 Brix Authors
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
package io.brix.platform.audit.api;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Catalog of platform audit action ids, owned by the platform audit Data Owner (super-admin §5:
 * audit action ids belong to the versioned contract). The identity actions keep the names
 * platform-auth's {@code AuditAction} gave them, so rows written before the split read the same.
 *
 * @since 3.2.0
 */
public final class PlatformAuditActions {

    public static final String SUPER_ADMIN_LOGIN_SUCCESS = "SUPER_ADMIN_LOGIN_SUCCESS";
    public static final String SUPER_ADMIN_LOGIN_FAILED = "SUPER_ADMIN_LOGIN_FAILED";
    public static final String SUPER_ADMIN_LOGOUT = "SUPER_ADMIN_LOGOUT";
    public static final String SUPER_ADMIN_CREATED = "SUPER_ADMIN_CREATED";
    public static final String SUPER_ADMIN_REVOKED = "SUPER_ADMIN_REVOKED";
    public static final String SUPER_ADMIN_PASSWORD_RESET = "SUPER_ADMIN_PASSWORD_RESET";
    public static final String SUPER_ADMIN_PASSWORD_CHANGED = "SUPER_ADMIN_PASSWORD_CHANGED";
    public static final String BOOTSTRAP_SESSION_OPENED = "BOOTSTRAP_SESSION_OPENED";
    public static final String BOOTSTRAP_ADMIN_CREATED = "BOOTSTRAP_ADMIN_CREATED";
    public static final String BOOTSTRAP_ADMIN_DEACTIVATED = "BOOTSTRAP_ADMIN_DEACTIVATED";
    public static final String SETUP_TOKEN_ISSUED = "SETUP_TOKEN_ISSUED";
    public static final String SETUP_TOKEN_USED = "SETUP_TOKEN_USED";
    public static final String SETUP_TOKEN_INVALID = "SETUP_TOKEN_INVALID";
    public static final String IDENTITY_PASSWORD_SET = "IDENTITY_PASSWORD_SET";
    public static final String TOTP_BOUND = "TOTP_BOUND";
    public static final String IDENTITY_ACTIVATED = "IDENTITY_ACTIVATED";
    public static final String IDENTITY_DISABLED = "IDENTITY_DISABLED";
    public static final String IDENTITY_LOCKED = "IDENTITY_LOCKED";
    public static final String TENANT_CREATED = "TENANT_CREATED";
    public static final String TENANT_STATUS_CHANGED = "TENANT_STATUS_CHANGED";

    /** A platform administrator sent a FIRST_OWNER invitation (super-admin §5; contract 1.1.0). */
    public static final String FIRST_OWNER_INVITATION_CREATED = "FIRST_OWNER_INVITATION_CREATED";

    /** A platform administrator resent a FIRST_OWNER invitation (super-admin §5; contract 1.1.0). */
    public static final String FIRST_OWNER_INVITATION_RESENT = "FIRST_OWNER_INVITATION_RESENT";

    /** A platform administrator revoked a FIRST_OWNER invitation (super-admin §5; contract 1.1.0). */
    public static final String FIRST_OWNER_INVITATION_REVOKED = "FIRST_OWNER_INVITATION_REVOKED";

    /** Login-time Actor discovery (multi-tenant §6.3, §6.4 P-1). Written only by the runtime custodian. */
    public static final String LOGIN_TENANT_DISCOVERY = "LOGIN_TENANT_DISCOVERY";

    /** Resend after an invitee completes setup (multi-tenant §6.4 P-3). Written only by the runtime custodian. */
    public static final String FIRST_OWNER_INVITEE_SETUP_FANOUT = "FIRST_OWNER_INVITEE_SETUP_FANOUT";

    /** Cross-tenant policy uses; only {@link PlatformAuditWriters#RUNTIME_TENANT_POLICY} writes them. */
    public static final Set<String> POLICY_USES = Set.of(LOGIN_TENANT_DISCOVERY, FIRST_OWNER_INVITEE_SETUP_FANOUT);

    /** Actions the owner itself records through the internal contract. */
    public static final Set<String> OWNER_RECORDED = Set.of(
        SUPER_ADMIN_LOGIN_SUCCESS, SUPER_ADMIN_LOGIN_FAILED, SUPER_ADMIN_LOGOUT, SUPER_ADMIN_CREATED,
        SUPER_ADMIN_REVOKED, SUPER_ADMIN_PASSWORD_RESET, SUPER_ADMIN_PASSWORD_CHANGED,
        BOOTSTRAP_SESSION_OPENED, BOOTSTRAP_ADMIN_CREATED, BOOTSTRAP_ADMIN_DEACTIVATED,
        SETUP_TOKEN_ISSUED, SETUP_TOKEN_USED, SETUP_TOKEN_INVALID,
        IDENTITY_PASSWORD_SET, TOTP_BOUND, IDENTITY_ACTIVATED, IDENTITY_DISABLED, IDENTITY_LOCKED,
        TENANT_CREATED, TENANT_STATUS_CHANGED,
        FIRST_OWNER_INVITATION_CREATED, FIRST_OWNER_INVITATION_RESENT, FIRST_OWNER_INVITATION_REVOKED);

    /** Every action id in the catalog. */
    public static final Set<String> ALL = Stream.concat(OWNER_RECORDED.stream(), POLICY_USES.stream())
        .collect(Collectors.toUnmodifiableSet());

    private PlatformAuditActions() {
    }
}
