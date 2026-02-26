package io.brix.platform.auth.filter;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.brix.platform.auth.context.AuthenticatedUser;
import io.brix.platform.auth.context.SecurityContextHolder;
import io.brix.platform.auth.jwt.JwtProperties;
import io.brix.platform.auth.jwt.JwtValidator;
import io.brix.platform.auth.jwt.JwtValidator.JwtValidationException;

/**
 * 安全上下文过滤器
 * <p>
 * 从请求头提取 JWT Token，验证后设置SecurityContextHolder
 * 请求结束后清理上下文
 * </p>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
@Order(-100)
public class SecurityContextFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(SecurityContextFilter.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtValidator jwtValidator;
    private final SecurityContextHolder securityContextHolder;
    private final JwtProperties properties;

    public SecurityContextFilter(JwtValidator jwtValidator, 
            SecurityContextHolder securityContextHolder,
            JwtProperties properties) {
        this.jwtValidator = jwtValidator;
        this.securityContextHolder = securityContextHolder;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
            HttpServletResponse response, 
            FilterChain filterChain) throws ServletException, IOException {
        
        try {
            // 提取 Token
            String token = extractToken(request);
            
            if (token != null && jwtValidator != null) {
                try {
                    AuthenticatedUser user = jwtValidator.validate(token);
                    securityContextHolder.setCurrentUser(user);
                    
                    logger.debug("User authenticated: {} (tenant: {})", 
                            user.getUserId(), user.getTenantId());
                    
                } catch (JwtValidationException e) {
                    logger.debug("Token validation failed: {} - {}", 
                            e.getReason(), e.getMessage());
                    // 不抛异常，让后续 @Anonymous @RequirePermission 决定是否需要认
                }
            }
            
            filterChain.doFilter(request, response);
            
        } finally {
            // 清理上下文，防止内存泄漏
            securityContextHolder.clear();
        }
    }

    /**
     * 从请求头提取 Token
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 如果安全功能未启用，跳过过滤
        if (!properties.isEnabled()) {
            return true;
        }
        
        // 健康检查和监控端点跳过
        String path = request.getRequestURI();
        return path.startsWith("/actuator/") || 
               path.equals("/health") ||
               path.startsWith("/favicon");
    }
}
