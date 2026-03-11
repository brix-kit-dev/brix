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

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Distributed Lock Capability Contract (Optional Capability)
 * 
 * <p>Provides a unified abstraction for distributed locks, used for cross-process concurrency control.
 * Modules acquire and release locks through this interface without knowing the underlying implementation (Redis/ZooKeeper/Database).</p>
 * 
 * <h3>Core Features</h3>
 * <ul>
 *   <li><b>Exclusive Lock</b>: Only one holder at a time</li>
 *   <li><b>Timeout Mechanism</b>: Prevents deadlocks</li>
 *   <li><b>Reentrancy</b>: Same thread can acquire repeatedly</li>
 * </ul>
 * 
 * <h3>Use Cases</h3>
 * <ul>
 *   <li>Distributed scheduled tasks (ensure only one node executes)</li>
 *   <li>Inventory deduction (prevent overselling)</li>
 *   <li>Order processing (prevent duplicate processing)</li>
 * </ul>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Inject
 * private LockCapability lockCapability;
 * 
 * // Method 1: try-with-resources (recommended)
 * public void processOrder(String orderId) {
 *     String lockKey = "order:process:" + orderId;
 *     try (DistributedLock lock = lockCapability.acquire(lockKey, Duration.ofSeconds(30))) {
 *         if (lock.isLocked()) {
 *             // Execute order processing logic...
 *         } else {
 *             throw new ConcurrentModificationException("Order is being processed");
 *         }
 *     }
 * }
 * 
 * // Method 2: Manual acquire and release
 * public void processOrderManual(String orderId) {
 *     String lockKey = "order:process:" + orderId;
 *     if (lockCapability.tryLock(lockKey, 5, TimeUnit.SECONDS)) {
 *         try {
 *             // Execute order processing logic...
 *         } finally {
 *             lockCapability.unlock(lockKey);
 *         }
 *     }
 * }
 * }</pre>
 * 
 * <h3>Notes</h3>
 * <ul>
 *   <li>Lock keys should have business meaning, avoid being too broad</li>
 *   <li>Set reasonable timeout to prevent deadlocks</li>
 *   <li>Ensure locks are released in finally blocks</li>
 *   <li>This is an optional capability; check availability before use</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public interface LockCapability {

    /**
     * Acquire distributed lock
     * 
     * <p>Blocks until lock is acquired or timeout</p>
     * 
     * @param key     unique lock key
     * @param timeout maximum wait time
     * @return lock object, usable with try-with-resources
     */
    DistributedLock acquire(String key, Duration timeout);

    /**
     * Try to acquire lock (non-blocking)
     * 
     * <p>Returns immediately without waiting</p>
     * 
     * @param key unique lock key
     * @return true if successfully acquired
     */
    boolean tryLock(String key);

    /**
     * Try to acquire lock (with wait time)
     * 
     * @param key      unique lock key
     * @param waitTime wait time
     * @param unit     time unit
     * @return true if successfully acquired
     */
    boolean tryLock(String key, long waitTime, TimeUnit unit);

    /**
     * Release lock
     * 
     * @param key unique lock key
     */
    void unlock(String key);

    /**
     * Check if lock is held
     * 
     * @param key unique lock key
     * @return true if lock is held
     */
    boolean isLocked(String key);

    /**
     * Check if current thread holds the lock
     * 
     * @param key unique lock key
     * @return true if current thread holds the lock
     */
    boolean isHeldByCurrentThread(String key);
}
