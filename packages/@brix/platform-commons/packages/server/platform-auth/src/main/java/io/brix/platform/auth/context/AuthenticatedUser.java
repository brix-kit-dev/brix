package io.brix.platform.auth.context;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 认证用户信息
 * <p>
 * JWT Token 解析出的用户上下文信息
 * </p>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
public class AuthenticatedUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 绉熸埛 ID
     */
    private String tenantId;

    /**
     * 用户
     */
    private String username;

    /**
     * 閭
     */
    private String email;

    /**
     * Token 版本号，用于强制失效
     */
    private Long tokenVersion;

    /**
     * 角色列表
     */
    private List<String> roles = new ArrayList<>();

    /**
     * 权限列表 (Immutable Permission ID)
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

    // ========== 便捷方法 ==========

    /**
     * 检查是否拥有指定角
     *
     * @param role 角色名称
     * @return 是否拥有
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    /**
     * 检查是否拥有任一角色
     *
     * @param requiredRoles 角色列表
     * @return 是否拥有任一角色
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
     * 检查是否拥有指定权
     *
     * @param permission 权限标识
     * @return 是否拥有
     */
    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }

    /**
     * 检查是否拥有任一权限
     *
     * @param requiredPermissions 权限列表
     * @return 是否拥有任一权限
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
     * 检查是否拥有所有权
     *
     * @param requiredPermissions 权限列表
     * @return 是否拥有所有权
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
     * 是否为超级管理员
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
