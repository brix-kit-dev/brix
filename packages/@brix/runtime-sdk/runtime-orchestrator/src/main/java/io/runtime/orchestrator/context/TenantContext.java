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
package io.runtime.orchestrator.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tenant Context Holder.
 * 
 * <p>ThreadLocal-based tenant context management, storing current tenant information during request processing.
 * This is the core component of multi-tenant isolation architecture.</p>
 * 
 * <h2>Core Use Cases</h2>
 * <ul>
 *   <li>Maintaining tenant identity during request processing</li>
 *   <li>Auto-appending tenant conditions to database queries</li>
 *   <li>Auto-adding tenant prefix to cache keys</li>
 *   <li>Auto-carrying tenant information when publishing events</li>
 * </ul>
 * 
 * <h2>Context Propagation Flow</h2>
 * <pre>
 * Client Request
 *     |
 *     v  Header: X-Tenant-Id: tenant-001
 * +------------------+
 * |  Gateway         |  TenantContextFilter parses and validates tenant ID
 * +--------+---------+
 *          |  TenantContext.set("tenant-001")
 *          v
 * +------------------+
 * |  Module Service  |  RuntimeContext.getTenantId()
 * +--------+---------+
 *          |  Auto-carries tenantId when publishing events
 *          v
 * +------------------+
 * |  EventBus        |  Event header contains tenantId, auto-restored on consume
 * +------------------+
 * </pre>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Set tenant context in Filter
 * public void doFilter(ServletRequest request, ...) {
 *     String tenantId = httpRequest.getHeader("X-Tenant-Id");
 *     TenantContext.set(tenantId);
 *     try {
 *         chain.doFilter(request, response);
 *     } finally {
 *         TenantContext.clear();  // Important! Must clear
 *     }
 * }
 * 
 * // Get tenant in business code
 * String tenantId = TenantContext.get();
 * }</pre>
 * 
 * <h2>Important Reminder</h2>
 * <p><b>Must</b> call {@link #clear()} method to clean up context at request end,
 * to avoid tenant information leakage due to thread reuse.</p>
 * 
 * <h2>Architecture Position</h2>
 * <p>This class belongs to the <b>Orchestration Layer (Orchestrator)</b>, responsible for runtime multi-tenant context management.
 * Migrated from runtime-sdk-api, because tenant context management is a runtime orchestration responsibility,
 * not a basic contract definition. SDK API layer only defines pure Capability interface contracts.</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public final class TenantContext {

    private static final Logger log = LoggerFactory.getLogger(TenantContext.class);

    /**
     * Tenant ID header field name in requests.
     */
    public static final String TENANT_HEADER = "X-Tenant-Id";

    /**
     * Default tenant ID (for system-level operations).
     */
    public static final String DEFAULT_TENANT = "default";

    /**
     * System tenant ID (for super admin use).
     */
    public static final String SYSTEM_TENANT = "system";

    /**
     * ThreadLocal storage for current tenant ID.
     * 
     * <p>Each thread holds an independent tenant ID copy, enabling thread-safe tenant context propagation.
     * In async scenarios (e.g., CompletableFuture), manual propagation is needed.</p>
     */
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    /**
     * ThreadLocal storage for whether to ignore tenant filtering.
     * 
     * <p>Used for system-level cross-tenant operations, such as scheduled task statistics, super admin queries.
     * Default value is false, meaning tenant filtering is enabled by default.</p>
     */
    private static final ThreadLocal<Boolean> IGNORE_TENANT = ThreadLocal.withInitial(() -> false);

    /**
     * Private constructor, utility class cannot be instantiated.
     */
    private TenantContext() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ==================== Basic Operations ====================

    /**
     * Sets current tenant ID.
     * 
     * <p>Usually called by Filter or Interceptor at request entry point.</p>
     * 
     * @param tenantId tenant ID, cannot be null or empty
     * @throws IllegalArgumentException if tenantId is null or empty
     */
    public static void set(String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant ID cannot be null or empty");
        }
        CURRENT_TENANT.set(tenantId.trim());
        log.debug("Set tenant context: {}", tenantId);
    }

    /**
     * Gets current tenant ID.
     * 
     * @return current tenant ID, returns null if not set
     */
    public static String get() {
        return CURRENT_TENANT.get();
    }

    /**
     * Gets current tenant ID, returns default value if not set.
     * 
     * @param defaultValue default value
     * @return current tenant ID or default value
     */
    public static String getOrDefault(String defaultValue) {
        String tenantId = CURRENT_TENANT.get();
        return tenantId != null ? tenantId : defaultValue;
    }

    /**
     * Gets current tenant ID, throws exception if not set.
     * 
     * @return current tenant ID
     * @throws TenantNotSetException if tenant context is not set
     */
    public static String getRequired() {
        String tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new TenantNotSetException("Tenant context not set");
        }
        return tenantId;
    }

    /**
     * Clears current tenant context.
     * 
     * <p><b>Important</b>: Must be called at request end to avoid data leakage due to thread reuse.
     * Recommended to use in try-finally.</p>
     */
    public static void clear() {
        String tenantId = CURRENT_TENANT.get();
        CURRENT_TENANT.remove();
        IGNORE_TENANT.remove();
        if (tenantId != null) {
            log.debug("Cleared tenant context: {}", tenantId);
        }
    }

    // ==================== Status Checks ====================

    /**
     * Checks if tenant context is set.
     * 
     * @return true if set
     */
    public static boolean isSet() {
        return CURRENT_TENANT.get() != null;
    }

    /**
     * Checks if current tenant is default tenant.
     * 
     * @return true if default tenant
     */
    public static boolean isDefaultTenant() {
        return DEFAULT_TENANT.equals(CURRENT_TENANT.get());
    }

    /**
     * Checks if current tenant is system tenant.
     * 
     * @return true if system tenant
     */
    public static boolean isSystemTenant() {
        return SYSTEM_TENANT.equals(CURRENT_TENANT.get());
    }

    // ==================== Tenant Filter Control ====================

    /**
     * Sets ignore tenant filter flag.
     * 
     * <p>Used for system-level operations that need to query data across all tenants.
     * After setting, data access layer should skip tenant filter conditions.</p>
     * 
     * @param ignore whether to ignore tenant filter
     */
    public static void setIgnoreFilter(boolean ignore) {
        IGNORE_TENANT.set(ignore);
        log.debug("Set ignore tenant filter: {}", ignore);
    }

    /**
     * Checks if tenant filter should be ignored.
     * 
     * @return true if should ignore
     */
    public static boolean shouldIgnoreFilter() {
        return Boolean.TRUE.equals(IGNORE_TENANT.get());
    }

    // ==================== Execution Context ====================

    /**
     * Executes operation in specified tenant context.
     * 
     * <p>Temporarily switches to specified tenant, auto-restores original tenant context after execution.
     * Suitable for cross-tenant operation scenarios, such as data migration, batch processing, etc.</p>
     * 
     * <h4>Usage Example</h4>
     * <pre>{@code
     * TenantContext.runAs("tenant-002", () -> {
     *     // Execute in tenant-002 context
     *     repository.findAll();  // Only queries tenant-002's data
     * });
     * // Restores original tenant context
     * }</pre>
     * 
     * @param tenantId temporary tenant ID
     * @param runnable operation to execute
     */
    public static void runAs(String tenantId, Runnable runnable) {
        String previous = CURRENT_TENANT.get();
        try {
            set(tenantId);
            runnable.run();
        } finally {
            if (previous != null) {
                CURRENT_TENANT.set(previous);
            } else {
                CURRENT_TENANT.remove();
            }
        }
    }

    /**
     * Executes operation in specified tenant context and returns result.
     * 
     * @param tenantId temporary tenant ID
     * @param supplier operation to execute
     * @param <T> return value type
     * @return operation result
     */
    public static <T> T callAs(String tenantId, java.util.function.Supplier<T> supplier) {
        String previous = CURRENT_TENANT.get();
        try {
            set(tenantId);
            return supplier.get();
        } finally {
            if (previous != null) {
                CURRENT_TENANT.set(previous);
            } else {
                CURRENT_TENANT.remove();
            }
        }
    }

    /**
     * Executes operation while ignoring tenant filter.
     * 
     * <p>Used for system-level operations that need access to all tenant data.
     * After execution, auto-restores previous filter state.</p>
     * 
     * @param runnable operation to execute
     */
    public static void runWithoutFilter(Runnable runnable) {
        Boolean previous = IGNORE_TENANT.get();
        try {
            IGNORE_TENANT.set(true);
            runnable.run();
        } finally {
            IGNORE_TENANT.set(previous);
        }
    }

    /**
     * Tenant context not set exception.
     * 
     * <p>Thrown when business code requires tenant context but it is not set.
     * Usually means TenantContextFilter is missing in the request chain or tenantId was not propagated.</p>
     */
    public static class TenantNotSetException extends RuntimeException {
        public TenantNotSetException(String message) {
            super(message);
        }
    }
}
