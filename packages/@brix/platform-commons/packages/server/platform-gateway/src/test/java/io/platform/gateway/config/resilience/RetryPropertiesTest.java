/*
 * Copyright 2026 Brix Platform Authors
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
package io.brix.platform.gateway.config.resilience;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * Retry Configuration Properties Test
 *
 * @author Brix Platform Authors
 */
class RetryPropertiesTest {

    @Test
    void shouldHaveDefaultValues() {
        RetryProperties properties = new RetryProperties();
        
        assertTrue(properties.isEnabled());
        assertEquals(3, properties.getMaxAttempts()); // MVP Guideline requirement
        assertEquals(500, properties.getInitialBackoffMs());
        assertEquals(5000, properties.getMaxBackoffMs());
        assertEquals(2.0, properties.getMultiplier());
        assertTrue(properties.isJitterEnabled());
        assertEquals(0.5, properties.getJitterFactor());
        assertTrue(properties.isRetryOnConnectionFailure());
        assertTrue(properties.isRetryOnTimeout());
    }

    @Test
    void shouldHaveCorrectRetryableStatusCodes() {
        RetryProperties properties = new RetryProperties();
        Set<Integer> codes = properties.getRetryableStatusCodes();
        
        assertTrue(codes.contains(502)); // Bad Gateway
        assertTrue(codes.contains(503)); // Service Unavailable
        assertTrue(codes.contains(504)); // Gateway Timeout
        assertFalse(codes.contains(500)); // Internal Server Error - not retryable
        assertFalse(codes.contains(400)); // Bad Request - not retryable
    }

    @Test
    void shouldHaveCorrectRetryableMethods() {
        RetryProperties properties = new RetryProperties();
        Set<HttpMethod> methods = properties.getRetryableMethods();
        
        // Idempotent methods should be retryable
        assertTrue(methods.contains(HttpMethod.GET));
        assertTrue(methods.contains(HttpMethod.HEAD));
        assertTrue(methods.contains(HttpMethod.OPTIONS));
        assertTrue(methods.contains(HttpMethod.PUT));
        assertTrue(methods.contains(HttpMethod.DELETE));
        
        // POST is not idempotent, not retryable by default
        assertFalse(methods.contains(HttpMethod.POST));
    }

    @Test
    void shouldRespectMvpMaxRetries() {
        RetryProperties properties = new RetryProperties();
        
// MVP Guideline requirement: max 3 retries
        assertEquals(3, properties.getMaxAttempts());
        
        // Can set fewer
        properties.setMaxAttempts(2);
        assertEquals(2, properties.getMaxAttempts());
        
        // Maximum should not exceed 5
        properties.setMaxAttempts(5);
        assertEquals(5, properties.getMaxAttempts());
    }
}
