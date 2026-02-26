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
package io.infra.adapter.fallback;

import io.runtime.sdk.capability.HttpCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

import java.net.http.HttpClient;

/**
 * Fallback HTTP 能力实现（基于 JDK HttpClient）
 * 
 * <p>使用 Java 标准库 {@link java.net.http.HttpClient} 提供 HTTP 通信能力。
 * 此实现作为 Fallback，当没有其他 HTTP 适配器（如基于 OkHttp、Apache HttpClient）时自动启用。</p>
 * 
 * <h3>架构说明</h3>
 * <p>
 * 根据 v3.0 运行壳架构设计蓝图：
 * <ul>
 *   <li>Host 层（shinwa-host-assembly）只做组装，不含实现代码</li>
 *   <li>所有能力实现必须在 infra-adapters 或 platform-commons</li>
 *   <li>此类从 host-shell-standalone 迁移至此，符合极薄 Host 原则</li>
 * </ul>
 * </p>
 * 
 * <h3>继承说明</h3>
 * <p>本类继承自 {@link AbstractJdkHttpCapability}，复用其所有 HTTP 方法实现。
 * 这消除了与 {@code JdkHttpCapability} 的代码重复，符合 DRY 原则。</p>
 * 
 * <h3>技术特性</h3>
 * <ul>
 *   <li><b>零外部依赖</b> — 仅使用 JDK 11+ 标准库 {@link java.net.http.HttpClient}</li>
 *   <li><b>HTTP/2 支持</b> — 自动协商协议版本（HTTP/1.1 或 HTTP/2）</li>
 *   <li><b>可配置超时</b> — 支持连接超时和请求超时配置</li>
 *   <li><b>线程安全</b> — {@link HttpClient} 实例线程安全，可安全共享</li>
 * </ul>
 * 
 * <h3>超时配置说明</h3>
 * <ul>
 *   <li><b>连接超时（connectTimeout）</b>：建立 TCP 连接的最大等待时间，默认 10 秒</li>
 *   <li><b>请求超时（requestTimeout）</b>：单次 HTTP 请求的最大等待时间，默认 30 秒</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 使用默认超时（连接10秒，请求30秒）
 * HttpCapability http = new FallbackHttpCapability();
 * 
 * // 自定义超时
 * HttpCapability http = new FallbackHttpCapability(5, 60);
 * 
 * // GET 请求
 * HttpResult result = http.get("https://api.example.com/users", 
 *     Map.of("Authorization", "Bearer token"));
 * 
 * // POST 请求
 * HttpResult result = http.post("https://api.example.com/users", 
 *     "{\"name\":\"John\"}", 
 *     Map.of("Content-Type", "application/json"));
 * }</pre>
 * 
 * @author Brix Team
 * @version 3.2.0
 * @since 3.0.0
 * @see HttpCapability
 * @see AbstractJdkHttpCapability
 * @see HttpClient
 */
@Capability(
    type = HttpCapability.class,
    name = "fallback-http",
    description = "Fallback HTTP 能力实现，基于 JDK HttpClient，当无其他 HTTP 适配器时自动启用",
    level = CapabilityLevel.FALLBACK,
    aliases = {"fallbackHttp", "defaultHttp"}
)
public class FallbackHttpCapability extends AbstractJdkHttpCapability {

    /**
     * 使用指定超时创建实例
     * 
     * <p>配置说明：
     * <ul>
     *   <li>连接超时：建立 TCP 连接的最大等待时间</li>
     *   <li>请求超时：单次 HTTP 请求的最大等待时间</li>
     * </ul>
     * </p>
     * 
     * @param connectTimeoutSeconds 连接超时秒数（建立 TCP 连接的最大等待时间）
     * @param requestTimeoutSeconds 请求超时秒数（单次 HTTP 请求的最大等待时间）
     */
    public FallbackHttpCapability(int connectTimeoutSeconds, int requestTimeoutSeconds) {
        super(connectTimeoutSeconds, requestTimeoutSeconds);
    }

    /**
     * 使用默认超时创建实例
     * 
     * <p>使用推荐的默认超时值：连接 10 秒，请求 30 秒。
     * 这些值适合大多数业务场景，避免长时间等待导致的资源占用。</p>
     */
    public FallbackHttpCapability() {
        super();
    }
}
