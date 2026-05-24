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

import io.brix.platform.auth.RoleCode;
import io.brix.platform.auth.enums.TokenRole;
import io.brix.platform.auth.enums.TokenType;

/**
 * Authenticated User Information
 * <p>
 * User context information parsed from JWT Token.
 * Supports B2B2C dual-track authentication: Actor (B-side) and Subject (C-side).
 * </p>
 *
 * <h3>Phase 2 — 双轨认证扩展</h3>
 * <ul>
 *   <li>{@link #memberId} / {@link #memberType} — Actor Token (B 端，mid/mtype)</li>
 *   <li>{@link #principalId} / {@link #principalType} — Subject Token (C 端，pid/ptype)</li>
 *   <li>{@link #tokenRole} — actor 或 subject，mid 与 pid 互斥</li>
 *   <li>{@link #tokenType} — ACCESS / IDENTITY / REFRESH</li>
 *   <li>{@link #allowedActions} — Identity Token 的操作白名单</li>
 * </ul>
 *
 * @author Brix Platform Authors Platform Team
 * @version 2.0.0
 */
public class AuthenticatedUser implements Serializable {

    private static final long serialVersionUID = 2L;

    /**
     * User ID (sub claim — identity_id or profile_id)
     */
    private String userId;

    /**
     * Tenant ID (tid claim)
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

    /** JWT ID (jti claim). */
    private String jti;

    /** Token scope claim, used by Bootstrap Setup tokens. */
    private String scope;

    /**
     * Role list (RBAC roles)
     */
    private List<String> roles = new ArrayList<>();

    /**
     * Permission list (Immutable Permission ID)
     */
    private List<String> permissions = new ArrayList<>();

    // ========== Phase 2 — B2B2C 双轨扩展字段 ==========

    /**
     * Member ID (mid claim) — Actor (B 端) 身份标识。
     *
     * <p>来自 {@code sys_tenant_member.id}。与 {@link #principalId} 互斥。
     */
    private String memberId;

    /**
     * Member Type (mtype claim) — Actor 的成员类型。
     *
     * <p>取值：OWNER / ADMIN / MEMBER。仅当 {@link #memberId} 非空时有效。
     */
    private String memberType;

    /**
     * Principal ID (pid claim) — Subject (C 端) 身份标识。
     *
     * <p>来自 {@code sys_tenant_principal.id}。与 {@link #memberId} 互斥。
     */
    private String principalId;

    /**
     * Principal Type (ptype claim) — Subject 的主体类型。
     *
     * <p>取值：CUSTOMER / GUEST。仅当 {@link #principalId} 非空时有效。
     */
    private String principalType;

    /**
     * Token 角色类型 (role claim) — actor 或 subject。
     *
     * <p>标识当前 Token 的双轨身份类型。
     */
    private TokenRole tokenRole;

    /**
     * Token 类型 (token_type claim) — ACCESS / IDENTITY / REFRESH。
     */
    private TokenType tokenType;

    /**
     * 允许的操作列表 (allowed_actions claim)。
     *
     * <p>仅 Identity Token 使用，限制可调用的端点
     * （例如 "select-tenant"、"register-tenant"）。
     */
    private List<String> allowedActions = new ArrayList<>();

    /**
    * Platform admin role code (platform_role claim) — e.g. {@code "PLATFORM_SUPER_ADMIN"}.
     *
     * <p>Non-null only for Platform Admin tokens. Sourced from
     * {@code sys_platform_admin.role} via the {@code platform_role} JWT claim.
     * Intentionally separate from the generic {@link #roles} list, which carries
     * tenant-scoped RBAC roles.
     */
    private String platformRole;

    /**
     * Original platform-admin identity that initiated an impersonation session
     * (the {@code original_sub} JWT claim). Non-null only for tokens issued by
     * {@code ViewModeCapability.switchTo} — i.e. when a platform admin is
     * temporarily viewing the system as a tenant Actor or Subject.
     *
     * <p>Used by the front-end banner ("您正在以平台超管身份操作")
     * and by the audit-log entry written on each view-mode switch.</p>
     *
     * @since 3.3.0
     */
    private String originalSub;

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

    public String getJti() {
        return jti;
    }

