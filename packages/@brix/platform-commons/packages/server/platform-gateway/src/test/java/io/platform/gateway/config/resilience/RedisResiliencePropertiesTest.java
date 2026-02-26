package io.brix.platform.gateway.config.resilience;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Redis 弹性配置属性测
 *
 * @author Brix Platform Authors
 */
class RedisResiliencePropertiesTest {

    @Test
    void shouldHaveDefaultValues() {
        RedisResilienceProperties properties = new RedisResilienceProperties();
        
        assertTrue(properties.isEnabled());
        assertEquals(5000, properties.getCommandTimeoutMs()); // MVP 红线要求
        assertEquals(5000, properties.getConnectTimeoutMs());
        assertEquals(3, properties.getMaxAttempts()); // MVP 红线：最3 
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
        
        // 连接池配置应该是合理
        assertTrue(properties.getMinIdleConnections() > 0);
        assertTrue(properties.getMaxConnections() >= properties.getMinIdleConnections());
        assertTrue(properties.getMaxConnections() <= 200); // 不要太大
    }
}
