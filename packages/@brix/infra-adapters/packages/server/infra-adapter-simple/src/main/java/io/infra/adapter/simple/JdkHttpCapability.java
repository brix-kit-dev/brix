/*
 * Copyright 2026 Brix Authors
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
package io.infra.adapter.simple;

import io.infra.adapter.fallback.AbstractJdkHttpCapability;
import io.runtime.sdk.capability.HttpCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

import java.net.http.HttpClient;

/**
 * 基于 JDK HttpClient 的 HTTP 能力实现
 * 
 * <p>使用 Java 标准库 {@link java.net.http.HttpClient} 提供 HTTP 通信能力。
 * 适用于 Simple Adapter 场景（开发、测试、嵌入式部署）。</p>
 * 
 * <h3>架构说明</h3>
 * <p>
 * 本类属于 {@code infra-adapter-simple} 模块（Layer 2.5: Adapter 层），
 * 实现 {@link HttpCapability} 契约，为插件提供 HTTP 通信能力。
 * </p>
 * 
 * <h3>继承说明</h3>
 * <p>本类继承自 {@link AbstractJdkHttpCapability}，复用其所有 HTTP 方法实现。
 * 这消除了与 {@code FallbackHttpCapability} 的代码重复，符合 DRY 原则。</p>
 * 
 * <h3>特性</h3>
 * <ul>
 *   <li><b>零外部依赖</b> — 仅使用 JDK 11+ 标准库 {@link java.net.http.HttpClient}</li>
 *   <li><b>可配置超时</b> — 支持连接超时和请求超时配置</li>
 *   <li><b>HTTP/2 支持</b> — 自动协商协议版本</li>
 *   <li><b>简单部署</b> — 适用于开发、测试、嵌入式部署等简化场景</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * HttpCapability http = new JdkHttpCapability();
 * HttpResult result = http.get("https://api.example.com/users", 
 *     Map.of("Authorization", "Bearer token"));
 * }</pre>
 * 
 * @author Brix Team
 * @since 3.1.0
 * @see HttpCapability
 * @see AbstractJdkHttpCapability
 * @see HttpClient
 */
@Capability(
    type = HttpCapability.class,
    name = "jdk-http",
    description = "基于 JDK HttpClient 的 HTTP 能力实现，零外部依赖，适用于开发和简化部署场景",
    level = CapabilityLevel.STANDARD,
    aliases = {"jdkHttp", "simpleHttp"}
)
public class JdkHttpCapability extends AbstractJdkHttpCapability {

    /**
     * 使用指定超时创建实例
     * 
     * @param connectTimeoutSeconds 连接超时秒数（建立 TCP 连接的最大等待时间）
     * @param requestTimeoutSeconds 请求超时秒数（单次 HTTP 请求的最大等待时间）
     */
    public JdkHttpCapability(int connectTimeoutSeconds, int requestTimeoutSeconds) {
        super(connectTimeoutSeconds, requestTimeoutSeconds);
    }

    /**
     * 使用默认超时创建实例（连接10秒，请求30秒）
     */
    public JdkHttpCapability() {
        super();
    }
}
