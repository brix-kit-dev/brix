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

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.capability.CircuitBreakerState;
import io.runtime.sdk.capability.ResilienceCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

/**
 * Fallback 韧性能力实现
 * 
 * <p>P-13: 提供透传式的默认韧性能力实现，当没有 Resilience4j 等具体适配器时作为 fallback。</p>
 * 
 * <h3>行为说明</h3>
 * <ul>
 *   <li><b>熔断器</b>：始终处于 {@link CircuitBreakerState#CLOSED} 状态，直接执行操作</li>
 *   <li><b>限流器</b>：始终返回未限流，允许所有请求通过</li>
 *   <li><b>降级</b>：仅在操作抛出异常时调用 fallback 函数</li>
 * </ul>
 * 
 * <p>⚠️ 警告：此实现不提供真实的弹性保护，仅保证 API 契约可用。
 * 生产环境应使用基于 Resilience4j 的正式适配器。</p>
 * 
 * @author Brix Team
 * @version 3.0.0
 * @since 3.0.0
 * @see ResilienceCapability
 */
@Capability(
    type = ResilienceCapability.class,
    name = "fallback-resilience",
    description = "透传式 Fallback 韧性实现 - 不提供真实弹性保护",
    level = CapabilityLevel.EXPERIMENTAL,
    aliases = {"fallbackResilience"}
)
public class FallbackResilienceCapability implements ResilienceCapability {

    private static final Logger log = LoggerFactory.getLogger(FallbackResilienceCapability.class);

    /**
     * 直接执行操作，不进行熔断保护
     * 
     * @param name      熔断器名称（日志用途）
     * @param operation 要执行的操作
     * @param <T>       返回类型
     * @return 操作结果
     */
    @Override
    public <T> T executeWithCircuitBreaker(String name, Supplier<T> operation) {
        log.debug("[Fallback Resilience] 执行操作（无熔断保护）: {}", name);
        return operation.get();
    }

    /**
     * 执行操作，失败时调用降级函数
     * 
     * <p>不使用熔断器状态判断，仅在操作抛出异常时触发降级</p>
     * 
     * @param name      熔断器名称（日志用途）
     * @param operation 要执行的操作
     * @param fallback  降级函数
     * @param <T>       返回类型
     * @return 操作结果或降级结果
     */
    @Override
    public <T> T executeWithFallback(String name, Supplier<T> operation, Supplier<T> fallback) {
        try {
            return operation.get();
        } catch (Exception e) {
            log.warn("[Fallback Resilience] 操作 '{}' 执行失败，触发降级: {}", name, e.getMessage());
            return fallback.get();
        }
    }

    /**
     * 始终返回 {@link CircuitBreakerState#CLOSED}
     * 
     * @param name 熔断器名称
     * @return 始终为 CLOSED
     */
    @Override
    public CircuitBreakerState getCircuitBreakerState(String name) {
        return CircuitBreakerState.CLOSED;
    }

    /**
     * 始终返回 false（未限流）
     * 
     * @param key 限流器键
     * @return 始终为 false
     */
    @Override
    public boolean isRateLimited(String key) {
        return false;
    }

    /**
     * 始终返回 true（令牌获取成功）
     * 
     * @param key     限流器键
     * @param permits 请求的令牌数（忽略）
     * @return 始终为 true
     */
    @Override
    public boolean tryAcquire(String key, int permits) {
        return true;
    }
}
