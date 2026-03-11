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
 * v2.1 CORS Auto-Configuration
 * 
 * <p>Provides Cross-Origin Resource Sharing configuration.</p>
 * 
 * <p>Configuration Example:</p>
 * <pre>
 * brix:
 *   cors:
 *     enabled: true
 *     allowed-origins:
 *       - http://localhost:3000
 *       - https://app.brix.io
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
    prefix = "brix.cors",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class CorsAutoConfiguration {
    
    /**
     * CORS Filter
     * 
     * <p>Allows cross-origin requests. Development environment allows all origins by default.</p>
     * 
     * @return CORS filter
     */
    @Bean
    @ConditionalOnMissingBean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // Development environment: allow all origins
        // Production environment should specify allowed origins via configuration
        config.setAllowedOriginPatterns(Collections.singletonList("*"));
        
        // Allowed HTTP methods
        config.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));
        
        // Allowed request headers
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
        
        // Exposed response headers
        config.setExposedHeaders(Arrays.asList(
            "Authorization",
            "X-Request-ID",
            "X-Trace-ID",
            "X-Total-Count",
            "X-Page-Count"
        ));
        
        // Allow credentials (Cookies)
        config.setAllowCredentials(true);
        
        // Preflight request cache duration (seconds)
        config.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}
