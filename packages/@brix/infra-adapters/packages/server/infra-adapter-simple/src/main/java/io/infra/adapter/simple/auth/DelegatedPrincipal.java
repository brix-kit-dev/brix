/*
 * Copyright 2026 Brix Authors
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
package io.infra.adapter.simple.auth;

import java.security.Principal;
import java.util.Collections;
import java.util.Set;

import io.runtime.sdk.capability.DataScope;

/**
 * Delegated Authentication Principal
 * 
 * <p>Represents the authenticated user in delegated authentication mode.
 * Contains user identity, permissions, roles, and data scopes.</p>
 * 
 * <h3>Architecture Note</h3>
 * <p>Extracted from DelegatedAuthContextCapability as a standalone class
 * following the Single Responsibility Principle.</p>
 * 
 * <p><b>Technical Notes:</b>
 * User identity object for delegated authentication mode, containing user ID,
 * username, tenant ID, permissions, roles, and other information.
 * Extracted from DelegatedAuthContextCapability inner class to standalone class.</p>
 *
 * @author Brix Platform Authors
 * @since 3.0.0
 */
public class DelegatedPrincipal implements Principal {

    private final String userId;
    private final String username;
    private final String tenantId;
    private final Set<String> permissions;
    private final Set<String> roles;
    private final Set<DataScope> dataScopes;

    private DelegatedPrincipal(Builder builder) {
        this.userId = builder.userId;
        this.username = builder.username;
        this.tenantId = builder.tenantId;
        this.permissions = Collections.unmodifiableSet(
                builder.permissions != null ? builder.permissions : Collections.emptySet());
        this.roles = Collections.unmodifiableSet(
                builder.roles != null ? builder.roles : Collections.emptySet());
        this.dataScopes = Collections.unmodifiableSet(
                builder.dataScopes != null ? builder.dataScopes : Collections.emptySet());
    }

    @Override
    public String getName() {
        return userId;
    }

    /**
     * Gets the user ID.
     *
     * @return User ID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Gets the username.
     *
     * @return Username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Gets the tenant ID.
     *
     * @return Tenant ID, may be null
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Gets the user's permissions.
     *
     * @return Immutable set of permissions
     */
    public Set<String> getPermissions() {
        return permissions;
    }

    /**
     * Gets the user's roles.
     *
     * @return Immutable set of roles
     */
    public Set<String> getRoles() {
        return roles;
    }

    /**
     * Gets the user's authorized data scopes.
     *
     * @return Immutable set of data scopes
     */
    public Set<DataScope> getDataScopes() {
        return dataScopes;
    }

    /**
     * Creates a new builder instance.
     *
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for DelegatedPrincipal.
     */
    public static class Builder {
        private String userId;
        private String username;
        private String tenantId;
        private Set<String> permissions;
        private Set<String> roles;
        private Set<DataScope> dataScopes;

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder permissions(Set<String> permissions) {
            this.permissions = permissions;
            return this;
        }

        public Builder roles(Set<String> roles) {
            this.roles = roles;
            return this;
        }

        public Builder dataScopes(Set<DataScope> dataScopes) {
            this.dataScopes = dataScopes;
            return this;
        }

        public DelegatedPrincipal build() {
            return new DelegatedPrincipal(this);
        }
    }

    @Override
    public String toString() {
        return "DelegatedPrincipal{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", roles=" + roles +
                '}';
    }
}
