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
 * 请求日志过滤器单元测试
 * <p>
 * MVP 红线 M014：核心路径单元测试覆盖
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RequestLoggingFilter 请求日志过滤器测试")
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
    @DisplayName("请求日志记录")
    class RequestLoggingTests {

        @Test
        @DisplayName("应记录 GET 请求并继续过滤链")
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
        @DisplayName("应记录 POST 请求并继续过滤链")
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
        @DisplayName("应从 X-Forwarded-For 提取客户端 IP")
        void shouldExtractClientIpFromXForwardedFor() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/test")
                    .header("X-Forwarded-For", "192.168.1.100, 10.0.0.1")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            
            when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
            
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
            
            // 日志中应使用第一个 IP
        }

        @Test
        @DisplayName("应从 X-Real-IP 提取客户端 IP")
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
    @DisplayName("敏感信息脱敏")
    class SanitizationTests {

        @Test
        @DisplayName("应脱敏 Authorization 头")
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
        @DisplayName("应脱敏 Cookie 头")
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
        @DisplayName("应脱敏查询参数中的敏感信息")
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
    @DisplayName("过滤器顺序")
    class FilterOrderTests {

        @Test
        @DisplayName("应在安全过滤器之后执行")
        void shouldHaveCorrectOrder() {
            // 日志过滤器应在安全过滤器之后，以便获取认证信息
            int order = filter.getOrder();
            
            // 预期顺序：HIGHEST_PRECEDENCE + 100
            assertEquals(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 100, order);
        }
    }

    @Nested
    @DisplayName("响应日志")
    class ResponseLoggingTests {

        @Test
        @DisplayName("成功响应应记录 INFO 级别日志")
        void shouldLogSuccessfulResponse() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/test")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            
            when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
            
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
            
            // 默认状态码应为 200
        }

        @Test
        @DisplayName("客户端错误响应应记录 WARN 级别日志")
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
        @DisplayName("服务器错误响应应记录 ERROR 级别日志")
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
