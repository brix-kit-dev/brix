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
 * Rate Limit Exceeded Exception
 * 
 * <p>Thrown when a request is rejected by the rate limiter.</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see ResilienceCapability#tryAcquire(String, int)
 */
public class RateLimitExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Rate limiter key
     */
    private final String rateLimiterKey;

    /**
     * Creates a rate limit exceeded exception
     * 
     * @param message the exception message
     */
    public RateLimitExceededException(String message) {
        super(message);
        this.rateLimiterKey = extractKeyFromMessage(message);
    }

    /**
     * Creates a rate limit exceeded exception
     * 
     * @param rateLimiterKey the rate limiter key
     * @param message        the exception message
     */
    public RateLimitExceededException(String rateLimiterKey, String message) {
        super(message);
        this.rateLimiterKey = rateLimiterKey;
    }

    /**
     * Gets the rate limiter key
     * 
     * @return the rate limiter key
     */
    public String getRateLimiterKey() {
        return rateLimiterKey;
    }

    /**
     * Extracts the rate limiter key from the message
     */
    private static String extractKeyFromMessage(String message) {
        if (message != null && message.contains(":")) {
            return message.substring(message.lastIndexOf(":") + 1).trim();
        }
        return null;
    }
}
