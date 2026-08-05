/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.sdk.capability;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.runtime.sdk.annotation.Since;

/**
 * Legacy identity-tenant membership compatibility contract.
 *
 * <p>This combined contract is retained for source/binary compatibility with
 * older integrations. New platform code must consume {@link IdentityAccountCapability}
 * for global identity credential state and {@link TenantAccessCapability} for
 * tenant membership/principalship state.
 *
 * <h3>Architecture Compliance</h3>
 * <ul>
 *   <li>Defined in Layer 2A ({@code runtime-sdk-api}) as a capability contract</li>
 *   <li>Production implementations are split by Data Owner</li>
 *   <li>Consumers must not require this combined contract for new code</li>
 * </ul>
 *
 * <h3>Security Considerations</h3>
 * <p>The {@link IdentityRecord} contains {@code passwordHash} for server-side
 * credential verification only. This field must NEVER be exposed in API responses
 * or logged in plaintext.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see TenantCapability
 */
@Since("3.2.0")
@Deprecated(since = "3.2.0", forRemoval = false)
public interface IdentityTenantCapability {

    /**
     * 根据邮箱查找全局身份（大小写不敏感）。
     *
     * <p>用于多租户登录流程中的全局凭证验证。
     *
     * @param email 邮箱地址
     * @return 身份记录，不存在时返回空
     */
    Optional<IdentityRecord> findIdentityByEmail(String email);

    /**
     * 根据身份 ID 查找全局身份。
     *
     * <p>用于 selectTenant / refreshToken 等需要重新读取身份状态但
     * 不依赖邮箱的服务端流程，典型地需要读取 {@code passwordMustChange} 以
     * 在最终开套响应中传递强制改密标志。
     *
     * @param id 身份 ID
     * @return 身份记录，不存在时返回空
     * @since 3.2.0
     */
    Optional<IdentityRecord> findIdentityById(Long id);

    /**
     * 获取指定身份的所有活跃成员关系（B 端 Actor）。
     *
     * <p>查询 {@code sys_tenant_member} 中 status=ACTIVE 的记录，
     * 并关联 {@code sys_tenant} 获取租户信息。
     *
     * @param identityId 身份 ID
     * @return 活跃成员关系列表
     */
    List<TenantMembershipRecord> getActiveMemberships(Long identityId);

    /**
     * 获取指定身份的所有活跃主体关系（C 端 Subject）。
     *
     * <p>查询 {@code sys_tenant_principal} 中 status=ACTIVE 的记录，
     * 并关联 {@code sys_tenant} 获取租户信息。
     *
     * @param identityId 身份 ID
     * @return 活跃主体关系列表
     */
    List<TenantPrincipalRecord> getActivePrincipalships(Long identityId);

    /**
     * 查找指定身份在指定租户的成员关系。
     *
     * @param identityId 身份 ID
     * @param tenantId   租户 ID
     * @return 成员关系记录，不存在时返回空
     */
    Optional<TenantMembershipRecord> findMembership(Long identityId, Long tenantId);

    /**
     * Finds an active Actor membership by immutable context ID.
     *
     * @param contextId immutable {@code sys_tenant_member.context_id}
     * @return membership record if present and active
     * @since 3.2.2
     */
    default Optional<TenantMembershipRecord> findMembershipByContextId(String contextId) {
        return Optional.empty();
    }

    /**
     * 查找指定身份在指定租户的主体关系。
     *
     * @param identityId 身份 ID
     * @param tenantId   租户 ID
     * @return 主体关系记录，不存在时返回空
     */
    Optional<TenantPrincipalRecord> findPrincipalship(Long identityId, Long tenantId);

    /**
     * Finds an active Subject principalship by immutable context ID.
     *
     * @param contextId immutable {@code sys_tenant_principal.context_id}
     * @return principal record if present and active
     * @since 3.2.2
     */
    default Optional<TenantPrincipalRecord> findPrincipalshipByContextId(String contextId) {
        return Optional.empty();
    }

