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

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new platform administrator account.
 *
 * <h3>Validation</h3>
 * <ul>
 *   <li>All fields are mandatory.</li>
 *   <li>{@code role} must be one of the four defined role codes.</li>
 *   <li>{@code notes} is optional — max 1 000 chars.</li>
 * </ul>
 *
 * <h3>Security</h3>
 * <p>No password is supplied in this request. The service generates a secure
 * temporary password that is returned once in {@link CreatePlatformAdminResponse}
 * and never written to any log or audit reason field.
 *
 * @param username display name for the new admin account
 * @param email    globally unique email; used as the login identifier
 * @param role     platform admin role code (SUPER_ADMIN / PLATFORM_ADMIN / SUPPORT_ADMIN / AUDITOR)
 * @param notes    optional notes about why this account was created (max 1000 chars)
 * @author Brix Platform Team
 * @since 3.2.0
 */
public record CreatePlatformAdminRequest(

        @NotBlank(message = "username must not be blank")
        @Size(max = 128, message = "username must not exceed 128 characters")
        String username,

        @NotBlank(message = "email must not be blank")
        @Email(message = "email must be a valid email address")
        @Size(max = 256, message = "email must not exceed 256 characters")
        String email,

        @NotNull(message = "role must not be null")
        @Pattern(
                regexp = "SUPER_ADMIN|PLATFORM_ADMIN|SUPPORT_ADMIN|AUDITOR",
                message = "role must be one of: SUPER_ADMIN, PLATFORM_ADMIN, SUPPORT_ADMIN, AUDITOR"
        )
        String role,

        @Size(max = 1000, message = "notes must not exceed 1000 characters")
        String notes
) {}
