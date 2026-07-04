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
package io.brix.platform.tenant;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.brix.platform.auth.context.AuthenticatedUser;
import io.brix.platform.auth.context.SecurityContextHolder;
import io.brix.platform.common.tenant.TenantContext;
import io.runtime.sdk.capability.TenantCapability;

/**
 * Server-side implementation of the TenantCapability contract.
 *
 * <p>Bridges the Runtime SDK TenantCapability interface to the platform-tenant
 * infrastructure by delegating to {@link TenantContext} (ThreadLocal-based) for
 * tenant resolution within the current request lifecycle.</p>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer — Implements the Layer 2A capability contract
 * ({@link TenantCapability}) defined in runtime-sdk-api.</p>
 *
 * <h3>Architecture Compliance</h3>
 * <ul>
 *   <li>Blueprint v3.0.9 Section 14.1: Three-layer identity model —
 *       TenantCapability implements the "Membership" layer</li>
 *   <li>Blueprint Constraint: All tenant resolution goes through
 *       the formal capability contract, not direct TenantContext access</li>
 *   <li>Phase 1.5: Formal TenantCapabilityImpl bridging TenantContext</li>
 * </ul>
 *
 * <h3>Resolution Flow</h3>
 * <pre>
 * HTTP Request → TenantFilter → TenantResolverChain → TenantContext (ThreadLocal)
 *                                                          ↑
 *                                                  TenantCapabilityImpl
 *                                                  (reads from ThreadLocal)
 * </pre>
 *
 * <h3>Thread Safety</h3>
 * <p>This implementation is thread-safe because it delegates all state access to
 * {@link TenantContext}, which uses ThreadLocal storage. Each request thread gets
 * its own tenant context, established by the filter chain before any business
 * logic executes.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @Inject
 * private TenantCapability tenantCapability;
 *
 * public void processOrder(Order order) {
 *     String tenantId = tenantCapability.getCurrentTenantId();
 *     order.setTenantId(tenantId);
 *     orderRepository.save(order);
 * }
 * }</pre>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see TenantCapability
 * @see TenantContext
 */
public class TenantCapabilityImpl implements TenantCapability {

    private static final Logger log = LoggerFactory.getLogger(TenantCapabilityImpl.class);

    /**
     * Default tenant ID, configurable via {@code brix.tenant.default-id}.
     * Falls back to {@link TenantContext#DEFAULT_TENANT_ID} ("default").
     */
    private final String defaultTenantId;

    /**
     * Security context holder for accessing authenticated user information.
     * Optional dependency from platform-auth module — may be null if platform-auth
     * is not on the classpath (e.g., in test or embedded scenarios).
     */
    private final SecurityContextHolder securityContextHolder;

    /**
     * Constructs a TenantCapabilityImpl with the given default tenant ID and
     * optional security context holder.
     *
     * @param defaultTenantId        the fallback tenant ID when no context is available
     * @param securityContextHolder  security context for auth user access (nullable)
     */
    public TenantCapabilityImpl(String defaultTenantId, SecurityContextHolder securityContextHolder) {
        this.defaultTenantId = defaultTenantId != null ? defaultTenantId : TenantContext.DEFAULT_TENANT_ID;
        this.securityContextHolder = securityContextHolder;
        log.info("TenantCapabilityImpl initialized: defaultTenantId={}, authIntegration={}",
                this.defaultTenantId, securityContextHolder != null);
    }

    /**
     * Constructs a TenantCapabilityImpl with the given default tenant ID.
     *
     * @param defaultTenantId the fallback tenant ID when no context is available
     */
    public TenantCapabilityImpl(String defaultTenantId) {
        this(defaultTenantId, null);
    }

