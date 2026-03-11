/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
 * Web MVC Auto-Configuration
 * 
 * <p>Auto-configures Web layer beans including tenant filters, request interceptors and CORS configuration.</p>
 * 
 * <p>Configuration Conditions:</p>
 * <ul>
 *   <li>Servlet Web application</li>
 *   <li>DispatcherServlet class exists in classpath</li>
 *   <li>brix.web.enabled=true (default)</li>
 * </ul>
 * 
 * <p>Provided Features:</p>
 * <ul>
 *   <li>TenantHeaderFilter: Automatically extracts X-Tenant-Id, X-User-Id and other request headers</li>
 *   <li>TraceHeaderInterceptor: Distributed tracing header injection</li>
 *   <li>ApiKeyAuthInterceptor: API Key/Secret auto-injection</li>
 *   <li>PlatformHeadersInterceptor: Unified platform header injection</li>
 *   <li>CORS configuration: Cross-origin request support</li>
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
@ConditionalOnProperty(prefix = "brix.web", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CorsProperties.class)
public class WebMvcAutoConfiguration implements WebMvcConfigurer {
    
    private static final Logger log = LoggerFactory.getLogger(WebMvcAutoConfiguration.class);
    
    private final CorsProperties corsProperties;
    private final ServiceProperties serviceProperties;
    
    /**
     * Constructor
     * 
     * @param corsProperties    CORS configuration
     * @param serviceProperties Service configuration
     */
    public WebMvcAutoConfiguration(CorsProperties corsProperties, ServiceProperties serviceProperties) {
        this.corsProperties = corsProperties;
        this.serviceProperties = serviceProperties;
        log.info("[WebMvcAutoConfiguration] Initializing Web MVC auto-configuration");
    }
    
    /**
     * Tenant Header Filter
     * 
     * <p>Highest priority to ensure tenant information is extracted before all other processing.</p>
     * 
     * @return Filter registration bean
     */
    @Bean
    @ConditionalOnMissingBean(TenantHeaderFilter.class)
    @ConditionalOnProperty(prefix = "brix.web.tenant", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<TenantHeaderFilter> tenantHeaderFilter() {
        log.info("[WebMvcAutoConfiguration] Registering tenant header filter");
        
        FilterRegistrationBean<TenantHeaderFilter> registrationBean = 
            new FilterRegistrationBean<>();
        
        registrationBean.setFilter(new TenantHeaderFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registrationBean.setName("tenantHeaderFilter");
        
        return registrationBean;
    }
    
    /**
     * Trace Header Interceptor
     * 
     * @return Trace header interceptor
     */
    @Bean
    @ConditionalOnMissingBean
    public TraceHeaderInterceptor traceHeaderInterceptor() {
        return new TraceHeaderInterceptor();
    }
    
    /**
     * API Key Auth Interceptor
     * 
     * @return API Key auth interceptor
     */
    @Bean
    @ConditionalOnMissingBean
    public ApiKeyAuthInterceptor apiKeyAuthInterceptor() {
        return new ApiKeyAuthInterceptor(serviceProperties);
    }
    
    /**
     * Platform Unified Header Interceptor
     * 
     * @return Platform unified header interceptor
     */
    @Bean
    @ConditionalOnMissingBean
    public PlatformHeadersInterceptor platformHeadersInterceptor() {
        return new PlatformHeadersInterceptor(serviceProperties);
    }
    
    /**
     * Configure RestTemplate interceptor list
     * 
     * <p>Used for header injection in outbound HTTP requests</p>
     * 
     * @param traceInterceptor    Trace header interceptor
     * @param apiKeyInterceptor   API Key interceptor
     * @param platformInterceptor Platform header interceptor
     * @return Interceptor list
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
     * Configure CORS
     * 
     * @param registry CORS registry
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (!corsProperties.isEnabled()) {
            log.info("[WebMvcAutoConfiguration] CORS is disabled");
            return;
        }
        
        log.info("[WebMvcAutoConfiguration] Configuring CORS - allowed origins: {}", 
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
     * Configure Web MVC interceptors
     * 
     * <p>Note: These are MVC-level interceptors for Controller processing flow</p>
     * 
     * @param registry Interceptor registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Add MVC-level interceptors here if needed
        // Currently handled mainly through Filter and ClientHttpRequestInterceptor
        log.debug("[WebMvcAutoConfiguration] Configuring MVC interceptors");
    }
}
