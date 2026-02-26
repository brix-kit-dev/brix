/*
 * Copyright 2026 Brix Authors
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
package io.infra.adapter.idgen;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.capability.IdGeneratorCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

/**
 * Snowflake ID Generator Implementation.
 *
 * <p>Production-grade implementation of {@link IdGeneratorCapability} using the Twitter
 * Snowflake algorithm for generating 64-bit unique IDs. This implementation is designed
 * for high-performance distributed systems requiring ordered, collision-free identifiers.</p>
 *
 * <h3>Snowflake ID Structure (64 bits)</h3>
 * <pre>
 * +------------------------------------------------------------------+
 * | 1 bit  |         41 bits          | 5 bits  | 5 bits  | 12 bits |
 * +--------+--------------------------+---------+---------+---------+
 * | unused |   timestamp (ms)         | dc id   | worker  | sequence|
 * +------------------------------------------------------------------+
 * </pre>
 *
 * <ul>
 *   <li>1 bit: Sign bit (always 0 for positive numbers)</li>
 *   <li>41 bits: Timestamp in milliseconds since custom epoch (supports ~69 years)</li>
 *   <li>5 bits: Datacenter ID (0-31)</li>
 *   <li>5 bits: Worker ID (0-31)</li>
 *   <li>12 bits: Sequence number within millisecond (0-4095, supports 4096 IDs/ms)</li>
 * </ul>
 *
 * <h3>Performance Characteristics</h3>
 * <ul>
 *   <li>Single node: ~4 million IDs per second theoretical maximum</li>
 *   <li>Cluster: Linear scalability with number of workers</li>
 *   <li>Thread-safe: Uses synchronized blocks for sequence generation</li>
 * </ul>
 *
 * <h3>Clock Skew Handling</h3>
 * <p>This implementation detects clock drift and throws an exception if the system
 * clock moves backwards. In production environments, use NTP with proper configuration
 * to minimize clock skew.</p>
 *
 * <h3>Configuration</h3>
 * <pre>
 * brix:
 *   idgen:
 *     strategy: snowflake
 *     snowflake:
 *       worker-id: 1       # 0-31
 *       datacenter-id: 1   # 0-31
 *       epoch: 1704067200000  # Custom epoch (default: 2024-01-01)
 * </pre>
 *
 * @author Brix Team
 * @version 3.1.0
 * @since 3.1.0
 * @see IdGeneratorCapability
 */
@Capability(
    type = IdGeneratorCapability.class,
    name = "snowflake-idgen",
    description = "Twitter Snowflake algorithm implementation for distributed unique ID generation",
    level = CapabilityLevel.STANDARD
)
public class SnowflakeIdGenerator implements IdGeneratorCapability {

    private static final Logger log = LoggerFactory.getLogger(SnowflakeIdGenerator.class);

    // =========================================================================
    // Snowflake Bit Allocation Constants
    // =========================================================================

    /**
     * Number of bits allocated for worker ID.
     * Supports 32 workers (0-31) per datacenter.
     */
    private static final long WORKER_ID_BITS = 5L;

    /**
     * Number of bits allocated for datacenter ID.
     * Supports 32 datacenters (0-31).
     */
    private static final long DATACENTER_ID_BITS = 5L;

    /**
     * Number of bits allocated for sequence number.
     * Supports 4096 IDs per millisecond per worker.
     */
    private static final long SEQUENCE_BITS = 12L;

