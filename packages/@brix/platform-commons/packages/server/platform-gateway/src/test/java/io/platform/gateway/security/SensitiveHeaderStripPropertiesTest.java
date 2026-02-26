package io.brix.platform.gateway.security;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 敏感头剥离配置属性单元测试
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
class SensitiveHeaderStripPropertiesTest {

    private SensitiveHeaderStripProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SensitiveHeaderStripProperties();
    }

    @Test
    @DisplayName("默认应该包含 MVP 红线要求的敏感头")
    void shouldContainMvpRequiredHeaders() {
        properties.init();
        
        assertTrue(properties.shouldStrip("x-user-id"));
        assertTrue(properties.shouldStrip("x-tenant-id"));
        assertTrue(properties.shouldStrip("x-role"));
        assertTrue(properties.shouldStrip("x-roles"));
    }

    @Test
    @DisplayName("头名称匹配应该不区分大小写")
    void shouldMatchHeadersCaseInsensitively() {
        properties.init();
        
        assertTrue(properties.shouldStrip("X-User-Id"));
        assertTrue(properties.shouldStrip("X-USER-ID"));
        assertTrue(properties.shouldStrip("x-user-id"));
        assertTrue(properties.shouldStrip("X-TENANT-ID"));
    }

    @Test
    @DisplayName("非敏感头不应该被剥离")
    void shouldNotStripNonSensitiveHeaders() {
        properties.init();
        
        assertFalse(properties.shouldStrip("Content-Type"));
        assertFalse(properties.shouldStrip("Accept"));
        assertFalse(properties.shouldStrip("User-Agent"));
    }

    @Test
    @DisplayName("禁用时不应该剥离任何头")
    void shouldNotStripWhenDisabled() {
        properties.setEnabled(false);
        properties.init();
        
        assertFalse(properties.shouldStrip("x-user-id"));
        assertFalse(properties.shouldStrip("x-tenant-id"));
    }

    @Test
    @DisplayName("应该能够自定义敏感头列表")
    void shouldAllowCustomHeaders() {
        properties.setHeaders(List.of("x-custom-header", "x-another-header"));
        properties.init();
        
        assertTrue(properties.shouldStrip("x-custom-header"));
        assertTrue(properties.shouldStrip("x-another-header"));
        assertFalse(properties.shouldStrip("x-user-id")); // 默认的不再生效
    }

    @Test
    @DisplayName("处理 null 头名称应该返回 false")
    void shouldHandleNullHeaderName() {
        properties.init();
        
        assertFalse(properties.shouldStrip(null));
    }

    @Test
    @DisplayName("getHeadersAsSet 应该返回不可变集合")
    void shouldReturnImmutableSet() {
        properties.init();
        
        var headersSet = properties.getHeadersAsSet();
        
        assertThrows(UnsupportedOperationException.class, () -> headersSet.add("new-header"));
    }
}
