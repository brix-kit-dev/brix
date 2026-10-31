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

import java.security.Principal;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.brix.platform.common.tenant.TenantContext;
import io.runtime.sdk.capability.AuthCapability;
import io.runtime.sdk.capability.AuthenticatedPrincipal;
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
 *   <li>Blueprint Constraint: All tenant resolution goes through
 *       the formal capability contract, not direct TenantContext access</li>
 *   <li>Phase 1.5: Formal TenantCapabilityImpl bridging TenantContext</li>
 * </ul>
 *
 * <h3>Resolution Flow</h3>
 * <pre>
 * Runtime-verified invocation context → TenantContext (private carrier)
 *                                                   ↑
 *                                           TenantCapabilityImpl
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

    private static final String ACTOR = "actor";
    private static final String SUBJECT = "subject";

    /**
     * The Host's authentication capability (L2A), which knows the verified caller of the current request. It may be
     * null where the Host has none, as in tests or embedded scenarios. platform-tenant reads the caller only through
     * it, not through the platform-auth plugin's own types (root BRX-RS-LIFE-010; standard-link plan L3.1).
     */
    private final AuthCapability auth;

    /**
     * Constructs a TenantCapabilityImpl with the given default tenant ID and
     * optional authentication capability.
     *
     * @param defaultTenantId the fallback tenant ID when no context is available
     * @param auth            the Host's authentication capability (nullable)
     */
    public TenantCapabilityImpl(String defaultTenantId, AuthCapability auth) {
        this.defaultTenantId = defaultTenantId != null ? defaultTenantId : TenantContext.DEFAULT_TENANT_ID;
        this.auth = auth;
        log.info("TenantCapabilityImpl initialized: defaultTenantId={}, authIntegration={}",
                this.defaultTenantId, auth != null);
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
     * <p>Reads the tenant ID from {@link TenantContext} private carrier
     * populated by Runtime-verified invocation context.</p>
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
                            "No tenant context available. Ensure Runtime established a verified invocation context.");
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
     * ThreadLocal-based tenant context. This is a compatibility method for
     * Runtime-owned context setup, not a plugin authority override path.</p>
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
     * <p>The verified caller's tenant role type: {@code memberType} for an actor token,
     * {@code principalType} for a subject token, {@code null} for other tokens.</p>
     *
     * @throws UnsupportedOperationException if the Host has no {@link AuthCapability}
     */
    @Override
    public String getCurrentRole() {
        return requirePrincipal("getCurrentRole").getTenantRoleType();
    }

    @Override
    public String getCurrentRefId() {
        return requirePrincipal("getCurrentRefId").getTenantRefId();
    }

    @Override
    public String getCurrentUserId() {
        return requirePrincipal("getCurrentUserId").getUserId();
    }

    /**
     * {@inheritDoc}
     *
     * <p>An actor (B-side) caller carries an actor token.</p>
     *
     * @throws UnsupportedOperationException if the Host has no {@link AuthCapability}
     */
    @Override
    public boolean isActor() {
        return ACTOR.equalsIgnoreCase(requirePrincipal("isActor").getTokenRole());
    }

    /**
     * {@inheritDoc}
     *
     * <p>A subject (C-side) caller carries a subject token.</p>
     *
     * @throws UnsupportedOperationException if the Host has no {@link AuthCapability}
     */
    @Override
    public boolean isSubject() {
        return SUBJECT.equalsIgnoreCase(requirePrincipal("isSubject").getTokenRole());
    }

    /**
     * Returns the verified caller of the current request.
     *
     * @param methodName the calling method name for error reporting
     * @return the current caller
     * @throws UnsupportedOperationException if the Host has no {@link AuthCapability}
     * @throws SecurityException if no caller is authenticated
     */
    private AuthenticatedPrincipal requirePrincipal(String methodName) {
        if (auth == null) {
            throw new UnsupportedOperationException(
                    methodName + "() needs the Host's AuthCapability to read the verified caller.");
        }
        Principal current = auth.getCurrentPrincipal();
        if (current instanceof AuthenticatedPrincipal principal) {
            return principal;
        }
        throw new SecurityException("No authenticated user in the current request");
    }
}
