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
package io.brix.platform.fixture.roles;

/**
 * Declares its own role check and authorizes with it: forbidden, declaring a role check exempts no class.
 */
public final class ChecksRoleThroughHelper {

    private final CallerIdentity caller;

    /**
     * Creates the check.
     *
     * @param caller the caller
     */
    public ChecksRoleThroughHelper(CallerIdentity caller) {
        this.caller = caller;
    }

    /**
     * Lets a super admin through.
     *
     * @return whether it may proceed
     */
    public boolean allowed() {
        return hasRole("PLATFORM_SUPER_ADMIN");
    }

    /**
     * Wraps the caller's role check.
     *
     * @param role role name
     * @return whether the caller has it
     */
    public boolean hasRole(String role) {
        return caller.hasRole(role);
    }
}
