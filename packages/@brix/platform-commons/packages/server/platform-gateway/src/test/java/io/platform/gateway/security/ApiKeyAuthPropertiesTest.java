package io.brix.platform.gateway.security;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * API Key 认证配置属性单元测试
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
class ApiKeyAuthPropertiesTest {

    private ApiKeyAuthProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ApiKeyAuthProperties();
    }

    @Test
    @DisplayName("有效配置应该通过验证")
    void shouldPassValidationWithValidConfig() {
        ApiKeyAuthProperties.ApiKeyEntry entry = new ApiKeyAuthProperties.ApiKeyEntry();
        entry.setName("test-key");
        entry.setKey("valid-api-key-1234567890");
        entry.setSecret("valid-api-secret-12345678901234567890");
        
        properties.setKeys(List.of(entry));
        
        assertDoesNotThrow(() -> properties.validate());
    }

    @Test
    @DisplayName("空 Key 列表启用认证时应该抛出异常")
    void shouldThrowExceptionWhenEnabledWithNoKeys() {
        properties.setEnabled(true);
        properties.setKeys(new ArrayList<>());
        
        assertThrows(IllegalStateException.class, () -> properties.validate());
    }

    @Test
    @DisplayName("Key 值为空时应该抛出异常")
    void shouldThrowExceptionWhenKeyIsEmpty() {
        ApiKeyAuthProperties.ApiKeyEntry entry = new ApiKeyAuthProperties.ApiKeyEntry();
        entry.setName("test-key");
        entry.setKey("");
        entry.setSecret("valid-secret-12345678901234567890");
        
        properties.setKeys(List.of(entry));
        
        assertThrows(IllegalStateException.class, () -> properties.validate());
    }

    @Test
    @DisplayName("Secret 值为空时应该抛出异常")
    void shouldThrowExceptionWhenSecretIsEmpty() {
        ApiKeyAuthProperties.ApiKeyEntry entry = new ApiKeyAuthProperties.ApiKeyEntry();
        entry.setName("test-key");
        entry.setKey("valid-key-1234567890");
        entry.setSecret("");
        
        properties.setKeys(List.of(entry));
        
        assertThrows(IllegalStateException.class, () -> properties.validate());
    }

    @Test
    @DisplayName("重复Key 应该抛出异常")
    void shouldThrowExceptionForDuplicateKeys() {
        ApiKeyAuthProperties.ApiKeyEntry entry1 = new ApiKeyAuthProperties.ApiKeyEntry();
        entry1.setName("key-1");
        entry1.setKey("same-api-key-123456");
        entry1.setSecret("secret-1-12345678901234567890");
        
        ApiKeyAuthProperties.ApiKeyEntry entry2 = new ApiKeyAuthProperties.ApiKeyEntry();
        entry2.setName("key-2");
        entry2.setKey("same-api-key-123456"); // 重复Key
        entry2.setSecret("secret-2-12345678901234567890");
        
        properties.setKeys(List.of(entry1, entry2));
        
        assertThrows(IllegalStateException.class, () -> properties.validate());
    }

    @Test
    @DisplayName("禁用认证时不应该验证 Key 配置")
    void shouldNotValidateKeysWhenDisabled() {
        properties.setEnabled(false);
        properties.setKeys(new ArrayList<>()); // 空列表
        
        assertDoesNotThrow(() -> properties.validate());
    }

    @Test
    @DisplayName("默认请求头名称应该正确")
    void shouldHaveCorrectDefaultHeaderNames() {
        assertEquals("X-API-Key", properties.getHeaderName());
        assertEquals("X-API-Secret", properties.getSecretHeaderName());
    }

    @Test
    @DisplayName("应该能够配置排除路径")
    void shouldAllowExcludePaths() {
        properties.setExcludePaths(List.of("/actuator/health", "/healthz"));
        
        assertEquals(2, properties.getExcludePaths().size());
        assertTrue(properties.getExcludePaths().contains("/actuator/health"));
    }

    @Test
    @DisplayName("ApiKeyEntry 应该支持路径限制")
    void shouldSupportAllowedPathsInEntry() {
        ApiKeyAuthProperties.ApiKeyEntry entry = new ApiKeyAuthProperties.ApiKeyEntry();
        entry.setAllowedPaths(List.of("/api/plugins/**", "/api/engine/**"));
        
        assertEquals(2, entry.getAllowedPaths().size());
    }
}
