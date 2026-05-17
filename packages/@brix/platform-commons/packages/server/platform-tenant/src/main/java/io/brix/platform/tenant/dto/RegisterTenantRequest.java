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
package io.brix.platform.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for tenant self-service registration.
 *
 * <p>Used by the public registration endpoint
 * {@code POST /api/public/tenant/register}. Contains tenant information
 * and the registrant's identity reference (extracted from Identity Token).
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons DTO — self-service registration request.</p>
 *
 * <h3>Validation Rules</h3>
 * <ul>
 *   <li><b>code:</b> Required, 2-64 chars, lowercase alphanumeric with hyphens,
 *       must start with a letter, cannot end with a hyphen</li>
 *   <li><b>name:</b> Required, 1-256 chars, display name for the tenant</li>
 *   <li><b>ownerIdentityId:</b> Required, the registrant's identity ID
 *       (in production, extracted from Identity Token by auth middleware)</li>
 *   <li><b>contactEmail:</b> Optional, valid email format if provided</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see io.brix.platform.tenant.controller.TenantRegistrationController
 */
public class RegisterTenantRequest {

    /**
     * Unique tenant code for URL routing and identification.
     *
     * <p>Format: lowercase letters, digits, and hyphens. Must start with a letter.
     * Used as subdomain component: {@code {code}.console.brix.com}.
     */
    @NotBlank(message = "Tenant code is required")
    @Size(min = 2, max = 64, message = "Tenant code must be between 2 and 64 characters")
    @Pattern(
        regexp = "^[a-z][a-z0-9]*(-[a-z0-9]+)*$",
        message = "Tenant code must start with lowercase letter, contain only lowercase letters, digits, and hyphens, and cannot end with hyphen"
    )
    private String code;

    /**
     * Human-readable display name for the tenant organization.
     */
    @NotBlank(message = "Tenant name is required")
    @Size(min = 1, max = 256, message = "Tenant name must be between 1 and 256 characters")
    private String name;

    /**
     * Identity ID of the registrant who will become the tenant OWNER.
     *
     * <p>In production, this value is extracted from the Identity Token
     * by authentication middleware. The Identity Token is issued after
     * the registrant completes identity verification (phone/email + OTP).
     */
    @NotNull(message = "Owner identity ID is required")
    private Long ownerIdentityId;

    /**
     * Contact email for the tenant organization.
     *
     * <p>Optional. Used for administrative communications,
     * billing notifications, and account recovery.
     */
    @Email(message = "Contact email must be a valid email address")
    @Size(max = 256, message = "Contact email must not exceed 256 characters")
    private String contactEmail;

    // ========================================================================
    // Constructors
    // ========================================================================

    /**
     * Default constructor for framework deserialization.
     */
    public RegisterTenantRequest() {
    }

    // ========================================================================
    // Getters and Setters
    // ========================================================================

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getOwnerIdentityId() {
        return ownerIdentityId;
    }

    public void setOwnerIdentityId(Long ownerIdentityId) {
        this.ownerIdentityId = ownerIdentityId;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }
}
