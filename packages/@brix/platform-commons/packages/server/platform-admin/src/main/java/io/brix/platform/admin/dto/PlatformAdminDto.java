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
 * Read-only view of a platform administrator account.
 *
 * <h3>Field Whitelist</h3>
 * <p>Only the fields listed here are returned to clients. Sensitive fields such as
 * {@code passwordHash} and {@code mfaSecret} MUST NOT appear in this DTO.
 *
 * @param adminId    {@code sys_platform_admin.id}
 * @param identityId {@code sys_identity.id}
 * @param username   display name
 * @param email      login identifier / email address
 * @param role       platform admin role code
 * @param status     account status (ACTIVE / SUSPENDED)
 * @param mfaEnabled whether MFA is currently enabled for this account
 * @param notes      optional administrative notes
 * @param createdAt  account creation timestamp
 * @author Brix Platform Team
 * @since 3.2.0
 */
public record PlatformAdminDto(
        @JsonSerialize(using = ToStringSerializer.class)
        Long adminId,
        @JsonSerialize(using = ToStringSerializer.class)
        Long identityId,
        String username,
        String email,
        String role,
        String status,
        boolean mfaEnabled,
        String notes,
        OffsetDateTime createdAt
) {}
