package io.brix.platform.starter.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import io.brix.platform.starter.config.CorsProperties;
import io.brix.platform.starter.config.ServiceProperties;
import io.brix.platform.starter.header.ApiKeyAuthInterceptor;
import io.brix.platform.starter.header.PlatformHeadersInterceptor;
import io.brix.platform.starter.header.TenantHeaderFilter;
import io.brix.platform.starter.header.TraceHeaderInterceptor;

import java.util.List;

/**
 * Web MVC 自动配置
 * 
 * <p>自动配置 Web 层相Bean，包括租户过滤器、请求拦截器CORS 配置</p>
 * 
 * <p>配置条件</p>
 * <ul>
 *   <li>Servlet Web 应用</li>
 *   <li>classpath 中存在 DispatcherServlet </li>
 *   <li>shinwa.web.enabled=true（默认）</li>
 * </ul>
 * 
 * <p>提供的功能：</p>
 * <ul>
 *   <li>TenantHeaderFilter：自动提X-Tenant-Id、X-User-Id 等请求头</li>
 *   <li>TraceHeaderInterceptor：分布式链路追踪头注</li>
 *   <li>ApiKeyAuthInterceptor：API Key/Secret 自动注入</li>
 *   <li>PlatformHeadersInterceptor：统一平台头注</li>
 *   <li>CORS 配置：跨域请求支</li>
 * </ul>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 * @see TenantHeaderFilter
 * @see CorsProperties
 */
@AutoConfiguration(after = PlatformCoreAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnProperty(prefix = "shinwa.web", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CorsProperties.class)
public class WebMvcAutoConfiguration implements WebMvcConfigurer {
    
    private static final Logger log = LoggerFactory.getLogger(WebMvcAutoConfiguration.class);
    
    private final CorsProperties corsProperties;
    private final ServiceProperties serviceProperties;
    
    /**
     * 构造函数
     * 
     * @param corsProperties    CORS 配置
     * @param serviceProperties 服务配置
     */
    public WebMvcAutoConfiguration(CorsProperties corsProperties, ServiceProperties serviceProperties) {
        this.corsProperties = corsProperties;
        this.serviceProperties = serviceProperties;
        log.info("[WebMvcAutoConfiguration] 初始Web MVC 自动配置");
    }
    
    /**
     * 租户请求头过滤器
     * 
     * <p>优先级最高，确保在所有处理之前提取租户信</p>
     * 
     * @return 过滤器注Bean
     */
    @Bean
    @ConditionalOnMissingBean(TenantHeaderFilter.class)
    @ConditionalOnProperty(prefix = "shinwa.web.tenant", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<TenantHeaderFilter> tenantHeaderFilter() {
        log.info("[WebMvcAutoConfiguration] 注册租户请求头过滤器");
        
        FilterRegistrationBean<TenantHeaderFilter> registrationBean = 
            new FilterRegistrationBean<>();
        
        registrationBean.setFilter(new TenantHeaderFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registrationBean.setName("tenantHeaderFilter");
        
        return registrationBean;
    }
    
    /**
     * 链路追踪头拦截器
     * 
     * @return 链路追踪头拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public TraceHeaderInterceptor traceHeaderInterceptor() {
        return new TraceHeaderInterceptor();
    }
    
    /**
     * API Key 认证拦截
     * 
     * @return API Key 认证拦截
     */
    @Bean
    @ConditionalOnMissingBean
    public ApiKeyAuthInterceptor apiKeyAuthInterceptor() {
        return new ApiKeyAuthInterceptor(serviceProperties);
    }
    
    /**
     * 平台统一头拦截器
     * 
     * @return 平台统一头拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public PlatformHeadersInterceptor platformHeadersInterceptor() {
        return new PlatformHeadersInterceptor(serviceProperties);
    }
    
    /**
     * 配置 RestTemplate 拦截器列
     * 
     * <p>用于出站 HTTP 请求的头信息注入</p>
     * 
     * @param traceInterceptor    链路追踪拦截
     * @param apiKeyInterceptor   API Key 拦截
     * @param platformInterceptor 平台头拦截器
     * @return 拦截器列
     */
    @Bean
    @ConditionalOnClass(RestTemplate.class)
    public List<org.springframework.http.client.ClientHttpRequestInterceptor> platformClientInterceptors(
            TraceHeaderInterceptor traceInterceptor,
            ApiKeyAuthInterceptor apiKeyInterceptor,
            PlatformHeadersInterceptor platformInterceptor) {
        
        return List.of(traceInterceptor, apiKeyInterceptor, platformInterceptor);
    }
    
    /**
     * 配置 CORS
     * 
     * @param registry CORS 注册
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (!corsProperties.isEnabled()) {
            log.info("[WebMvcAutoConfiguration] CORS 宸茬");
            return;
        }
        
        log.info("[WebMvcAutoConfiguration] 配置 CORS - 允许的源: {}", 
            corsProperties.getAllowedOrigins());
        
        registry.addMapping("/**")
            .allowedOriginPatterns(corsProperties.getAllowedOrigins().toArray(new String[0]))
            .allowedMethods(corsProperties.getAllowedMethods().toArray(new String[0]))
            .allowedHeaders(corsProperties.getAllowedHeaders().toArray(new String[0]))
            .exposedHeaders(corsProperties.getExposedHeaders().toArray(new String[0]))
            .allowCredentials(corsProperties.isAllowCredentials())
            .maxAge(corsProperties.getMaxAge());
    }
    
    /**
     * 配置 Web MVC 拦截
     * 
     * <p>注意：这里配置的MVC 层面的拦截器，用Controller 处理流程</p>
     * 
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 如果需要在 MVC 层面添加拦截器，可在此配
        // 目前主要通过 Filter ClientHttpRequestInterceptor 处理
        log.debug("[WebMvcAutoConfiguration] 配置 MVC 拦截");
    }
}
