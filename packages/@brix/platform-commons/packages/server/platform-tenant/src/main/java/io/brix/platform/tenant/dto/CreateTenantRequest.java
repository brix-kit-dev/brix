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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Objects;

/**
 * Data Transfer Object for tenant creation requests.
 *
 * <p>This DTO encapsulates all required information to create a new tenant
 * through {@link io.brix.platform.tenant.service.TenantProvisioningService#createTenant}.
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer - Platform Commons DTO</p>
 *
 * <h3>Validation Rules</h3>
 * <ul>
 *   <li><b>code:</b> Required, 2-64 characters, lowercase alphanumeric with hyphens,
 *       must start with letter, cannot end with hyphen</li>
 *   <li><b>name:</b> Required, 1-256 characters, display name for the tenant</li>
 * </ul>
 *
 * <h3>Code Format Guidelines</h3>
 * <p>The tenant code is used in URLs and external integrations:
 * <ul>
 *   <li>Subdomain: {@code acme.platform.com}</li>
 *   <li>API header: {@code X-Tenant-ID: acme}</li>
 *   <li>Database queries: {@code WHERE tenant_code = 'acme'}</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * CreateTenantRequest request = CreateTenantRequest.builder()
 *     .code("acme-corp")
 *     .name("ACME Corporation")
     *     .build();
 *
 * Tenant tenant = tenantProvisioningService.createTenant(request);
 * }</pre>
 *
 * <h3>Builder Pattern</h3>
 * <p>This class provides a builder pattern for fluent object construction.
 * Use {@link #builder()} to create instances.</p>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see io.brix.platform.tenant.service.TenantProvisioningService
 */
public class CreateTenantRequest {

    /**
     * Unique tenant code for identification and URL routing.
     *
     * <p>Used as the primary business identifier for the tenant.
     * Must be globally unique across all tenants.
     *
     * <h4>Format Requirements</h4>
     * <ul>
     *   <li>Start with a lowercase letter (a-z)</li>
     *   <li>Contain only lowercase letters, digits, and hyphens</li>
     *   <li>Cannot end with a hyphen</li>
     *   <li>Length: 2-64 characters</li>
     * </ul>
     *
     * <h4>Valid Examples</h4>
     * <p>{@code acme}, {@code acme-corp}, {@code company-123}</p>
     *
     * <h4>Invalid Examples</h4>
     * <p>{@code ACME} (uppercase), {@code 123corp} (starts with digit),
     * {@code acme-} (ends with hyphen)</p>
     */
    @NotBlank(message = "Tenant code is required")
    @Size(min = 2, max = 64, message = "Tenant code must be between 2 and 64 characters")
    @Pattern(
        regexp = "^[a-z][a-z0-9]*(-[a-z0-9]+)*$",
        message = "Tenant code must start with lowercase letter, contain only lowercase letters, digits, and hyphens, and cannot end with hyphen"
    )
    private String code;

    /**
     * Human-readable display name for the tenant.
     *
     * <p>Used in UI, reports, and communications. Does not need to be unique,
     * but should be descriptive enough to identify the tenant.
     */
    @NotBlank(message = "Tenant name is required")
    @Size(min = 1, max = 256, message = "Tenant name must be between 1 and 256 characters")
    private String name;

    /**
     * Deprecated pre-v3.0.10 owner field.
     *
     * <p>New tenant creation must not create a tenant member. The first owner
     * is established only through the FIRST_OWNER invitation workflow.
     */
    @Deprecated(since = "3.2.0", forRemoval = false)
    private Long ownerIdentityId;

    // ========================================================================
    // Constructors
    // ========================================================================

    /**
     * Default constructor for framework use.
     *
     * <p>Required for JSON deserialization and Spring binding.
     * Use {@link #builder()} for programmatic construction.
     */
    public CreateTenantRequest() {
    }

    /**
     * All-args constructor for complete initialization.
     *
     * @param code unique tenant code
     * @param name display name
     * @param ownerIdentityId identity ID of the tenant owner
     */
    public CreateTenantRequest(String code, String name, Long ownerIdentityId) {
        this.code = code;
        this.name = name;
        this.ownerIdentityId = ownerIdentityId;
    }

    // ========================================================================
    // Builder Pattern
    // ========================================================================

    /**
     * Creates a new builder instance for constructing CreateTenantRequest.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for fluent CreateTenantRequest construction.
     */
    public static class Builder {
        private String code;
        private String name;
        private Long ownerIdentityId;

        private Builder() {
        }

        /**
         * Sets the tenant code.
         *
         * @param code unique tenant code
         * @return this builder for chaining
         */
        public Builder code(String code) {
            this.code = code;
            return this;
        }

        /**
         * Sets the tenant name.
         *
         * @param name display name
         * @return this builder for chaining
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the owner identity ID.
         *
         * @param ownerIdentityId identity ID of the tenant owner
         * @return this builder for chaining
         */
        public Builder ownerIdentityId(Long ownerIdentityId) {
            this.ownerIdentityId = ownerIdentityId;
            return this;
        }

        /**
         * Builds the CreateTenantRequest instance.
         *
         * @return a new CreateTenantRequest with the configured values
         */
        public CreateTenantRequest build() {
            return new CreateTenantRequest(code, name, ownerIdentityId);
        }
    }

    // ========================================================================
    // Getters and Setters
    // ========================================================================

    /**
     * Gets the tenant code.
     *
     * @return the tenant code
     */
    public String getCode() {
        return code;
    }

    /**
     * Sets the tenant code.
     *
     * @param code the tenant code to set
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * Gets the tenant name.
     *
     * @return the tenant name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the tenant name.
     *
     * @param name the tenant name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the owner identity ID.
     *
     * @return the owner identity ID
     */
    public Long getOwnerIdentityId() {
        return ownerIdentityId;
    }

    /**
     * Sets the owner identity ID.
     *
     * @param ownerIdentityId the owner identity ID to set
     */
    public void setOwnerIdentityId(Long ownerIdentityId) {
        this.ownerIdentityId = ownerIdentityId;
    }

    // ========================================================================
    // Object Methods
    // ========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CreateTenantRequest that = (CreateTenantRequest) o;
        return Objects.equals(code, that.code) &&
               Objects.equals(name, that.name) &&
               Objects.equals(ownerIdentityId, that.ownerIdentityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, name, ownerIdentityId);
    }

    @Override
    public String toString() {
        return "CreateTenantRequest{" +
               "code='" + code + '\'' +
               ", name='" + name + '\'' +
               ", ownerIdentityId=" + ownerIdentityId +
               '}';
    }
}
