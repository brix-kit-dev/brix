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
 * Security Auto Configuration - Standardization v1.0
 * <p>
 * Provides lightweight security capabilities including:
 * <ul>
 *   <li>JWT public key verification (does not issue Token, only verifies)</li>
 *   <li>Permission annotations (@RequirePermission, @RequireRole)</li>
 *   <li>Security context (SecurityContextHolder)</li>
 * </ul>
 * </p>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @RequirePermission("USER_READ")
 * @GetMapping("/{id}")
 * public ApiResponse<UserDTO> getUser(@PathVariable Long id) {
 *     // Permission check is automatic, throws 403 if unauthorized
 * }
 * 
 * // Get current user
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
