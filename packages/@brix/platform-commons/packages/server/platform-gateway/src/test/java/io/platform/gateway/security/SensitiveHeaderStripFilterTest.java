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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Sensitive Header Strip Filter Unit Tests
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class SensitiveHeaderStripFilterTest {

    @Mock
    private GatewayFilterChain chain;

    private SensitiveHeaderStripProperties properties;
    private SensitiveHeaderStripFilter filter;

    @BeforeEach
    void setUp() {
        properties = new SensitiveHeaderStripProperties();
        properties.setEnabled(true);
        properties.setLogStripped(true);
        properties.setLogStrippedValue(false);
        properties.init();
        
        filter = new SensitiveHeaderStripFilter(properties);
    }

    @Test
    @DisplayName("Should strip x-user-id header")
    void shouldStripXUserId() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header("x-user-id", "12345")
                .header("Content-Type", "application/json")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Verify forwarded request no longer contains x-user-id
        ServerWebExchange capturedExchange = exchangeCaptor.getValue();
        assertNull(capturedExchange.getRequest().getHeaders().getFirst("x-user-id"));
        // Content-Type should be retained
        assertEquals("application/json", capturedExchange.getRequest().getHeaders().getFirst("Content-Type"));
    }

    @Test
    @DisplayName("Should strip x-tenant-id header")
    void shouldStripXTenantId() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header("X-Tenant-Id", "tenant-123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ServerWebExchange capturedExchange = exchangeCaptor.getValue();
        assertNull(capturedExchange.getRequest().getHeaders().getFirst("X-Tenant-Id"));
    }

    @Test
    @DisplayName("Should strip x-role header")
    void shouldStripXRole() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header("x-role", "admin")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ServerWebExchange capturedExchange = exchangeCaptor.getValue();
        assertNull(capturedExchange.getRequest().getHeaders().getFirst("x-role"));
    }

    @Test
    @DisplayName("Should strip multiple sensitive headers at once")
    void shouldStripMultipleSensitiveHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header("x-user-id", "user-1")
                .header("x-tenant-id", "tenant-1")
                .header("x-role", "admin")
                .header("x-permissions", "read,write")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ServerWebExchange capturedExchange = exchangeCaptor.getValue();
        ServerHttpRequest mutatedRequest = capturedExchange.getRequest();
        
        assertNull(mutatedRequest.getHeaders().getFirst("x-user-id"));
        assertNull(mutatedRequest.getHeaders().getFirst("x-tenant-id"));
        assertNull(mutatedRequest.getHeaders().getFirst("x-role"));
        assertNull(mutatedRequest.getHeaders().getFirst("x-permissions"));
    }

    @Test
    @DisplayName("Should not modify request when no sensitive headers present")
    void shouldNotModifyRequestWithoutSensitiveHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Verify chain.filter is called with original exchange
        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("Should not strip any headers when disabled")
    void shouldNotStripWhenDisabled() {
        properties.setEnabled(false);
        properties.init();
        filter = new SensitiveHeaderStripFilter(properties);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header("x-user-id", "12345")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Verify original exchange is passed (not modified)
        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("Should skip stripping for excluded paths")
    void shouldSkipStripForExcludedPaths() {
        properties.setExcludePaths(List.of("/internal/**"));
        properties.init();
        filter = new SensitiveHeaderStripFilter(properties);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/internal/service")
                .header("x-user-id", "12345")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Verify original exchange is passed (not modified)
        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("Filter order should be after authentication filter")
    void shouldExecuteAfterAuthFilter() {
        ApiKeyAuthFilter authFilter = new ApiKeyAuthFilter(new ApiKeyAuthProperties());
        
        assertTrue(filter.getOrder() > authFilter.getOrder());
    }
}
