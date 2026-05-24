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
package io.brix.platform.auth.enums;

/**
 * JWT Token 类型枚举。
 *
 * <p>区分 Access Token、Identity Token 和 Refresh Token 的用途，
 * 通过 JWT claim {@code token_type} 携带。
 *
 * <h3>B2B2C 多租户认证流程中的 Token 类型</h3>
 * <ul>
 *   <li>{@link #ACCESS} — 完整访问令牌，含 tid/mid 或 pid，可访问业务 API</li>
 *   <li>{@link #IDENTITY} — 临时身份令牌（5min），仅含 sub，用于租户选择阶段</li>
 *   <li>{@link #REFRESH} — 刷新令牌，用于轮转换发新 Access Token</li>
 *   <li>{@link #BOOTSTRAP_SETUP} — dedicated first-admin setup token</li>
 *   <li>{@link #MFA_CHALLENGE} — password-verified platform MFA challenge token</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public enum TokenType {

    /**
     * 完整访问令牌 — 可访问全部授权 API。
     *
     * <p>包含租户上下文（tid）和成员/主体标识（mid 或 pid）。
     */
    ACCESS("access"),

    /**
     * 临时身份令牌 — 仅用于租户选择阶段。
     *
     * <p>有效期 5 分钟，仅含 {@code sub}（identity_id），
     * 通过 {@code allowed_actions} claim 限制可调用的端点
     * （select-tenant、register-tenant）。
     */
    IDENTITY("identity"),

    /**
     * 刷新令牌 — 用于 Token 轮转。
     *
     * <p>绑定 device_id，支持 Family 机制检测重放攻击。
     */
    REFRESH("refresh"),

    /** Dedicated Bootstrap Setup token, not a PLATFORM login token. */
    BOOTSTRAP_SETUP("BOOTSTRAP_SETUP"),

    /** Short-lived platform-admin MFA challenge token. */
    MFA_CHALLENGE("mfa_challenge");

    private final String value;

    TokenType(String value) {
        this.value = value;
    }

    /**
     * 返回 JWT claim 中使用的字符串值。
     *
     * @return claim 值
     */
    public String getValue() {
        return value;
    }

    /**
     * 从 JWT claim 字符串值解析枚举。
     *
     * @param value claim 字符串值
     * @return 对应的 TokenType
     * @throws IllegalArgumentException 如果值无法识别
     */
    public static TokenType fromValue(String value) {
        if (value == null) {
            return ACCESS; // 向后兼容：无 token_type claim 的旧 Token 视为 ACCESS
        }
        for (TokenType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown token type: " + value);
    }
}
