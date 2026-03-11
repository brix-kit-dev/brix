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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * API Key Authentication Filter Unit Tests
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unused") // setUp used by JUnit
class ApiKeyAuthFilterTest {

    @Mock
    private GatewayFilterChain chain;

    private ApiKeyAuthProperties properties;
    private ApiKeyAuthFilter filter;

    private static final String VALID_KEY = "test-api-key-12345";
    private static final String VALID_SECRET = "test-api-secret-123456789012";
    private static final String INVALID_KEY = "invalid-key";
    private static final String INVALID_SECRET = "invalid-secret";

    @BeforeEach
    void setUp() {
        properties = new ApiKeyAuthProperties();
        properties.setEnabled(true);
        properties.setHeaderName("X-API-Key");
        properties.setSecretHeaderName("X-API-Secret");
        properties.setExcludePaths(List.of("/actuator/health", "/healthz"));

        ApiKeyAuthProperties.ApiKeyEntry entry = new ApiKeyAuthProperties.ApiKeyEntry();
        entry.setName("test");
        entry.setKey(VALID_KEY);
        entry.setSecret(VALID_SECRET);
        entry.setAllowedPaths(List.of());

        properties.setKeys(List.of(entry));

        filter = new ApiKeyAuthFilter(properties);
    }

    @Test
    @DisplayName("Valid credentials should pass authentication")
    void shouldPassWithValidCredentials() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header("X-API-Key", VALID_KEY)
                .header("X-API-Secret", VALID_SECRET)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Verify authentication info is injected
        assertEquals("test", exchange.getAttribute(ApiKeyAuthFilter.AUTH_KEY_NAME_ATTR));
    }

    @Test
    @DisplayName("Missing credentials should return 401")
    void shouldReturn401WhenCredentialsMissing() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Invalid credentials should return 401")
    void shouldReturn401WithInvalidCredentials() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header("X-API-Key", INVALID_KEY)
                .header("X-API-Secret", INVALID_SECRET)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Excluded paths should skip authentication")
    void shouldSkipAuthForExcludedPaths() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/actuator/health")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Excluded paths should not set authentication info
        assertNull(exchange.getAttribute(ApiKeyAuthFilter.AUTH_KEY_NAME_ATTR));
    }

    @Test
    @DisplayName("Should skip validation when auth is disabled")
    void shouldSkipAuthWhenDisabled() {
        properties.setEnabled(false);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Response status should be default (not rejected)
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Correct Key but incorrect Secret should return 401")
    void shouldReturn401WithValidKeyButInvalidSecret() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header("X-API-Key", VALID_KEY)
                .header("X-API-Secret", INVALID_SECRET)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Path not in allowed list should return 403")
    void shouldReturn403WhenPathNotAllowed() {
        // Create a Key with path restrictions
        ApiKeyAuthProperties.ApiKeyEntry restrictedEntry = new ApiKeyAuthProperties.ApiKeyEntry();
        restrictedEntry.setName("restricted");
        restrictedEntry.setKey("restricted-key-123");
        restrictedEntry.setSecret("restricted-secret-12345678");
        restrictedEntry.setAllowedPaths(List.of("/api/allowed/**"));
        
        properties.setKeys(List.of(restrictedEntry));
        filter = new ApiKeyAuthFilter(properties);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/forbidden/resource")
                .header("X-API-Key", "restricted-key-123")
                .header("X-API-Secret", "restricted-secret-12345678")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Filter order should be highest precedence")
    void shouldHaveHighestPrecedence() {
        assertTrue(filter.getOrder() < 100);
    }
}
