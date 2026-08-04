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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import io.brix.platform.auth.context.AuthenticatedUser;
import io.brix.platform.auth.context.SecurityContextHolder;
import io.brix.platform.tenant.repository.TenantMemberRepository;
import io.brix.platform.tenant.repository.TenantPrincipalRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Identity Validation Filter — verifies JWT mid/pid claims against database records.
 *
 * <p>This filter runs <b>after</b> {@code SecurityContextFilter} (order = -90 vs -100)
 * to perform a secondary validation step: checking that the member ID ({@code mid})
 * or principal ID ({@code pid}) extracted from the JWT actually exists in
 * {@code sys_tenant_member} or {@code sys_tenant_principal} respectively.</p>
 *
 * <h3>Architecture Position</h3>
 * <ul>
 *   <li><b>Layer</b>: 2C — Platform Commons (platform-tenant)</li>
 *   <li><b>Blueprint ref</b>: multi-tenant v4.0 — Actor/Subject member-principal validation</li>
 *   <li><b>Check item</b>: T-5/T-11/T-22</li>
 * </ul>
 *
 * <h3>Why a separate filter instead of modifying SecurityContextFilter?</h3>
 * <p>{@code SecurityContextFilter} lives in {@code platform-auth-servlet} (pure JWT crypto,
 * no database dependencies). Adding repository lookups there would create a reverse dependency
 * from {@code platform-auth-servlet} → {@code platform-tenant}, violating the dependency
 * direction rule. This filter keeps the concern separation clean:</p>
 * <ul>
 *   <li>{@code SecurityContextFilter} — cryptographic JWT validation (signature, expiry, issuer)</li>
 *   <li>{@code IdentityValidationFilter} — identity existence validation (database lookup)</li>
 * </ul>
 *
 * <h3>Security Flow</h3>
 * <ol>
 *   <li>SecurityContextFilter validates JWT → populates SecurityContextHolder</li>
 *   <li>This filter reads the authenticated user from SecurityContextHolder</li>
 *   <li>If {@code mid} is present → verifies record exists in {@code sys_tenant_member}</li>
 *   <li>If {@code pid} is present → verifies record exists in {@code sys_tenant_principal}</li>
 *   <li>On failure → returns 401 Unauthorized and clears security context</li>
 * </ol>
 *
 * <h3>Performance Consideration</h3>
 * <p>This filter performs a single {@code findById} query per request (at most).
 * The query hits a primary key index and should be sub-millisecond. For high-throughput
 * scenarios, consider adding a short-lived cache (e.g., Caffeine, 30s TTL).</p>
 *
 * @author Brix Architecture Team
 * @since 3.2.0
 * @see io.brix.platform.auth.servlet.SecurityContextFilter
 */
@Order(-90) // Runs after SecurityContextFilter (-100) but before business filters
public class IdentityValidationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(IdentityValidationFilter.class);

    /** HTTP status code for unauthorized requests. */
    private static final int SC_UNAUTHORIZED = 401;

    /** JSON content type for error responses. */
    private static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";

    /** Error response template — uses constant error code to prevent information leakage. */
    private static final String ERROR_RESPONSE_TEMPLATE =
            "{\"code\":\"40101\",\"message\":\"%s\"}";

    private final SecurityContextHolder securityContextHolder;
    private final TenantMemberRepository tenantMemberRepository;
    private final TenantPrincipalRepository tenantPrincipalRepository;

    /**
     * Creates a new IdentityValidationFilter.
     *
     * @param securityContextHolder    thread-local security context populated by SecurityContextFilter
     * @param tenantMemberRepository   repository for sys_tenant_member lookups
     * @param tenantPrincipalRepository repository for sys_tenant_principal lookups
     */
    public IdentityValidationFilter(SecurityContextHolder securityContextHolder,
                                    TenantMemberRepository tenantMemberRepository,
                                    TenantPrincipalRepository tenantPrincipalRepository) {
        this.securityContextHolder = securityContextHolder;
        this.tenantMemberRepository = tenantMemberRepository;
        this.tenantPrincipalRepository = tenantPrincipalRepository;
    }

    /**
     * Validates the authenticated user's mid/pid against database records.
     *
     * <p>The mid/pid mutual exclusion constraint is already enforced by JwtValidator.
     * This filter only validates the existence of the referenced record.</p>
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        AuthenticatedUser user = securityContextHolder.getCurrentUser().orElse(null);

        // No authenticated user — skip validation (anonymous endpoint or failed JWT)
        if (user == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Validate Actor track: mid → sys_tenant_member
        if (user.getMemberId() != null) {
            if (!validateMemberId(user.getMemberId(), request)) {
                writeUnauthorized(response, "Invalid member identity");
                securityContextHolder.clear();
                return;
            }
        }

        // Validate Subject track: pid → sys_tenant_principal
        if (user.getPrincipalId() != null) {
            if (!validatePrincipalId(user.getPrincipalId(), request)) {
                writeUnauthorized(response, "Invalid principal identity");
                securityContextHolder.clear();
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Validates that the member ID exists in sys_tenant_member.
     *
     * @param memberId the member ID from JWT mid claim (String representation of Long)
     * @param request  the current HTTP request (for logging context)
     * @return true if valid, false if not found or parse error
     */
    private boolean validateMemberId(String memberId, HttpServletRequest request) {
        try {
            Long id = Long.parseLong(memberId);
            boolean exists = tenantMemberRepository.existsById(id);
            if (!exists) {
                logger.warn("JWT mid={} references non-existent sys_tenant_member record, uri={}",
                        memberId, request.getRequestURI());
            }
            return exists;
        } catch (NumberFormatException e) {
            logger.warn("JWT mid={} is not a valid numeric ID, uri={}",
                    memberId, request.getRequestURI());
            return false;
        }
    }

    /**
     * Validates that the principal ID exists in sys_tenant_principal.
     *
     * @param principalId the principal ID from JWT pid claim (String representation of Long)
     * @param request     the current HTTP request (for logging context)
     * @return true if valid, false if not found or parse error
     */
    private boolean validatePrincipalId(String principalId, HttpServletRequest request) {
        try {
            Long id = Long.parseLong(principalId);
            boolean exists = tenantPrincipalRepository.existsById(id);
            if (!exists) {
                logger.warn("JWT pid={} references non-existent sys_tenant_principal record, uri={}",
                        principalId, request.getRequestURI());
            }
            return exists;
        } catch (NumberFormatException e) {
            logger.warn("JWT pid={} is not a valid numeric ID, uri={}",
                    principalId, request.getRequestURI());
            return false;
        }
    }

    /**
     * Writes a 401 Unauthorized JSON response.
     *
     * <p>Uses a generic error message to prevent identity enumeration attacks.
     * Specific details are logged at WARN level for operational monitoring.</p>
     */
    private void writeUnauthorized(HttpServletResponse response, String internalMessage)
            throws IOException {
        response.setStatus(SC_UNAUTHORIZED);
        response.setContentType(CONTENT_TYPE_JSON);
        // Return generic message to client; specific reason is in server logs
        response.getWriter().write(
                String.format(ERROR_RESPONSE_TEMPLATE, "Authentication identity validation failed"));
        response.getWriter().flush();

        logger.warn("Identity validation failed: {}", internalMessage);
    }

    /**
     * Skip validation for actuator, health, and static resource endpoints.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator")
                || uri.startsWith("/health")
                || uri.startsWith("/favicon");
    }
}
