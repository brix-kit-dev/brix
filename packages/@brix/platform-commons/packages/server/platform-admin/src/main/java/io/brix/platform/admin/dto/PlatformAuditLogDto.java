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

/**
 * Read-only view of a platform audit log entry.
 *
 * <h3>Field Whitelist</h3>
 * <p>Only the fields listed here are returned to clients. Internal fields such as
 * {@code ownerMemberId} and {@code ownerOrgId} are excluded as they are not
 * meaningful in the platform-admin context.
 *
 * @param id           audit log ID
 * @param action       action code (e.g. SUPER_ADMIN_LOGIN_SUCCESS)
 * @param resourceType type of resource affected (e.g. PLATFORM_ADMIN, TENANT, SELF)
 * @param resourceId   ID of the affected resource (may be null)
 * @param description  human-readable description
 * @param actorId      identity_id of the actor who performed the action
 * @param clientIp     remote IP address (may be null)
 * @param success      whether the action succeeded
 * @param errorMessage error message if the action failed (may be null)
 * @param createdAt    timestamp when the event was recorded
 * @author Brix Platform Team
 * @since 3.2.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlatformAuditLogDto(
        Long id,
        String action,
        String resourceType,
        String resourceId,
        String description,
        Long actorId,
        String clientIp,
        boolean success,
        String errorMessage,
        OffsetDateTime createdAt
) {}
