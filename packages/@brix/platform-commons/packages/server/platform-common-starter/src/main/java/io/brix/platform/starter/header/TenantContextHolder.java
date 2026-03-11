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
package io.brix.platform.starter.header;

/**
 * Tenant Context Holder
 * 
 * <p>Uses ThreadLocal to propagate tenant ID along the request chain,
 * ensuring the current tenant information is accessible at any time within the same request thread.</p>
 * 
 * <p>Design Purpose:</p>
 * <ul>
 *   <li>Resolve Issue 6: X-Tenant-Id request header is often missing</li>
 *   <li>Automatically extract tenant ID from request headers via interceptor and store in context</li>
 *   <li>Automatically propagate tenant ID during inter-service calls</li>
 * </ul>
 * 
 * <p>Usage Flow:</p>
 * <ol>
 *   <li>TenantHeaderFilter extracts X-Tenant-Id on request inbound and calls setTenantId()</li>
 *   <li>Business code retrieves current tenant via getTenantId()</li>
 *   <li>PlatformHeadersInterceptor automatically adds X-Tenant-Id on outbound requests</li>
 *   <li>TenantHeaderFilter calls clear() at request end to clean up context</li>
 * </ol>
 * 
 * <p>Usage Example:</p>
 * <pre>
 * // Get current tenant ID
 * String tenantId = TenantContextHolder.getTenantId();
 * 
 * // Manually set tenant ID (usually handled automatically by Filter)
 * TenantContextHolder.setTenantId("tenant-123");
 * 
 * // Clear context (usually handled automatically by Filter)
 * TenantContextHolder.clear();
 * </pre>
 * 
 * <p>Thread Safety Notes:</p>
 * <ul>
 *   <li>Each thread has independent tenant context</li>
 *   <li>For async scenarios, context must be manually propagated or use context propagation tools</li>
 * </ul>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 * @see TenantHeaderFilter
 * @see PlatformHeadersInterceptor
 */
public final class TenantContextHolder {
    
    /**
     * Thread-local variable - stores the tenant ID of the current thread
     * 
     * <p>Uses ThreadLocal to ensure thread isolation</p>
     */
    private static final ThreadLocal<String> TENANT_ID_HOLDER = new ThreadLocal<>();
    
    /**
     * Thread-local variable - stores the user ID of the current thread
     */
    private static final ThreadLocal<String> USER_ID_HOLDER = new ThreadLocal<>();
    
    /**
     * Thread-local variable - stores the trace ID of the current thread
     */
    private static final ThreadLocal<String> TRACE_ID_HOLDER = new ThreadLocal<>();
    
    /**
     * Private constructor to prevent instantiation
     */
    private TenantContextHolder() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    // ==================== Tenant ID ====================
    
    /**
     * Set the tenant ID of the current thread
     * 
     * <p>Usually called by TenantHeaderFilter on request inbound</p>
     * 
     * @param tenantId Tenant ID, cannot be null
     * @throws IllegalArgumentException if tenantId is null or empty string
     */
    public static void setTenantId(String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant ID cannot be empty");
        }
        TENANT_ID_HOLDER.set(tenantId.trim());
    }
    
    /**
     * Get the tenant ID of the current thread
     * 
     * <p>If not set, returns default tenant ID</p>
     * 
     * @return Current tenant ID, never returns null
     */
    public static String getTenantId() {
        String tenantId = TENANT_ID_HOLDER.get();
        return tenantId != null ? tenantId : PlatformHeaders.DEFAULT_TENANT_ID;
    }
    
    /**
     * Get the tenant ID of the current thread (nullable)
     * 
     * <p>Does not use default value, returns null if not set</p>
     * 
     * @return Current tenant ID, may be null
     */
    public static String getTenantIdNullable() {
        return TENANT_ID_HOLDER.get();
    }
    
    /**
     * Check if tenant ID is set for the current thread
     * 
     * @return true if tenant ID is already set
     */
    public static boolean hasTenantId() {
        return TENANT_ID_HOLDER.get() != null;
    }
    
    // ==================== User ID ====================
    
    /**
     * Set the user ID of the current thread
     * 
     * @param userId User ID
     */
    public static void setUserId(String userId) {
        if (userId != null && !userId.trim().isEmpty()) {
            USER_ID_HOLDER.set(userId.trim());
        }
    }
    
    /**
     * Get the user ID of the current thread
     * 
     * @return User ID, may be null
     */
    public static String getUserId() {
        return USER_ID_HOLDER.get();
    }
    
    /**
     * Check if user ID is set for the current thread
     * 
     * @return true if user ID is already set
     */
    public static boolean hasUserId() {
        return USER_ID_HOLDER.get() != null;
    }
    
    // ==================== Trace ID ====================
    
    /**
     * Set the trace ID of the current thread
     * 
     * @param traceId Trace ID
     */
    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.trim().isEmpty()) {
            TRACE_ID_HOLDER.set(traceId.trim());
        }
    }
    
    /**
     * Get the trace ID of the current thread
     * 
     * @return Trace ID, may be null
     */
    public static String getTraceId() {
        return TRACE_ID_HOLDER.get();
    }
    
    // ==================== Context Management ====================
    
    /**
     * Clear all context of the current thread
     * 
     * <p>Must be called after request processing completes to prevent memory leaks</p>
     * <p>Usually called by TenantHeaderFilter in finally block</p>
     */
    public static void clear() {
        TENANT_ID_HOLDER.remove();
        USER_ID_HOLDER.remove();
        TRACE_ID_HOLDER.remove();
    }
    
    /**
     * Get a snapshot of the current context
     * 
     * <p>Used for passing context in async scenarios</p>
     * 
     * @return Context snapshot
     */
    public static ContextSnapshot snapshot() {
        return new ContextSnapshot(
            TENANT_ID_HOLDER.get(),
            USER_ID_HOLDER.get(),
            TRACE_ID_HOLDER.get()
        );
    }
    
    /**
     * Restore context from snapshot
     * 
     * <p>Used for restoring context in async scenarios</p>
     * 
     * @param snapshot Context snapshot
     */
    public static void restore(ContextSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        if (snapshot.tenantId() != null) {
            TENANT_ID_HOLDER.set(snapshot.tenantId());
        }
        if (snapshot.userId() != null) {
            USER_ID_HOLDER.set(snapshot.userId());
        }
        if (snapshot.traceId() != null) {
            TRACE_ID_HOLDER.set(snapshot.traceId());
        }
    }
    
    /**
     * Context Snapshot
     * 
     * <p>Used for passing tenant context in async scenarios</p>
     * 
     * @param tenantId Tenant ID
     * @param userId   User ID
     * @param traceId  Trace ID
     */
    public record ContextSnapshot(
        String tenantId,
        String userId,
        String traceId
    ) {}
}
