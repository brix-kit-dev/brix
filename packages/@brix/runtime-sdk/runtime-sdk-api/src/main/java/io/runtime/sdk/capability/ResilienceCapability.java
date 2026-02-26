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

import java.util.function.Supplier;

/**
 * 韧性能力契约
 * 
 * <p>提供熔断、限流、降级的统一抽象，增强系统的容错能力。
 * 模块通过此接口保护外部调用，无需直接使用 Resilience4j 等框架。</p>
 * 
 * <h3>核心功能</h3>
 * <ul>
 *   <li><b>熔断器（Circuit Breaker）</b>：防止故障扩散</li>
 *   <li><b>限流器（Rate Limiter）</b>：保护系统不被过载</li>
 *   <li><b>降级（Fallback）</b>：故障时提供备选方案</li>
 * </ul>
 * 
 * <h3>熔断器状态</h3>
 * <pre>{@code
 * CLOSED (正常) -> OPEN (熔断) -> HALF_OPEN (半开)
 *    ^                              |
 *    |______________________________|
 *              (恢复)
 * }</pre>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Inject
 * private ResilienceCapability resilience;
 * 
 * // 带熔断的外部调用
 * public UserInfo getUserInfo(String userId) {
 *     return resilience.executeWithCircuitBreaker("user-service", 
 *         () -> userServiceClient.getUser(userId));
 * }
 * 
 * // 带降级的调用
 * public UserInfo getUserInfoWithFallback(String userId) {
 *     return resilience.executeWithFallback("user-service",
 *         () -> userServiceClient.getUser(userId),
 *         () -> new UserInfo(userId, "Unknown"));  // 降级返回
 * }
 * 
 * // 限流检查
 * public void processRequest(Request request) {
 *     if (resilience.isRateLimited("api-calls")) {
 *         throw new TooManyRequestsException();
 *     }
 *     // 处理请求...
 * }
 * }</pre>
 * 
 * <h3>配置说明</h3>
 * <p>熔断器和限流器的配置在 module-manifest.yaml 中声明：</p>
 * <pre>{@code
 * resilience:
 *   circuit-breaker:
 *     - name: "user-service"
 *       failure-rate-threshold: 50
 *       wait-duration-in-open: "5s"
 *   rate-limiter:
 *     - name: "api-calls"
 *       limit-for-period: 100
 *       limit-refresh-period: "1s"
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public interface ResilienceCapability {

    /**
     * 使用熔断器执行操作
     * 
     * <p>当失败率超过阈值时，熔断器打开，后续调用直接抛出 {@link CircuitBreakerOpenException}</p>
     * 
     * @param name      熔断器名称，对应 manifest 中的配置
     * @param operation 要执行的操作
     * @param <T>       返回类型
     * @return 操作结果
     * @throws CircuitBreakerOpenException 如果熔断器处于打开状态
     */
    <T> T executeWithCircuitBreaker(String name, Supplier<T> operation);

    /**
     * 使用熔断器执行操作（带降级）
     * 
     * <p>当操作失败或熔断器打开时，调用降级函数返回备选结果</p>
     * 
     * @param name      熔断器名称
     * @param operation 要执行的操作
     * @param fallback  降级函数
     * @param <T>       返回类型
     * @return 操作结果或降级结果
     */
    <T> T executeWithFallback(String name, Supplier<T> operation, Supplier<T> fallback);

    /**
     * 获取熔断器状态
     * 
     * @param name 熔断器名称
     * @return 熔断器当前状态
     */
    CircuitBreakerState getCircuitBreakerState(String name);

    /**
     * 检查是否被限流
     * 
     * <p>此方法不消耗令牌，仅检查状态</p>
     * 
     * @param key 限流器键
     * @return 如果当前已被限流返回 true
     */
    boolean isRateLimited(String key);

    /**
     * 尝试获取令牌
     * 
     * <p>如果令牌可用则消耗一个令牌并返回 true，否则返回 false</p>
     * 
     * @param key     限流器键
     * @param permits 请求的令牌数
     * @return 如果成功获取令牌返回 true
     */
    boolean tryAcquire(String key, int permits);

    /**
     * 尝试获取单个令牌
     * 
     * @param key 限流器键
     * @return 如果成功获取令牌返回 true
     */
    default boolean tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    /**
     * 使用限流器执行操作
     * 
     * <p>如果令牌不足则抛出 {@link RateLimitExceededException}</p>
     * 
     * @param key       限流器键
     * @param operation 要执行的操作
     * @param <T>       返回类型
     * @return 操作结果
     * @throws RateLimitExceededException 如果被限流
     */
    default <T> T executeWithRateLimit(String key, Supplier<T> operation) {
        if (!tryAcquire(key)) {
            throw new RateLimitExceededException("Rate limit exceeded for: " + key);
        }
        return operation.get();
    }
}
