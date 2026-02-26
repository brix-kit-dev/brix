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

/**
 * 认证能力契约（标准名称）
 * 
 * <p>这是 {@link AuthContextCapability} 的标准化别名，用于统一前后端能力命名。
 * 建议新代码使用此接口名称。</p>
 * 
 * <h3>命名统一说明</h3>
 * <ul>
 *   <li>前端 TypeScript 端使用 {@code AuthCapability}</li>
 *   <li>后端 Java 端原使用 {@code AuthContextCapability}，现提供此别名</li>
 *   <li>两者接口定义一致，仅名称不同</li>
 * </ul>
 * 
 * <h3>迁移建议</h3>
 * <p>新代码建议使用 {@code AuthCapability}，旧代码可继续使用 {@code AuthContextCapability}。
 * 两者在运行时完全等价，无需强制迁移。</p>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Inject
 * private AuthCapability auth;
 * 
 * public void createReservation(ReservationCommand command) {
 *     if (!auth.hasPermission("booking:create")) {
 *         throw new AccessDeniedException("无预约创建权限");
 *     }
 *     // ...
 * }
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.2.0
 * @see AuthContextCapability
 */
public interface AuthCapability extends AuthContextCapability {
    // 此接口继承 AuthContextCapability 的所有方法
    // 作为标准化名称的别名，无需添加额外方法
}
