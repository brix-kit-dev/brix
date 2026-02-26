package io.brix.platform.gateway.config.resilience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * HTTP 超时配置属性测
 *
 * @author Brix Platform Authors
 */
class HttpTimeoutPropertiesTest {

    @Test
    void shouldHaveDefaultValues() {
        HttpTimeoutProperties properties = new HttpTimeoutProperties();
        
        assertTrue(properties.isEnabled());
        assertEquals(5000, properties.getConnectTimeoutMs());
        assertEquals(30000, properties.getResponseTimeoutMs());
        assertEquals(60000, properties.getGlobalTimeoutMs());
        assertEquals(30000, properties.getReadTimeoutMs());
        assertEquals(30000, properties.getWriteTimeoutMs());
    }

    @Test
    void shouldAllowCustomValues() {
        HttpTimeoutProperties properties = new HttpTimeoutProperties();
        
        properties.setEnabled(false);
        properties.setConnectTimeoutMs(10000);
        properties.setResponseTimeoutMs(60000);
        properties.setGlobalTimeoutMs(120000);
        
        assertFalse(properties.isEnabled());
        assertEquals(10000, properties.getConnectTimeoutMs());
        assertEquals(60000, properties.getResponseTimeoutMs());
        assertEquals(120000, properties.getGlobalTimeoutMs());
    }
}
