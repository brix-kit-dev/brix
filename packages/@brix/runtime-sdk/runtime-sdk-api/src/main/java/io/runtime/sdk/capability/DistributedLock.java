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

/**
 * Distributed Lock Interface
 * 
 * <p>Represents an acquired or attempted distributed lock, supports try-with-resources auto-release.</p>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * try (DistributedLock lock = lockCapability.acquire("my-lock", Duration.ofSeconds(10))) {
 *     if (lock.isLocked()) {
 *         // Execute operations requiring lock protection
 *     }
 * } // Lock automatically released
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see LockCapability#acquire(String, java.time.Duration)
 */
public interface DistributedLock extends AutoCloseable {

    /**
     * Get lock key
     * 
     * @return unique lock key
     */
    String getKey();

    /**
     * Check if lock was successfully acquired
     * 
     * @return true if holding lock
     */
    boolean isLocked();

    /**
     * Manually release lock
     * 
     * <p>If using try-with-resources, no need to call this method manually</p>
     */
    void release();

    /**
     * Implements AutoCloseable, auto-releases lock
     */
    @Override
    default void close() {
        if (isLocked()) {
            release();
        }
    }
}
