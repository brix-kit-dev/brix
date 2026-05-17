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

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.capability.StateStoreCapability;

/**
 * Tenant-Isolated State Store Decorator.
 * 
 * <p>Automatically adds tenant prefix to keys, ensuring data isolation in multi-tenant environments.
 * This is the core component for implementing multi-tenant state storage isolation.</p>
 * 
 * <h2>Isolation Strategy</h2>
 * <p>All storage keys are automatically prefixed with tenant identifier:</p>
 * <pre>
 * Original Key: "session:user123"
 * Isolated Key: "tenant:tenant001:session:user123"
 * </pre>
 * 
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><b>Transparent Isolation</b>: Business code doesn't need to be aware of tenant prefix</li>
 *   <li><b>Decorator Pattern</b>: Doesn't modify the original StateStore implementation</li>
 *   <li><b>Runtime Binding</b>: Dynamically gets tenant ID from TenantContext</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create tenant-isolated state store
 * StateStoreCapability delegate = // Original implementation (Redis/Memory)
 * StateStoreCapability isolated = new TenantIsolatedStateStore(delegate, "tenant001");
 * 
 * // Store data - Actual Key: "tenant:tenant001:user:123"
 * isolated.put("user:123", userData, Duration.ofHours(1));
 * 
 * // Read data - Can only read data from current tenant
 * Optional<UserData> data = isolated.get("user:123", UserData.class);
 * }</pre>
 * 
 * <h2>Dynamic Tenant Binding</h2>
 * <pre>{@code
 * // Get tenant dynamically from TenantContext
 * StateStoreCapability isolated = TenantIsolatedStateStore.wrap(delegate);
 * 
 * // Set tenant context
 * TenantContext.set("tenant002");
 * 
 * // Automatically uses current tenant when storing
 * isolated.put("key", value, ttl);  // Key: "tenant:tenant002:key"
 * }</pre>
 * 
 * <h2>Architecture Position</h2>
 * <p>This class belongs to the <b>Orchestration Layer (Orchestrator)</b>, serving as a runtime decorator for StateStoreCapability.
 * Migrated from runtime-sdk-api, because tenant isolation logic is a runtime orchestration responsibility,
 * not a basic Capability interface contract.</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see StateStoreCapability
 * @see TenantContext
 */
public class TenantIsolatedStateStore implements StateStoreCapability {

    private static final Logger log = LoggerFactory.getLogger(TenantIsolatedStateStore.class);

    /**
     * Tenant key prefix format.
     * 
     * <p>Format is "tenant:{tenantId}:", concatenated with original key to form isolated key.</p>
     */
    private static final String TENANT_KEY_PREFIX = "tenant:%s:";

    /**
     * Delegated state store instance.
     */
    private final StateStoreCapability delegate;

    /**
     * Fixed tenant ID (optional, gets dynamically from TenantContext if null).
     */
    private final String fixedTenantId;

