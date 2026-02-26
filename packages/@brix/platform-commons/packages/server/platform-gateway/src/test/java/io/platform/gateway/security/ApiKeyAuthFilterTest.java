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
 * API Key 认证过滤器单元测
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
    @DisplayName("有效凭证应该通过认证")
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

        // 验证认证信息被注
        assertEquals("test", exchange.getAttribute(ApiKeyAuthFilter.AUTH_KEY_NAME_ATTR));
    }

    @Test
    @DisplayName("缺少凭证应该返回 401")
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
    @DisplayName("无效凭证应该返回 401")
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
    @DisplayName("排除路径应该跳过认证")
    void shouldSkipAuthForExcludedPaths() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/actuator/health")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // 排除路径不应该设置认证信息
        assertNull(exchange.getAttribute(ApiKeyAuthFilter.AUTH_KEY_NAME_ATTR));
    }

    @Test
    @DisplayName("禁用认证时应该跳过验证")
    void shouldSkipAuthWhenDisabled() {
        properties.setEnabled(false);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // 响应状态应该是默认的（未被拒绝
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("正确Key 但错误的 Secret 应该返回 401")
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
    @DisplayName("路径不在允许列表中应该返403")
    void shouldReturn403WhenPathNotAllowed() {
        // 创建一个有路径限制Key
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
    @DisplayName("过滤器顺序应该是最高优先级")
    void shouldHaveHighestPrecedence() {
        assertTrue(filter.getOrder() < 100);
    }
}
