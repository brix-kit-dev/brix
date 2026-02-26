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
 * 敏感头剥离过滤器单元测试
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
    @DisplayName("应该剥离 x-user-id 头")
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

        // 验证转发的请求中不再包含 x-user-id
        ServerWebExchange capturedExchange = exchangeCaptor.getValue();
        assertNull(capturedExchange.getRequest().getHeaders().getFirst("x-user-id"));
        // Content-Type 应该保留
        assertEquals("application/json", capturedExchange.getRequest().getHeaders().getFirst("Content-Type"));
    }

    @Test
    @DisplayName("应该剥离 x-tenant-id 头")
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
    @DisplayName("应该剥离 x-role 头")
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
    @DisplayName("应该同时剥离多个敏感头")
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
    @DisplayName("没有敏感头时不应该修改请求")
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

        // 验证 chain.filter 被调用时传入的是原始 exchange
        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("禁用时不应该剥离任何头")
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

        // 验证传入的是原始 exchange（未修改）
        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("排除路径应该跳过剥离")
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

        // 验证传入的是原始 exchange（未修改）
        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("过滤器顺序应该在认证过滤器之后")
    void shouldExecuteAfterAuthFilter() {
        ApiKeyAuthFilter authFilter = new ApiKeyAuthFilter(new ApiKeyAuthProperties());
        
        assertTrue(filter.getOrder() > authFilter.getOrder());
    }
}
