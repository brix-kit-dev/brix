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
package io.brix.platform.admin.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * Read-only view of a tenant as seen from the platform administration perspective.
 *
 * <h3>Field Whitelist</h3>
 * <p>Only the fields listed here are returned. Internal configuration fields
 * (password policy, notification channels, etc.) are excluded.
 *
 * @param tenantId  {@code sys_tenant.id}
 * @param code      unique tenant code / slug
 * @param name      display name
 * @param status    tenant lifecycle status (PENDING_ACTIVATION / ACTIVE / SUSPENDED / TERMINATED)
 * @param createdAt tenant creation timestamp
 * @author Brix Platform Team
 * @since 3.2.0
 */
public record PlatformTenantDto(
        @JsonSerialize(using = ToStringSerializer.class)
        Long tenantId,
        String code,
        String name,
        String status,
        OffsetDateTime createdAt
) {}
