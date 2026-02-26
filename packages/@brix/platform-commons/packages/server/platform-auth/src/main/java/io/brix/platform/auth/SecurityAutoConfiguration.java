package io.brix.platform.auth;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import io.brix.platform.auth.aspect.PermissionAspect;
import io.brix.platform.auth.context.SecurityContextHolder;
import io.brix.platform.auth.filter.SecurityContextFilter;
import io.brix.platform.auth.jwt.JwtProperties;
import io.brix.platform.auth.jwt.JwtValidator;

/**
 * 安全自动配置 - 标准v1.0
 * <p>
 * 提供轻量级安全能力，包括
 * <ul>
 *   <li>JWT 公钥验证（不签发 Token，仅验证</li>
 *   <li>权限注解 (@RequirePermission, @RequireRole)</li>
 *   <li>安全上下(SecurityContextHolder)</li>
 * </ul>
 * </p>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * @RequirePermission("USER_READ")
 * @GetMapping("/{id}")
 * public ApiResponse<UserDTO> getUser(@PathVariable Long id) {
 *     // 自动检查权限，无权限抛403
 * }
 * 
 * // 获取当前用户
 * AuthenticatedUser user = SecurityContextHolder.getUser();
 * }</pre>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0 (Standardization v1.0)
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "platform.security", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SecurityAutoConfiguration {

    @Bean
    public SecurityContextHolder securityContextHolder() {
        return new SecurityContextHolder();
    }

    @Bean
    @ConditionalOnProperty(prefix = "platform.security.jwt", name = "enabled", havingValue = "true", matchIfMissing = true)
    public JwtValidator jwtValidator(JwtProperties properties) {
        return new JwtValidator(properties);
    }

    @Bean
    public PermissionAspect permissionAspect(SecurityContextHolder securityContextHolder) {
        return new PermissionAspect(securityContextHolder);
    }

    @Bean
    @ConditionalOnBean(JwtValidator.class)
    public FilterRegistrationBean<SecurityContextFilter> securityContextFilter(
            JwtValidator jwtValidator,
            SecurityContextHolder securityContextHolder,
            JwtProperties properties) {
        FilterRegistrationBean<SecurityContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SecurityContextFilter(jwtValidator, securityContextHolder, properties));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(-100);
        registration.setName("securityContextFilter");
        return registration;
    }
}
