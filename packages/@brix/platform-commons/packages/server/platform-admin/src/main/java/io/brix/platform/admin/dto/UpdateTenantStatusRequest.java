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
 * Request DTO for changing a tenant's lifecycle status.
 *
 * <h3>Allowed Transitions (SSOT §7 Lifecycle FSM)</h3>
 * <pre>
 * PENDING_ACTIVATION → ACTIVE
 * ACTIVE             → SUSPENDED
 * SUSPENDED          → ACTIVE
 * Any state          → TERMINATED (irreversible)
 * </pre>
 *
 * <h3>Security (R-10)</h3>
 * <p>The {@code reason} field MUST NOT contain passwords, tokens, or secret material.
 *
 * @param status target tenant lifecycle status
 * @param reason optional human-readable reason for the status change (max 512 chars)
 * @author Brix Platform Team
 * @since 3.2.0
 */
public record UpdateTenantStatusRequest(

        @NotBlank(message = "status must not be blank")
        @Pattern(
                regexp = "ACTIVE|SUSPENDED|TERMINATED|PENDING_ACTIVATION",
                message = "status must be one of: ACTIVE, SUSPENDED, TERMINATED, PENDING_ACTIVATION"
        )
        String status,

        @Size(max = 512, message = "reason must not exceed 512 characters")
        String reason
) {}
