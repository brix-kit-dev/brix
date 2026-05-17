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
package io.brix.platform.tenant.resolver;

import io.brix.platform.tenant.exception.TenantMismatchException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Composite tenant resolver that chains multiple resolvers with conflict detection.
 * 
 * <p>The resolver chain processes resolvers in priority order and provides
 * configurable conflict detection when multiple sources specify different tenants.
 * This prevents tenant spoofing attacks where a malicious client might try to
 * override the JWT tenant via request headers.</p>
 * 
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer (platform-tenant module)</p>
 * 
 * <h3>Resolution Algorithm</h3>
 * <ol>
 *   <li>Sort resolvers by priority (lowest order = highest priority)</li>
 *   <li>Iterate through resolvers until one resolves a tenant</li>
 *   <li>If conflict detection is enabled, check remaining resolvers</li>
 *   <li>If any resolver returns a different tenant, throw TenantMismatchException</li>
 *   <li>Return the resolved tenant (or empty if none found)</li>
 * </ol>
 * 
 * <h3>Conflict Detection</h3>
 * <p>When enabled (default), the chain validates that all resolvers that CAN resolve
 * a tenant return the SAME tenant ID. This prevents attacks where:</p>
 * <ul>
 *   <li>JWT specifies tenant-A (authenticated, trusted)</li>
 *   <li>Header specifies tenant-B (potentially spoofed)</li>
 * </ul>
 * 
 * <p>In this scenario, the chain throws {@link TenantMismatchException} with HTTP 403.</p>
 * 
 * <h3>Default Configuration</h3>
 * <pre>{@code
 * TenantResolverChain chain = new TenantResolverChain()
 *     .addResolver(new JwtTenantResolver())      // Priority 0
 *     .addResolver(new HeaderTenantResolver());  // Priority 100
 * }</pre>
 * 
 * <h3>Security Model</h3>
 * <ul>
 *   <li>JWT resolver has highest priority (trusted source)</li>
 *   <li>Conflict detection catches spoofing attempts</li>
 *   <li>All detected conflicts are logged for security auditing</li>
 * </ul>
 * 
 * @author Brix Platform Team
 * @since 3.1.0
 * @see TenantResolver
 * @see TenantMismatchException
 */
public class TenantResolverChain implements TenantResolver {

    private static final Logger log = LoggerFactory.getLogger(TenantResolverChain.class);

    /**
     * List of tenant resolvers in this chain.
     */
    private final List<TenantResolver> resolvers;

    /**
     * Whether to detect and reject conflicting tenant specifications.
     */
    private final boolean detectConflicts;

    /**
     * Creates a TenantResolverChain with conflict detection enabled.
     */
    public TenantResolverChain() {
        this(true);
    }

    /**
     * Creates a TenantResolverChain with configurable conflict detection.
     * 
     * @param detectConflicts whether to detect and reject conflicting tenants
     */
    public TenantResolverChain(boolean detectConflicts) {
        this.resolvers = new ArrayList<>();
        this.detectConflicts = detectConflicts;
    }

    /**
     * Creates a TenantResolverChain with the given resolvers.
     * 
     * @param resolvers the list of resolvers to use
     * @param detectConflicts whether to detect and reject conflicting tenants
     */
    public TenantResolverChain(List<TenantResolver> resolvers, boolean detectConflicts) {
        this.resolvers = new ArrayList<>(resolvers);
        this.detectConflicts = detectConflicts;
        sortResolvers();
    }

    /**
     * Adds a resolver to this chain.
     * 
     * <p>The chain is automatically re-sorted after adding.</p>
     * 
     * @param resolver the resolver to add
     * @return this chain for method chaining
     * @throws IllegalArgumentException if resolver is null
     */
    public TenantResolverChain addResolver(TenantResolver resolver) {
        if (resolver == null) {
            throw new IllegalArgumentException("Resolver cannot be null");
        }
        resolvers.add(resolver);
        sortResolvers();
        return this;
    }

    /**
     * Sorts resolvers by priority (ascending order value = higher priority).
     */
    private void sortResolvers() {
        resolvers.sort(Comparator.comparingInt(TenantResolver::getOrder));
    }

