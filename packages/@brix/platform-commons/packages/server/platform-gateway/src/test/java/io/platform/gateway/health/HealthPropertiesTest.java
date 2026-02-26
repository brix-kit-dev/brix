package io.brix.platform.gateway.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * HealthProperties 单元测试
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@DisplayName("Gateway HealthProperties 测试")
class HealthPropertiesTest {

    private HealthProperties properties;

    @BeforeEach
    void setUp() {
        properties = new HealthProperties();
    }

    @Test
    @DisplayName("默认值应正确设置")
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
    @DisplayName("设置自定义 Engine URL")
    void shouldSetCustomEngineUrl() {
        String customUrl = "http://plugin-engine:8085";
        properties.setEngineUrl(customUrl);
        assertEquals(customUrl, properties.getEngineUrl());
    }

    @Test
    @DisplayName("设置自定义超时时间")
    void shouldSetCustomTimeout() {
        properties.setEngineTimeoutMs(5000);
        assertEquals(5000, properties.getEngineTimeoutMs());
    }

    @Test
    @DisplayName("禁用 Engine 健康检查")
    void shouldDisableEngineCheck() {
        properties.setEngineCheckEnabled(false);
        assertFalse(properties.isEngineCheckEnabled());
    }

    @Test
    @DisplayName("禁用 Redis 健康检查")
    void shouldDisableRedisCheck() {
        properties.setRedisCheckEnabled(false);
        assertFalse(properties.isRedisCheckEnabled());
    }

    @Test
    @DisplayName("设置缓存 TTL")
    void shouldSetCacheTtl() {
        properties.setCacheTtlSeconds(10);
        assertEquals(10, properties.getCacheTtlSeconds());
    }

    @Test
    @DisplayName("设置自定义健康检查路径")
    void shouldSetCustomHealthPath() {
        String customPath = "/actuator/health";
        properties.setEngineHealthPath(customPath);
        assertEquals(customPath, properties.getEngineHealthPath());
    }

    @Test
    @DisplayName("隐藏详细信息")
    void shouldHideDetails() {
        properties.setShowDetails(false);
        assertFalse(properties.isShowDetails());
    }

    @Test
    @DisplayName("禁用健康检查增强功能")
    void shouldDisableHealthEnhancement() {
        properties.setEnabled(false);
        assertFalse(properties.isEnabled());
    }
}
