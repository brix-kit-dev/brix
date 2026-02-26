package io.brix.platform.common.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 租户上下文过滤器
 * 
 * <p>HTTP Header 中提取租户ID 并设置到 TenantContext 中
 * 
 * <p>优先级为最高（-100），确保在业务逻辑之前执行
 * 
 * @author Brix Platform Authors Platform Team
 * @since 1.0.0
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class TenantFilter extends OncePerRequestFilter {

    /**
     * 是否必须提供租户 ID
     */
    private final boolean required;

    /**
     * 默认租户 ID（当未提供时使用
     */
    private final String defaultTenantId;

    public TenantFilter() {
        this(false, TenantContext.DEFAULT_TENANT_ID);
    }

    public TenantFilter(boolean required) {
        this(required, TenantContext.DEFAULT_TENANT_ID);
    }

    public TenantFilter(boolean required, String defaultTenantId) {
        this.required = required;
        this.defaultTenantId = defaultTenantId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // Header 中获取租户ID
            String tenantId = request.getHeader(TenantContext.TENANT_HEADER);
            String userId = request.getHeader(TenantContext.USER_HEADER);

            // 设置租户上下
            if (tenantId != null && !tenantId.isBlank()) {
                TenantContext.setTenantId(tenantId);
            } else if (required) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":\"TENANT_REQUIRED\",\"message\":\"租户 ID 是必需的\"}");;
                return;
            } else if (defaultTenantId != null) {
                TenantContext.setTenantId(defaultTenantId);
            }

            // 设置用户上下
            if (userId != null && !userId.isBlank()) {
                TenantContext.setUserId(userId);
            }

            filterChain.doFilter(request, response);
        } finally {
            // 请求结束后清理上下文
            TenantContext.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 健康检查和 Actuator 端点不需要租户上下文
        return path.startsWith("/actuator/") 
            || path.equals("/health") 
            || path.equals("/ready")
            || path.equals("/live");
    }
}
