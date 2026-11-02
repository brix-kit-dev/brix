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
package io.brix.platform.fixture;

import io.brix.enterprise.host.fixture.HostOnlyType;

/**
 * A platform-commons class that uses Host source: forbidden.
 */
public final class PlatformUsesHost {

    /**
     * Returns the forbidden type for bytecode import.
     *
     * @return the Host type
     */
    public Class<HostOnlyType> hostType() {
        return HostOnlyType.class;
    }
}
