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

import java.security.Principal;
import java.util.Set;

/**
 * Authentication Context Capability Contract
 * 
 * <p>Provides authentication and authorization information for the current request,
 * serving as the core security capability abstraction.
 * Modules use this interface to retrieve user identity and check permissions
 * without knowing authentication implementation details (JWT/OAuth/SAML).</p>
 * 
 * <h3>Naming Note (v3.2.0)</h3>
 * <p>To unify frontend/backend capability naming, {@link AuthCapability} was added as the standard name.
 * New code should use {@code AuthCapability}; this interface is retained for backward compatibility.</p>
 * 
 * <h3>Core Responsibilities</h3>
 * <ul>
 *   <li>Get current user identity (Principal)</li>
 *   <li>Permission checking (Permission)</li>
 *   <li>Role checking (Role)</li>
 *   <li>Data permission scope (DataScope)</li>
 * </ul>
 * 
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><b>Context Transparency</b>: Authentication info automatically propagated through request context</li>
 *   <li><b>Implementation Agnostic</b>: Does not expose implementation details like JWT tokens</li>
 *   <li><b>Multi-tenancy Support</b>: Supports tenant info and data permissions</li>
 * </ul>
 * 
 * <h3>Permission Model</h3>
 * <ul>
 *   <li><b>Permission</b>: Fine-grained operation permissions, e.g., "booking:create"</li>
 *   <li><b>Role</b>: Permission collections, e.g., "ADMIN", "OPERATOR"</li>
 *   <li><b>DataScope</b>: Data access boundaries, e.g., department, region</li>
 * </ul>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Inject
 * private AuthContextCapability authContext;
 * 
 * public void createReservation(ReservationCommand command) {
 *     // Get current user
 *     Principal user = authContext.getCurrentPrincipal();
 *     
 *     // Check permission
 *     if (!authContext.hasPermission("booking:create")) {
 *         throw new AccessDeniedException("No booking create permission");
 *     }
 *     
 *     // Get data permission scope
 *     Set<DataScope> scopes = authContext.getAuthorizedScopes();
 *     // Filter accessible data based on scopes...
 * }
 * }</pre>
 * 
 * <h3>Implementation Notes</h3>
 * <ul>
 *   <li>Full Product Host: JWT + local permission cache</li>
 *   <li>Embedded Host: Delegated Auth to customer system</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see AuthCapability Recommended standardized name
 * @see Principal
 * @see DataScope
 */
public interface AuthContextCapability {

    /**
     * Get current user identity
     * 
     * <p>The returned Principal contains user identification information, possibly including:</p>
     * <ul>
     *   <li>User ID</li>
     *   <li>Username</li>
     *   <li>Tenant ID</li>
     * </ul>
     * 
     * @return current user identity, null if not authenticated
     */
    Principal getCurrentPrincipal();

    /**
     * Check if user has specified permission
     * 
     * <p>Permission naming convention: {module}:{operation}, e.g., "booking:create", "user:read"</p>
     * 
     * @param permission permission identifier, cannot be empty
     * @return true if user has the permission, false otherwise
     * @throws IllegalArgumentException if permission is null or empty
     */
    boolean hasPermission(String permission);

    /**
     * Check if user has specified role
     * 
     * <p>Roles are typically uppercase, e.g., "ADMIN", "OPERATOR", "USER"</p>
     * 
     * @param role role identifier, cannot be empty
     * @return true if user has the role, false otherwise
     * @throws IllegalArgumentException if role is null or empty
     */
    boolean hasRole(String role);

    /**
     * Get authorized data scopes
     * 
     * <p>Data scopes are used for row-level data permission control. Common types:</p>
     * <ul>
     *   <li>Department scope: User can only access data from their department</li>
     *   <li>Region scope: User can only access data from specified regions</li>
     *   <li>Custom scope: Business-defined data boundaries</li>
     * </ul>
     * 
     * @return set of authorized data scopes, never returns null
     */
    Set<DataScope> getAuthorizedScopes();

    /**
     * Check if authenticated
     * 
     * @return true if current request is authenticated
     */
    default boolean isAuthenticated() {
        return getCurrentPrincipal() != null;
    }

    /**
     * Check if user has all specified permissions
     * 
     * @param permissions permission list
     * @return true if user has all permissions
     */
    default boolean hasAllPermissions(String... permissions) {
        for (String permission : permissions) {
            if (!hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if user has any of specified permissions
     * 
     * @param permissions permission list
     * @return true if user has any permission
     */
    default boolean hasAnyPermission(String... permissions) {
        for (String permission : permissions) {
            if (hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get current tenant ID
     * 
     * <p>In multi-tenant scenarios, returns the tenant identifier for the current request</p>
     * 
     * @return tenant ID, null if not in multi-tenant scenario
     */
    default String getTenantId() {
        Principal principal = getCurrentPrincipal();
        if (principal instanceof TenantAwarePrincipal) {
            return ((TenantAwarePrincipal) principal).getTenantId();
        }
        return null;
    }
}
