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

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * Strategy interface for resolving tenant identity from HTTP requests.
 * 
 * <p>This interface defines the contract for tenant resolution strategies.
 * Different implementations can extract tenant information from various
 * sources such as HTTP headers, JWT tokens, subdomains, or request paths.</p>
 * 
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer (platform-tenant module)</p>
 * 
 * <h3>Design Pattern</h3>
 * <p>This follows the Strategy Pattern, allowing different tenant resolution
 * mechanisms to be plugged in based on deployment requirements:</p>
 * <ul>
 *   <li>{@code JwtTenantResolver} - Extracts tenant from JWT 'tid' claim</li>
 *   <li>{@code HeaderTenantResolver} - Extracts tenant from X-Tenant-ID header</li>
 *   <li>{@code SubdomainTenantResolver} - Extracts tenant from subdomain (future)</li>
 *   <li>{@code PathTenantResolver} - Extracts tenant from URL path (future)</li>
 * </ul>
 * 
 * <h3>Resolution Order</h3>
 * <p>Multiple resolvers are typically chained using {@link TenantResolverChain}.
 * The chain follows a priority-based resolution order, with JWT having highest
 * precedence for security reasons.</p>
 * 
 * <h3>Implementation Guidelines</h3>
 * <ol>
 *   <li>Return {@code Optional.empty()} if the resolver cannot determine tenant</li>
 *   <li>Do NOT throw exceptions for missing tenant - let the chain try next resolver</li>
 *   <li>Implement {@link #supports(HttpServletRequest)} to enable fast-fail optimization</li>
 *   <li>Keep resolution logic stateless for thread safety</li>
 * </ol>
 * 
 * <h3>Security Considerations</h3>
 * <ul>
 *   <li>JWT-based resolution should be preferred as it's cryptographically signed</li>
 *   <li>Header-based resolution should validate against authenticated tenant</li>
 *   <li>Conflict detection between sources prevents tenant spoofing attacks</li>
 * </ul>
 * 
 * @author Brix Platform Team
 * @since 3.1.0
 * @see TenantResolverChain
 * @see JwtTenantResolver
 * @see HeaderTenantResolver
 */
public interface TenantResolver {

    /**
     * Resolves the tenant ID from the HTTP request.
     * 
     * <p>Implementations should extract the tenant identifier from their
     * respective source (JWT, header, subdomain, etc.) and return it
     * wrapped in an Optional.</p>
     * 
     * @param request the HTTP servlet request to resolve tenant from
     * @return an Optional containing the resolved tenant ID, or empty if
     *         this resolver cannot determine the tenant from the request
     */
    Optional<String> resolve(HttpServletRequest request);

    /**
     * Checks if this resolver can potentially handle the given request.
     * 
     * <p>This method provides an optimization opportunity: if a resolver
     * knows it cannot resolve a tenant from a request (e.g., required
     * header is missing), it can return false to skip the full resolution.</p>
     * 
     * <p>Default implementation returns true, meaning the resolver will
     * always attempt resolution. Override for optimization.</p>
     * 
     * @param request the HTTP servlet request to check
     * @return true if this resolver might be able to resolve the tenant,
     *         false if it definitely cannot
     */
    default boolean supports(HttpServletRequest request) {
        return true;
    }

    /**
     * Returns the priority order of this resolver.
     * 
     * <p>Lower values indicate higher priority. The chain processes
     * resolvers in priority order, stopping at the first successful resolution.</p>
     * 
     * <p>Recommended priority ranges:</p>
     * <ul>
     *   <li>0-99: Cryptographic sources (JWT, signed tokens)</li>
     *   <li>100-199: Trusted sources (authenticated headers)</li>
     *   <li>200-299: Request-based sources (path, subdomain)</li>
     *   <li>300+: Fallback sources (default tenant, config-based)</li>
     * </ul>
     * 
     * @return the priority order (lower = higher priority)
     */
    default int getOrder() {
        return 100;
    }

    /**
     * Returns a human-readable name for this resolver.
     * 
     * <p>Used for logging and debugging purposes to identify which
     * resolver successfully resolved the tenant.</p>
     * 
     * @return the resolver name
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
}
