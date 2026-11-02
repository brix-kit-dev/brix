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
package io.brix.samples.fixturealpha.core;

import io.brix.platform.tenant.entity.TenantMemberRow;

/**
 * A plugin class that reads platform-tenant's relationship entity directly: forbidden.
 */
public final class AlphaTenantReader {

    /**
     * Reads a member row.
     *
     * @param row platform-tenant's internal entity
     * @return its tenant id
     */
    public long tenantOf(TenantMemberRow row) {
        return row.tenantId();
    }
}
