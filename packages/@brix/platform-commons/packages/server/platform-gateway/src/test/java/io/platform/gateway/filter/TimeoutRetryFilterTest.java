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
 * 超时重试过滤器单元测试
 * <p>
 * MVP 红线 M014：核心路径单元测试覆盖
 * </p>
 * <p>
 * MVP 红线要求
 * <ul>
 *   <li>显式超时配置</li>
 *   <li>有限重试（最多3次）</li>
 * </ul>
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TimeoutRetryFilter 超时重试过滤器测试")
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
        httpTimeoutProperties.setGlobalTimeoutMs(5000); // 5秒超时
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

    // ========== 正常请求测试 ==========

    @Nested
    @DisplayName("正常请求处理")
    class NormalRequestTests {

        @Test
        @DisplayName("正常请求应成功完成")
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
        @DisplayName("POST 请求应正常处理但不重试")
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

    // ========== 超时测试 ==========

    @Nested
    @DisplayName("超时处理")
    class TimeoutTests {

        @Test
        @DisplayName("请求超时应返回504 Gateway Timeout")
        void shouldReturn504OnTimeout() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/slow")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // 模拟超时：返回一个延迟超过全局超时的Mono
            when(chain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.delay(Duration.ofSeconds(10)).then());

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            // 验证返回 504
            assertEquals(HttpStatus.GATEWAY_TIMEOUT, exchange.getResponse().getStatusCode());
        }

        @Test
        @DisplayName("快速响应应在超时前完成")
        void shouldCompleteBeforeTimeout() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/fast")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // 模拟快速响应
            when(chain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.delay(Duration.ofMillis(100)).then());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectComplete()
                    .verify(Duration.ofSeconds(2));
        }
    }

    // ========== 重试测试 ==========

    @Nested
    @DisplayName("重试机制")
    class RetryTests {

        @Test
        @DisplayName("GET 请求连接失败应触发重试（重试耗尽后失败）")
        void shouldRetryOnConnectionFailure() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/flaky")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // 持续失败测试重试机制
            when(chain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.error(new ConnectException("Connection refused")));

            // 重试耗尽后应返回错误
            StepVerifier.create(filter.filter(exchange, chain))
                    .expectError(ConnectException.class)
                    .verify(Duration.ofSeconds(10));

            // 验证重试次数属性已设置
            Integer retryCount = exchange.getAttribute("retryCount");
            assertNotNull(retryCount);
            assertTrue(retryCount > 0, "重试次数应大于0");
        }

        @Test
        @DisplayName("POST 请求不应重试")
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
        @DisplayName("重试禁用时不应重试")
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
        @DisplayName("达到最大重试次数后应失败")
        void shouldFailAfterMaxRetries() {
            retryProperties.setMaxAttempts(2);
            filter = new TimeoutRetryFilter(httpTimeoutProperties, retryProperties);

            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/always-fail")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // 始终失败
            when(chain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.error(new ConnectException("Connection refused")));

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyError(ConnectException.class);
        }
    }

    // ========== 过滤器顺序测试 ==========

    @Nested
    @DisplayName("过滤器顺序")
    class FilterOrderTests {

        @Test
        @DisplayName("应在路由之前但在日志之后执行")
        void shouldHaveCorrectOrder() {
            int order = filter.getOrder();
            
            // 预期顺序：LOWEST_PRECEDENCE - 100
            assertEquals(org.springframework.core.Ordered.LOWEST_PRECEDENCE - 100, order);
        }
    }

    // ========== HTTP 方法可重试性测试 ==========

    @Nested
    @DisplayName("HTTP 方法可重试性")
    class RetryableMethodTests {

        @Test
        @DisplayName("GET 方法应可重试（持续失败时最终返回错误）")
        void getShouldBeRetryable() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/test")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(chain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.error(new ConnectException("Connection refused")));

            // GET 方法会重试，但持续失败最终返回错误
            StepVerifier.create(filter.filter(exchange, chain))
                    .expectError(ConnectException.class)
                    .verify(Duration.ofSeconds(10));
            
            // 验证确实进行了重试
            Integer retryCount = exchange.getAttribute("retryCount");
            assertNotNull(retryCount);
            assertTrue(retryCount > 0, "GET 请求应触发重试");
        }

        @Test
        @DisplayName("HEAD 方法应可重试（持续失败时最终返回错误）")
        void headShouldBeRetryable() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .head("/api/test")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(chain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.error(new ConnectException("Connection refused")));

            // HEAD 方法会重试，但持续失败最终返回错误
            StepVerifier.create(filter.filter(exchange, chain))
                    .expectError(ConnectException.class)
                    .verify(Duration.ofSeconds(10));
            
            // 验证确实进行了重试
            Integer retryCount = exchange.getAttribute("retryCount");
            assertNotNull(retryCount);
            assertTrue(retryCount > 0, "HEAD 请求应触发重试");
        }

        @Test
        @DisplayName("OPTIONS 方法应可重试（持续失败时最终返回错误）")
        void optionsShouldBeRetryable() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .options("/api/test")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(chain.filter(any(ServerWebExchange.class)))
                    .thenReturn(Mono.error(new ConnectException("Connection refused")));

            // OPTIONS 方法会重试，但持续失败最终返回错误
            StepVerifier.create(filter.filter(exchange, chain))
                    .expectError(ConnectException.class)
                    .verify(Duration.ofSeconds(10));
            
            // 验证确实进行了重试
            Integer retryCount = exchange.getAttribute("retryCount");
            assertNotNull(retryCount);
            assertTrue(retryCount > 0, "OPTIONS 请求应触发重试");
        }

        @Test
        @DisplayName("PUT 方法默认不可重试")
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
        @DisplayName("DELETE 方法默认不可重试")
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