    /**
     * Resolves tenant ID using the resolver chain with optional conflict detection.
     * 
     * <p>Resolution process:</p>
     * <ol>
     *   <li>Try each resolver in priority order</li>
     *   <li>Stop at the first successful resolution (primary tenant)</li>
     *   <li>If conflict detection is enabled, verify remaining resolvers agree</li>
     *   <li>Throw TenantMismatchException if conflicting tenants detected</li>
     * </ol>
     * 
     * @param request the HTTP servlet request
     * @return Optional containing resolved tenant ID, or empty if no resolver succeeds
     * @throws TenantMismatchException if different sources specify different tenants
     */
    @Override
    public Optional<String> resolve(HttpServletRequest request) {
        if (resolvers.isEmpty()) {
            log.debug("No resolvers configured in chain");
            return Optional.empty();
        }

        String primaryTenant = null;
        String primarySource = null;

        // First pass: find the primary (highest priority) tenant
        for (TenantResolver resolver : resolvers) {
            if (!resolver.supports(request)) {
                continue;
            }

            Optional<String> result = resolver.resolve(request);
            if (result.isPresent()) {
                primaryTenant = result.get();
                primarySource = resolver.getName();
                log.debug("Primary tenant '{}' resolved by {}", primaryTenant, primarySource);
                break;
            }
        }

        // If no tenant found, return empty
        if (primaryTenant == null) {
            log.debug("No resolver in chain could determine tenant");
            return Optional.empty();
        }

        // Second pass: check for conflicts (if enabled)
        if (detectConflicts) {
            checkForConflicts(request, primaryTenant, primarySource);
        }

        return Optional.of(primaryTenant);
    }

    /**
     * Checks if any other resolver returns a different tenant ID.
     * 
     * <p>This method iterates through ALL remaining resolvers (not just those
     * with higher priority) to ensure no source specifies a conflicting tenant.</p>
     * 
     * @param request the HTTP servlet request
     * @param primaryTenant the already-resolved tenant ID
     * @param primarySource the name of the resolver that resolved primaryTenant
     * @throws TenantMismatchException if a conflict is detected
     */
    private void checkForConflicts(HttpServletRequest request, String primaryTenant, String primarySource) {
        for (TenantResolver resolver : resolvers) {
            // Skip the primary resolver and unsupported resolvers
            if (resolver.getName().equals(primarySource) || !resolver.supports(request)) {
                continue;
            }

            Optional<String> alternativeTenant = resolver.resolve(request);
            
            if (alternativeTenant.isPresent() && !alternativeTenant.get().equals(primaryTenant)) {
                String conflictingSource = resolver.getName();
                String conflictingTenant = alternativeTenant.get();
                
                log.warn(
                    "SECURITY: Tenant conflict detected! Primary: {}='{}' vs Secondary: {}='{}'",
                    primarySource, primaryTenant,
                    conflictingSource, conflictingTenant
                );
                
                throw new TenantMismatchException(
                    primaryTenant, primarySource,
                    conflictingTenant, conflictingSource
                );
            }
        }
    }

    /**
     * Returns true if any resolver supports the request.
     * 
     * @param request the HTTP servlet request
     * @return true if at least one resolver might handle this request
     */
    @Override
    public boolean supports(HttpServletRequest request) {
        return resolvers.stream().anyMatch(r -> r.supports(request));
    }

    /**
     * Returns the highest priority among all resolvers.
     * 
     * @return the minimum order value, or Integer.MAX_VALUE if no resolvers
     */
    @Override
    public int getOrder() {
        return resolvers.stream()
            .mapToInt(TenantResolver::getOrder)
            .min()
            .orElse(Integer.MAX_VALUE);
    }

    /**
     * Returns a name indicating this is a chain with resolver count.
     * 
     * @return "TenantResolverChain[n resolvers]"
     */
    @Override
    public String getName() {
        return "TenantResolverChain[" + resolvers.size() + " resolvers]";
    }

    /**
     * Returns the number of resolvers in this chain.
     * 
     * @return resolver count
     */
    public int size() {
        return resolvers.size();
    }

    /**
     * Checks if this chain has conflict detection enabled.
     * 
     * @return true if conflict detection is enabled
     */
    public boolean isDetectConflicts() {
        return detectConflicts;
    }

    /**
     * Creates a default resolver chain with JWT and Header resolvers.
     * 
     * <p>This convenience method creates a pre-configured chain suitable
     * for most multi-tenant applications:</p>
     * <ul>
     *   <li>JwtTenantResolver (priority 0) - Extracts "tid" claim</li>
     *   <li>HeaderTenantResolver (priority 100) - Extracts "X-Tenant-ID"</li>
     * </ul>
     * 
     * @return a configured TenantResolverChain
     */
    public static TenantResolverChain createDefault() {
        return new TenantResolverChain()
            .addResolver(new JwtTenantResolver())
            .addResolver(new HeaderTenantResolver());
    }
}
