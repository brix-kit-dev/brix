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
 * Implements role and permission checks, like platform-auth's authenticated user: not a caller of them.
 */
public final class CallerIdentity {

    /**
     * Checks a role name.
     *
     * @param role role name
     * @return whether the caller has it
     */
    public boolean hasRole(String role) {
        return false;
    }

    /**
     * Checks a permission.
     *
     * @param permission permission id
     * @return whether the caller has it
     */
    public boolean hasPermission(String permission) {
        return false;
    }
}
