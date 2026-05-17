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
package io.brix.platform.tenant.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Enumeration;

/**
 * Snowflake ID Generator Implementation.
 *
 * <p>Implements Twitter's Snowflake algorithm for generating 64-bit unique IDs
 * suitable for distributed systems. The generated IDs are:
 * <ul>
 *   <li>Globally unique across all nodes</li>
 *   <li>Time-ordered (roughly sortable by creation time)</li>
 *   <li>High throughput (4096 IDs per millisecond per worker)</li>
 *   <li>Lock-free and thread-safe</li>
 * </ul>
 *
 * <h3>ID Structure (64 bits)</h3>
 * <pre>
 * +-------------------+-------------------+-------------------+
 * | 1 bit (sign)      | 41 bits           | 10 bits           | 12 bits        |
 * | always 0          | timestamp         | worker ID         | sequence       |
 * +-------------------+-------------------+-------------------+
 *
 * - Sign bit: Always 0 to ensure positive numbers
 * - Timestamp: Milliseconds since custom epoch (allows ~69 years)
 * - Worker ID: Unique identifier for the generator instance (0-1023)
 * - Sequence: Counter for IDs generated in the same millisecond (0-4095)
 * </pre>
 *
 * <h3>Capacity</h3>
 * <ul>
 *   <li>Time range: ~69 years from epoch (until 2088 with 2019 epoch)</li>
 *   <li>Workers: 1024 unique worker IDs (10 bits)</li>
 *   <li>Throughput: 4096 IDs per millisecond per worker</li>
 *   <li>Total: ~4 million IDs per second per worker</li>
 * </ul>
 *
 * <h3>Worker ID Assignment</h3>
 * <p>Worker ID must be unique across all generator instances to guarantee
 * uniqueness. Assignment strategies:
 * <ul>
 *   <li>Manual configuration via environment variable or config</li>
 *   <li>Kubernetes stateful set ordinal</li>
 *   <li>MAC address-based (default fallback)</li>
 *   <li>Random (for testing only)</li>
 * </ul>
 *
 * <h3>Clock Skew Handling</h3>
 * <p>If the system clock moves backwards, the generator will:
 * <ul>
 *   <li>Wait for clock to catch up (small skew, &lt; 5ms)</li>
 *   <li>Throw exception for large skew (&gt; 5ms)</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>This implementation uses synchronized blocks to ensure thread safety.
 * The critical section is minimal (only sequence update) for high throughput.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Create generator with explicit worker ID
 * IdGenerator generator = new SnowflakeIdGenerator(1);
 *
 * // Generate IDs
 * long id1 = generator.nextId();
 * long id2 = generator.nextId();
 *
 * // Parse ID components
 * long timestamp = generator.parseTimestamp(id1);
 * long workerId = generator.parseWorkerId(id1);
 * }</pre>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see IdGenerator
 */
public class SnowflakeIdGenerator implements IdGenerator {

    private static final Logger log = LoggerFactory.getLogger(SnowflakeIdGenerator.class);

    // ========================================================================
    // Snowflake Algorithm Constants
    // ========================================================================

    /**
     * Custom epoch: 2019-01-01 00:00:00 UTC.
     *
     * <p>Using a custom epoch closer to the system's deployment date
     * extends the usable time range. With this epoch, IDs are valid
     * until approximately 2088.
     */
    private static final long CUSTOM_EPOCH = 1546300800000L;

    /**
     * Number of bits allocated for worker ID.
     * Supports up to 1024 workers (2^10).
     */
    private static final int WORKER_ID_BITS = 10;

    /**
     * Number of bits allocated for sequence number.
     * Supports up to 4096 IDs per millisecond (2^12).
     */
    private static final int SEQUENCE_BITS = 12;

    /**
     * Maximum worker ID value (1023).
     */
    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;

    /**
     * Maximum sequence value (4095).
     */
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    /**
     * Bit shift for timestamp (22 bits = worker + sequence).
     */
    private static final int TIMESTAMP_SHIFT = WORKER_ID_BITS + SEQUENCE_BITS;

    /**
     * Bit shift for worker ID (12 bits = sequence).
     */
    private static final int WORKER_ID_SHIFT = SEQUENCE_BITS;

    /**
     * Maximum tolerable clock skew in milliseconds.
     * If clock moves back more than this, throw exception.
     */
    private static final long MAX_CLOCK_SKEW_MS = 5L;

    // ========================================================================
    // Instance State
    // ========================================================================

    /**
     * Worker ID for this generator instance.
     * Must be unique across all instances in the distributed system.
     */
    private final long workerId;

    /**
     * Last timestamp when an ID was generated.
     * Used for sequence management and clock skew detection.
     */
    private volatile long lastTimestamp = -1L;

    /**
     * Sequence number within the current millisecond.
     * Resets to 0 when millisecond changes.
     */
    private volatile long sequence = 0L;

