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

import java.util.List;

import io.runtime.sdk.annotation.Since;

/**
 * JWT 签发能力契约（Layer 2A）。
 *
 * <p>Brix Platform 多租户认证体系的统一令牌签发入口，由 Layer 2C
 * （{@code platform-auth}）提供 RS256 默认实现。仅契约层定义令牌结构，
 * 具体算法（RS256 / EdDSA）、密钥来源（classpath / Vault）由实现层决定。</p>
 *
 * <h3>令牌种类</h3>
 * <ul>
 *   <li><b>Actor Access Token</b> — B 端成员令牌，包含 {@code mid/mtype/role=actor} 声明</li>
 *   <li><b>Subject Access Token</b> — C 端主体令牌，包含 {@code pid/ptype/role=subject} 声明</li>
 *   <li><b>Identity Token</b> — 多租户登录第一阶段的过渡令牌（短命，仅允许 select-tenant）</li>
 *   <li><b>Platform Admin Token</b> — 平台管理员令牌（{@code role=platform-admin}，无 tenantId）</li>
 * </ul>
 *
 * <h3>架构合规</h3>
 * <ul>
 *   <li>仅依赖 JDK 类型，{@code runtime-sdk-api} 零运行时依赖原则不被打破</li>
 *   <li>Actor / Subject 互斥：{@code mid} 与 {@code pid} 不会同时出现在一个令牌中</li>
 *   <li>实现层必须保证密钥安全（生产环境禁止 classpath 私钥）</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see AuthFlowCapability
 */
@Since("3.2.0")
public interface JwtIssuerCapability {

    /**
     * 签发 B 端 Actor Access Token。
     *
     * @param request Actor 令牌签发请求
     * @return 已签名的 JWT 字符串
     */
    String issueActorAccessToken(ActorTokenRequest request);

    /**
     * 签发 C 端 Subject Access Token。
     *
     * @param request Subject 令牌签发请求
     * @return 已签名的 JWT 字符串
     */
    String issueSubjectAccessToken(SubjectTokenRequest request);

    /**
     * 签发 Identity Token（多租户登录第一阶段）。
     *
     * <p>典型用法：用户拥有多个租户时，登录第一步只验证全局凭证，
     * 然后签发短命 Identity Token，前端凭此调用 {@code selectTenant} 完成第二步。</p>
     *
     * @param request Identity 令牌签发请求
     * @return 已签名的 JWT 字符串
     */
    String issueIdentityToken(IdentityTokenRequest request);

    /**
     * 签发平台管理员令牌（无 tenant 上下文）。
     *
     * <p>用于 {@code sys_platform_admin} 表中的超级管理员 / 平台运维人员，
     * 令牌 {@code role=platform-admin}，不携带 {@code tenant_id / mid / pid}。</p>
     *
     * @param request 平台管理员令牌签发请求
     * @return 已签名的 JWT 字符串
     * @since 3.2.0
     */
    String issuePlatformAdminToken(PlatformAdminTokenRequest request);

    /**
     * 返回 Access Token 的默认有效期（秒）。
     *
     * @return Access Token 有效期
     */
    long getAccessTokenExpirationSeconds();

    /**
     * 返回 Identity Token 的有效期（秒）。
     *
     * @return Identity Token 有效期
     */
    long getIdentityTokenExpirationSeconds();

    // ========== 请求记录 ==========

    /**
     * Actor (B 端) Access Token 签发请求。
     *
     * @param identityId   全局身份 ID（{@code sys_identity.id}）
     * @param email        用户邮箱（写入 {@code email} claim）
     * @param username     用户名（写入 {@code username} claim）
     * @param tenantId     租户 ID
     * @param memberId     租户成员 ID（{@code sys_tenant_member.id}），写入 {@code mid} claim
     * @param memberType   成员类型（OWNER / ADMIN / MEMBER），写入 {@code mtype} claim
     * @param roles        角色编码列表（不可为 {@code null}，可为空）
     * @param permissions  权限编码列表（不可为 {@code null}，可为空）
     * @param tokenVersion 令牌版本号（A3，写入 {@code tv} claim；与 DB 值不符则令牌失效）
     */
    record ActorTokenRequest(
            Long identityId,
            String email,
            String username,
            Long tenantId,
            Long memberId,
            String memberType,
            List<String> roles,
            List<String> permissions,
            long tokenVersion
    ) {}

