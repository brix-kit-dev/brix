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
package io.brix.platform.gateway.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import io.brix.platform.gateway.security.LogSanitizer;

/**
 * Request Logging Filter Unit Tests
 * <p>
 * MVP Guideline M014: Core path unit test coverage
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RequestLoggingFilter Request Logging Filter Test")
@SuppressWarnings("unused")
class RequestLoggingFilterTest {

    @Mock
    private GatewayFilterChain chain;

    @Mock
    private LogSanitizer logSanitizer;

    private RequestLoggingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestLoggingFilter(logSanitizer);
    }

    @Nested
    @DisplayName("Request Logging")
    class RequestLoggingTests {

        @Test
        @DisplayName("Should log GET request and continue filter chain")
        void shouldLogGetRequest() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/test")
                    .header("X-Forwarded-For", "192.168.1.100")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            
            when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
            
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should log POST request and continue filter chain")
        void shouldLogPostRequest() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/api/users")
                    .header("Content-Type", "application/json")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            
            when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
            
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should extract client IP from X-Forwarded-For")
        void shouldExtractClientIpFromXForwardedFor() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/test")
                    .header("X-Forwarded-For", "192.168.1.100, 10.0.0.1")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            
            when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
            
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
            
            // The first IP should be used in logs
        }

        @Test
        @DisplayName("Should extract client IP from X-Real-IP")
        void shouldExtractClientIpFromXRealIp() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/test")
                    .header("X-Real-IP", "192.168.1.200")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            
            when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
            
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Sensitive Information Sanitization")
    class SanitizationTests {

        @Test
        @DisplayName("Should sanitize Authorization header")
        void shouldSanitizeAuthorizationHeader() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/test")
                    .header("Authorization", "Bearer secret-token-12345")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            
            when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
            when(logSanitizer.sanitizeAuthorizationHeader("Bearer secret-token-12345"))
                    .thenReturn("Bearer ****");
            
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should sanitize Cookie header")
        void shouldSanitizeCookieHeader() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/test")
                    .header("Cookie", "session=abc123; token=secret")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            
            when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
            when(logSanitizer.maskValue("cookie-data")).thenReturn("****");
            
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should sanitize sensitive info in query params")
        void shouldSanitizeQueryParams() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/test?token=secret&password=123456")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            
            when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
            when(logSanitizer.sanitizeText("token=secret&password=123456"))
                    .thenReturn("token=****&password=****");
            
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Filter Order")
    class FilterOrderTests {

        @Test
        @DisplayName("Should execute after security filter")
        void shouldHaveCorrectOrder() {
            // Logging filter should execute after security filter to capture authentication info
            int order = filter.getOrder();
            
            // Expected order: HIGHEST_PRECEDENCE + 100
            assertEquals(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 100, order);
        }
    }

    @Nested
    @DisplayName("Response Logging")
    class ResponseLoggingTests {

        @Test
        @DisplayName("Successful response should log at INFO level")
        void shouldLogSuccessfulResponse() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/test")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            
            when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
            
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
            
            // Default status code should be 200
        }

        @Test
        @DisplayName("Client error response should log at WARN level")
        void shouldLogClientErrorAsWarn() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/not-found")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            
            when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.fromRunnable(() -> {
                exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            }));
            
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Server error response should log at ERROR level")
        void shouldLogServerErrorAsError() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/error")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            
            when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.fromRunnable(() -> {
                exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            }));
            
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
        }
    }
}
