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
package io.brix.platform.tenant.filter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import io.brix.platform.common.tenant.TenantContext;
import io.brix.platform.tenant.exception.TenantMismatchException;
import io.brix.platform.tenant.resolver.TenantResolver;
import io.brix.platform.tenant.resolver.TenantResolverChain;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Advanced Tenant Context Filter with resolver chain and conflict detection.
 * 
 * <p>This filter uses a {@link TenantResolverChain} to extract tenant identity
 * from multiple sources (JWT, headers, etc.) and sets the tenant context for
 * downstream processing. It includes conflict detection to prevent spoofing.</p>
 * 
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer (platform-tenant module)</p>
 * 
 * <h3>Filter Order</h3>
 * <p>This filter runs at highest precedence + 100 to ensure it executes
 * AFTER security filters (which validate JWT) but BEFORE business logic.</p>
 * 
 * <h3>Processing Flow</h3>
 * <ol>
 *   <li>Check if request should be filtered (skip health checks, actuator)</li>
 *   <li>Use resolver chain to extract tenant ID with conflict detection</li>
 *   <li>If tenant required and not found, return 400 Bad Request</li>
 *   <li>If conflict detected, return 403 Forbidden</li>
 *   <li>Set tenant context and proceed with filter chain</li>
 *   <li>Clear tenant context after request completes</li>
 * </ol>
 * 
 * <h3>Conflict Detection</h3>
 * <p>When JWT specifies tenant-A and header specifies tenant-B, this filter
 * detects the conflict and returns 403 Forbidden. This prevents tenant
 * spoofing attacks where malicious clients try to override JWT tenant.</p>
 * 
 * <h3>Response Codes</h3>
 * <ul>
 *   <li><b>400 Bad Request:</b> Tenant required but not found</li>
 *   <li><b>403 Forbidden:</b> Tenant conflict detected (spoofing attempt)</li>
 * </ul>
 * 
 * <h3>Configuration Examples</h3>
 * <pre>{@code
 * // Default configuration (uses JwtTenantResolver + HeaderTenantResolver)
 * TenantFilter filter = new TenantFilter();
 * 
 * // Require tenant on all requests
 * TenantFilter filter = new TenantFilter(true);
 * 
 * // Custom resolver chain
 * TenantResolverChain chain = new TenantResolverChain()
 *     .addResolver(new JwtTenantResolver())
 *     .addResolver(new HeaderTenantResolver());
 * TenantFilter filter = new TenantFilter(chain, true);
 * }</pre>
 * 
 * @author Brix Platform Team
 * @since 3.1.0
 * @see TenantResolverChain
 * @see TenantContext
 * @see TenantMismatchException
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class TenantFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);

    /**
     * Default paths that should skip tenant resolution.
     */
    private static final Set<String> DEFAULT_SKIP_PATHS = Set.of(
        "/actuator",
        "/health",
        "/ready",
        "/live",
        "/swagger-ui",
        "/v3/api-docs",
        "/favicon.ico"
    );

    /**
     * The resolver chain to use for tenant extraction.
     */
    private final TenantResolver resolverChain;

    /**
     * Whether tenant ID is required for all requests.
     */
    private final boolean required;

    /**
     * Default tenant ID (used when not required and not provided).
     */
    private final String defaultTenantId;

    /**
     * Paths to skip tenant resolution.
     */
    private final Set<String> skipPaths;

    /**
     * Tenant mode: "single" (default) or "multi".
     * In multi-tenant mode, defaultTenantId is ignored to prevent cross-tenant access.
     * @since 3.2.0
     */
    private final String tenantMode;

    /**
     * Creates a TenantFilter with default resolver chain (JWT + Header).
     * Tenant is not required, defaults to "default" tenant.
     */
    public TenantFilter() {
        this(TenantResolverChain.createDefault(), false, TenantContext.DEFAULT_TENANT_ID, DEFAULT_SKIP_PATHS, "single");
    }

    /**
     * Creates a TenantFilter with specified required flag.
     * 
     * @param required whether tenant ID is required
     */
    public TenantFilter(boolean required) {
        this(TenantResolverChain.createDefault(), required, TenantContext.DEFAULT_TENANT_ID, DEFAULT_SKIP_PATHS, "single");
    }

    /**
     * Creates a TenantFilter with custom resolver chain.
     * 
     * @param resolverChain the resolver chain to use
     * @param required whether tenant ID is required
     */
    public TenantFilter(TenantResolver resolverChain, boolean required) {
        this(resolverChain, required, TenantContext.DEFAULT_TENANT_ID, DEFAULT_SKIP_PATHS, "single");
    }

    /**
     * Creates a fully customized TenantFilter.
     * 
     * @param resolverChain the resolver chain to use
     * @param required whether tenant ID is required
     * @param defaultTenantId the default tenant ID when not required and not found
     * @param skipPaths paths to skip tenant resolution
     */
    public TenantFilter(
            TenantResolver resolverChain,
            boolean required,
            String defaultTenantId,
            Set<String> skipPaths) {
        this(resolverChain, required, defaultTenantId, skipPaths, "single");
    }

    /**
     * Creates a fully customized TenantFilter with tenant mode awareness.
     *
     * <p>When {@code tenantMode} is {@code "multi"}, the {@code defaultTenantId} is
     * suppressed to prevent unauthenticated requests from falling back to a default
     * tenant, which would break tenant isolation boundaries.</p>
     * 
     * @param resolverChain the resolver chain to use
     * @param required whether tenant ID is required
     * @param defaultTenantId the default tenant ID when not required and not found
     * @param skipPaths paths to skip tenant resolution
     * @param tenantMode tenant mode: "single" or "multi" (defaults to "single")
     * @since 3.2.0
     */
    public TenantFilter(
            TenantResolver resolverChain,
            boolean required,
            String defaultTenantId,
            Set<String> skipPaths,
            String tenantMode) {
        if (resolverChain == null) {
            throw new IllegalArgumentException("Resolver chain cannot be null");
        }
        this.resolverChain = resolverChain;
        this.required = required;
        this.defaultTenantId = defaultTenantId;
        this.skipPaths = skipPaths != null ? skipPaths : DEFAULT_SKIP_PATHS;
        this.tenantMode = tenantMode != null ? tenantMode : "single";
    }

    /**
     * Performs tenant resolution and context setup.
     * 
     * <p>This method:</p>
     * <ol>
     *   <li>Uses resolver chain to extract tenant (with conflict detection)</li>
     *   <li>Handles missing tenant based on required flag</li>
     *   <li>Handles conflict exceptions with 403 response</li>
     *   <li>Sets TenantContext for downstream processing</li>
     *   <li>Clears context in finally block to prevent leakage</li>
     * </ol>
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        
        try {
            // Attempt to resolve tenant using the chain
            Optional<String> tenantId = resolveTenant(request, response);
            
            // If response already committed (error sent), stop processing
            if (response.isCommitted()) {
                return;
            }

            // Handle tenant resolution result
            if (tenantId.isPresent()) {
                TenantContext.setTenantId(tenantId.get());
                log.debug("Tenant context set: {}", tenantId.get());
            } else if (required) {
                // Tenant required but not found
                sendTenantRequiredError(response);
                return;
            } else if (defaultTenantId != null) {
                // Resolve default tenant with mode awareness (P0-5 / R16.12)
                String effectiveDefault = resolveDefaultTenant();
                if (effectiveDefault != null) {
                    TenantContext.setTenantId(effectiveDefault);
                    log.debug("Using default tenant: {}", effectiveDefault);
                } else {
                    // Multi-tenant mode: no fallback allowed, return 403
                    sendTenantRequiredError(response);
                    return;
                }
            }

            // Also extract user ID from header if present
            String userId = request.getHeader(TenantContext.USER_HEADER);
            if (userId != null && !userId.isBlank()) {
                TenantContext.setUserId(userId.trim());
            }

            // Continue with the filter chain
            filterChain.doFilter(request, response);

        } finally {
            // Always clear context after request completes
            TenantContext.clear();
        }
    }

    /**
     * Resolves the effective default tenant based on the current tenant mode.
     *
     * <p>In multi-tenant mode ({@code brix.tenant.mode=multi}), defaultTenantId is
     * suppressed to prevent unauthenticated requests from falling through to a
     * default tenant, which would break tenant isolation (R16.12).</p>
     *
     * @return the effective default tenant ID, or null in multi-tenant mode
     * @since 3.2.0
     */
    private String resolveDefaultTenant() {
        if ("multi".equals(tenantMode) && defaultTenantId != null) {
            log.warn("defaultTenantId is configured but tenant mode is 'multi'. "
                    + "Ignoring default tenant to prevent cross-tenant access.");
            return null;
        }
        return defaultTenantId;
    }

    /**
     * Resolves tenant using the resolver chain, handling exceptions.
     * 
     * @param request the HTTP request
     * @param response the HTTP response (for sending errors)
     * @return Optional containing tenant ID, or empty if not found
     * @throws IOException if error response cannot be sent
     */
    private Optional<String> resolveTenant(
            HttpServletRequest request, 
            HttpServletResponse response) throws IOException {
        try {
            return resolverChain.resolve(request);
        } catch (TenantMismatchException e) {
            // Log security event
            log.warn("SECURITY: {}", e.getMessage());
            
            // Send 403 Forbidden
            sendTenantMismatchError(response, e);
            return Optional.empty();
        }
    }

    /**
     * Sends 400 Bad Request response for missing required tenant.
     */
    private void sendTenantRequiredError(HttpServletResponse response) throws IOException {
        log.warn("Tenant ID required but not provided");
        
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
            "{\"code\":\"TENANT_REQUIRED\",\"message\":\"Tenant ID is required\"}"
        );
    }

    /**
     * Sends 403 Forbidden response for tenant conflict.
     * 
     * <p>Note: The response does NOT include specific tenant IDs
     * to prevent information leakage.</p>
     */
    private void sendTenantMismatchError(
            HttpServletResponse response, 
            TenantMismatchException e) throws IOException {
        
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
            "{\"code\":\"" + e.getErrorCode() + "\",\"message\":\"" + e.getSanitizedMessage() + "\"}"
        );
    }

    /**
     * Determines if tenant resolution should be skipped for this request.
     * 
     * <p>Tenant resolution is skipped for:</p>
     * <ul>
     *   <li>Health check endpoints</li>
     *   <li>Actuator endpoints</li>
     *   <li>Swagger/OpenAPI documentation</li>
     *   <li>Static resources</li>
     * </ul>
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // Check exact matches and prefix matches
        for (String skipPath : skipPaths) {
            if (path.equals(skipPath) || path.startsWith(skipPath + "/")) {
                log.trace("Skipping tenant resolution for path: {}", path);
                return true;
            }
        }
        
        return false;
    }

    /**
     * Returns whether tenant ID is required.
     * 
     * @return true if tenant is required, false otherwise
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * Returns the default tenant ID.
     * 
     * @return the default tenant ID, or null if none
     */
    public String getDefaultTenantId() {
        return defaultTenantId;
    }

    /**
     * Returns the paths that skip tenant resolution.
     * 
     * @return set of skip paths
     */
    public Set<String> getSkipPaths() {
        return Set.copyOf(skipPaths);
    }
}