    /**
     * Subject (C 端) Access Token 签发请求。
     *
     * @param identityId    全局身份 ID
     * @param email         用户邮箱
     * @param username      用户名
     * @param tenantId      租户 ID
     * @param principalId   租户主体 ID（{@code sys_tenant_principal.id}），写入 {@code pid} claim
     * @param principalType 主体类型（CUSTOMER / GUEST），写入 {@code ptype} claim
     * @param displayName   C 端显示名称（写入 {@code display_name} claim，可为 {@code null}）
     * @param tokenVersion  令牌版本号（A3，写入 {@code tv} claim）
     */
    record SubjectTokenRequest(
            Long identityId,
            String email,
            String username,
            Long tenantId,
            Long principalId,
            String principalType,
            String displayName,
            long tokenVersion
    ) {}

    /**
     * Identity Token 签发请求（多租户选择阶段使用）。
     *
     * @param identityId 全局身份 ID
     * @param email      用户邮箱
     * @param username   用户名
     */
    record IdentityTokenRequest(
            Long identityId,
            String email,
            String username
    ) {}

    /**
     * 平台管理员令牌签发请求（无租户上下文）。
     *
     * @param adminId      平台管理员 ID（{@code sys_platform_admin.id}）
     * @param identityId   关联身份 ID（可为 {@code null}，若管理员有 sys_identity 记录）
     * @param email        管理员邮箱
     * @param username     管理员用户名
     * @param adminRole    平台角色（SUPER_ADMIN / OPS / SECURITY 等）
     * @param permissions  权限编码列表（不可为 {@code null}，可为空）
     * @param tokenVersion 令牌版本号（A3，写入 {@code tv} claim）
     */
    record PlatformAdminTokenRequest(
            Long adminId,
            Long identityId,
            String email,
            String username,
            String adminRole,
            List<String> permissions,
            long tokenVersion
    ) {}

    // ========== Phase 2 / C-4 ViewMode — Platform-Admin Viewing Token ==========

    /**
     * Signs a brand-new <i>platform-admin viewing</i> access token. Per the
     * <i>v3.0.9 Runtime Shell Blueprint</i> and the B2B2C tenancy model, a
     * platform admin may temporarily bind their session to a target tenant
     * (for support / debugging) without losing platform-admin privileges.
     *
     * <p>The resulting JWT preserves {@code role=platform-admin} but adds two
     * new claims: {@code tenant_id} (the tenant being viewed) and
     * {@code original_sub} (the platform-admin identity that initiated the
     * view session). It deliberately omits {@code mid} / {@code pid} — the
     * admin is not impersonating a specific member, only adopting a tenant
     * context.</p>
     *
     * <p>To exit viewing mode, callers re-issue a normal token via
     * {@link #issuePlatformAdminToken(PlatformAdminTokenRequest)}.</p>
     *
     * @param request the viewing-token request
     * @return the freshly signed JWT
     * @since 3.3.0
     */
    @Since("3.3.0")
    String issuePlatformAdminViewToken(PlatformAdminViewTokenRequest request);

    /**
     * Platform-admin viewing-token signing request.
     *
     * @param adminId       platform-admin ID ({@code sys_platform_admin.id})
     * @param identityId    associated identity ID
     * @param email         admin email
     * @param username      admin username
     * @param adminRole     platform role (SUPER_ADMIN / OPS / SECURITY ...)
     * @param permissions   permission codes (non-null, possibly empty)
     * @param tokenVersion  current token version
     * @param viewTenantId  target tenant being viewed (non-null)
     * @param originalSub   platform-admin identity initiating the view session
     *                      (typically equals {@link #identityId} on first switch;
     *                      preserved when chaining further switches)
     * @since 3.3.0
     */
    record PlatformAdminViewTokenRequest(
            Long adminId,
            Long identityId,
            String email,
            String username,
            String adminRole,
            List<String> permissions,
            long tokenVersion,
            Long viewTenantId,
            String originalSub
    ) {}
}
