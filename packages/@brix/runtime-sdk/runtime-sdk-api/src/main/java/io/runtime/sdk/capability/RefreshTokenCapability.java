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

import java.util.Optional;

import io.runtime.sdk.annotation.Since;

/**
 * Refresh Token 持久化与吊销能力契约（Layer 2A — A2 Security Baseline）。
 *
 * <p>Brix Platform 有状态 Refresh Token 管理的统一入口。由 Layer 2C
 * ({@code platform-auth}) 提供默认的 DB 实现。将来可替换为 Redis 实现以提高性能。</p>
 *
 * <h3>设计目标</h3>
 * <ul>
 *   <li>所有 Refresh Token 必须持久化，用于吊销（Revoke）和旋转（Rotate）</li>
 *   <li>用户修改密码时自动吊销所有 Refresh Token</li>
 *   <li>Refresh Token 使用一次即旋转（Rotation-on-Use 策略）</li>
 * </ul>
 *
 * <h3>架构合规</h3>
 * <ul>
 *   <li>仅依赖 JDK 类型，{@code runtime-sdk-api} 零运行时依赖原则不被打破</li>
 *   <li>实现层在 {@code platform-auth}（Layer 2C），通过 Spring AutoConfiguration 注册</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.2.1
 */
@Since("3.2.1")
public interface RefreshTokenCapability {

    /**
     * 存储一个新的 Refresh Token（颁发时调用）。
     *
     * @param tokenId    不透明的令牌标识（UUID v4，不得可逆解码）
     * @param identityId 关联的身份 ID
     * @param adminId    平台管理员 ID（租户用户传 {@code null}）
     * @param ttlSeconds 有效期（秒）
     */
    void store(String tokenId, Long identityId, Long adminId, long ttlSeconds);

    /**
     * 验证 Refresh Token 并旋转（使用后立即作废旧令牌，颁发新令牌标识）。
     *
     * <p>实现必须在同一事务内完成：吊销旧令牌 + 创建新令牌记录，防止并发竞争条件。</p>
     *
     * @param tokenId 客户端提交的旧 Refresh Token
     * @return 旋转后的新令牌记录；如果 Token 无效 / 已吊销 / 已过期返回 {@link Optional#empty()}
     */
    Optional<RotatedToken> validateAndRotate(String tokenId);

    /**
     * 按 Token ID 吊销单个 Refresh Token。
     *
     * @param tokenId 要吊销的令牌 ID
     */
    void revokeByTokenId(String tokenId);

    /**
     * 吊销指定身份的所有 Refresh Token（密码修改 / 强制局放场景）。
     *
     * @param identityId 身份 ID
     */
    void revokeAllByIdentityId(Long identityId);

    // ========== Inner Record Types ==========

    /**
     * Refresh Token 旋转结果。
     *
     * @param newTokenId  新 Refresh Token ID（客户端下一次请求使用）
     * @param identityId  关联的身份 ID
     * @param adminId     平台管理员 ID（租户用户为 {@code null}）
     */
    record RotatedToken(String newTokenId, Long identityId, Long adminId) {}
}
