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
package io.brix.platform.auth.context;

import java.util.Optional;

/**
 * Security Context Holder
 * <p>
 * Uses ThreadLocal to store the authenticated user information for the current thread.
 * Works with SecurityContextFilter to set on request start and clear on request end.
 * </p>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
public class SecurityContextHolder {

    private static final ThreadLocal<AuthenticatedUser> CONTEXT = new ThreadLocal<>();

    /**
     * Set current user
     *
     * @param user Authenticated user
     */
    public void setCurrentUser(AuthenticatedUser user) {
        CONTEXT.set(user);
    }

    /**
     * Get current user
     *
     * @return Authenticated user, may be empty
     */
    public Optional<AuthenticatedUser> getCurrentUser() {
        return Optional.ofNullable(CONTEXT.get());
    }

    /**
     * Get current user, throws exception if not exists
     *
     * @return Authenticated user
     * @throws SecurityException User not authenticated
     */
    public AuthenticatedUser requireCurrentUser() {
        return getCurrentUser()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
    }

    /**
     * Get current user ID
     *
     * @return User ID, may be empty
     */
    public Optional<String> getCurrentUserId() {
        return getCurrentUser().map(AuthenticatedUser::getUserId);
    }

    /**
     * Get current tenant ID
     *
     * @return Tenant ID, may be empty
     */
    public Optional<String> getCurrentTenantId() {
        return getCurrentUser().map(AuthenticatedUser::getTenantId);
    }

    /**
     * Check if current user is authenticated
     *
     * @return Whether authenticated
     */
    public boolean isAuthenticated() {
        return CONTEXT.get() != null;
    }

    /**
     * Check if current user has specified permission
     *
     * @param permission Permission identifier
     * @return Whether has permission
     */
    public boolean hasPermission(String permission) {
        AuthenticatedUser user = CONTEXT.get();
        return user != null && user.hasPermission(permission);
    }

    /**
     * Check if current user has specified role
     *
     * @param role Role name
     * @return Whether has role
     */
    public boolean hasRole(String role) {
        AuthenticatedUser user = CONTEXT.get();
        return user != null && user.hasRole(role);
    }

    /**
     * Clear current context
     * <p>
     * Must be called when request ends to prevent memory leak
     * </p>
     */
    public void clear() {
        CONTEXT.remove();
    }

    /**
     * Static method: get current context (backwards compatible)
     *
     * @return Authenticated user, may be null
     */
    public static AuthenticatedUser getContext() {
        return CONTEXT.get();
    }

    /**
     * Static method: set current context (backwards compatible)
     *
     * @param user Authenticated user
     */
    public static void setContext(AuthenticatedUser user) {
        CONTEXT.set(user);
    }

    /**
     * Static method: clear context (backwards compatible)
     */
    public static void clearContext() {
        CONTEXT.remove();
    }
}
