/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.sdk.event;

/**
 * Runtime scope carried by the canonical integration event envelope.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public enum EventScope {

    /**
     * Tenant-scoped fact. The canonical envelope must carry a tenant id.
     */
    TENANT,

    /**
     * Platform-scoped fact. The canonical envelope must not claim tenant ownership.
     */
    PLATFORM
}