    /**
     * Creates tenant-isolated state store (fixed tenant).
     * 
     * @param delegate delegated state store instance
     * @param tenantId fixed tenant ID
     */
    public TenantIsolatedStateStore(StateStoreCapability delegate, String tenantId) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.fixedTenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        log.debug("Created tenant-isolated state store: tenantId={}", tenantId);
    }

    /**
     * Creates tenant-isolated state store (dynamic tenant).
     * 
     * <p>Gets current tenant ID from TenantContext at runtime.</p>
     * 
     * @param delegate delegated state store instance
     */
    private TenantIsolatedStateStore(StateStoreCapability delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.fixedTenantId = null;
        log.debug("Created dynamic tenant-isolated state store");
    }

    /**
     * Wraps state store as dynamic tenant-isolated version.
     * 
     * <p>The returned instance will get tenant ID from TenantContext at runtime.
     * Suitable for request-level tenant isolation scenarios.</p>
     * 
     * @param delegate delegated state store instance
     * @return tenant-isolated state store
     */
    public static StateStoreCapability wrap(StateStoreCapability delegate) {
        return new TenantIsolatedStateStore(delegate);
    }

    // ==================== StateStoreCapability Implementation ====================

    /**
     * Gets stored value.
     * 
     * @param key  storage key (tenant prefix added automatically)
     * @param type value type
     * @param <T>  value type
     * @return stored value, returns empty if not exists
     */
    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        String tenantKey = tenantKey(key);
        log.debug("Tenant-isolated read: originalKey={}, tenantKey={}", key, tenantKey);

        return delegate.get(tenantKey, type);
    }

    /**
     * Stores value.
     * 
     * @param key   storage key (tenant prefix added automatically)
     * @param value value to store
     */
    @Override
    public void put(String key, Object value) {
        Objects.requireNonNull(key, "key cannot be null");

        String tenantKey = tenantKey(key);
        log.debug("Tenant-isolated store: originalKey={}, tenantKey={}", key, tenantKey);

        delegate.put(tenantKey, value);
    }

    /**
     * Stores value (with TTL).
     * 
     * @param key   storage key (tenant prefix added automatically)
     * @param value value to store
     * @param ttl   time to live
     */
    @Override
    public void put(String key, Object value, Duration ttl) {
        Objects.requireNonNull(key, "key cannot be null");

        String tenantKey = tenantKey(key);
        log.debug("Tenant-isolated store: originalKey={}, tenantKey={}, ttl={}", key, tenantKey, ttl);

        delegate.put(tenantKey, value, ttl);
    }

    /**
     * Removes stored value.
     * 
     * @param key storage key (tenant prefix added automatically)
     */
    @Override
    public void remove(String key) {
        Objects.requireNonNull(key, "key cannot be null");

        String tenantKey = tenantKey(key);
        log.debug("Tenant-isolated remove: originalKey={}, tenantKey={}", key, tenantKey);

        delegate.remove(tenantKey);
    }

    /**
     * Checks if key exists.
     * 
     * @param key storage key (tenant prefix added automatically)
     * @return true if exists
     */
    @Override
    public boolean exists(String key) {
        Objects.requireNonNull(key, "key cannot be null");

        String tenantKey = tenantKey(key);
        return delegate.exists(tenantKey);
    }

    @Override
    public boolean putIfAbsent(String key, Object value, Duration ttl) {
        Objects.requireNonNull(key, "key cannot be null");

        String tenantKey = tenantKey(key);
        log.debug("Tenant-isolated put-if-absent: originalKey={}, tenantKey={}, ttl={}", key, tenantKey, ttl);

        return delegate.putIfAbsent(tenantKey, value, ttl);
    }

    // ==================== Helper Methods ====================

    /**
     * Generates tenant-isolated key.
     * 
     * <p>Concatenates original key with tenant prefix to generate unique isolated key.
     * Format: tenant:{tenantId}:{originalKey}</p>
     * 
     * @param key original key
     * @return key with tenant prefix
     */
    private String tenantKey(String key) {
        String tenantId = getCurrentTenantId();
        return String.format(TENANT_KEY_PREFIX, tenantId) + key;
    }

    /**
     * Gets current tenant ID.
     * 
     * <p>Prefers fixed tenant ID, otherwise gets dynamically from TenantContext.
     * Throws exception if TenantContext is not set in dynamic mode.</p>
     * 
     * @return tenant ID
     * @throws TenantContext.TenantNotSetException if using dynamic tenant but context is not set
     */
    private String getCurrentTenantId() {
        if (fixedTenantId != null) {
            return fixedTenantId;
        }

        // Get dynamically from TenantContext
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new TenantContext.TenantNotSetException(
                    "Dynamic tenant isolation requires TenantContext to be set, ensure tenant context is set in request processing chain");
        }
        return tenantId;
    }

    /**
     * Gets delegated state store.
     * 
     * @return delegate instance
     */
    public StateStoreCapability getDelegate() {
        return delegate;
    }

    /**
     * Gets fixed tenant ID.
     * 
     * @return fixed tenant ID, returns null in dynamic mode
     */
    public String getFixedTenantId() {
        return fixedTenantId;
    }

    @Override
    public String toString() {
        return "TenantIsolatedStateStore{" +
                "tenantId=" + (fixedTenantId != null ? fixedTenantId : "dynamic") +
                ", delegate=" + delegate.getClass().getSimpleName() +
                '}';
    }
}
