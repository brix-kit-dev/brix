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
package io.brix.platform.auth.servlet;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import io.brix.platform.auth.aspect.PermissionAspect;
import io.brix.platform.auth.context.SecurityContextHolder;
import io.brix.platform.auth.jwt.JwtProperties;
import io.brix.platform.auth.jwt.JwtValidator;
import io.runtime.sdk.capability.AuthCapability;
import io.runtime.sdk.capability.AuthContextCapability;
import io.runtime.sdk.capability.IdentityTenantCapability;

/**
 * Servlet-based Security Auto-Configuration.
 *
 * <p>Activates automatically in Servlet web applications when
 * {@code platform.security.enabled=true} (default). Registers:
 * <ul>
 *   <li>{@link SecurityContextHolder} — Thread-local authenticated user store.</li>
 *   <li>{@link JwtValidator} — RS256 public-key token validator (when JWT is enabled).</li>
 *   <li>{@link PermissionAspect} — AOP aspect for {@code @RequirePermission} /
 *       {@code @RequireRole} annotation enforcement.</li>
 *   <li>{@link SecurityContextFilter} — Servlet filter that populates the
 *       security context from the Bearer token.</li>
 * </ul>
 *
 * <h3>Architecture Context (D7 — Solution B)</h3>
 * <p>This auto-configuration is the Servlet-specific counterpart extracted from
 * the original monolithic {@code SecurityAutoConfiguration}. It only activates
 * for {@code ConditionalOnWebApplication.Type.SERVLET}, ensuring no conflict
 * with the reactive stack.
 *
 * <h3>Usage</h3>
 * <p>Simply include {@code platform-auth-servlet} on the classpath. Spring Boot
 * auto-configuration will register all beans. Configuration via:
 * <pre>
 * platform:
 *   security:
 *     enabled: true          # default
 *     jwt:
 *       enabled: true        # default
 *       public-key-path: classpath:keys/public.pem
 *       issuer: brix-auth-center
 *       audience: brix-platform-api
 * </pre>
 *
 * @author Brix Platform Authors
 * @version 2.0.0 — Extracted from monolithic platform-auth (D7 fix, Solution B)
 * @since 3.1.0
 * @see SecurityContextFilter
 * @see JwtValidator
 * @see PermissionAspect
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "platform.security", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SecurityServletAutoConfiguration {

    /**
     * Thread-local security context holder for the current authenticated user.
     *
     * @return a new SecurityContextHolder instance
     */
    @Bean
    public SecurityContextHolder securityContextHolder() {
        return new SecurityContextHolder();
    }

    /**
     * JWT validator using RS256 public-key verification.
     * Only created when JWT is explicitly enabled (default: true).
     *
     * <p>A3: wires {@link IdentityTenantCapability} (if available) to validate the
     * {@code tv} claim against the DB version on every request.</p>
     *
     * @param properties              JWT configuration (key path, issuer, audience, clock skew)
     * @param identityTenantProvider  optional A3 identity capability
     * @return a configured JwtValidator
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.security.jwt", name = "enabled", havingValue = "true", matchIfMissing = true)
    public JwtValidator jwtValidator(JwtProperties properties,
                                     ObjectProvider<IdentityTenantCapability> identityTenantProvider) {
        return new JwtValidator(properties, identityTenantProvider.getIfAvailable());
    }

    /**
     * Permission enforcement aspect for {@code @RequirePermission} / {@code @RequireRole}.
     *
     * @param securityContextHolder the context holder to check current user
     * @return a configured PermissionAspect
     */
    @Bean
    public PermissionAspect permissionAspect(SecurityContextHolder securityContextHolder) {
        return new PermissionAspect(securityContextHolder);
    }

    /**
     * Maps {@link io.brix.platform.auth.aspect.PermissionAspect.PermissionDeniedException}
     * to HTTP 401 (unauthenticated) or HTTP 403 (forbidden), replacing the generic 500 fall-through
     * from the catch-all handler in {@code GlobalExceptionHandler}.
     *
     * @return a configured AuthorizationExceptionAdvice
     */
    @Bean
    public AuthorizationExceptionAdvice authorizationExceptionAdvice() {
        return new AuthorizationExceptionAdvice();
    }

    /**
     * AuthCapability adapter bridging SecurityContextHolder to runtime-sdk-api contract.
     *
     * <p>Enables Layer 1 plugins to access the current authenticated user via
     * {@link AuthCapability} / {@link AuthContextCapability} without depending
     * on platform-auth directly.</p>
     *
     * @param securityContextHolder the thread-local context holder
     * @return a capability adapter exposing the current user's auth context
     * @since 3.2.0
     */
    @Bean
    public SimpleAuthContextCapability simpleAuthContextCapability(SecurityContextHolder securityContextHolder) {
        return new SimpleAuthContextCapability(securityContextHolder);
    }

    /**
     * Registers the Servlet security context filter on {@code /api/*} paths.
     * Filter order is {@code -100} to run before business filters.
     *
     * @param jwtValidator           JWT validator (must be present)
     * @param securityContextHolder  thread-local context holder
     * @param properties             JWT configuration
     * @return a configured FilterRegistrationBean
     */
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
