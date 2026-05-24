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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * Platform admin login response.
 *
 * <h3>Field Whitelist</h3>
 * <p>Only the fields declared here are ever returned. Fields such as
 * {@code passwordHash} or {@code mfaSecret} MUST NOT appear in this DTO.
 *
 * <h3>Token Design</h3>
 * <p>The issued JWT carries {@code scope=PLATFORM} and {@code platform_role}
 * but deliberately omits {@code tenant_id} / {@code mid} / {@code pid} claims.
 *
 * @param status            login outcome — one of: COMPLETE, PASSWORD_MUST_CHANGE, MFA_REQUIRED
 * @param accessToken       signed JWT (null for MFA_REQUIRED / PASSWORD_MUST_CHANGE)
 * @param refreshToken      opaque refresh token (null for non-COMPLETE statuses)
 * @param expiresIn         access token TTL in seconds
 * @param platformRole      role string (PLATFORM_SUPER_ADMIN)
 * @param permissions       permission code list embedded in the JWT
 * @param identityId        authenticated identity id
 * @param email             authenticated identity email
 * @param displayName       authenticated identity display name
 * @param mustChangePassword true if the admin must rotate their password before continuing
 * @param mfaChallengeToken short-lived token for TOTP verification (non-null only when MFA_REQUIRED)
 * @author Brix Platform Team
 * @since 3.2.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlatformLoginResponse(
        String status,
        String accessToken,
        String refreshToken,
        long expiresIn,
        String platformRole,
        List<String> permissions,
        @JsonSerialize(using = ToStringSerializer.class)
        Long identityId,
        String email,
        String displayName,
        Boolean mustChangePassword,
        String mfaChallengeToken
) {}
