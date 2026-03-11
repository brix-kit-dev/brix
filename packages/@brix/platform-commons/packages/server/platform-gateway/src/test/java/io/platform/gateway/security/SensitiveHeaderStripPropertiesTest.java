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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Sensitive Header Strip Configuration Properties Unit Tests
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
    @DisplayName("Default should contain MVP guideline required sensitive headers")
    void shouldContainMvpRequiredHeaders() {
        properties.init();
        
        assertTrue(properties.shouldStrip("x-user-id"));
        assertTrue(properties.shouldStrip("x-tenant-id"));
        assertTrue(properties.shouldStrip("x-role"));
        assertTrue(properties.shouldStrip("x-roles"));
    }

    @Test
    @DisplayName("Header name matching should be case-insensitive")
    void shouldMatchHeadersCaseInsensitively() {
        properties.init();
        
        assertTrue(properties.shouldStrip("X-User-Id"));
        assertTrue(properties.shouldStrip("X-USER-ID"));
        assertTrue(properties.shouldStrip("x-user-id"));
        assertTrue(properties.shouldStrip("X-TENANT-ID"));
    }

    @Test
    @DisplayName("Non-sensitive headers should not be stripped")
    void shouldNotStripNonSensitiveHeaders() {
        properties.init();
        
        assertFalse(properties.shouldStrip("Content-Type"));
        assertFalse(properties.shouldStrip("Accept"));
        assertFalse(properties.shouldStrip("User-Agent"));
    }

    @Test
    @DisplayName("Should not strip any headers when disabled")
    void shouldNotStripWhenDisabled() {
        properties.setEnabled(false);
        properties.init();
        
        assertFalse(properties.shouldStrip("x-user-id"));
        assertFalse(properties.shouldStrip("x-tenant-id"));
    }

    @Test
    @DisplayName("Should allow custom sensitive headers list")
    void shouldAllowCustomHeaders() {
        properties.setHeaders(List.of("x-custom-header", "x-another-header"));
        properties.init();
        
        assertTrue(properties.shouldStrip("x-custom-header"));
        assertTrue(properties.shouldStrip("x-another-header"));
        assertFalse(properties.shouldStrip("x-user-id")); // Default no longer takes effect
    }

    @Test
    @DisplayName("Handling null header name should return false")
    void shouldHandleNullHeaderName() {
        properties.init();
        
        assertFalse(properties.shouldStrip(null));
    }

    @Test
    @DisplayName("getHeadersAsSet should return immutable set")
    void shouldReturnImmutableSet() {
        properties.init();
        
        var headersSet = properties.getHeadersAsSet();
        
        assertThrows(UnsupportedOperationException.class, () -> headersSet.add("new-header"));
    }
}
