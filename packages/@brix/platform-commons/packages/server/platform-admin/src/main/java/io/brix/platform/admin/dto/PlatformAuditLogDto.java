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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * Read-only view of a platform audit log entry.
 *
 * <h3>Field Whitelist</h3>
 * <p>Only the fields listed here are returned to clients. Internal fields such as
 * {@code ownerMemberId} and {@code ownerOrgId} are excluded as they are not
 * meaningful in the platform-admin context.
 *
 * @param id              audit log ID
 * @param actorIdentityId identity ID of the actor who performed the action
 * @param actorUsername   actor display name when available
 * @param action          action code
 * @param targetType      type of resource affected
 * @param targetId        ID of the affected resource
 * @param tenantId        tenant ID; always null for platform-scoped audit rows
 * @param ip              remote IP address
 * @param userAgent       user agent string
 * @param result          SUCCESS or FAILURE
 * @param reason          audit description or failure reason
 * @param requestId       correlation ID
 * @param createdAt       timestamp when the event was recorded
 * @author Brix Platform Team
 * @since 3.2.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlatformAuditLogDto(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        @JsonSerialize(using = ToStringSerializer.class)
        Long actorIdentityId,
        String actorUsername,
        String action,
        String targetType,
        String targetId,
        @JsonSerialize(using = ToStringSerializer.class)
        Long tenantId,
        String ip,
        String userAgent,
        String result,
        String reason,
        String requestId,
        OffsetDateTime createdAt
) {}
