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
package io.brix.platform.common.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Tenant Context Filter
 * 
 * <p>Extracts tenant ID from HTTP Header and sets it to TenantContext
 * 
 * <p>Highest priority (-100), ensures execution before business logic
 * 
 * @author Brix Platform Authors Platform Team
 * @since 1.0.0
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class TenantFilter extends OncePerRequestFilter {

    /**
     * Whether tenant ID is required
     */
    private final boolean required;

    /**
     * Default tenant ID (used when not provided)
     */
    private final String defaultTenantId;

    public TenantFilter() {
        this(false, TenantContext.DEFAULT_TENANT_ID);
    }

    public TenantFilter(boolean required) {
        this(required, TenantContext.DEFAULT_TENANT_ID);
    }

    public TenantFilter(boolean required, String defaultTenantId) {
        this.required = required;
        this.defaultTenantId = defaultTenantId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // Get tenant ID from Header
            String tenantId = request.getHeader(TenantContext.TENANT_HEADER);
            String userId = request.getHeader(TenantContext.USER_HEADER);

            // Set tenant context
            if (tenantId != null && !tenantId.isBlank()) {
                TenantContext.setTenantId(tenantId);
            } else if (required) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":\"TENANT_REQUIRED\",\"message\":\"Tenant ID is required\"}");;
                return;
            } else if (defaultTenantId != null) {
                TenantContext.setTenantId(defaultTenantId);
            }

            // Set user context
            if (userId != null && !userId.isBlank()) {
                TenantContext.setUserId(userId);
            }

            filterChain.doFilter(request, response);
        } finally {
            // Clean up context after request completes
            TenantContext.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Health check and Actuator endpoints do not require tenant context
        return path.startsWith("/actuator/") 
            || path.equals("/health") 
            || path.equals("/ready")
            || path.equals("/live");
    }
}
