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
 * Stable platform role identifiers (super-admin 4.0.1 §4).
 *
 * <p>A role ID is stored with each platform administrator and carried by platform tokens, so once published it never
 * changes and is never reused. Endpoints authorize on {@link PermissionCode} IDs, never on a role name (super-admin
 * BRX-SA-AUTH-006).</p>
 *
 * <p>Bootstrap is not a role: it is the type and scope of a short-lived setup session (super-admin §3.3).</p>
 *
 * @since 3.2.0
 */
public final class RoleCode {

    /** The only formal platform administrator role in the MVP. */
    public static final String PLATFORM_SUPER_ADMIN = "PLATFORM_SUPER_ADMIN";

    private RoleCode() {
    }
}
