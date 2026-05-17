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

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import io.brix.platform.auth.context.AuthenticatedUser;
import io.brix.platform.auth.context.SecurityContextHolder;
import io.brix.platform.auth.jwt.JwtProperties;
import io.brix.platform.auth.jwt.JwtValidator;
import io.brix.platform.auth.jwt.JwtValidator.JwtValidationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet-based Security Context Filter.
 *
 * <p>Intercepts every HTTP request in a Servlet (Spring MVC) environment,
 * extracts the JWT Bearer token from the {@code Authorization} header,
 * validates it via {@link JwtValidator}, and stores the resulting
 * {@link AuthenticatedUser} in a thread-local {@link SecurityContextHolder}.
 *
 * <p>The context is always cleared in a {@code finally} block to prevent
 * thread-local leaks in pooled thread environments (Tomcat, Undertow).
 *
 * <h3>Request Flow</h3>
 * <ol>
 *   <li>Extract Bearer token from the {@code Authorization} header.</li>
 *   <li>Validate via {@link JwtValidator} (RS256 public key verification).</li>
 *   <li>On success, populate {@link SecurityContextHolder} for downstream
 *       {@code @RequirePermission} / {@code @RequireRole} checks.</li>
 *   <li>On failure, log and continue — the permission aspect will reject
 *       unauthenticated calls where required.</li>
 *   <li>Clear context in {@code finally} to prevent memory leaks.</li>
 * </ol>
 *
 * <h3>Skipped Paths</h3>
 * <p>Health-check and monitoring endpoints ({@code /actuator/*}, {@code /health},
 * {@code /favicon*}) are skipped to avoid unnecessary JWT parsing overhead.
 *
 * @author Brix Platform Authors
 * @version 2.0.0 — Extracted from monolithic platform-auth (D7 fix, Solution B)
 * @since 3.1.0
 * @see JwtValidator
 * @see SecurityContextHolder
 */
@Order(-100)
public class SecurityContextFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(SecurityContextFilter.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtValidator jwtValidator;
    private final SecurityContextHolder securityContextHolder;
    private final JwtProperties properties;

    /**
     * Creates a new SecurityContextFilter.
     *
     * @param jwtValidator           the JWT validator for token verification
     * @param securityContextHolder  the thread-local security context holder
     * @param properties             JWT configuration properties
     */
    public SecurityContextFilter(JwtValidator jwtValidator,
                                 SecurityContextHolder securityContextHolder,
                                 JwtProperties properties) {
        this.jwtValidator = jwtValidator;
        this.securityContextHolder = securityContextHolder;
        this.properties = properties;
    }

    /**
     * Core filter logic: extract token, validate, populate context, and always clear on exit.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
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
                    // Do not throw — let @Anonymous or @RequirePermission decide
                }
            }

            filterChain.doFilter(request, response);

        } finally {
            // Always clear context to prevent thread-local memory leak
            securityContextHolder.clear();
        }
    }

    /**
     * Extracts the Bearer token from the Authorization header.
     *
     * @param request the incoming HTTP request
     * @return the raw JWT string, or {@code null} if absent
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }

        return null;
    }

    /**
     * Skips filtering for health-check and monitoring endpoints.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }

        String path = request.getRequestURI();
        return path.startsWith("/actuator/")
                || path.equals("/health")
                || path.startsWith("/favicon");
    }
}
