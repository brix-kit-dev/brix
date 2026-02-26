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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import io.runtime.sdk.capability.AuthContextCapability;
import io.runtime.sdk.capability.ConfigStoreCapability;
import io.runtime.sdk.capability.HttpCapability;
import io.runtime.sdk.capability.LifecycleCapability;
import io.runtime.sdk.capability.ObservabilityCapability;
import io.runtime.sdk.capability.ResilienceCapability;

/**
 * Fallback 能力适配器自动配置
 * 
 * <p>提供最小化的默认能力实现，当没有其他具体适配器时作为 fallback。</p>
 * 
 * <h3>架构说明</h3>
 * <p>
 * 根据 v3.0 运行壳架构设计蓝图：
 * <ul>
 *   <li>Host 层（shinwa-host-assembly）只做组装，不含实现代码</li>
 *   <li>所有能力实现必须在 infra-adapters 或 platform-commons</li>
 *   <li>此模块提供 fallback 实现，确保基本功能可用</li>
 * </ul>
 * </p>
 * 
 * <h3>提供的默认能力</h3>
 * <ul>
 *   <li>{@link AuthContextCapability} - 匿名访问，允许所有权限</li>
 *   <li>{@link ObservabilityCapability} - 基于 SLF4J 的日志实现</li>
 *   <li>{@link ConfigStoreCapability} - 基于环境变量和系统属性</li>
 *   <li>{@link LifecycleCapability} - 空操作实现</li>
 *   <li>{@link ResilienceCapability} - 透传式实现，无真实弹性保护</li>
 *   <li>{@link HttpCapability} - 基于 JDK HttpClient 的 HTTP 通信能力</li>
 * </ul>
 * 
 * @author Brix Team
 * @version 3.0.0
 * @since 3.0.0
 */
@AutoConfiguration
public class FallbackCapabilitiesAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FallbackCapabilitiesAutoConfiguration.class);

    /**
     * Default authentication context capability - anonymous access, allows all permissions.
     *
     * <h3>Security Warning</h3>
     * <p>
     * This bean grants unrestricted access to all permissions and roles.
     * <strong>MUST NOT</strong> be enabled in production environments.
     * </p>
     *
     * <h3>Production Protection</h3>
     * <ul>
     *   <li>{@code @Profile("!production")} - Excluded when production profile is active</li>
     *   <li>{@code @ConditionalOnProperty} - Requires explicit opt-in via configuration</li>
     *   <li>{@code @ConditionalOnMissingBean} - Skipped if a real auth capability exists</li>
     * </ul>
     *
     * <!-- 生产环境三重保护机制 -->
     * <!-- 1. Profile 排除：spring.profiles.active 包含 production 时不注册 -->
     * <!-- 2. 配置门控：必须显式设置 brix.fallback.auth.enabled=true -->
     * <!-- 3. Bean 优先级：存在真实认证实现时自动跳过 -->
     *
     * @return the fallback authentication context capability
     */
    @Bean
    @Profile("!production")
    @ConditionalOnProperty(
        prefix = "brix.fallback.auth",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
    )
    @ConditionalOnMissingBean(AuthContextCapability.class)
    public AuthContextCapability fallbackAuthContextCapability() {
        log.warn("[Fallback] Creating Fallback AuthContextCapability (allows all access) - FOR DEVELOPMENT ONLY");
        return new FallbackAuthContextCapability();
    }

    /**
     * 默认可观测性能力 - 基于 SLF4J 日志
     */
    @Bean
    @ConditionalOnMissingBean(ObservabilityCapability.class)
    public ObservabilityCapability fallbackObservabilityCapability() {
        log.info("[Fallback] 创建 Fallback ObservabilityCapability（基于 SLF4J）");
        return new FallbackObservabilityCapability();
    }

    /**
     * 默认配置存储能力 - 基于环境变量和系统属性
     */
    @Bean
    @ConditionalOnMissingBean(ConfigStoreCapability.class)
    public ConfigStoreCapability fallbackConfigStoreCapability() {
        log.info("[Fallback] 创建 Fallback ConfigStoreCapability（基于环境变量和系统属性）");
        return new FallbackConfigStoreCapability();
    }

    /**
     * 默认生命周期能力 - 空操作实现
     */
    @Bean
    @ConditionalOnMissingBean(LifecycleCapability.class)
    public LifecycleCapability fallbackLifecycleCapability() {
        log.info("[Fallback] 创建 Fallback LifecycleCapability（空操作）");
        return new FallbackLifecycleCapability();
    }

    /**
     * 默认韧性能力 - 透传式实现，不提供真实弹性保护
     * 
     * <p>⚠️ 警告：此实现不具备熔断/限流能力，仅保证 API 契约可用。
     * 生产环境应使用基于 Resilience4j 的正式适配器。</p>
     */
    @Bean
    @ConditionalOnMissingBean(ResilienceCapability.class)
    public ResilienceCapability fallbackResilienceCapability() {
        log.warn("[Fallback] 创建 Fallback ResilienceCapability（透传，无真实弹性保护）- 生产环境应使用 Resilience4j 适配器");
        return new FallbackResilienceCapability();
    }

    /**
     * 默认 HTTP 能力 - 基于 JDK HttpClient
     * 
     * <p>使用 Java 标准库 {@link java.net.http.HttpClient} 提供 HTTP 通信能力。
     * 此实现零外部依赖，适用于大多数场景。</p>
     * 
     * <h4>技术特性</h4>
     * <ul>
     *   <li>零外部依赖 — 仅使用 JDK 11+ 标准库</li>
     *   <li>HTTP/2 支持 — 自动协商协议版本</li>
     *   <li>可配置超时 — 连接 10 秒，请求 30 秒（默认）</li>
     * </ul>
     * 
     * <p>如需更高级功能（连接池管理、拦截器等），可引入
     * {@code infra-adapter-okhttp} 或 {@code infra-adapter-apache-http}。</p>
     */
    @Bean
    @ConditionalOnMissingBean(HttpCapability.class)
    public HttpCapability fallbackHttpCapability() {
        log.info("[Fallback] 创建 Fallback HttpCapability（基于 JDK HttpClient）");
        return new FallbackHttpCapability();
    }
}
