/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.sdk.capability;

import java.util.Optional;

import io.runtime.sdk.annotation.Since;

/**
 * Tenant Capability Contract — Multi-tenancy support for the Runtime Shell.
 *
 * <p>Provides tenant identification, resolution, and context propagation
 * throughout the request lifecycle. This is the formal capability contract
 * that all tenant-aware operations depend on.</p>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2A: Capability Contract (runtime-sdk-api). Implementations reside
 * in Layer 2C (platform-tenant, infra-adapters).</p>
 *
 * <h3>Identity Model Alignment</h3>
 * <p>Per v3.0.9 Blueprint Section 14.1, the three-layer identity model is:</p>
 * <ol>
 *   <li><b>Identity</b> — "Who am I?" (auth_user)</li>
 *   <li><b>Membership</b> — "Which tenant do I belong to?" (TenantCapability)</li>
 *   <li><b>Profile</b> — "What is my role within this tenant?" (AuthContextCapability)</li>
 * </ol>
 *
 * <h3>Tenant Resolution Priority</h3>
 * <ol>
 *   <li>JWT {@code tenant_id} claim (highest priority, cryptographically signed)</li>
 *   <li>{@code X-Tenant-ID} HTTP header (for service-to-service calls)</li>
 *   <li>Subdomain resolution (e.g., {@code acme.brix.io} → tenant "acme")</li>
 *   <li>Default tenant fallback (configurable)</li>
 * </ol>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Inject
 * private TenantCapability tenantCapability;
 *
 * public List<Booking> listBookings() {
 *     String tenantId = tenantCapability.getCurrentTenantId();
 *     return bookingRepository.findByTenantId(tenantId);
 * }
 * }</pre>
 *
 * <h3>Security Red Line</h3>
 * <ul>
 *   <li>All data queries MUST include tenant_id filter — no cross-tenant data leakage</li>
 *   <li>Tenant ID comes from the authenticated context, NEVER from user input</li>
 *   <li>Tenant switching requires re-authentication</li>
 * </ul>
 *
 * @author Runtime SDK Team
 * @since 3.1.0
 * @see AuthContextCapability
 * @see TenantAwarePrincipal
 */
@Since("3.1.0")
public interface TenantCapability {

    /**
     * Returns the current tenant ID from the request context.
     *
     * <p>The tenant ID is resolved from the highest-priority source available
     * (JWT claim → HTTP header → subdomain → default). This method never returns
     * {@code null} — if no tenant can be resolved, it throws an exception.</p>
     *
     * @return the current tenant ID, guaranteed non-null and non-blank
     * @throws TenantResolutionException if no tenant can be resolved from any source
     */
    String getCurrentTenantId();

    /**
     * Returns the current tenant ID as an Optional, without throwing exceptions.
     *
     * <p>Useful for scenarios where tenant absence is expected (e.g., public APIs,
     * health checks, system-level operations).</p>
     *
     * @return Optional containing the tenant ID, or empty if not resolvable
     */
    Optional<String> resolveTenantId();

    /**
     * Returns the default tenant ID used when no tenant context is available.
     *
     * <p>Typically {@code "default"} for single-tenant deployments or development
     * environments. Configurable via ConfigStoreCapability.</p>
     *
     * @return the default tenant ID, never null
     */
    String getDefaultTenantId();

    /**
     * Checks whether the current request has a valid tenant context.
     *
     * @return {@code true} if a tenant ID is available in the current context
     */
    boolean hasTenantContext();

