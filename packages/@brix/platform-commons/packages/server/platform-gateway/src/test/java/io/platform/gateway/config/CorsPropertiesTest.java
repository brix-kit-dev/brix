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
package io.brix.platform.gateway.config;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * CORS Configuration Properties Unit Tests
 * <p>
 * MVP Guideline M014: Core path unit test coverage
 * </p>
 * <p>
 * MVP Guideline Requirements:
 * <ul>
 *   <li>Explicit CORS whitelist configuration</li>
 *   <li>Wildcard prohibition in production environment</li>
 * </ul>
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@DisplayName("CorsProperties CORS Configuration Properties Test")
@SuppressWarnings("unused") // JUnit nested classes, setUp used by JUnit
class CorsPropertiesTest {

    private CorsProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CorsProperties();
    }

    // ========== Default Value Tests ==========

    @Nested
    @DisplayName("Default Value Verification")
    class DefaultValueTests {

        @Test
        @DisplayName("Should have default allowed origins (development mode)")
        void shouldHaveDefaultAllowedOrigins() {
            List<String> patterns = properties.getAllowedOriginPatterns();
            
            assertNotNull(patterns);
            assertTrue(patterns.contains("*"));
        }

        @Test
        @DisplayName("Should have default allowed HTTP methods")
        void shouldHaveDefaultAllowedMethods() {
            List<String> methods = properties.getAllowedMethods();
            
            assertNotNull(methods);
            assertTrue(methods.contains("GET"));
            assertTrue(methods.contains("POST"));
            assertTrue(methods.contains("PUT"));
            assertTrue(methods.contains("DELETE"));
            assertTrue(methods.contains("OPTIONS"));
        }

        @Test
        @DisplayName("Should have default allowed headers")
        void shouldHaveDefaultAllowedHeaders() {
            List<String> headers = properties.getAllowedHeaders();
            
            assertNotNull(headers);
            assertTrue(headers.contains("*"));
        }

        @Test
        @DisplayName("Should allow credentials by default")
        void shouldAllowCredentialsByDefault() {
            assertTrue(properties.isAllowCredentials());
        }

        @Test
        @DisplayName("Default cache max age should be 3600 seconds")
        void shouldHaveDefaultMaxAge() {
            assertEquals(3600L, properties.getMaxAge());
        }

        @Test
        @DisplayName("Should enable wildcard warning by default")
        void shouldWarnOnWildcardByDefault() {
            assertTrue(properties.isWarnOnWildcard());
        }

        @Test
        @DisplayName("Should not block wildcard in production by default")
        void shouldBlockWildcardInProductionByDefault() {
            assertFalse(properties.isBlockWildcardInProduction());
        }
    }

    // ========== Custom Configuration Tests ==========

    @Nested
    @DisplayName("Custom Configuration")
    class CustomConfigurationTests {

        @Test
        @DisplayName("Should support custom whitelist domains")
        void shouldAllowCustomWhitelist() {
            properties.setAllowedOriginPatterns(List.of(
                "https://www.example.com",
                "https://*.example.com"
            ));
            
            List<String> patterns = properties.getAllowedOriginPatterns();
            
            assertEquals(2, patterns.size());
            assertTrue(patterns.contains("https://www.example.com"));
            assertTrue(patterns.contains("https://*.example.com"));
        }

        @Test
        @DisplayName("Should support restricted HTTP methods")
        void shouldAllowRestrictedMethods() {
            properties.setAllowedMethods(List.of("GET", "POST"));
            
            List<String> methods = properties.getAllowedMethods();
            
            assertEquals(2, methods.size());
            assertTrue(methods.contains("GET"));
            assertTrue(methods.contains("POST"));
            assertFalse(methods.contains("DELETE"));
        }

        @Test
        @DisplayName("Should support custom header whitelist")
        void shouldAllowCustomHeaders() {
            properties.setAllowedHeaders(List.of(
                "Content-Type",
                "Authorization",
                "X-Custom-Header"
            ));
            
            List<String> headers = properties.getAllowedHeaders();
            
            assertEquals(3, headers.size());
        }

        @Test
        @DisplayName("Should support disabling credentials")
        void shouldAllowDisablingCredentials() {
            properties.setAllowCredentials(false);
            
            assertFalse(properties.isAllowCredentials());
        }

        @Test
        @DisplayName("Should support custom cache max age")
        void shouldAllowCustomMaxAge() {
            properties.setMaxAge(7200L);
            
            assertEquals(7200L, properties.getMaxAge());
        }
    }

    // ========== Wildcard Detection Tests ==========

    @Nested
    @DisplayName("Wildcard Detection")
    class WildcardDetectionTests {

        @Test
        @DisplayName("Should detect wildcard configuration")
        void shouldDetectWildcard() {
            properties.setAllowedOriginPatterns(List.of("*"));
            
            assertTrue(properties.hasWildcardOrigin());
        }

        @Test
        @DisplayName("Should detect no wildcard configuration")
        void shouldDetectNoWildcard() {
            properties.setAllowedOriginPatterns(List.of(
                "https://www.example.com"
            ));
            
            assertFalse(properties.hasWildcardOrigin());
        }

        @Test
        @DisplayName("Should detect partial wildcard configuration")
        void shouldDetectPartialWildcard() {
            properties.setAllowedOriginPatterns(List.of(
                "https://www.example.com",
                "*"
            ));
            
            assertTrue(properties.hasWildcardOrigin());
        }
    }

    // ========== Exposed Headers Configuration Tests ==========

    @Nested
    @DisplayName("Exposed Headers Configuration")
    class ExposedHeadersTests {

        @Test
        @DisplayName("Should not expose additional headers by default")
        void shouldNotExposeHeadersByDefault() {
            List<String> headers = properties.getExposedHeaders();
            
            assertTrue(headers == null || headers.isEmpty());
        }

        @Test
        @DisplayName("Should support configuring exposed headers")
        void shouldAllowConfiguringExposedHeaders() {
            properties.setExposedHeaders(List.of(
                "X-Total-Count",
                "X-Page-Size"
            ));
            
            List<String> headers = properties.getExposedHeaders();
            
            assertEquals(2, headers.size());
            assertTrue(headers.contains("X-Total-Count"));
        }
    }
}
