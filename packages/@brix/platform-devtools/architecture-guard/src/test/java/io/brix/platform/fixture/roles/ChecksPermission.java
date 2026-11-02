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
 * Authorizes on a permission: allowed.
 */
public final class ChecksPermission {

    /**
     * Lets a caller with the read permission through.
     *
     * @param caller the caller
     * @return whether it may proceed
     */
    public boolean allowed(CallerIdentity caller) {
        return caller.hasPermission("platform:admin:read");
    }
}
