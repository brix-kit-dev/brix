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
 * JWT Token 角色类型枚举 — B2B2C 双轨认证。
 *
 * <p>标识当前 Token 持有者的角色类型，通过 JWT claim {@code role} 携带。
 * Actor 和 Subject 在同一 Token 中互斥。
 *
 * <h3>双轨模型</h3>
 * <ul>
 *   <li>{@link #ACTOR} — B 端操作者（Owner/Admin/Member），Token 含 {@code mid} + {@code mtype}</li>
 *   <li>{@link #SUBJECT} — C 端主体（Customer/Guest），Token 含 {@code pid} + {@code ptype}</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public enum TokenRole {

    /**
     * Actor（B 端操作者）。
     *
     * <p>对应 {@code sys_tenant_member} 表。
     * Token 包含 {@code mid}（memberId）和 {@code mtype}（OWNER/ADMIN/MEMBER）。
     */
    ACTOR("actor"),

    /**
     * Subject（C 端主体）。
     *
     * <p>对应 {@code sys_tenant_principal} 表。
     * Token 包含 {@code pid}（principalId）和 {@code ptype}（CUSTOMER/GUEST）。
     */
    SUBJECT("subject"),

    /**
     * Platform Admin（平台超管 / 平台管理员）。
     *
     * <p>对应 {@code sys_platform_admin} 表。Token 不含租户上下文，
     * 包含 {@code admin_id} 与 {@code admin_role}（SUPER_ADMIN / OPERATOR / AUDITOR），
     * 跨租户操作由 {@code @CrossTenantAccess} 显式声明并由 Aspect/Interceptor 桥接。
     *
     * <p>Issuer 端见 {@link io.brix.platform.auth.jwt.JwtIssuerCapabilityImpl}
     * 的 {@code ROLE_PLATFORM_ADMIN = "platform-admin"} 常量。
     */
    PLATFORM_ADMIN("platform-admin");

    private final String value;

    TokenRole(String value) {
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
     * @return 对应的 TokenRole
     * @throws IllegalArgumentException 如果值无法识别
     */
    public static TokenRole fromValue(String value) {
        if (value == null) {
            return ACTOR; // 向后兼容：无 role claim 的旧 Token 视为 ACTOR
        }
        for (TokenRole role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown token role: " + value);
    }
}
