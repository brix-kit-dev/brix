package io.brix.platform.gateway.config.resilience;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * 重试配置属性测
 *
 * @author Brix Platform Authors
 */
class RetryPropertiesTest {

    @Test
    void shouldHaveDefaultValues() {
        RetryProperties properties = new RetryProperties();
        
        assertTrue(properties.isEnabled());
        assertEquals(3, properties.getMaxAttempts()); // MVP 红线要求
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
        assertFalse(codes.contains(500)); // Internal Server Error - 不重
        assertFalse(codes.contains(400)); // Bad Request - 不重
    }

    @Test
    void shouldHaveCorrectRetryableMethods() {
        RetryProperties properties = new RetryProperties();
        Set<HttpMethod> methods = properties.getRetryableMethods();
        
        // 幂等方法应该重试
        assertTrue(methods.contains(HttpMethod.GET));
        assertTrue(methods.contains(HttpMethod.HEAD));
        assertTrue(methods.contains(HttpMethod.OPTIONS));
        assertTrue(methods.contains(HttpMethod.PUT));
        assertTrue(methods.contains(HttpMethod.DELETE));
        
        // POST 不是幂等的，默认不重
        assertFalse(methods.contains(HttpMethod.POST));
    }

    @Test
    void shouldRespectMvpMaxRetries() {
        RetryProperties properties = new RetryProperties();
        
        // MVP 红线要求：最3 次重
        assertEquals(3, properties.getMaxAttempts());
        
        // 可以设置更少
        properties.setMaxAttempts(2);
        assertEquals(2, properties.getMaxAttempts());
        
        // 最大不超过 5
        properties.setMaxAttempts(5);
        assertEquals(5, properties.getMaxAttempts());
    }
}