    /**
     * Explicitly sets the tenant context for the current request thread.
     *
     * <p>This method is designed for <strong>unauthenticated request paths</strong> where
     * the standard TenantFilter/TenantResolverChain cannot resolve a tenant from
     * JWT claims or HTTP headers. Typical use cases include:</p>
     * <ul>
     *   <li>OAuth2 callbacks — no JWT token is available yet</li>
     *   <li>Webhook receivers — external systems don't carry tenant headers</li>
     *   <li>Background task initialization — no HTTP request context</li>
     * </ul>
     *
     * <p>The tenant ID set by this method is picked up by the SQL tenant isolation
     * guard (TenantSqlGuardInterceptor) and any downstream tenant-aware queries.
     * Context cleanup is handled by the TenantFilter's {@code finally} block at
     * the end of the request lifecycle.</p>
     *
     * <h4>Security Note</h4>
     * <p>The tenant ID passed to this method MUST originate from a trusted source
     * (e.g., configuration, verified OAuth state parameter), never from raw user input.</p>
     *
     * @param tenantId the tenant ID to set, must not be null or blank
     * @throws IllegalArgumentException if tenantId is null or blank
     * @since 3.1.0
     */
    void setCurrentTenantId(String tenantId);

    // ==================== v1.2 Blueprint Methods (B2B2C Dual-Track Identity) ====================

    /**
     * Returns the current user's primary role within the active tenant.
     *
     * <p>Per v3.0.9 Blueprint Section 14.1, the role is determined by the token's
     * membership context (Actor: {@code memberType}) or principal context
     * (Subject: {@code principalType}). The resolved role string follows the format
     * used in JWT claims.</p>
     *
     * <p>Default implementation throws {@link UnsupportedOperationException} — the
     * platform-tenant module must override this method and delegate to the
     * authentication context (e.g., {@code SecurityContextHolder}) to extract the
     * role from the current JWT token.</p>
     *
     * @return the current role string (e.g., "OWNER", "ADMIN", "MEMBER", "CUSTOMER", "GUEST"),
     *         or {@code null} if no role information is available
     * @since 3.2.0
     */
    @Since("3.2.0")
    default String getCurrentRole() {
        throw new UnsupportedOperationException(
                "getCurrentRole() must be implemented by platform-tenant. " +
                "Ensure platform-tenant module is on the classpath.");
    }

    /**
     * Checks whether the current authenticated user is an Actor (B-side identity).
     *
     * <p>Per v3.0.9 Blueprint Phase 2 dual-track authentication, an Actor represents
     * a B-side business operator (tenant member) identified by {@code memberId} (mid claim)
     * in the JWT token. Actor and Subject identities are mutually exclusive within
     * a single token.</p>
     *
     * @return {@code true} if the current token carries an Actor (B-side) identity
     * @since 3.2.0
     */
    @Since("3.2.0")
    default boolean isActor() {
        throw new UnsupportedOperationException(
                "isActor() must be implemented by platform-tenant. " +
                "Ensure platform-tenant module is on the classpath.");
    }

    /**
     * Checks whether the current authenticated user is a Subject (C-side identity).
     *
     * <p>Per v3.0.9 Blueprint Phase 2 dual-track authentication, a Subject represents
     * a C-side end-user (customer/guest) identified by {@code principalId} (pid claim)
     * in the JWT token. Subject and Actor identities are mutually exclusive within
     * a single token.</p>
     *
     * @return {@code true} if the current token carries a Subject (C-side) identity
     * @since 3.2.0
     */
    @Since("3.2.0")
    default boolean isSubject() {
        throw new UnsupportedOperationException(
                "isSubject() must be implemented by platform-tenant. " +
                "Ensure platform-tenant module is on the classpath.");
    }

    /**
     * Exception thrown when tenant resolution fails and a tenant ID is required.
     *
     * <p>This indicates a configuration or security issue — all business requests
     * should have tenant context established by the security filter chain.</p>
     */
    class TenantResolutionException extends RuntimeException {

        /**
         * Constructs a new TenantResolutionException.
         *
         * @param message descriptive error message
         */
        public TenantResolutionException(String message) {
            super(message);
        }

        /**
         * Constructs a new TenantResolutionException with a cause.
         *
         * @param message descriptive error message
         * @param cause   the underlying cause
         */
        public TenantResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
