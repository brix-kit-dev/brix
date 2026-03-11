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

import java.net.ConnectException;
import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import io.brix.platform.gateway.config.resilience.HttpTimeoutProperties;
import io.brix.platform.gateway.config.resilience.RetryProperties;

/**
 * Timeout Retry Filter Unit Tests
 * <p>
 * MVP Guideline M014: Core path unit test coverage
 * </p>
 * <p>
 * MVP Guideline Requirements:
 * <ul>
 *   <li>Explicit timeout configuration</li>
 *   <li>Limited retries (max 3 times)</li>
 * </ul>
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TimeoutRetryFilter Timeout Retry Filter Test")
@SuppressWarnings("unused") // JUnit nested classes, setUp used by JUnit
class TimeoutRetryFilterTest {

    @Mock
    private GatewayFilterChain chain;

    private HttpTimeoutProperties httpTimeoutProperties;
    private RetryProperties retryProperties;
    private TimeoutRetryFilter filter;

    @BeforeEach
    void setUp() {
        httpTimeoutProperties = new HttpTimeoutProperties();
        httpTimeoutProperties.setGlobalTimeoutMs(5000); // 5 second timeout
        httpTimeoutProperties.setConnectTimeoutMs(3000);
        httpTimeoutProperties.setResponseTimeoutMs(10000);

        retryProperties = new RetryProperties();
        retryProperties.setEnabled(true);
        retryProperties.setMaxAttempts(3);
        retryProperties.setInitialBackoffMs(100);
        retryProperties.setMaxBackoffMs(1000);
        retryProperties.setMultiplier(2.0);
        retryProperties.setJitterEnabled(true);
        retryProperties.setJitterFactor(0.5);
        retryProperties.setRetryOnConnectionFailure(true);
        retryProperties.setRetryOnTimeout(true);
        retryProperties.setRetryableMethods(Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS));

        filter = new TimeoutRetryFilter(httpTimeoutProperties, retryProperties);
    }

    // ========== Normal Request Tests ==========

    @Nested
    @DisplayName("Normal Request Handling")
    class NormalRequestTests {

        @Test
        @DisplayName("Normal request should complete successfully")
        void shouldCompleteNormalRequest() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/test")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
        }

        @Test
        @DisplayName("POST request should be processed normally without retry")
        void shouldProcessPostRequestWithoutRetry() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/api/users")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
        }
    }

    // ========== Timeout Tests ==========

    @Nested
    @DisplayName("Timeout Handling")
    class TimeoutTests {

        @Test
        @DisplayName("Request timeout should return 504 Gateway Timeout")
        void shouldReturn504OnTimeout() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/slow")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // Simulate timeout: return a Mono with delay exceeding global timeout
                    when(chain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.delay(Duration.ofSeconds(10)).then());

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            // Verify return 504
            assertEquals(HttpStatus.GATEWAY_TIMEOUT, exchange.getResponse().getStatusCode());
        }

        @Test
        @DisplayName("Fast response should complete before timeout")
        void shouldCompleteBeforeTimeout() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/fast")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // Simulate fast response
            when(chain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.delay(Duration.ofMillis(100)).then());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify(Duration.ofSeconds(2));
        }
    }

    // ========== Retry Tests ==========

    @Nested
    @DisplayName("Retry Mechanism")
    class RetryTests {

        @Test
        @DisplayName("GET request connection failure should trigger retry (fails after retries exhausted)")
        void shouldRetryOnConnectionFailure() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/flaky")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // Continuously fail to test retry mechanism
            when(chain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.error(new ConnectException("Connection refused")));

            // Should return error after retries exhausted
            StepVerifier.create(filter.filter(exchange, chain))
                    .expectError(ConnectException.class)
                    .verify(Duration.ofSeconds(10));

            // Verify retry count property is set
            Integer retryCount = exchange.getAttribute("retryCount");
            assertNotNull(retryCount);
            assertTrue(retryCount > 0, "Retry count should be greater than 0");
        }

        @Test
        @DisplayName("POST request should not be retried")
        void shouldNotRetryPostRequest() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/api/create")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(chain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.error(new ConnectException("Connection refused")));

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyError(ConnectException.class);
        }

        @Test
        @DisplayName("Should not retry when retry is disabled")
        void shouldNotRetryWhenDisabled() {
            retryProperties.setEnabled(false);
            filter = new TimeoutRetryFilter(httpTimeoutProperties, retryProperties);

            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/flaky")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(chain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.error(new ConnectException("Connection refused")));

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyError(ConnectException.class);
        }

        @Test
        @DisplayName("Should fail after max retries reached")
        void shouldFailAfterMaxRetries() {
            retryProperties.setMaxAttempts(2);
            filter = new TimeoutRetryFilter(httpTimeoutProperties, retryProperties);

            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/always-fail")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // Always fail
            when(chain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.error(new ConnectException("Connection refused")));

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyError(ConnectException.class);
        }
    }

    // ========== Filter Order Tests ==========

    @Nested
    @DisplayName("Filter Order")
    class FilterOrderTests {

        @Test
        @DisplayName("Should execute before routing but after logging")
        void shouldHaveCorrectOrder() {
            int order = filter.getOrder();
            
            // Expected order: LOWEST_PRECEDENCE - 100
            assertEquals(org.springframework.core.Ordered.LOWEST_PRECEDENCE - 100, order);
        }
    }

    // ========== HTTP Method Retryability Tests ==========

    @Nested
    @DisplayName("HTTP Method Retryability")
    class RetryableMethodTests {

        @Test
        @DisplayName("GET method should be retryable (eventually returns error on continuous failure)")
        void getShouldBeRetryable() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/test")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(chain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.error(new ConnectException("Connection refused")));

            // GET method will retry, but eventually returns error on continuous failure
            StepVerifier.create(filter.filter(exchange, chain))
                    .expectError(ConnectException.class)
                    .verify(Duration.ofSeconds(10));
            
            // Verify retry was actually performed
            Integer retryCount = exchange.getAttribute("retryCount");
            assertNotNull(retryCount);
            assertTrue(retryCount > 0, "GET request should trigger retry");
        }

        @Test
        @DisplayName("HEAD method should be retryable (eventually returns error on continuous failure)")
        void headShouldBeRetryable() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .head("/api/test")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(chain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.error(new ConnectException("Connection refused")));

            // HEAD method will retry, but eventually returns error on continuous failure
            StepVerifier.create(filter.filter(exchange, chain))
                    .expectError(ConnectException.class)
                    .verify(Duration.ofSeconds(10));
            
            // Verify retry was actually performed
            Integer retryCount = exchange.getAttribute("retryCount");
            assertNotNull(retryCount);
            assertTrue(retryCount > 0, "HEAD request should trigger retry");
        }

        @Test
        @DisplayName("OPTIONS method should be retryable (eventually returns error on continuous failure)")
        void optionsShouldBeRetryable() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .options("/api/test")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(chain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.error(new ConnectException("Connection refused")));

            // OPTIONS method will retry, but eventually returns error on continuous failure
            StepVerifier.create(filter.filter(exchange, chain))
                    .expectError(ConnectException.class)
                    .verify(Duration.ofSeconds(10));
            
            // Verify retry was actually performed
            Integer retryCount = exchange.getAttribute("retryCount");
            assertNotNull(retryCount);
            assertTrue(retryCount > 0, "OPTIONS request should trigger retry");
        }

        @Test
        @DisplayName("PUT method should not be retryable by default")
        void putShouldNotBeRetryableByDefault() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .put("/api/test")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(chain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.error(new ConnectException("Connection refused")));

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyError(ConnectException.class);
        }

        @Test
        @DisplayName("DELETE method should not be retryable by default")
        void deleteShouldNotBeRetryableByDefault() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .delete("/api/test")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(chain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.error(new ConnectException("Connection refused")));

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyError(ConnectException.class);
        }
    }
}
