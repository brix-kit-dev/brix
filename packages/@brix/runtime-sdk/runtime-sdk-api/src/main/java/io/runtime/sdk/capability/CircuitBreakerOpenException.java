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
 * 熔断器打开异常
 * 
 * <p>当熔断器处于打开状态时抛出此异常，表示请求被拒绝。</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see ResilienceCapability#executeWithCircuitBreaker(String, java.util.function.Supplier)
 */
public class CircuitBreakerOpenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 熔断器名称
     */
    private final String circuitBreakerName;

    /**
     * 创建熔断器打开异常
     * 
     * @param circuitBreakerName 熔断器名称
     */
    public CircuitBreakerOpenException(String circuitBreakerName) {
        super("Circuit breaker '" + circuitBreakerName + "' is open");
        this.circuitBreakerName = circuitBreakerName;
    }

    /**
     * 创建熔断器打开异常
     * 
     * @param circuitBreakerName 熔断器名称
     * @param message            异常消息
     */
    public CircuitBreakerOpenException(String circuitBreakerName, String message) {
        super(message);
        this.circuitBreakerName = circuitBreakerName;
    }

    /**
     * 获取熔断器名称
     * 
     * @return 熔断器名称
     */
    public String getCircuitBreakerName() {
        return circuitBreakerName;
    }
}