    /**
     * 更新指定身份的密码哈希（S3 — 改密 / 强制改密回执）。
     *
     * <p>实现必须：
     * <ul>
     *   <li>把 {@code sys_identity.password_hash} 写入新值</li>
     *   <li>把 {@code sys_identity.password_must_change} 置为 {@code false}</li>
     *   <li>把 {@code sys_identity.updated_at} 置为当前时间</li>
     * </ul>
     *
     * <p><b>安全</b>：调用方负责使用 {@link PasswordCapability#hash(String)} 生成 BCrypt
     * 哈希；此方法不再校验明文长度 / 格式，也不验证旧密码（由 {@link AuthFlowCapability#changePassword} 编排）。
     *
     * @param identityId      身份 ID
     * @param newPasswordHash 新密码哈希（已通过 {@link PasswordCapability} 加密）
     * @throws IllegalArgumentException identity 不存在
     * @since 3.2.0
     */
    void updatePasswordHash(Long identityId, String newPasswordHash);

    /**
     * 递增指定身份的 token_version（A3 — 密码修改 / 管理员强制局放后调用）。
     *
     * <p>该操作使该身份当前所有已颁发的有效 JWT（千不含 {@code tv}≥新版本）均失效。
     *
     * @param identityId 身份 ID
     * @since 3.2.1
     */
    void incrementTokenVersion(Long identityId);

    /**
     * 读取指定身份当前的 token_version（A3 — 令牌版本校验）。
     *
     * @param identityId 身份 ID
     * @return 当前 token_version（首次创建为 1）
     * @since 3.2.1
     */
    long getTokenVersion(Long identityId);

        /**
         * Records one failed password verification attempt for a global identity.
         *
         * <p>Implementations must increment the consecutive failure counter and,
         * once {@code maxAttempts} is reached, transition the identity to a locked
         * state until {@code now + lockMinutes}.</p>
         *
         * @param identityId identity ID
         * @param maxAttempts number of consecutive failures that triggers lockout
         * @param lockMinutes lockout duration in minutes
         * @param clientIp remote client IP, if known
         * @return resulting failure counter and lock state
         * @since 3.2.1
         */
        LoginFailureRecord recordFailedLogin(Long identityId, int maxAttempts, int lockMinutes, String clientIp);

        /**
         * Records a successful password verification and clears the failure counter.
         *
         * @param identityId identity ID
         * @param clientIp remote client IP, if known
         * @since 3.2.1
         */
        void recordSuccessfulLogin(Long identityId, String clientIp);

        /**
         * Unlocks an identity when its temporary lockout deadline has elapsed.
         *
         * @param identityId identity ID
         * @param now current time supplied by the caller
         * @return true when the identity was unlocked by this call
         * @since 3.2.1
         */
        boolean unlockExpiredLoginLock(Long identityId, Instant now);

    /**
     * 查询指定身份是否为活跃的平台管理员（S3 — PlatformAdmin 登录路径）。
     *
     * <p>查询 {@code sys_platform_admin}，仅返回 {@code status=ACTIVE} 的记录。
     * 用于 {@link AuthFlowCapability#login} 在密码校验通过后判定是否走"无租户"
     * Platform Admin Token 路径。
     *
     * @param identityId 身份 ID
     * @return 平台管理员记录；若不是平台管理员或已停用，返回 {@link Optional#empty()}
     * @since 3.2.0
     */
    Optional<PlatformAdminRecord> findActivePlatformAdmin(Long identityId);

    /**
     * 更新成员关系的最后访问时间。
     *
     * @param memberId 成员 ID
     */
    void touchMemberAccess(Long memberId);

    /**
     * 更新主体关系的最后访问时间。
     *
     * @param principalId 主体 ID
     */
    void touchPrincipalAccess(Long principalId);

    // ========== Inner Record Types ==========

