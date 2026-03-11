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
package io.brix.platform.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Log Sanitizer Service Unit Tests
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
    @DisplayName("Should sanitize Authorization header")
    void shouldSanitizeAuthorizationHeader() {
        String headerValue = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";
        
        String sanitized = logSanitizer.sanitizeHeader("Authorization", headerValue);
        
        assertNotEquals(headerValue, sanitized);
        assertTrue(sanitized.contains("****"));
        assertFalse(sanitized.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"));
    }

    @Test
    @DisplayName("Should sanitize X-API-Key header")
    void shouldSanitizeApiKeyHeader() {
        String apiKey = "sk-1234567890abcdef1234567890abcdef";
        
        String sanitized = logSanitizer.sanitizeHeader("X-API-Key", apiKey);
        
        assertNotEquals(apiKey, sanitized);
        assertTrue(sanitized.contains("****"));
    }

    @Test
    @DisplayName("Should keep original value for non-sensitive headers")
    void shouldKeepNonSensitiveHeaderValue() {
        String contentType = "application/json";
        
        String result = logSanitizer.sanitizeHeader("Content-Type", contentType);
        
        assertEquals(contentType, result);
    }

    @Test
    @DisplayName("Should handle Bearer token specially")
    void shouldHandleBearerTokenSpecially() {
        String bearerToken = "Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature";
        
        String sanitized = logSanitizer.sanitizeAuthorizationHeader(bearerToken);
        
        assertTrue(sanitized.startsWith("Bearer "));
        assertTrue(sanitized.contains("****"));
        assertFalse(sanitized.contains("payload"));
    }

    @Test
    @DisplayName("Should sanitize Basic auth")
    void shouldSanitizeBasicAuth() {
        String basicAuth = "Basic dXNlcm5hbWU6cGFzc3dvcmQ=";
        
        String sanitized = logSanitizer.sanitizeAuthorizationHeader(basicAuth);
        
        assertTrue(sanitized.startsWith("Basic "));
        assertTrue(sanitized.contains("****"));
        assertFalse(sanitized.contains("dXNlcm5hbWU"));
    }

    @Test
    @DisplayName("Should sanitize password patterns in text")
    void shouldSanitizePasswordPatternInText() {
        String text = "user=admin&password=secret123&token=abc";
        
        String sanitized = logSanitizer.sanitizeText(text);
        
        assertTrue(sanitized.contains("user=admin"));
        assertFalse(sanitized.contains("secret123"));
        assertTrue(sanitized.contains("****"));
    }

    @Test
    @DisplayName("Should fully mask short values")
    void shouldFullyMaskShortValues() {
        String shortValue = "abc";
        
        String masked = logSanitizer.maskValue(shortValue);
        
        assertFalse(masked.contains("a"));
        assertFalse(masked.contains("b"));
        assertFalse(masked.contains("c"));
        assertTrue(masked.contains("*"));
    }

    @Test
    @DisplayName("Should keep prefix and suffix for long values")
    void shouldKeepPrefixAndSuffixForLongValues() {
        String longValue = "abcdefghijklmnopqrstuvwxyz";
        
        String masked = logSanitizer.maskValue(longValue);
        
        assertTrue(masked.startsWith("abcd"));
        assertTrue(masked.endsWith("wxyz"));
        assertTrue(masked.contains("****"));
    }

    @Test
    @DisplayName("Handling null value should return null")
    void shouldHandleNullValue() {
        assertNull(logSanitizer.maskValue(null));
        assertNull(logSanitizer.sanitizeHeader("Authorization", null));
        assertNull(logSanitizer.sanitizeText(null));
    }

    @Test
    @DisplayName("Should return original value when sanitization is disabled")
    void shouldReturnOriginalValueWhenDisabled() {
        properties.setEnabled(false);
        properties.init();
        
        String sensitiveValue = "secret-token-value";
        
        assertEquals(sensitiveValue, logSanitizer.sanitizeHeader("Authorization", sensitiveValue));
        assertEquals(sensitiveValue, logSanitizer.sanitizeText(sensitiveValue));
    }
}
