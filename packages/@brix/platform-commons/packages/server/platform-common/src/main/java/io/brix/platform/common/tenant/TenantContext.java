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
package io.brix.platform.common.tenant;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Tenant Context (Unified Version).
 * 
 * <p>Core component for multi-tenancy support, uses ThreadLocal to store current request's tenant information.</p>
 * 
 * <h3>Design Notes</h3>
 * <ul>
 *   <li>Tenant ID is extracted and set from HTTP Header via TenantFilter</li>
 *   <li>All database operations automatically apply tenant filter conditions</li>
 *   <li>Must call clear() method at request end to clean up context</li>
 * </ul>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * // Get current tenant ID
 * String tenantId = TenantContext.getTenantId()
 *     .orElseThrow(() -> new TenantNotFoundException("Tenant info missing"));
 * 
 * // Set tenant ID for new entity
 * entity.setTenantId(TenantContext.requireTenantId());
 * }</pre>
 * 
 * <h3>Important Notes</h3>
 * <ul>
 *   <li>Async threads require manual tenant ID propagation</li>
 *   <li>Scheduled tasks need explicit tenant specification or use system tenant</li>
 *   <li>Cross-service calls need to pass tenant ID in Header</li>
 * </ul>
 * 
 * @author Brix Platform Team
 * @since 1.0.0
 */
public final class TenantContext {

    /**
     * Default Tenant ID
     * Used for scenarios where no tenant is specified (e.g., system initialization, scheduled tasks)
     */
    public static final String DEFAULT_TENANT_ID = "default";

    /**
     * System Tenant ID
     * Used for platform-level operations, not subject to tenant isolation constraints
     */
    public static final String SYSTEM_TENANT_ID = "system";

    /**
     * Tenant ID HTTP Header name
     */
    public static final String TENANT_HEADER = "X-Tenant-ID";

    /**
     * User ID HTTP Header name
     */
    public static final String USER_HEADER = "X-User-ID";

    /**
     * ThreadLocal storage for Tenant ID
     */
    private static final ThreadLocal<String> TENANT_ID_HOLDER = new ThreadLocal<>();

    /**
     * ThreadLocal storage for User ID
     */
    private static final ThreadLocal<String> USER_ID_HOLDER = new ThreadLocal<>();

    /**
     * ThreadLocal storage for tenant additional info (optional)
     */
    private static final ThreadLocal<TenantInfo> TENANT_INFO_HOLDER = new ThreadLocal<>();

    /**
     * Private constructor to prevent instantiation
     */
    private TenantContext() {
        throw new UnsupportedOperationException("TenantContext is a utility class and cannot be instantiated");
    }

    // =====================================================
    // Tenant ID Operations
    // =====================================================

