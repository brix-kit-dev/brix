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

import io.runtime.sdk.annotation.Since;

/**
 * Authentication Capability Contract (Standard Name)
 * 
 * <p>This is a standardized alias for {@link AuthContextCapability}, used to unify
 * frontend and backend capability naming. New code is recommended to use this interface name.</p>
 * 
 * <h3>Naming Convention</h3>
 * <ul>
 *   <li>Frontend TypeScript uses {@code AuthCapability}</li>
 *   <li>Backend Java originally used {@code AuthContextCapability}, now provides this alias</li>
 *   <li>Both interfaces have identical definitions, only names differ</li>
 * </ul>
 * 
 * <h3>Migration Recommendation</h3>
 * <p>New code is recommended to use {@code AuthCapability}, existing code can continue
 * using {@code AuthContextCapability}. Both are fully equivalent at runtime,
 * no forced migration required.</p>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Inject
 * private AuthCapability auth;
 * 
 * public void createReservation(ReservationCommand command) {
 *     if (!auth.hasPermission("booking:create")) {
 *         throw new AccessDeniedException("No permission to create reservation");
 *     }
 *     // ...
 * }
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.2.0
 * @see AuthContextCapability
 */
@Since("3.2.0")
public interface AuthCapability extends AuthContextCapability {

    /**
     * Check if user has specified role.
     *
     * <p>Redeclared so that the standard interface states the deprecation itself: a caller's warning and this
     * interface's documentation name {@code AuthCapability}, not the deprecated old name.</p>
     *
     * @param role role identifier, cannot be empty
     * @return true if user has the role, false otherwise
     * @throws IllegalArgumentException if role is null or empty
     * @since 3.0.0
     * @deprecated since 3.3.0, for removal in 4.0.0: authorize on a permission code with
     *     {@link #hasPermission(String)} or {@link #hasAnyPermission(String...)}, never on a role name (super-admin
     *     BRX-SA-AUTH-006, multi-tenant T-29)
     */
    @Override
    @Deprecated(since = "3.3.0", forRemoval = true)
    @SuppressWarnings("removal")
    boolean hasRole(String role);
}
