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
import java.util.concurrent.Callable;
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

    // =====================================================
    // Context Propagation Operations (for async execution)
    // =====================================================

    /**
     * Wraps a Runnable to propagate the current tenant context to another thread.
     * 
     * <p>This method captures the current tenant ID and user ID at the time of wrapping,
     * and ensures they are properly set in the target thread before execution,
     * and cleaned up after execution completes (whether successfully or with exception).
     * 
     * <h3>Usage Example</h3>
     * <pre>{@code
     * // In a controller or service method:
     * String tenantId = TenantContext.getCurrentTenantId();
     * 
     * // Wrap the task to propagate context
     * Runnable task = TenantContext.wrap(() -> {
     *     // Inside async thread, tenant context is available
     *     String tid = TenantContext.getCurrentTenantId(); // Same as original
     *     processDataForTenant(tid);
     * });
     * 
     * // Submit to executor
     * executor.submit(task);
     * }</pre>
     * 
     * <h3>Thread Safety</h3>
     * <ul>
     *   <li>The captured tenant ID is immutable after wrapping</li>
     *   <li>Each wrapped runnable operates independently</li>
     *   <li>Context is thread-local and isolated between threads</li>
     * </ul>
     * 
     * @param runnable the original Runnable to wrap
     * @return a new Runnable that propagates tenant context
     * @throws IllegalArgumentException if runnable is null
     * @since 3.1.0
     */
    public static Runnable wrap(Runnable runnable) {
        if (runnable == null) {
            throw new IllegalArgumentException("Runnable cannot be null");
        }
        
        // Capture current context at wrap time
        final String capturedTenantId = TENANT_ID_HOLDER.get();
        final String capturedUserId = USER_ID_HOLDER.get();
        final TenantInfo capturedTenantInfo = TENANT_INFO_HOLDER.get();
        
        return () -> {
            // Store previous context of target thread (if any)
            String previousTenantId = TENANT_ID_HOLDER.get();
            String previousUserId = USER_ID_HOLDER.get();
            TenantInfo previousTenantInfo = TENANT_INFO_HOLDER.get();
            
            try {
                // Set captured context in target thread
                if (capturedTenantId != null) {
                    TENANT_ID_HOLDER.set(capturedTenantId);
                }
                if (capturedUserId != null) {
                    USER_ID_HOLDER.set(capturedUserId);
                }
                if (capturedTenantInfo != null) {
                    TENANT_INFO_HOLDER.set(capturedTenantInfo);
                }
                
                // Execute the original runnable
                runnable.run();
            } finally {
                // Restore previous context or clear
                restoreOrClear(TENANT_ID_HOLDER, previousTenantId);
                restoreOrClear(USER_ID_HOLDER, previousUserId);
                restoreOrClear(TENANT_INFO_HOLDER, previousTenantInfo);
            }
        };
    }

    /**
     * Wraps a Callable to propagate the current tenant context to another thread.
     * 
     * <p>This method captures the current tenant ID and user ID at the time of wrapping,
     * and ensures they are properly set in the target thread before execution,
     * and cleaned up after execution completes (whether successfully or with exception).
     * 
     * <h3>Usage Example</h3>
     * <pre>{@code
     * // In a service method:
     * Callable<List<Order>> task = TenantContext.wrap(() -> {
     *     // Inside async thread, tenant context is available
     *     return orderRepository.findByTenantId(TenantContext.getCurrentTenantId());
     * });
     * 
     * // Submit to executor and get future
     * Future<List<Order>> future = executor.submit(task);
     * List<Order> orders = future.get();
     * }</pre>
     * 
     * <h3>Exception Handling</h3>
     * <p>If the wrapped callable throws an exception, the context is still properly
     * cleaned up before the exception propagates. The original exception is wrapped
     * in an {@link Exception} as required by the {@link Callable} interface.
     * 
     * @param callable the original Callable to wrap
     * @param <V> the return type of the callable
     * @return a new Callable that propagates tenant context
     * @throws IllegalArgumentException if callable is null
     * @since 3.1.0
     */
    public static <V> Callable<V> wrap(Callable<V> callable) {
        if (callable == null) {
            throw new IllegalArgumentException("Callable cannot be null");
        }
        
        // Capture current context at wrap time
        final String capturedTenantId = TENANT_ID_HOLDER.get();
        final String capturedUserId = USER_ID_HOLDER.get();
        final TenantInfo capturedTenantInfo = TENANT_INFO_HOLDER.get();
        
        return () -> {
            // Store previous context of target thread (if any)
            String previousTenantId = TENANT_ID_HOLDER.get();
            String previousUserId = USER_ID_HOLDER.get();
            TenantInfo previousTenantInfo = TENANT_INFO_HOLDER.get();
            
            try {
                // Set captured context in target thread
                if (capturedTenantId != null) {
                    TENANT_ID_HOLDER.set(capturedTenantId);
                }
                if (capturedUserId != null) {
                    USER_ID_HOLDER.set(capturedUserId);
                }
                if (capturedTenantInfo != null) {
                    TENANT_INFO_HOLDER.set(capturedTenantInfo);
                }
                
                // Execute the original callable
                return callable.call();
            } finally {
                // Restore previous context or clear
                restoreOrClear(TENANT_ID_HOLDER, previousTenantId);
                restoreOrClear(USER_ID_HOLDER, previousUserId);
                restoreOrClear(TENANT_INFO_HOLDER, previousTenantInfo);
            }
        };
    }

    /**
     * Helper method to restore previous ThreadLocal value or clear if null.
     * 
     * @param holder the ThreadLocal holder
     * @param previousValue the previous value to restore (or null to clear)
     * @param <T> the type of the ThreadLocal value
     */
    private static <T> void restoreOrClear(ThreadLocal<T> holder, T previousValue) {
        if (previousValue != null) {
            holder.set(previousValue);
        } else {
            holder.remove();
        }
    }

    /**
     * Sets the current tenant (convenience method combining tenant ID and info).
     * 
     * <p>This method sets both the tenant ID and tenant information in one call.
     * It is useful when you have a complete TenantInfo object.
     * 
     * @param tenantInfo the tenant information to set
     * @throws IllegalArgumentException if tenantInfo is null or has null tenantId
     * @since 3.1.0
     */
    public static void setCurrentTenant(TenantInfo tenantInfo) {
        if (tenantInfo == null) {
            throw new IllegalArgumentException("TenantInfo cannot be null");
        }
        if (tenantInfo.getTenantId() == null || tenantInfo.getTenantId().isBlank()) {
            throw new IllegalArgumentException("TenantInfo.tenantId cannot be null or blank");
        }
        setTenantId(tenantInfo.getTenantId());
        setTenantInfo(tenantInfo);
    }

    /**
     * Gets the current tenant information.
     * 
     * <p>This method returns the full TenantInfo if available, otherwise
     * constructs a minimal TenantInfo from the tenant ID.
     * 
     * @return current TenantInfo, or null if no tenant context is set
     * @since 3.1.0
     */
    public static TenantInfo getCurrentTenant() {
        TenantInfo info = TENANT_INFO_HOLDER.get();
        if (info != null) {
            return info;
        }
        
        String tenantId = TENANT_ID_HOLDER.get();
        if (tenantId != null) {
            return TenantInfo.builder(tenantId).build();
        }
        
        return null;
    }
}