    /**
     * Lock object for synchronizing ID generation.
     */
    private final Object lock = new Object();

    // ========================================================================
    // Constructors
    // ========================================================================

    /**
     * Creates a Snowflake ID generator with auto-detected worker ID.
     *
     * <p>Worker ID is derived from:
     * <ol>
     *   <li>WORKER_ID environment variable (if set)</li>
     *   <li>MAC address of first network interface</li>
     *   <li>Random value (fallback)</li>
     * </ol>
     *
     * <p><b>Warning:</b> Auto-detection may not guarantee uniqueness
     * in all deployment scenarios. For production, consider explicit
     * worker ID configuration.
     */
    public SnowflakeIdGenerator() {
        this(resolveWorkerId());
    }

    /**
     * Creates a Snowflake ID generator with specified worker ID.
     *
     * <p><b>Important:</b> The worker ID must be unique across all
     * generator instances in the distributed system. Duplicate worker
     * IDs will result in ID collisions.
     *
     * @param workerId unique identifier for this generator (0-1023)
     * @throws IllegalArgumentException if workerId is out of range
     */
    public SnowflakeIdGenerator(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                String.format("Worker ID must be between 0 and %d, got: %d", MAX_WORKER_ID, workerId)
            );
        }
        this.workerId = workerId;
        log.info("SnowflakeIdGenerator initialized with worker ID: {}", workerId);
    }

    // ========================================================================
    // ID Generation
    // ========================================================================

    /**
     * {@inheritDoc}
     *
     * <p>This implementation is thread-safe and uses minimal synchronization
     * to maintain high throughput. The algorithm:
     * <ol>
     *   <li>Get current timestamp</li>
     *   <li>Handle clock skew if detected</li>
     *   <li>Increment sequence or wait for next millisecond if overflow</li>
     *   <li>Combine components into final ID</li>
     * </ol>
     *
     * @return unique 64-bit ID
     * @throws IdGenerationException if clock moved backwards significantly
     */
    @Override
    public long nextId() {
        synchronized (lock) {
            long currentTimestamp = currentTimeMillis();

            // Handle clock skew (clock moved backwards)
            if (currentTimestamp < lastTimestamp) {
                long skew = lastTimestamp - currentTimestamp;
                
                if (skew <= MAX_CLOCK_SKEW_MS) {
                    // Small skew: wait for clock to catch up
                    log.warn("Clock moved backwards by {}ms, waiting...", skew);
                    sleepUninterruptibly(skew + 1);
                    currentTimestamp = currentTimeMillis();
                } else {
                    // Large skew: throw exception
                    throw new IdGenerationException(
                        String.format("Clock moved backwards by %dms. Refusing to generate ID.", skew)
                    );
                }
            }

            // Same millisecond: increment sequence
            if (currentTimestamp == lastTimestamp) {
                sequence = (sequence + 1) & MAX_SEQUENCE;
                
                // Sequence overflow: wait for next millisecond
                if (sequence == 0) {
                    currentTimestamp = waitNextMillis(lastTimestamp);
                }
            } else {
                // New millisecond: reset sequence
                sequence = 0;
            }

            lastTimestamp = currentTimestamp;

            // Compose the ID from components
            return ((currentTimestamp - CUSTOM_EPOCH) << TIMESTAMP_SHIFT)
                    | (workerId << WORKER_ID_SHIFT)
                    | sequence;
        }
    }

    // ========================================================================
    // ID Parsing
    // ========================================================================

    /**
     * {@inheritDoc}
     *
     * <p>Extracts the timestamp component from a Snowflake ID and converts
     * it back to a Unix timestamp in milliseconds.
     *
     * @param id the Snowflake ID to parse
     * @return Unix timestamp in milliseconds
     */
    @Override
    public long parseTimestamp(long id) {
        // Extract timestamp bits and add back the custom epoch
        return ((id >> TIMESTAMP_SHIFT) + CUSTOM_EPOCH);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Extracts the worker ID component from a Snowflake ID.
     *
     * @param id the Snowflake ID to parse
     * @return worker ID (0-1023)
     */
    @Override
    public long parseWorkerId(long id) {
        // Extract worker ID bits
        return (id >> WORKER_ID_SHIFT) & MAX_WORKER_ID;
    }

    /**
     * Extracts the sequence number component from a Snowflake ID.
     *
     * @param id the Snowflake ID to parse
     * @return sequence number (0-4095)
     */
    public long parseSequence(long id) {
        return id & MAX_SEQUENCE;
    }

    /**
     * Returns human-readable breakdown of a Snowflake ID.
     *
     * <p>Useful for debugging and logging. Example output:
     * <pre>
     * ID: 6789012345678901234
     *   Timestamp: 2026-03-16T10:30:00Z
     *   Worker ID: 42
     *   Sequence: 1234
     * </pre>
     *
     * @param id the Snowflake ID to describe
     * @return human-readable breakdown
     */
    public String describe(long id) {
        long timestamp = parseTimestamp(id);
        long worker = parseWorkerId(id);
        long seq = parseSequence(id);
        
        return String.format(
            "ID: %d%n  Timestamp: %s%n  Worker ID: %d%n  Sequence: %d",
            id,
            Instant.ofEpochMilli(timestamp),
            worker,
            seq
        );
    }

    // ========================================================================
    // Worker ID Resolution
    // ========================================================================

    /**
     * Resolves worker ID from environment or hardware.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>WORKER_ID environment variable</li>
     *   <li>HOSTNAME environment variable (hash if numeric pattern found)</li>
     *   <li>MAC address-based derivation</li>
     *   <li>Random fallback</li>
     * </ol>
     *
     * @return worker ID between 0 and MAX_WORKER_ID
     */
    private static long resolveWorkerId() {
        // 1. Check environment variable
        String envWorkerId = System.getenv("WORKER_ID");
        if (envWorkerId != null && !envWorkerId.isBlank()) {
            try {
                long id = Long.parseLong(envWorkerId.trim());
                if (id >= 0 && id <= MAX_WORKER_ID) {
                    log.info("Using WORKER_ID from environment: {}", id);
                    return id;
                }
                log.warn("WORKER_ID {} out of range, falling back to auto-detection", id);
            } catch (NumberFormatException e) {
                log.warn("Invalid WORKER_ID format: {}, falling back to auto-detection", envWorkerId);
            }
        }

        // 2. Try to extract from hostname (for Kubernetes stateful sets)
        String hostname = System.getenv("HOSTNAME");
        if (hostname != null) {
            // Pattern: pod-name-0, pod-name-1, etc.
            int lastDash = hostname.lastIndexOf('-');
            if (lastDash >= 0) {
                try {
                    long ordinal = Long.parseLong(hostname.substring(lastDash + 1));
                    if (ordinal >= 0 && ordinal <= MAX_WORKER_ID) {
                        log.info("Using worker ID from hostname ordinal: {}", ordinal);
                        return ordinal;
                    }
                } catch (NumberFormatException ignored) {
                    // Not a numeric suffix, continue to next method
                }
            }
        }

        // 3. Try MAC address-based derivation
        try {
            long macBasedId = deriveMacBasedWorkerId();
            log.info("Using MAC address-based worker ID: {}", macBasedId);
            return macBasedId;
        } catch (Exception e) {
            log.warn("Failed to derive worker ID from MAC address: {}", e.getMessage());
        }

        // 4. Fall back to random
        long randomId = new SecureRandom().nextInt((int) (MAX_WORKER_ID + 1));
        log.warn("Using random worker ID: {} - NOT RECOMMENDED for production!", randomId);
        return randomId;
    }

    /**
     * Derives a worker ID from the MAC address of the first network interface.
     *
     * @return worker ID derived from MAC address
     * @throws Exception if MAC address cannot be obtained
     */
    private static long deriveMacBasedWorkerId() throws Exception {
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface ni = networkInterfaces.nextElement();
            byte[] mac = ni.getHardwareAddress();
            if (mac != null && mac.length >= 2) {
                // Use last 2 bytes of MAC address, mask to 10 bits
                int id = ((mac[mac.length - 2] & 0xFF) << 8) | (mac[mac.length - 1] & 0xFF);
                return id & MAX_WORKER_ID;
            }
        }
        throw new RuntimeException("No network interface with MAC address found");
    }

    // ========================================================================
    // Time Utilities
    // ========================================================================

    /**
     * Returns current time in milliseconds since Unix epoch.
     *
     * <p>Separated method to allow testing with mock time.
     *
     * @return current timestamp in milliseconds
     */
    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * Waits until the next millisecond after the given timestamp.
     *
     * <p>Used when sequence overflows within a millisecond.
     *
     * @param lastTs last timestamp when sequence overflowed
     * @return next millisecond timestamp
     */
    private long waitNextMillis(long lastTs) {
        long timestamp = currentTimeMillis();
        while (timestamp <= lastTs) {
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }

    /**
     * Sleeps for the specified duration without throwing InterruptedException.
     *
     * @param millis milliseconds to sleep
     */
    private static void sleepUninterruptibly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ========================================================================
    // Accessors
    // ========================================================================

    /**
     * Returns the worker ID of this generator.
     *
     * @return worker ID (0-1023)
     */
    public long getWorkerId() {
        return workerId;
    }

    /**
     * Returns the custom epoch used by this generator.
     *
     * @return custom epoch in milliseconds since Unix epoch
     */
    public static long getCustomEpoch() {
        return CUSTOM_EPOCH;
    }
}
