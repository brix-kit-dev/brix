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
 * 登录与会话编排能力契约（Layer 2A）。
 *
 * <p>Brix Platform 多租户登录流程的统一编排入口，由 Layer 2C
 * （{@code platform-auth}）提供默认实现。该契约取代历史上散落在
 * {@code app-identity} 插件内的 {@code AuthService}，符合蓝图约束 1/2/3。</p>
 *
 * <h3>登录两阶段模型</h3>
 * <ol>
 *   <li><b>Stage 1 - login</b>: 验证全局凭证（{@code sys_identity}），
 *       根据租户关联数量返回：
 *       <ul>
 *         <li>0 关联 → 错误</li>
 *         <li>1 关联 → 直接签发 Access Token（{@link LoginStatus#COMPLETE}）</li>
 *         <li>多关联 → 签发 Identity Token + 租户列表（{@link LoginStatus#SELECT_TENANT}）</li>
 *         <li>平台管理员 → 必须通过 {@link #loginPlatformAdmin(LoginCommand)} 使用独立入口登录</li>
 *       </ul>
 *   </li>
 *   <li><b>Stage 2 - selectTenant</b>: 凭 Identity Token + tenantId 完成 Access Token 签发</li>
 * </ol>
 *
 * <h3>架构合规</h3>
 * <ul>
 *   <li>仅依赖 JDK 类型 + Runtime SDK 内部能力，{@code runtime-sdk-api} 零运行时依赖原则不被打破</li>
 *   <li>不暴露 Spring / OpenAPI / Servlet 类型，REST DTO 转换由 Layer 2C/3 适配</li>
 *   <li>异常通过 {@link AuthFlowException} 统一抛出，承载错误码 + 安全可读消息</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see JwtIssuerCapability
 * @see IdentityAccountCapability
 * @see TenantAccessCapability
 */
@Since("3.2.0")
public interface AuthFlowCapability {

    /**
     * 多租户登录（Stage 1）。
     *
     * @param command 登录命令
     * @return 登录结果（COMPLETE 或 SELECT_TENANT）
     * @throws AuthFlowException 凭证错误 / 账户禁用 / 无租户关联等
     */
    LoginResult login(LoginCommand command);

    /**
     * B-side Actor login entry point.
     *
     * <p>Actor login may enumerate active tenant memberships for the authenticated
     * identity and return context selection tickets when more than one membership
     * is available. It must not include C-side Subject contexts.</p>
     *
     * @param command login command
     * @return login result
     * @throws AuthFlowException when authentication fails or no active Actor context exists
     * @since 3.2.2
     */
    default LoginResult loginActor(LoginCommand command) {
        return login(command);
    }

    /**
     * C-side Subject login entry point.
     *
     * <p>Subject login is tenant-bound. Implementations must only discover the
     * Subject context in the current tenant context and must not enumerate Actor
     * memberships or cross-tenant Subject contexts.</p>
     *
     * @param command login command
     * @return login result
     * @throws AuthFlowException when authentication fails or no active Subject context exists
     * @since 3.2.2
     */
    default LoginResult loginSubject(LoginCommand command) {
        return login(command);
    }

    /**
     * Platform administrator login through the isolated platform entry point.
     *
     * <p>This method is intentionally separate from {@link #login(LoginCommand)} so
     * the tenant login endpoint can never issue a PLATFORM-scoped token. The
     * implementation must validate credentials, verify that the identity is an
     * active platform administrator, and issue a token whose scope is determined
     * by the token claims rather than by any boolean response flag.</p>
     *
     * @param command login command
     * @return platform login result
     * @throws AuthFlowException when credentials are invalid or the identity is not an active platform administrator
     */
    LoginResult loginPlatformAdmin(LoginCommand command);

    /**
     * 多租户选择（Stage 2）。
     *
     * @param identityId 已通过 Identity Token 验证的身份 ID
     * @param command    租户选择命令
     * @return 登录结果（必为 COMPLETE）
     * @throws AuthFlowException 无目标租户访问权限等
     */
    LoginResult selectTenant(Long identityId, SelectTenantCommand command);

    /**
     * Selects a tenant context using an opaque one-time selection ticket.
     *
     * <p>The ticket is produced during {@link #loginActor(LoginCommand)} or the
     * legacy multi-context login flow. It is bound to the Identity Token {@code jti}
     * and is consumed exactly once.</p>
     *
     * @param identityId authenticated identity ID from the Identity Token
     * @param command selection command carrying the one-time ticket and identity token jti
     * @return completed login result
     * @throws AuthFlowException when the ticket is expired, replayed, or belongs to another identity token
     * @since 3.2.2
     */
    default LoginResult selectContext(Long identityId, SelectContextCommand command) {
        throw new AuthFlowException(
                AuthFlowException.CODE_CONTEXT_SELECTION_TICKET_INVALID,
                "Context selection ticket is not supported by this auth flow implementation");
    }

    /**
     * 刷新 Access Token。
     *
     * @param command 刷新命令
     * @return 新的登录结果
     * @throws AuthFlowException Refresh Token 无效 / 已撤销 / 已过期
     */
    LoginResult refreshToken(RefreshCommand command);

    /**
     * 修改自身密码（已登录用户主动修改 / 强制改密回执）。
     *
     * <p>实现必须：
     * <ul>
     *   <li>校验 {@code oldPassword} 与 {@code sys_identity.password_hash} 匹配</li>
     *   <li>清除 {@code passwordMustChange} 标志</li>
     *   <li>更新 {@code password_updated_at}</li>
     *   <li>递增 {@code token_version}（使所有已颁发的旧令牌失效）</li>
     *   <li>失败次数计数器重置</li>
     * </ul>
     * </p>
     *
     * @param command 改密命令
     * @throws AuthFlowException 旧密码错误 / 新密码不符合策略 / 身份不存在
     */
    void changePassword(ChangePasswordCommand command);

    /**
     * MFA 二次验证（A1 — 登录第二步，当 {@link LoginStatus#MFA_REQUIRED} 时调用）。
     *
     * <p>客户端持 MFA 挑战令牌（{@code challengeToken}）+ OTP 码调用本接口。
     * 验证通过后颁发正式 Access Token。</p>
     *
     * @param command MFA 验证命令
     * @return 最终登录结果（必为 {@link LoginStatus#COMPLETE}）
     * @throws AuthFlowException MFA 验证失败 / 令牌无效
     * @since 3.2.1
     */
    LoginResult mfaVerify(MfaVerifyCommand command);

    // ========== 命令记录 ==========

    /**
     * 登录命令。
     *
     * @param loginId  登录标识（邮箱 / 用户名 — 多租户路径强制邮箱）
     * @param password 明文密码
     * @param clientIp 客户端 IP（可为 {@code null}，用于审计 / 风控）
     */
    record LoginCommand(String loginId, String password, String clientIp) {
        /** 简化构造，不带 clientIp。 */
        public LoginCommand(String loginId, String password) {
            this(loginId, password, null);
        }
    }

    /**
     * 租户选择命令。
     *
     * @param tenantId 目标租户 ID
     */
    record SelectTenantCommand(Long tenantId) {}

    /**
     * Context selection command.
     *
     * @param selectionTicket opaque one-time ticket returned in {@link TenantOption#selectionTicket()}
     * @param identityTokenJti JWT ID of the Identity Token used to authorize the selection
     * @since 3.2.2
     */
    record SelectContextCommand(String selectionTicket, String identityTokenJti) {}

    /**
     * 刷新令牌命令。
     *
     * @param refreshToken Refresh Token 字符串
     */
    record RefreshCommand(String refreshToken) {}

    /**
     * 改密命令。
     *
     * @param identityId  身份 ID（来自当前 Access Token）
     * @param oldPassword 旧密码（明文）
     * @param newPassword 新密码（明文）
     */
    record ChangePasswordCommand(Long identityId, String oldPassword, String newPassword) {}

    /**
     * MFA 验证命令。
     *
     * @param challengeToken 登录第一步颁发的短命挑战令牌
     * @param otpCode        6 位 TOTP / HOTP 验证码
     */
    record MfaVerifyCommand(String challengeToken, String otpCode) {}

    // ========== 结果 ==========

    /**
     * 登录结果状态。
     */
    enum LoginStatus {
        /** 登录完成，{@code accessToken} 与 {@code refreshToken} 有效。 */
        COMPLETE,
        /** 需要选择租户，{@code identityToken} 与 {@code tenantOptions} 有效。 */
        SELECT_TENANT,
        /**
         * 必须修改密码后才能使用系统（Bootstrap / 管理员重置场景）。
         * {@code accessToken} 为 {@code null}，客户端必须先完成改密再重新登录。
         * @since 3.2.1
         */
        PASSWORD_MUST_CHANGE,
        /**
         * 需要完成 MFA 二次验证（A1 — admin 启用 MFA 时）。
         * {@code identityToken} 作为 MFA 挑战令牌传递，客户端调用 {@link AuthFlowCapability#mfaVerify}。
         * @since 3.2.1
         */
        MFA_REQUIRED
    }

    /**
     * 登录结果统一记录。
     *
     * <p>对前端的契约：根据 {@link #status()} 字段分支取值；
     * REST DTO ({@code LoginResponse}) 直接由本记录映射。</p>
     *
     * @param status              登录状态
     * @param accessToken         Access Token（COMPLETE 时有效）
     * @param refreshToken        Refresh Token（COMPLETE 时有效）
     * @param expiresIn           Access Token 有效期（秒）
     * @param identityToken       Identity Token（SELECT_TENANT 时有效）
     * @param tenantOptions       租户选项列表（SELECT_TENANT 时有效）
     * @param identityId          身份 ID
     * @param displayName         显示名称（可为 {@code null}）
     * @param email               邮箱
     * @param primaryRole         主角色（首项）
     * @param roles               角色列表
     * @param permissions         权限列表
     * @param mustChangePassword  是否要求改密
     * @param mfaRequired         是否要求 MFA
     */
    record LoginResult(
            LoginStatus status,
            String accessToken,
            String refreshToken,
            Long expiresIn,
            String identityToken,
            List<TenantOption> tenantOptions,
            Long identityId,
            String displayName,
            String email,
            String primaryRole,
            List<String> roles,
            List<String> permissions,
            boolean mustChangePassword,
                boolean mfaRequired
    ) {}

    /**
     * 租户选项（多租户选择阶段返回）。
     *
     * @param tenantId      租户 ID
     * @param tenantCode    租户编码
     * @param tenantName    租户名称
     * @param roleType      角色类型（{@code actor} / {@code subject}）
     * @param role          B 端 memberType 或 C 端 principalType
     * @param lastAccessAt  最后访问时间（ISO-8601 字符串，可为 {@code null}）
     */
    record TenantOption(
            Long tenantId,
            String tenantCode,
            String tenantName,
            String roleType,
            String role,
            String lastAccessAt,
            String selectionTicket
    ) {
        public TenantOption(Long tenantId, String tenantCode, String tenantName,
                            String roleType, String role, String lastAccessAt) {
            this(tenantId, tenantCode, tenantName, roleType, role, lastAccessAt, null);
        }
    }

    // ========== 异常 ==========

    /**
     * 认证流程异常 — 承载机器可读错误码 + 安全可读消息。
     *
     * <p>错误码遵循 {@code AUTH_xxx} 命名，Layer 2C 实现负责映射 HTTP 状态码。</p>
     *
     * @since 3.2.0
     */
    final class AuthFlowException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /** 错误码常量：凭证无效（用户名 / 密码错误，统一返回避免账号枚举）。 */
        public static final String CODE_INVALID_CREDENTIALS = "AUTH_INVALID_CREDENTIALS";
        /** 错误码常量：账户被禁用 / 状态非 ACTIVE。 */
        public static final String CODE_ACCOUNT_DISABLED = "AUTH_ACCOUNT_DISABLED";
        /** 错误码常量：账户因连续失败被临时锁定。 */
        public static final String CODE_ACCOUNT_LOCKED = "AUTH_ACCOUNT_LOCKED";
        /** 错误码常量：身份存在但尚未完成 setup，不能走标准登录。 */
        public static final String CODE_PENDING_SETUP = "AUTH_PENDING_SETUP";
        /** 错误码常量：身份存在但无任何活跃租户关联。 */
        public static final String CODE_NO_TENANT_ASSOCIATION = "AUTH_NO_TENANT_ASSOCIATION";
        /** 错误码常量：所选租户无访问权限。 */
        public static final String CODE_TENANT_ACCESS_DENIED = "AUTH_TENANT_ACCESS_DENIED";
        /** 错误码常量：Refresh Token 无效 / 过期 / 已撤销。 */
        public static final String CODE_INVALID_REFRESH_TOKEN = "AUTH_INVALID_REFRESH_TOKEN";
        /** 错误码常量：旧密码错误。 */
        public static final String CODE_OLD_PASSWORD_MISMATCH = "AUTH_OLD_PASSWORD_MISMATCH";
        /** 错误码常量：新密码不符合策略。 */
        public static final String CODE_PASSWORD_POLICY_VIOLATION = "AUTH_PASSWORD_POLICY_VIOLATION";
        /** 错误码常量：身份未找到（仅在已登录上下文中抛出，登录时统一返回 INVALID_CREDENTIALS）。 */
        public static final String CODE_IDENTITY_NOT_FOUND = "AUTH_IDENTITY_NOT_FOUND";
        /** 错误码常量：未启用多租户能力 / 配置缺失。 */
        public static final String CODE_CAPABILITY_UNAVAILABLE = "AUTH_CAPABILITY_UNAVAILABLE";
        /** 错误码常量：MFA 验证失败（OTP 码错误 / 令牌过期）。 */
        public static final String CODE_MFA_REQUIRED = "AUTH_MFA_REQUIRED";
        /** 错误码常量：账户要求 MFA 但尚未完成 MFA 注册 — 需先完成 MFA 设置。 */
        public static final String CODE_MFA_SETUP_REQUIRED = "AUTH_MFA_SETUP_REQUIRED";
        /** Error code for an expired, replayed, malformed, or cross-token context selection ticket. */
        public static final String CODE_CONTEXT_SELECTION_TICKET_INVALID = "AUTH_CONTEXT_SELECTION_TICKET_INVALID";
        /** Error code for a token whose membership/principal authorization version is stale. */
        public static final String CODE_STALE_AUTHZ_VERSION = "STALE_AUTHZ_VERSION";

        private final String errorCode;

        public AuthFlowException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public AuthFlowException(String errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }

        /** 返回机器可读错误码。 */
        public String getErrorCode() {
            return errorCode;
        }
    }
}