    /**
     * Sets the current tenant ID.
     * 
     * @param tenantId tenant ID, cannot be null or blank
     * @throws IllegalArgumentException if tenantId is null or blank
     */
    public static void setTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant ID cannot be null or blank");
        }
        TENANT_ID_HOLDER.set(tenantId);
    }

    /**
     * Gets the current tenant ID.
     * 
     * @return current tenant ID, or {@link Optional#empty()} if not set
     */
    public static Optional<String> getTenantId() {
        return Optional.ofNullable(TENANT_ID_HOLDER.get());
    }

    /**
     * Gets the current tenant ID (required).
     * 
     * @return current tenant ID
     * @throws IllegalStateException if tenant ID is not set
     */
    public static String requireTenantId() {
        return getTenantId()
            .orElseThrow(() -> new IllegalStateException("Tenant context not initialized, ensure request passes through TenantFilter"));
    }

    /**
     * Gets the current tenant ID, returns default if not set.
     * 
     * @return current tenant ID or default tenant ID
     */
    public static String getTenantIdOrDefault() {
        return getTenantId().orElse(DEFAULT_TENANT_ID);
    }

    /**
     * Checks if tenant context is present.
     * 
     * @return true if tenant ID is set
     */
    public static boolean hasTenant() {
        return TENANT_ID_HOLDER.get() != null;
    }

    /**
     * Checks if current tenant is system tenant.
     * 
     * @return true if current tenant is system tenant
     */
    public static boolean isSystemTenant() {
        return SYSTEM_TENANT_ID.equals(TENANT_ID_HOLDER.get());
    }

    /**
     * Gets the current tenant ID (convenience method).
     * 
     * @return current tenant ID
     * @throws IllegalStateException if tenant ID is not set
     */
    public static String getCurrentTenantId() {
        return requireTenantId();
    }

    // =====================================================
    // User ID Operations
    // =====================================================

    /**
     * Sets the current user ID.
     * 
     * @param userId user ID, cannot be null or blank
     * @throws IllegalArgumentException if userId is null or blank
     */
    public static void setUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID cannot be null or blank");
        }
        USER_ID_HOLDER.set(userId);
    }

    /**
     * Gets the current user ID.
     * 
     * @return current user ID, or {@link Optional#empty()} if not set
     */
    public static Optional<String> getUserId() {
        return Optional.ofNullable(USER_ID_HOLDER.get());
    }

    /**
     * Gets the current user ID (required).
     * 
     * @return current user ID
     * @throws IllegalStateException if user ID is not set
     */
    public static String requireUserId() {
        return getUserId()
            .orElseThrow(() -> new IllegalStateException("User context not initialized, ensure request passes through auth filter"));
    }

    /**
     * Gets the current user ID (convenience method).
     * 
     * @return current user ID
     * @throws IllegalStateException if user ID is not set
     */
    public static String getCurrentUserId() {
        return requireUserId();
    }

    /**
     * Checks if user context is present.
     * 
     * @return true if user ID is set
     */
    public static boolean hasUser() {
        return USER_ID_HOLDER.get() != null;
    }

    // =====================================================
    // Tenant Info Operations
    // =====================================================

    /**
     * Sets tenant additional information.
     * 
     * @param info tenant additional information
     */
    public static void setTenantInfo(TenantInfo info) {
        TENANT_INFO_HOLDER.set(info);
    }

    /**
     * Gets tenant additional information.
     * 
     * @return tenant additional information
     */
    public static Optional<TenantInfo> getTenantInfo() {
        return Optional.ofNullable(TENANT_INFO_HOLDER.get());
    }

    // =====================================================
    // Cleanup Operations
    // =====================================================

    /**
     * Clears tenant context for current thread.
     * 
     * <p>Must be called at request end to prevent tenant info leakage during thread reuse.</p>
     */
    public static void clear() {
        TENANT_ID_HOLDER.remove();
        USER_ID_HOLDER.remove();
        TENANT_INFO_HOLDER.remove();
    }

    // =====================================================
    // Context Switching Operations
    // =====================================================

    /**
     * Executes operation in specified tenant context.
     * 
     * @param tenantId target tenant ID
     * @param runnable operation to execute
     */
    public static void runWithTenant(String tenantId, Runnable runnable) {
        String previousTenantId = TENANT_ID_HOLDER.get();
        try {
            setTenantId(tenantId);
            runnable.run();
        } finally {
            if (previousTenantId != null) {
                TENANT_ID_HOLDER.set(previousTenantId);
            } else {
                TENANT_ID_HOLDER.remove();
            }
        }
    }

    /**
     * Execute operation in specified tenant context and return result
     * 
     * @param tenantId Target tenant ID
     * @param supplier Operation to execute
     * @param <T> Return value type
     * @return Operation result
     */
    public static <T> T runWithTenant(String tenantId, Supplier<T> supplier) {
        String previousTenantId = TENANT_ID_HOLDER.get();
        try {
            setTenantId(tenantId);
            return supplier.get();
        } finally {
            if (previousTenantId != null) {
                TENANT_ID_HOLDER.set(previousTenantId);
            } else {
                TENANT_ID_HOLDER.remove();
            }
        }
    }

    /**
     * Execute operation in system tenant context
     * 
     * @param runnable Operation to execute
     */
    public static void runAsSystem(Runnable runnable) {
        runWithTenant(SYSTEM_TENANT_ID, runnable);
    }

    /**
     * Execute operation in system tenant context and return result
     * 
     * @param supplier Operation to execute
     * @param <T> Return value type
     * @return Operation result
     */
    public static <T> T runAsSystem(Supplier<T> supplier) {
        return runWithTenant(SYSTEM_TENANT_ID, supplier);
    }
}
