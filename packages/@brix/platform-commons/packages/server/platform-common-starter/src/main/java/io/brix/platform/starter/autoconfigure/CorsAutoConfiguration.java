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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import io.brix.platform.starter.config.CorsProperties;


/**
 * v2.1 CORS Auto-Configuration
 * 
 * <p>The platform is same-origin by default: without configured origins no CORS headers are
 * sent, so browsers keep other origins out. Browser consoles call the API on their own origin
 * with a BFF session cookie (root BRX-RS-SECURITY-012), so cross-origin access with credentials
 * would let any listed site act with the user's session. Credentials are therefore never
 * allowed for wildcard origins.</p>
 * 
 * <p>Configuration example for an additional, explicitly trusted origin:</p>
 * <pre>
 * brix:
 *   cors:
 *     allowed-origins:
 *       - https://partner.example.com
 *     allowed-methods:
 *       - GET
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
@EnableConfigurationProperties(CorsProperties.class)
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
    public CorsFilter corsFilter(CorsProperties properties) {
        properties.validate();
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (!properties.hasCrossOriginClients()) {
            // Same-origin only: no CORS headers, so browsers keep other origins out.
            return new CorsFilter(source);
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.getAllowedOrigins());
        config.setAllowedOriginPatterns(properties.getAllowedOriginPatterns());
        config.setAllowedMethods(properties.getAllowedMethods());
        config.setAllowedHeaders(properties.getAllowedHeaders());
        config.setExposedHeaders(properties.getExposedHeaders());
        config.setAllowCredentials(properties.isAllowCredentials());
        config.setMaxAge(properties.getMaxAge());
        source.registerCorsConfiguration(properties.getPathPattern(), config);
        return new CorsFilter(source);
    }
}
