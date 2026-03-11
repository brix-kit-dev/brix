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
package io.brix.platform.auth.filter;

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
 * Security Context Filter
 * <p>
 * Extracts JWT Token from request header, validates and sets to SecurityContextHolder.
 * Clears context after request ends.
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
            // Extract Token
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
                    // Do not throw exception, let @Anonymous or @RequirePermission decide if authentication is needed
                }
            }
            
            filterChain.doFilter(request, response);
            
        } finally {
            // Clear context to prevent memory leak
            securityContextHolder.clear();
        }
    }

    /**
     * Extract Token from request header
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
        // Skip filtering if security feature is not enabled
        if (!properties.isEnabled()) {
            return true;
        }
        
        // Skip health check and monitoring endpoints
        String path = request.getRequestURI();
        return path.startsWith("/actuator/") || 
               path.equals("/health") ||
               path.startsWith("/favicon");
    }
}