    /**
     * Maximum worker ID value (31).
     */
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);

    /**
     * Maximum datacenter ID value (31).
     */
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

    /**
     * Mask for extracting sequence number (4095).
     */
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    // =========================================================================
    // Bit Shift Positions
    // =========================================================================

    /**
     * Left shift for worker ID (12 bits for sequence).
     */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    /**
     * Left shift for datacenter ID (12 + 5 = 17 bits).
     */
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    /**
     * Left shift for timestamp (12 + 5 + 5 = 22 bits).
     */
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    // =========================================================================
    // Instance Fields
    // =========================================================================

    /**
     * Custom epoch timestamp in milliseconds.
     * Default: 2024-01-01 00:00:00 UTC (1704067200000L)
     */
    private final long epoch;

    /**
     * Worker ID (0-31).
     */
    private final long workerId;

    /**
     * Datacenter ID (0-31).
     */
    private final long datacenterId;

    /**
     * Current sequence number within the millisecond.
     */
    private long sequence = 0L;

    /**
     * Last timestamp when ID was generated.
     */
    private long lastTimestamp = -1L;

    /**
     * Lock object for thread-safe ID generation.
     */
    private final Object lock = new Object();

    // =========================================================================
    // Constructors
    // =========================================================================

    /**
     * Creates a new SnowflakeIdGenerator with default epoch.
     *
     * @param workerId     Worker ID (0-31)
     * @param datacenterId Datacenter ID (0-31)
     * @throws IllegalArgumentException if workerId or datacenterId is out of range
     */
    public SnowflakeIdGenerator(long workerId, long datacenterId) {
        this(workerId, datacenterId, 1704067200000L); // 2024-01-01 00:00:00 UTC
    }

    /**
     * Creates a new SnowflakeIdGenerator with custom epoch.
     *
     * @param workerId     Worker ID (0-31)
     * @param datacenterId Datacenter ID (0-31)
     * @param epoch        Custom epoch timestamp in milliseconds
     * @throws IllegalArgumentException if workerId or datacenterId is out of range
     */
    public SnowflakeIdGenerator(long workerId, long datacenterId, long epoch) {
        // Validate worker ID
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException(String.format(
                "Worker ID must be between 0 and %d, but got %d", MAX_WORKER_ID, workerId));
        }

        // Validate datacenter ID
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException(String.format(
                "Datacenter ID must be between 0 and %d, but got %d", MAX_DATACENTER_ID, datacenterId));
        }

        this.workerId = workerId;
        this.datacenterId = datacenterId;
        this.epoch = epoch;

        log.info("[Snowflake] Initialized with workerId={}, datacenterId={}, epoch={}",
            workerId, datacenterId, epoch);
    }

    // =========================================================================
    // IdGeneratorCapability Implementation
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * <p>Generates a globally unique 64-bit ID using the Snowflake algorithm.
     * The returned ID is a string representation of the numeric ID for
     * compatibility with systems that don't support 64-bit integers.</p>
     *
     * @return A unique identifier string (e.g., "1234567890123456789")
     */
    @Override
    public String generate() {
        return String.valueOf(nextId());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Generates a unique ID with a business prefix for easier identification.
     * Format: "{prefix}-{snowflake_id}"</p>
     *
     * @param prefix The prefix to prepend (e.g., "BK" for booking)
     * @return A prefixed unique identifier (e.g., "BK-1234567890123456789")
     * @throws IllegalArgumentException if prefix is null or empty
     */
    @Override
    public String generate(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            throw new IllegalArgumentException("Prefix must not be null or empty");
        }
        return prefix + "-" + nextId();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Generates multiple unique IDs in batch for performance optimization.
     * All IDs are generated atomically to ensure no duplicates.</p>
     *
     * @param count The number of IDs to generate (must be positive)
     * @return A list of unique identifier strings
     * @throws IllegalArgumentException if count is less than 1
     */
    @Override
    public List<String> generateBatch(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Count must be at least 1, but got " + count);
        }

        List<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(String.valueOf(nextId()));
        }
        return ids;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Generates multiple unique IDs with prefix in batch.</p>
     *
     * @param prefix The prefix to prepend to each ID
     * @param count  The number of IDs to generate (must be positive)
     * @return A list of prefixed unique identifier strings
     * @throws IllegalArgumentException if prefix is null/empty or count is less than 1
     */
    @Override
    public List<String> generateBatch(String prefix, int count) {
        if (prefix == null || prefix.isEmpty()) {
            throw new IllegalArgumentException("Prefix must not be null or empty");
        }
        if (count < 1) {
            throw new IllegalArgumentException("Count must be at least 1, but got " + count);
        }

        List<String> ids = new ArrayList<>(count);
        String prefixWithSeparator = prefix + "-";
        for (int i = 0; i < count; i++) {
            ids.add(prefixWithSeparator + nextId());
        }
        return ids;
    }

    // =========================================================================
    // Core Snowflake Algorithm
    // =========================================================================

    /**
     * Generates the next unique 64-bit Snowflake ID.
     *
     * <p>This method is thread-safe and handles:</p>
     * <ul>
     *   <li>Sequence overflow within millisecond (waits for next ms)</li>
     *   <li>Clock drift detection (throws exception if clock moves backward)</li>
     *   <li>Concurrent access via synchronized block</li>
     * </ul>
     *
     * @return A unique 64-bit ID
     * @throws IllegalStateException if clock moved backwards
     */
    protected long nextId() {
        synchronized (lock) {
            long timestamp = currentTimeMillis();

            // Handle clock skew - clock moved backwards
            if (timestamp < lastTimestamp) {
                long offset = lastTimestamp - timestamp;
                log.error("[Snowflake] Clock moved backwards by {} ms", offset);
                throw new IllegalStateException(String.format(
                    "Clock moved backwards. Refusing to generate ID for %d milliseconds", offset));
            }

            // Same millisecond - increment sequence
            if (timestamp == lastTimestamp) {
                sequence = (sequence + 1) & SEQUENCE_MASK;

                // Sequence overflow - wait for next millisecond
                if (sequence == 0) {
                    timestamp = waitNextMillis(lastTimestamp);
                }
            } else {
                // New millisecond - reset sequence
                sequence = 0L;
            }

            lastTimestamp = timestamp;

            // Compose the 64-bit ID
            // Structure: [timestamp][datacenter][worker][sequence]
            return ((timestamp - epoch) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
        }
    }

    /**
     * Waits until the next millisecond.
     *
     * <p>This method is called when the sequence number overflows within
     * a single millisecond (more than 4096 IDs generated in 1ms).</p>
     *
     * @param lastTimestamp The timestamp to wait past
     * @return The new timestamp (guaranteed > lastTimestamp)
     */
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }

    /**
     * Returns the current time in milliseconds.
     *
     * <p>This method can be overridden in tests to control time behavior.</p>
     *
     * @return Current timestamp in milliseconds
     */
    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    // =========================================================================
    // Diagnostic Methods
    // =========================================================================

    /**
     * Gets the worker ID of this generator.
     *
     * @return Worker ID (0-31)
     */
    public long getWorkerId() {
        return workerId;
    }

    /**
     * Gets the datacenter ID of this generator.
     *
     * @return Datacenter ID (0-31)
     */
    public long getDatacenterId() {
        return datacenterId;
    }

    /**
     * Gets the custom epoch used by this generator.
     *
     * @return Epoch timestamp in milliseconds
     */
    public long getEpoch() {
        return epoch;
    }

    /**
     * Returns a string representation of this generator for diagnostics.
     *
     * @return Diagnostic string
     */
    @Override
    public String toString() {
        return String.format("SnowflakeIdGenerator[workerId=%d, datacenterId=%d, epoch=%d]",
            workerId, datacenterId, epoch);
    }
}
