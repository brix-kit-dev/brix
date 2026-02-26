package io.brix.platform.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 日志脱敏服务单元测试
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
class LogSanitizerTest {

    private LogSanitizer logSanitizer;
    private LogSanitizationProperties properties;

    @BeforeEach
    void setUp() {
        properties = new LogSanitizationProperties();
        properties.init();
        logSanitizer = new LogSanitizer(properties);
    }

    @Test
    @DisplayName("应该对 Authorization 头进行脱敏")
    void shouldSanitizeAuthorizationHeader() {
        String headerValue = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";
        
        String sanitized = logSanitizer.sanitizeHeader("Authorization", headerValue);
        
        assertNotEquals(headerValue, sanitized);
        assertTrue(sanitized.contains("****"));
        assertFalse(sanitized.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"));
    }

    @Test
    @DisplayName("应该对 X-API-Key 头进行脱敏")
    void shouldSanitizeApiKeyHeader() {
        String apiKey = "sk-1234567890abcdef1234567890abcdef";
        
        String sanitized = logSanitizer.sanitizeHeader("X-API-Key", apiKey);
        
        assertNotEquals(apiKey, sanitized);
        assertTrue(sanitized.contains("****"));
    }

    @Test
    @DisplayName("应该保留非敏感头原始值")
    void shouldKeepNonSensitiveHeaderValue() {
        String contentType = "application/json";
        
        String result = logSanitizer.sanitizeHeader("Content-Type", contentType);
        
        assertEquals(contentType, result);
    }

    @Test
    @DisplayName("应该Bearer token 进行特殊处理")
    void shouldHandleBearerTokenSpecially() {
        String bearerToken = "Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature";
        
        String sanitized = logSanitizer.sanitizeAuthorizationHeader(bearerToken);
        
        assertTrue(sanitized.startsWith("Bearer "));
        assertTrue(sanitized.contains("****"));
        assertFalse(sanitized.contains("payload"));
    }

    @Test
    @DisplayName("应该Basic auth 进行脱敏")
    void shouldSanitizeBasicAuth() {
        String basicAuth = "Basic dXNlcm5hbWU6cGFzc3dvcmQ=";
        
        String sanitized = logSanitizer.sanitizeAuthorizationHeader(basicAuth);
        
        assertTrue(sanitized.startsWith("Basic "));
        assertTrue(sanitized.contains("****"));
        assertFalse(sanitized.contains("dXNlcm5hbWU"));
    }

    @Test
    @DisplayName("应该对文本中的密码模式进行脱敏")
    void shouldSanitizePasswordPatternInText() {
        String text = "user=admin&password=secret123&token=abc";
        
        String sanitized = logSanitizer.sanitizeText(text);
        
        assertTrue(sanitized.contains("user=admin"));
        assertFalse(sanitized.contains("secret123"));
        assertTrue(sanitized.contains("****"));
    }

    @Test
    @DisplayName("应该对短值进行完全掩码")
    void shouldFullyMaskShortValues() {
        String shortValue = "abc";
        
        String masked = logSanitizer.maskValue(shortValue);
        
        assertFalse(masked.contains("a"));
        assertFalse(masked.contains("b"));
        assertFalse(masked.contains("c"));
        assertTrue(masked.contains("*"));
    }

    @Test
    @DisplayName("应该对长值保留首尾字符")
    void shouldKeepPrefixAndSuffixForLongValues() {
        String longValue = "abcdefghijklmnopqrstuvwxyz";
        
        String masked = logSanitizer.maskValue(longValue);
        
        assertTrue(masked.startsWith("abcd"));
        assertTrue(masked.endsWith("wxyz"));
        assertTrue(masked.contains("****"));
    }

    @Test
    @DisplayName("处理 null 值应该返回 null")
    void shouldHandleNullValue() {
        assertNull(logSanitizer.maskValue(null));
        assertNull(logSanitizer.sanitizeHeader("Authorization", null));
        assertNull(logSanitizer.sanitizeText(null));
    }

    @Test
    @DisplayName("禁用脱敏时应该返回原始值")
    void shouldReturnOriginalValueWhenDisabled() {
        properties.setEnabled(false);
        properties.init();
        
        String sensitiveValue = "secret-token-value";
        
        assertEquals(sensitiveValue, logSanitizer.sanitizeHeader("Authorization", sensitiveValue));
        assertEquals(sensitiveValue, logSanitizer.sanitizeText(sensitiveValue));
    }
}