    /**
     * Constructs a TenantCapabilityImpl with the platform default tenant ID.
     */
    public TenantCapabilityImpl() {
        this(TenantContext.DEFAULT_TENANT_ID, null);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reads the tenant ID from {@link TenantContext} ThreadLocal storage.
     * The tenant context is established by the TenantFilter/TenantResolverChain
     * earlier in the request lifecycle.</p>
     *
     * @throws TenantResolutionException if no tenant context has been established
     */
    @Override
    public String getCurrentTenantId() {
        return TenantContext.getTenantId()
                .orElseThrow(() -> {
                    log.warn("Tenant resolution failed: no tenant context in current thread [{}]",
                            Thread.currentThread().getName());
                    return new TenantResolutionException(
                            "No tenant context available. Ensure the request passes through " +
                            "TenantFilter or tenant context is explicitly set for background tasks.");
                });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the tenant ID from ThreadLocal without throwing an exception.
     * Useful for conditional logic where tenant absence is expected.</p>
     */
    @Override
    public Optional<String> resolveTenantId() {
        return TenantContext.getTenantId();
    }

    /**
     * {@inheritDoc}
     *
     * @return the configured default tenant ID
     */
    @Override
    public String getDefaultTenantId() {
        return defaultTenantId;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Checks if the current thread has a tenant ID set in its ThreadLocal context.</p>
     */
    @Override
    public boolean hasTenantContext() {
        return TenantContext.getTenantId().isPresent();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link TenantContext#setTenantId(String)} to populate the
     * ThreadLocal-based tenant context. This is used by unauthenticated request paths
     * (e.g., OAuth2 callbacks) where the TenantFilter cannot resolve tenant from
     * JWT claims or HTTP headers.</p>
     *
     * @throws IllegalArgumentException if tenantId is null or blank
     */
    @Override
    public void setCurrentTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be null or blank");
        }
        log.debug("Explicitly setting tenant context: tenantId={}, thread={}",
                tenantId, Thread.currentThread().getName());
        TenantContext.setTenantId(tenantId);
    }

    // ==================== v1.2 Blueprint Methods (B2B2C Dual-Track) ====================

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link SecurityContextHolder} to extract the current role from
     * the authenticated JWT token. For Actor tokens, returns {@code memberType};
     * for Subject tokens, returns {@code principalType}.</p>
     *
     * @throws UnsupportedOperationException if platform-auth is not on the classpath
     */
    @Override
    public String getCurrentRole() {
        AuthenticatedUser user = requireAuthenticatedUser("getCurrentRole");
        if (user.isActor()) {
            return user.getMemberType();
        } else if (user.isSubject()) {
            return user.getPrincipalType();
        }
        // Fallback: return first role if available
        return user.getRoles().isEmpty() ? null : user.getRoles().get(0);
    }

    @Override
    public String getCurrentRefId() {
        AuthenticatedUser user = requireAuthenticatedUser("getCurrentRefId");
        if (user.isActor()) {
            return user.getMemberId();
        }
        if (user.isSubject()) {
            return user.getPrincipalId();
        }
        return null;
    }

    @Override
    public String getCurrentUserId() {
        return requireAuthenticatedUser("getCurrentUserId").getUserId();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link AuthenticatedUser#isActor()} via the security context.
     * An Actor (B-side) identity is identified by the presence of {@code memberId}
     * (mid claim) in the JWT token.</p>
     *
     * @throws UnsupportedOperationException if platform-auth is not on the classpath
     */
    @Override
    public boolean isActor() {
        AuthenticatedUser user = requireAuthenticatedUser("isActor");
        return user.isActor();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link AuthenticatedUser#isSubject()} via the security context.
     * A Subject (C-side) identity is identified by the presence of {@code principalId}
     * (pid claim) in the JWT token.</p>
     *
     * @throws UnsupportedOperationException if platform-auth is not on the classpath
     */
    @Override
    public boolean isSubject() {
        AuthenticatedUser user = requireAuthenticatedUser("isSubject");
        return user.isSubject();
    }

    /**
     * Retrieves the authenticated user from the security context, validating
     * that platform-auth integration is available.
     *
     * @param methodName the calling method name for error reporting
     * @return the current authenticated user
     * @throws UnsupportedOperationException if SecurityContextHolder is not available
     * @throws SecurityException if no user is authenticated
     */
    private AuthenticatedUser requireAuthenticatedUser(String methodName) {
        if (securityContextHolder == null) {
            throw new UnsupportedOperationException(
                    methodName + "() requires platform-auth module on the classpath. " +
                    "Add platform-auth dependency to enable B2B2C dual-track methods.");
        }
        return securityContextHolder.requireCurrentUser();
    }
}
