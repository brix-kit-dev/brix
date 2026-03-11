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
package io.brix.platform.gateway.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * HealthProperties Unit Tests
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@DisplayName("Gateway HealthProperties Test")
class HealthPropertiesTest {

    private HealthProperties properties;

    @BeforeEach
    void setUp() {
        properties = new HealthProperties();
    }

    @Test
    @DisplayName("Default values should be correctly set")
    void defaultValuesShouldBeCorrect() {
        assertTrue(properties.isEnabled());
        assertTrue(properties.isEngineCheckEnabled());
        assertEquals("http://localhost:8085", properties.getEngineUrl());
        assertEquals("/actuator/health/liveness", properties.getEngineHealthPath());
        assertEquals(3000, properties.getEngineTimeoutMs());
        assertTrue(properties.isRedisCheckEnabled());
        assertEquals(5, properties.getCacheTtlSeconds());
        assertTrue(properties.isShowDetails());
    }

    @Test
    @DisplayName("Set custom Engine URL")
    void shouldSetCustomEngineUrl() {
        String customUrl = "http://plugin-engine:8085";
        properties.setEngineUrl(customUrl);
        assertEquals(customUrl, properties.getEngineUrl());
    }

    @Test
    @DisplayName("Set custom timeout")
    void shouldSetCustomTimeout() {
        properties.setEngineTimeoutMs(5000);
        assertEquals(5000, properties.getEngineTimeoutMs());
    }

    @Test
    @DisplayName("Disable Engine health check")
    void shouldDisableEngineCheck() {
        properties.setEngineCheckEnabled(false);
        assertFalse(properties.isEngineCheckEnabled());
    }

    @Test
    @DisplayName("Disable Redis health check")
    void shouldDisableRedisCheck() {
        properties.setRedisCheckEnabled(false);
        assertFalse(properties.isRedisCheckEnabled());
    }

    @Test
    @DisplayName("Set cache TTL")
    void shouldSetCacheTtl() {
        properties.setCacheTtlSeconds(10);
        assertEquals(10, properties.getCacheTtlSeconds());
    }

    @Test
    @DisplayName("Set custom health check path")
    void shouldSetCustomHealthPath() {
        String customPath = "/actuator/health";
        properties.setEngineHealthPath(customPath);
        assertEquals(customPath, properties.getEngineHealthPath());
    }

    @Test
    @DisplayName("Hide detailed information")
    void shouldHideDetails() {
        properties.setShowDetails(false);
        assertFalse(properties.isShowDetails());
    }

    @Test
    @DisplayName("Disable health check enhancement")
    void shouldDisableHealthEnhancement() {
        properties.setEnabled(false);
        assertFalse(properties.isEnabled());
    }
}
