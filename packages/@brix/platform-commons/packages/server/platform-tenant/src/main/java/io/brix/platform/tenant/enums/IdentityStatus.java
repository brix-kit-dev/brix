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
 * Status lifecycle for global identities.
 *
 * <p>This enum is deliberately independent from {@link MemberStatus}. Tenant
 * memberships keep their own membership lifecycle while {@code sys_identity}
 * expresses account setup, login, lockout, and disablement states.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public enum IdentityStatus {

    /** Account exists but has not completed password setup and TOTP binding. */
    PENDING_SETUP,

    /** Account is fully activated and may authenticate through its permitted flows. */
    ACTIVE,

    /** Account is temporarily locked by security policy. */
    LOCKED,

    /** Account is permanently disabled until an administrator re-enables it. */
    DISABLED;

    /**
     * Returns whether this state may transition to {@link #ACTIVE} through an
     * approved setup or unlock flow.
     *
     * @return {@code true} when activation is allowed
     */
    public boolean canBeActivated() {
        return this == PENDING_SETUP || this == LOCKED;
    }

    /**
     * Returns whether password login is allowed for this identity state.
     *
     * @return {@code true} only for active identities
     */
    public boolean allowsLogin() {
        return this == ACTIVE;
    }
}