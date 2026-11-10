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

import java.util.stream.Stream;

/**
 * Authorizes on role names through a method reference: forbidden like a call.
 */
public final class ReferencesRoleCheck {

    /**
     * Lets a caller with either role through.
     *
     * @param caller the caller
     * @return whether it may proceed
     */
    public boolean allowed(CallerIdentity caller) {
        return Stream.of("PLATFORM_SUPER_ADMIN", "TENANT_OWNER").anyMatch(caller::hasRole);
    }
}
