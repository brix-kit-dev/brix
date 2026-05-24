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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for an admin changing their own password.
 *
 * <h3>Security</h3>
 * <p>Both fields are mandatory and are never logged. The service verifies
 * {@code oldPassword} against the stored hash before applying the update.
 * Changing the password increments {@code token_version} to invalidate all
 * existing JWTs for this identity (A3 security baseline).
 *
 * @param oldPassword current password — used for re-authentication before change
 * @param newPassword new password (min 12 chars, enforced by the service layer)
 * @param totpCode current six-digit TOTP code
 * @author Brix Platform Team
 * @since 3.2.0
 */
public record ChangeOwnPasswordRequest(

        @NotBlank(message = "oldPassword must not be blank")
        String oldPassword,

        @NotBlank(message = "newPassword must not be blank")
        @Size(min = 12, max = 128, message = "newPassword must be between 12 and 128 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message = "newPassword must contain upper, lower, digit and symbol")
        String newPassword,

        @NotBlank(message = "totpCode must not be blank")
        @Size(min = 6, max = 6, message = "totpCode must be 6 digits")
        @Pattern(regexp = "\\d{6}", message = "totpCode must be 6 digits")
        String totpCode
) {}
