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

import org.junit.jupiter.api.Test;

/**
 * Redis Resilience Configuration Properties Test
 *
 * @author Brix Platform Authors
 */
class RedisResiliencePropertiesTest {

    @Test
    void shouldHaveDefaultValues() {
        RedisResilienceProperties properties = new RedisResilienceProperties();
        
        assertTrue(properties.isEnabled());
        assertEquals(5000, properties.getCommandTimeoutMs()); // MVP Guideline requirement
        assertEquals(5000, properties.getConnectTimeoutMs());
        assertEquals(3, properties.getMaxAttempts()); // MVP Guideline: max 3 retries
        assertEquals(200, properties.getRetryInitialDelayMs());
        assertEquals(2000, properties.getRetryMaxDelayMs());
        assertTrue(properties.isAutoReconnect());
        assertEquals(5, properties.getMinIdleConnections());
        assertEquals(50, properties.getMaxConnections());
    }

    @Test
    void shouldAllowCustomValues() {
        RedisResilienceProperties properties = new RedisResilienceProperties();
        
        properties.setCommandTimeoutMs(10000);
        properties.setConnectTimeoutMs(8000);
        properties.setMaxAttempts(5);
        properties.setAutoReconnect(false);
        
        assertEquals(10000, properties.getCommandTimeoutMs());
        assertEquals(8000, properties.getConnectTimeoutMs());
        assertEquals(5, properties.getMaxAttempts());
        assertFalse(properties.isAutoReconnect());
    }

    @Test
    void shouldHaveReasonablePoolDefaults() {
        RedisResilienceProperties properties = new RedisResilienceProperties();
        
        // Connection pool configuration should be reasonable
        assertTrue(properties.getMinIdleConnections() > 0);
        assertTrue(properties.getMaxConnections() >= properties.getMinIdleConnections());
        assertTrue(properties.getMaxConnections() <= 200); // Should not be too large
    }
}