    /**
     * 身份记录 — 包含凭证信息，仅用于服务端验证。
     *
     * @param id                   身份 ID (sys_identity.id)
     * @param email                邮箱
     * @param username             用户名
     * @param passwordHash         密码哈希（安全敏感，禁止输出到日志或 API 响应）
     * @param status               状态
     * @param passwordMustChange   是否必须在下次登录时强制修改密码
     *                             (Bootstrap / 管理员重置 / 到期轮换场景；@since 3.2.0)
     * @param tokenVersion         令牌版本号（A3 — 密码修改时递增，使旧令牌失效；@since 3.2.1）
     */
    record IdentityRecord(
            Long id,
            String email,
            String username,
            String passwordHash,
            String status,
            boolean passwordMustChange,
            long tokenVersion
    ) {
        @Override
        public String toString() {
            // 安全: 不输出 passwordHash
            return "IdentityRecord{id=" + id + ", email='" + email + "', status='" + status
                    + "', passwordMustChange=" + passwordMustChange
                    + ", tokenVersion=" + tokenVersion + "}";
        }
    }

        /**
         * Result of recording a failed password verification attempt.
         *
         * @param failedLoginCount consecutive failed password count after the update
         * @param locked whether the identity is locked after the update
         * @param lockedUntil temporary lock deadline, null when not locked
         * @since 3.2.1
         */
        record LoginFailureRecord(int failedLoginCount, boolean locked, Instant lockedUntil) {
        }

    /**
     * 租户成员关系记录（B 端 Actor）。
     *
     * @param memberId   成员 ID (sys_tenant_member.id)
     * @param tenantId   租户 ID
     * @param tenantCode 租户编码
     * @param tenantName 租户名称
     * @param identityId 身份 ID
     * @param memberType 成员类型 (OWNER/ADMIN/MEMBER)
     * @param status     状态
     * @param joinedAt   加入时间
     */
    record TenantMembershipRecord(
            Long memberId,
            Long tenantId,
            String tenantCode,
            String tenantName,
            Long identityId,
            String memberType,
            String status,
            Instant joinedAt,
            String contextId,
            long authzVersion
    ) {
        public TenantMembershipRecord(Long memberId, Long tenantId, String tenantCode,
                                      String tenantName, Long identityId, String memberType,
                                      String status, Instant joinedAt) {
            this(memberId, tenantId, tenantCode, tenantName, identityId, memberType,
                    status, joinedAt, null, 1L);
        }
    }

    /**
     * 租户主体关系记录（C 端 Subject）。
     *
     * @param principalId   主体 ID (sys_tenant_principal.id)
     * @param tenantId      租户 ID
     * @param tenantCode    租户编码
     * @param tenantName    租户名称
     * @param identityId    身份 ID
     * @param principalType 主体类型 (CUSTOMER/GUEST)
     * @param displayName   显示名称
     * @param status        状态
     * @param lastAccessAt  最后访问时间
     */
    record TenantPrincipalRecord(
            Long principalId,
            Long tenantId,
            String tenantCode,
            String tenantName,
            Long identityId,
            String principalType,
            String displayName,
            String status,
            Instant lastAccessAt,
            String contextId,
            long authzVersion
    ) {
        public TenantPrincipalRecord(Long principalId, Long tenantId, String tenantCode,
                                     String tenantName, Long identityId, String principalType,
                                     String displayName, String status, Instant lastAccessAt) {
            this(principalId, tenantId, tenantCode, tenantName, identityId, principalType,
                    displayName, status, lastAccessAt, null, 1L);
        }
    }

    /**
     * 平台管理员记录 — 由 {@link #findActivePlatformAdmin(Long)} 返回。
     *
     * <p>{@code adminRole} 取值：{@code SUPER_ADMIN} / {@code PLATFORM_ADMIN}。
     * {@code mfaEnabled} 当前未参与登录决策，但保留给 S3+ MFA 改造。
     *
     * @param adminId    sys_platform_admin.id
     * @param identityId 关联身份 ID
     * @param adminRole  管理员角色字符串（PlatformAdminRole 的枚举名）
     * @param mfaEnabled 是否启用了 MFA
     * @since 3.2.0
     */
    record PlatformAdminRecord(
            Long adminId,
            Long identityId,
            String adminRole,
            boolean mfaEnabled
    ) {}
}