    public void setJti(String jti) {
        this.jti = jti;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
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
     * Check if this is a Platform Admin token (any platform admin role).
     *
      * @return {@code true} for formal platform-admin access tokens
     */
    public boolean isPlatformAdmin() {
            return platformRole != null && (tokenType == null || tokenType == TokenType.ACCESS);
    }

    /**
     * Check if this is a Super Administrator token.
     *
     * <p>Evaluated against the {@code platform_role} JWT claim, not a role-name
     * string in the generic {@code roles} list. This eliminates the former ambiguity
      * between role names in the generic {@code roles} list.
     *
      * @return {@code true} only when {@code platform_role == "PLATFORM_SUPER_ADMIN"}
     */
    public boolean isSuperAdmin() {
          return RoleCode.PLATFORM_SUPER_ADMIN.equals(platformRole);
    }

    // ========== Phase 3 — Platform Admin ==========

    public String getPlatformRole() {
        return platformRole;
    }

    public void setPlatformRole(String platformRole) {
        this.platformRole = platformRole;
    }

    // ========== Phase 2 / C-4 ViewMode — Impersonation ==========

    /**
     * Returns the platform-admin identity ID that initiated the current
     * impersonation session, or {@code null} if this token is not an
     * impersonation token.
     *
     * @return the {@code original_sub} JWT claim value, or {@code null}
     * @since 3.3.0
     */
    public String getOriginalSub() {
        return originalSub;
    }

    /**
     * Sets the {@code original_sub} value (extracted by {@code JwtValidator}).
     *
     * @since 3.3.0
     */
    public void setOriginalSub(String originalSub) {
        this.originalSub = originalSub;
    }

    /**
     * Convenience predicate — {@code true} when the current session represents
     * a platform-admin impersonating a tenant view.
     *
     * @return {@code true} iff {@link #getOriginalSub()} is non-null
     * @since 3.3.0
     */
    public boolean isImpersonating() {
        return originalSub != null;
    }

    // ========== Phase 2 — 双轨 Getters & Setters ==========

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getMemberType() {
        return memberType;
    }

    public void setMemberType(String memberType) {
        this.memberType = memberType;
    }

    public String getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(String principalId) {
        this.principalId = principalId;
    }

    public String getPrincipalType() {
        return principalType;
    }

    public void setPrincipalType(String principalType) {
        this.principalType = principalType;
    }

    public TokenRole getTokenRole() {
        return tokenRole;
    }

    public void setTokenRole(TokenRole tokenRole) {
        this.tokenRole = tokenRole;
    }

    public TokenType getTokenType() {
        return tokenType;
    }

    public void setTokenType(TokenType tokenType) {
        this.tokenType = tokenType;
    }

    public List<String> getAllowedActions() {
        return allowedActions;
    }

    public void setAllowedActions(List<String> allowedActions) {
        this.allowedActions = allowedActions != null ? allowedActions : new ArrayList<>();
    }

    // ========== Phase 2 — 双轨 Convenience Methods ==========

    /**
     * 判断是否为 Actor（B 端）身份。
     *
     * @return 是否 Actor
     */
    public boolean isActor() {
        return tokenRole == TokenRole.ACTOR || memberId != null;
    }

    /**
     * 判断是否为 Subject（C 端）身份。
     *
     * @return 是否 Subject
     */
    public boolean isSubject() {
        return tokenRole == TokenRole.SUBJECT || principalId != null;
    }

    /**
     * 判断是否为 Identity Token（临时身份令牌）。
     *
     * @return 是否 Identity Token
     */
    public boolean isIdentityToken() {
        return tokenType == TokenType.IDENTITY;
    }

    public boolean isBootstrapSetupToken() {
        return tokenType == TokenType.BOOTSTRAP_SETUP && tokenRole == TokenRole.BOOTSTRAP;
    }

    /**
     * 判断 Identity Token 是否允许指定操作。
     *
     * @param action 操作标识
     * @return 是否允许
     */
    public boolean isActionAllowed(String action) {
        if (tokenType != TokenType.IDENTITY) {
            return true; // Access Token 不受 allowed_actions 限制
        }
        return allowedActions != null && allowedActions.contains(action);
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
                ", tokenRole=" + tokenRole +
                ", memberId='" + memberId + '\'' +
                ", principalId='" + principalId + '\'' +
                ", roles=" + roles +
                '}';
    }
}
