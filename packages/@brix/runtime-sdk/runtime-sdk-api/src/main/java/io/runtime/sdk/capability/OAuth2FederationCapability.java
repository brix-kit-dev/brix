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

import io.runtime.sdk.annotation.Since;

/**
 * OAuth2 / OIDC 联邦登录能力契约（Layer 2A）。
 *
 * <p>统一封装第三方身份提供商（Google / Apple / 企业 SSO 等）的
 * ID Token 验证 + 本地身份联邦绑定流程。由 Layer 2C
 * （{@code platform-auth}）提供默认实现，验证通过后委派给
 * {@link AuthFlowCapability} 完成多租户登录编排，复用同一套
 * 令牌签发与租户选择路径。</p>
 *
 * <h3>架构合规</h3>
 * <ul>
 *   <li>仅依赖 JDK 类型 + 同层契约，{@code runtime-sdk-api} 零运行时依赖原则不被打破</li>
 *   <li>具体的 ID Token 公钥获取（JWKS）、缓存、时钟漂移容忍均由实现层处理</li>
 *   <li>本地身份不存在时，实现可选择自动联邦创建或抛出 {@link AuthFlowCapability.AuthFlowException}</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see AuthFlowCapability
 */
@Since("3.2.0")
public interface OAuth2FederationCapability {

    /**
     * 使用 Google ID Token 完成联邦登录。
     *
     * <p>实现步骤：
     * <ol>
     *   <li>从 Google JWKS 验证 ID Token 签名（RS256）</li>
     *   <li>校验 {@code aud / iss / exp / nonce}（如启用）</li>
     *   <li>提取 {@code sub / email / email_verified / name}</li>
     *   <li>查询 / 创建本地 {@code sys_identity}（联邦绑定）</li>
     *   <li>委派 {@link AuthFlowCapability} 完成租户选择与令牌签发</li>
     * </ol>
     * </p>
     *
     * @param idToken Google 颁发的 ID Token JWT
     * @return 登录结果（COMPLETE 或 SELECT_TENANT）
     * @throws AuthFlowCapability.AuthFlowException ID Token 无效 / 邮箱未验证 / 联邦失败
     */
    AuthFlowCapability.LoginResult loginWithGoogleIdToken(String idToken);
}
