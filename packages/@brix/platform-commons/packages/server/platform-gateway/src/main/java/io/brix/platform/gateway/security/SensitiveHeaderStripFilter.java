package io.brix.platform.gateway.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 敏感请求头剥离过滤器
 * <p>
 * 在请求转发到下游服务之前，剥离可能被客户端伪造的敏感头，
 * 防止身份伪造攻击。这些头将由后续的认授权服务重新注入
 * </p>
 * <p>
 * MVP 红线要求剥离的头
 * <ul>
 *   <li>x-user-id - 用户ID</li>
 *   <li>x-tenant-id - 绉熸埛ID</li>
 *   <li>x-role / x-roles - 角色信息</li>
 * </ul>
 * </p>
 * <p>
 * 执行优先级：在认证过滤器之后，业务过滤器之前
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@Component
public class SensitiveHeaderStripFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(SensitiveHeaderStripFilter.class);
    private static final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final SensitiveHeaderStripProperties properties;

    public SensitiveHeaderStripFilter(SensitiveHeaderStripProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String requestId = request.getId();

        // 检查是否为排除路径
        if (isExcludedPath(path)) {
            logger.debug("[shinwa] Header strip bypassed for excluded path: {} (ID: {})", 
                    path, requestId);
            return chain.filter(exchange);
        }

        HttpHeaders headers = request.getHeaders();
        Set<String> sensitiveHeaders = properties.getHeadersAsSet();
        List<String> strippedHeaders = new ArrayList<>();

        // 检查并记录哪些敏感头存在于请求
        for (String headerName : headers.keySet()) {
            if (sensitiveHeaders.contains(headerName.toLowerCase())) {
                strippedHeaders.add(headerName);
            }
        }

        // 如果没有需要剥离的头，直接放行
        if (strippedHeaders.isEmpty()) {
            return chain.filter(exchange);
        }

        // 构建新的请求，剥离敏感头
        ServerHttpRequest.Builder requestBuilder = request.mutate();
        for (String header : strippedHeaders) {
            requestBuilder.headers(httpHeaders -> httpHeaders.remove(header));
        }

        // 记录剥离日志
        if (properties.isLogStripped()) {
            logStrippedHeaders(request, strippedHeaders, requestId);
        }

        ServerHttpRequest mutatedRequest = requestBuilder.build();
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * 记录被剥离的头信
     */
    private void logStrippedHeaders(ServerHttpRequest request, List<String> strippedHeaders, String requestId) {
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("[shinwa] 馃敀 Stripped ")
                  .append(strippedHeaders.size())
                  .append(" sensitive header(s) from request: ");

        for (int i = 0; i < strippedHeaders.size(); i++) {
            String header = strippedHeaders.get(i);
            logMessage.append(header);

            if (properties.isLogStrippedValue()) {
                // 仅在开发环境记录原始值（生产环境不应该启用）
                List<String> values = request.getHeaders().get(header);
                if (values != null && !values.isEmpty()) {
                    // 对值进行脱敏处
                    String maskedValue = maskValue(values.get(0));
                    logMessage.append("=").append(maskedValue);
                }
            }

            if (i < strippedHeaders.size() - 1) {
                logMessage.append(", ");
            }
        }

        logMessage.append(" (ID: ").append(requestId).append(")");
        
        // 使用 WARN 级别，因为这可能是恶意请求的迹象
        logger.warn(logMessage.toString());
    }

    /**
     * 对值进行脱敏处
     */
    private String maskValue(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        int visibleLength = Math.min(4, value.length() / 4);
        return value.substring(0, visibleLength) + "****" + 
               value.substring(value.length() - visibleLength);
    }

    /**
     * 检查路径是否在排除列表
     */
    private boolean isExcludedPath(String path) {
        for (String pattern : properties.getExcludePaths()) {
            if (pathMatcher.match(Objects.requireNonNull(pattern), Objects.requireNonNull(path))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        // 在认证过滤器之后执行
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
