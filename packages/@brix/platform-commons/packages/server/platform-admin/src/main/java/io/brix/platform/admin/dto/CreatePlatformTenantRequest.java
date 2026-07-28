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
 * Request DTO for creating a new tenant via the platform super-admin console.
 *
 * <h3>Architecture Position</h3>
 * <p>L2B {@code platform-admin} operational module. Consumed by the
 * descriptor-declared tenant creation handler.</p>
 *
 * <h3>Tenant Code Format</h3>
 * <pre>
 * - Starts with a lowercase letter (a-z)
 * - Contains only lowercase letters, digits, and hyphens
 * - Cannot end with a hyphen
 * - Length: 2–64 characters
 * Examples: acme, acme-corp, company-123
 * </pre>
 *
 * <h3>Lifecycle</h3>
 * <p>Tenants created via this endpoint are initialised in
 * {@code PENDING_ACTIVATION} status. A platform admin must explicitly
 * activate them ({@code PATCH /api/platform/tenants/{id}/status}) before
 * users of that tenant can log in.
 *
 * @param code unique tenant code (URL-safe slug)
 * @param name human-readable display name (1–256 characters)
 * @author Brix Platform Team
 * @since 3.2.0
 */
public record CreatePlatformTenantRequest(

        @NotBlank(message = "code must not be blank")
        @Size(min = 2, max = 64, message = "code must be between 2 and 64 characters")
        @Pattern(
                regexp = "^[a-z][a-z0-9]*(-[a-z0-9]+)*$",
                message = "code must start with a lowercase letter and contain only lowercase letters, digits and hyphens; cannot end with a hyphen"
        )
        String code,

        @NotBlank(message = "name must not be blank")
        @Size(min = 1, max = 256, message = "name must be between 1 and 256 characters")
        String name
) {}
