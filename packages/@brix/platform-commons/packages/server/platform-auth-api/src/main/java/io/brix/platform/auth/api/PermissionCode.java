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
package io.brix.platform.auth.api;

/**
 * Stable platform permission identifiers: the versioned catalog of super-admin 4.0.1 §3.3 and §4.
 *
 * <p>An endpoint descriptor names these IDs under {@code authorization.permissions} and the Dispatcher checks them
 * before the handler runs. They are also seeded in {@code auth_permission} and carried by platform tokens, so once
 * published an ID never changes and is never reused, even after no endpoint uses it any more.</p>
 *
 * @since 3.2.0
 */
public final class PermissionCode {

    public static final String PLATFORM_ADMIN_READ = "platform:admin:read";
    public static final String PLATFORM_ADMIN_CREATE = "platform:admin:create";
    public static final String PLATFORM_ADMIN_REVOKE = "platform:admin:revoke";
    public static final String PLATFORM_ADMIN_RESET_PASSWORD = "platform:admin:reset-password";

    /**
     * Kept, though no endpoint has used it since v4.0: administrators manage their own credentials on the identity
     * protocol surface (super-admin §3.3).
     */
    public static final String PLATFORM_ADMIN_CHANGE_OWN_PASSWORD = "platform:admin:change-own-password";

    public static final String PLATFORM_AUDIT_READ = "platform:audit:read";
    public static final String PLATFORM_TENANT_READ = "platform:tenant:read";
    public static final String PLATFORM_TENANT_CREATE = "platform:tenant:create";
    public static final String PLATFORM_TENANT_UPDATE_STATUS = "platform:tenant:update-status";
    public static final String PLATFORM_TENANT_FIRST_OWNER_INVITE = "platform:tenant:first-owner-invite";

    private PermissionCode() {
    }
}
