package io.brix.platform.starter.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.Collections;

/**
 * v2.1 CORS 自动配置
 * 
 * <p>提供跨域资源共享配置</p>
 * 
 * <p>配置示例</p>
 * <pre>
 * shinwa:
 *   cors:
 *     enabled: true
 *     allowed-origins:
 *       - http://localhost:3000
 *       - https://app.shinwa.com
 *     allowed-methods:
 *       - GET
 *       - POST
 *       - PUT
 *       - DELETE
 *     allow-credentials: true
 * </pre>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(CorsFilter.class)
@ConditionalOnProperty(
    prefix = "shinwa.cors",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class CorsAutoConfiguration {
    
    /**
     * CORS 过滤
     * 
     * <p>允许跨域请求，开发环境默认允许所有来</p>
     * 
     * @return CORS 过滤
     */
    @Bean
    @ConditionalOnMissingBean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // 开发环境：允许所有来
        // 生产环境应通过配置指定具体的允许来
        config.setAllowedOriginPatterns(Collections.singletonList("*"));
        
        // 允许HTTP 方法
        config.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));
        
        // 允许的请求头
        config.setAllowedHeaders(Arrays.asList(
            "Origin",
            "Content-Type",
            "Accept",
            "Authorization",
            "X-Request-ID",
            "X-Trace-ID",
            "X-Span-ID",
            "X-Tenant-ID",
            "X-User-ID"
        ));
        
        // 暴露的响应头
        config.setExposedHeaders(Arrays.asList(
            "Authorization",
            "X-Request-ID",
            "X-Trace-ID",
            "X-Total-Count",
            "X-Page-Count"
        ));
        
        // 允许携带凭证（Cookie
        config.setAllowCredentials(true);
        
        // 预检请求缓存时间（秒
        config.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}
