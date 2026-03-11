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
 * API Key Authentication Configuration Properties Unit Tests
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
    @DisplayName("Valid configuration should pass validation")
    void shouldPassValidationWithValidConfig() {
        ApiKeyAuthProperties.ApiKeyEntry entry = new ApiKeyAuthProperties.ApiKeyEntry();
        entry.setName("test-key");
        entry.setKey("valid-api-key-1234567890");
        entry.setSecret("valid-api-secret-12345678901234567890");
        
        properties.setKeys(List.of(entry));
        
        assertDoesNotThrow(() -> properties.validate());
    }

    @Test
    @DisplayName("Empty Key list with auth enabled should throw exception")
    void shouldThrowExceptionWhenEnabledWithNoKeys() {
        properties.setEnabled(true);
        properties.setKeys(new ArrayList<>());
        
        assertThrows(IllegalStateException.class, () -> properties.validate());
    }

    @Test
    @DisplayName("Empty Key value should throw exception")
    void shouldThrowExceptionWhenKeyIsEmpty() {
        ApiKeyAuthProperties.ApiKeyEntry entry = new ApiKeyAuthProperties.ApiKeyEntry();
        entry.setName("test-key");
        entry.setKey("");
        entry.setSecret("valid-secret-12345678901234567890");
        
        properties.setKeys(List.of(entry));
        
        assertThrows(IllegalStateException.class, () -> properties.validate());
    }

    @Test
    @DisplayName("Empty Secret value should throw exception")
    void shouldThrowExceptionWhenSecretIsEmpty() {
        ApiKeyAuthProperties.ApiKeyEntry entry = new ApiKeyAuthProperties.ApiKeyEntry();
        entry.setName("test-key");
        entry.setKey("valid-key-1234567890");
        entry.setSecret("");
        
        properties.setKeys(List.of(entry));
        
        assertThrows(IllegalStateException.class, () -> properties.validate());
    }

    @Test
    @DisplayName("Duplicate Key should throw exception")
    void shouldThrowExceptionForDuplicateKeys() {
        ApiKeyAuthProperties.ApiKeyEntry entry1 = new ApiKeyAuthProperties.ApiKeyEntry();
        entry1.setName("key-1");
        entry1.setKey("same-api-key-123456");
        entry1.setSecret("secret-1-12345678901234567890");
        
        ApiKeyAuthProperties.ApiKeyEntry entry2 = new ApiKeyAuthProperties.ApiKeyEntry();
        entry2.setName("key-2");
        entry2.setKey("same-api-key-123456"); // Duplicate Key
        entry2.setSecret("secret-2-12345678901234567890");
        
        properties.setKeys(List.of(entry1, entry2));
        
        assertThrows(IllegalStateException.class, () -> properties.validate());
    }

    @Test
    @DisplayName("Should not validate Key configuration when auth is disabled")
    void shouldNotValidateKeysWhenDisabled() {
        properties.setEnabled(false);
        properties.setKeys(new ArrayList<>()); // Empty list
        
        assertDoesNotThrow(() -> properties.validate());
    }

    @Test
    @DisplayName("Default header names should be correct")
    void shouldHaveCorrectDefaultHeaderNames() {
        assertEquals("X-API-Key", properties.getHeaderName());
        assertEquals("X-API-Secret", properties.getSecretHeaderName());
    }

    @Test
    @DisplayName("Should allow configuring exclude paths")
    void shouldAllowExcludePaths() {
        properties.setExcludePaths(List.of("/actuator/health", "/healthz"));
        
        assertEquals(2, properties.getExcludePaths().size());
        assertTrue(properties.getExcludePaths().contains("/actuator/health"));
    }

    @Test
    @DisplayName("ApiKeyEntry should support path restrictions")
    void shouldSupportAllowedPathsInEntry() {
        ApiKeyAuthProperties.ApiKeyEntry entry = new ApiKeyAuthProperties.ApiKeyEntry();
        entry.setAllowedPaths(List.of("/api/plugins/**", "/api/engine/**"));
        
        assertEquals(2, entry.getAllowedPaths().size());
    }
}
