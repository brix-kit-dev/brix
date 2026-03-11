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
package io.brix.platform.auth.context;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Authenticated User Information
 * <p>
 * User context information parsed from JWT Token
 * </p>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
public class AuthenticatedUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * User ID
     */
    private String userId;

    /**
     * Tenant ID
     */
    private String tenantId;

    /**
     * Username
     */
    private String username;

    /**
     * Email address
     */
    private String email;

    /**
     * Token version number, used for forced invalidation
     */
    private Long tokenVersion;

    /**
     * Role list
     */
    private List<String> roles = new ArrayList<>();

    /**
     * Permission list (Immutable Permission ID)
     */
    private List<String> permissions = new ArrayList<>();

    // ========== Getters & Setters ==========

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(Long tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles != null ? roles : new ArrayList<>();
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions != null ? permissions : new ArrayList<>();
    }

    // ========== Convenience Methods ==========

    /**
     * Check if user has specified role
     *
     * @param role Role name
     * @return Whether the role is owned
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    /**
     * Check if user has any of the specified roles
     *
     * @param requiredRoles Role list
     * @return Whether any role is owned
     */
    public boolean hasAnyRole(String... requiredRoles) {
        if (roles == null || requiredRoles == null) {
            return false;
        }
        for (String role : requiredRoles) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if user has specified permission
     *
     * @param permission Permission identifier
     * @return Whether the permission is owned
     */
    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }

    /**
     * Check if user has any of the specified permissions
     *
     * @param requiredPermissions Permission list
     * @return Whether any permission is owned
     */
    public boolean hasAnyPermission(String... requiredPermissions) {
        if (permissions == null || requiredPermissions == null) {
            return false;
        }
        for (String perm : requiredPermissions) {
            if (permissions.contains(perm)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if user has all specified permissions
     *
     * @param requiredPermissions Permission list
     * @return Whether all permissions are owned
     */
    public boolean hasAllPermissions(String... requiredPermissions) {
        if (permissions == null || requiredPermissions == null) {
            return false;
        }
        for (String perm : requiredPermissions) {
            if (!permissions.contains(perm)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if user is super admin
     */
    public boolean isSuperAdmin() {
        return hasRole("SUPER_ADMIN") || hasRole("ROLE_SUPER_ADMIN");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthenticatedUser that = (AuthenticatedUser) o;
        return Objects.equals(userId, that.userId) && 
               Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, tenantId);
    }

    @Override
    public String toString() {
        return "AuthenticatedUser{" +
                "userId='" + userId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", username='" + username + '\'' +
                ", roles=" + roles +
                '}';
    }
}
