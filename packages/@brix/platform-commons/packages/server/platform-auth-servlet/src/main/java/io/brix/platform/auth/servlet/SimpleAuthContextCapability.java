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
package io.brix.platform.auth.servlet;

import java.security.Principal;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import io.brix.platform.auth.context.AuthenticatedUser;
import io.brix.platform.auth.context.SecurityContextHolder;
import io.runtime.sdk.capability.AuthCapability;
import io.runtime.sdk.capability.DataScope;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

/**
 * Simple AuthContextCapability adapter that bridges
 * {@link SecurityContextHolder} to {@link AuthCapability}.
 *
 * <p>This adapter implements the {@link AuthCapability} contract (Layer 2A)
 * by delegating to the thread-local {@link SecurityContextHolder} populated
 * by {@link SecurityContextFilter}. It enables all Layer 1 plugins to
 * access the current authenticated user's context via the standard
 * capability contract without depending on platform-auth directly.</p>
 *
 * <h3>Architecture — v3.0.9 Blueprint Alignment</h3>
 * <p>Mentioned as {@code SimpleAuthContextAdapter} in the blueprint.
 * Resides in platform-auth-servlet (Layer 2C) and bridges:</p>
 * <pre>
 *   SecurityContextFilter → SecurityContextHolder → SimpleAuthContextCapability → AuthCapability
 * </pre>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see AuthCapability
 * @see SecurityContextHolder
 */
@Capability(
    type = AuthCapability.class,
    name = "simple-auth-context",
    description = "Servlet-based AuthCapability bridging SecurityContextFilter → SecurityContextHolder. "
        + "Production-grade implementation backed by JWT validation in platform-auth-servlet (Layer 2C). "
        + "Also satisfies the legacy AuthContextCapability contract via super-interface registration.",
    level = CapabilityLevel.CORE,
    aliases = {"simpleAuth", "servletAuth", "AuthContextCapability"}
)
public class SimpleAuthContextCapability implements AuthCapability {

    private final SecurityContextHolder securityContextHolder;

    /**
     * Creates the adapter with the given security context holder.
     *
     * @param securityContextHolder the thread-local security context holder
     */
    public SimpleAuthContextCapability(SecurityContextHolder securityContextHolder) {
        this.securityContextHolder = securityContextHolder;
    }

    @Override
    public Principal getCurrentPrincipal() {
        return securityContextHolder.getCurrentUser()
                .map(SimpleAuthPrincipal::new)
                .orElse(null);
    }

    @Override
    public boolean hasPermission(String permission) {
        if (permission == null || permission.isEmpty()) {
            throw new IllegalArgumentException("permission must not be null or empty");
        }
        return securityContextHolder.getCurrentUser()
                .map(u -> u.getPermissions() != null && u.getPermissions().contains(permission))
                .orElse(false);
    }

    @Override
    public boolean hasRole(String role) {
        if (role == null || role.isEmpty()) {
            throw new IllegalArgumentException("role must not be null or empty");
        }
        return securityContextHolder.getCurrentUser()
                .map(u -> u.getRoles() != null && u.getRoles().contains(role))
                .orElse(false);
    }

    @Override
    public Set<DataScope> getAuthorizedScopes() {
        return Collections.emptySet();
    }

    @Override
    public String getTenantId() {
        return securityContextHolder.getCurrentUser()
                .map(AuthenticatedUser::getTenantId)
                .orElse(null);
    }

    /**
     * Returns the raw {@link AuthenticatedUser} from the security context.
     *
     * <p>Provides access to Phase 2 dual-track fields (tokenType, tokenRole,
     * memberId, principalId, etc.) that are not part of the standard
     * {@link AuthCapability} contract. Use sparingly — prefer the capability
     * contract methods for cross-module code.</p>
     *
     * @return the authenticated user, or empty if not authenticated
     * @since 3.2.0
     */
    public Optional<AuthenticatedUser> getAuthenticatedUser() {
        return securityContextHolder.getCurrentUser();
    }

    // ==================== Inner Classes ====================

    /**
     * Lightweight Principal wrapper around AuthenticatedUser.
     */
    private static final class SimpleAuthPrincipal implements Principal {

        private final AuthenticatedUser user;

        private SimpleAuthPrincipal(AuthenticatedUser user) {
            this.user = user;
        }

        @Override
        public String getName() {
            return user.getUserId();
        }

        @Override
        public String toString() {
            return "SimpleAuthPrincipal{userId=" + user.getUserId()
                    + ", tenantId=" + user.getTenantId() + "}";
        }
    }
}
