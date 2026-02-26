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
 * 限流超出异常
 * 
 * <p>当请求被限流器拒绝时抛出此异常。</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see ResilienceCapability#tryAcquire(String, int)
 */
public class RateLimitExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 限流器键
     */
    private final String rateLimiterKey;

    /**
     * 创建限流超出异常
     * 
     * @param message 异常消息
     */
    public RateLimitExceededException(String message) {
        super(message);
        this.rateLimiterKey = extractKeyFromMessage(message);
    }

    /**
     * 创建限流超出异常
     * 
     * @param rateLimiterKey 限流器键
     * @param message        异常消息
     */
    public RateLimitExceededException(String rateLimiterKey, String message) {
        super(message);
        this.rateLimiterKey = rateLimiterKey;
    }

    /**
     * 获取限流器键
     * 
     * @return 限流器键
     */
    public String getRateLimiterKey() {
        return rateLimiterKey;
    }

    /**
     * 从消息中提取限流器键
     */
    private static String extractKeyFromMessage(String message) {
        if (message != null && message.contains(":")) {
            return message.substring(message.lastIndexOf(":") + 1).trim();
        }
        return null;
    }
}
