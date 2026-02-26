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
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.capability.IdGeneratorCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

/**
 * UUID-based ID Generator Implementation.
 *
 * <p>Fallback implementation of {@link IdGeneratorCapability} using Java's standard
 * UUID generation. This implementation is suitable for scenarios where:</p>
 * <ul>
 *   <li>No ordering requirement for generated IDs</li>
 *   <li>No cluster coordination is available for Snowflake worker IDs</li>
 *   <li>Simplicity is preferred over performance</li>
 *   <li>IDs need to be generated without any configuration</li>
 * </ul>
 *
 * <h3>UUID Characteristics</h3>
 * <table border="1">
 *   <tr><th>Property</th><th>Value</th></tr>
 *   <tr><td>Format</td><td>550e8400-e29b-41d4-a716-446655440000</td></tr>
 *   <tr><td>Length</td><td>36 characters (with hyphens)</td></tr>
 *   <tr><td>Uniqueness</td><td>Theoretically collision-free (2^122 combinations)</td></tr>
 *   <tr><td>Ordering</td><td>Random, not time-ordered</td></tr>
 *   <tr><td>Performance</td><td>~3 million/second (single thread)</td></tr>
 * </table>
 *
 * <h3>Compact UUID Mode</h3>
 * <p>When configured with {@code compactMode=true}, generates UUIDs without hyphens:</p>
 * <pre>550e8400e29b41d4a716446655440000</pre>
 *
 * <h3>Usage Scenarios</h3>
 * <ul>
 *   <li><b>Distributed Systems</b>: Zero coordination required between nodes</li>
 *   <li><b>Embedded Mode</b>: When running as SDK within customer's system</li>
 *   <li><b>Testing</b>: Simple setup without worker ID configuration</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * <pre>
 * brix:
 *   idgen:
 *     strategy: uuid
 *     uuid:
 *       compact: true  # Remove hyphens from UUID
 * </pre>
 *
 * @author Brix Team
 * @version 3.1.0
 * @since 3.1.0
 * @see IdGeneratorCapability
 * @see SnowflakeIdGenerator
 */
@Capability(
    type = IdGeneratorCapability.class,
    name = "uuid-idgen",
    description = "UUID-based ID generation fallback for scenarios without cluster coordination",
    level = CapabilityLevel.STANDARD
)
public class UuidIdGenerator implements IdGeneratorCapability {

    private static final Logger log = LoggerFactory.getLogger(UuidIdGenerator.class);

    /**
     * Whether to generate compact UUIDs without hyphens.
     */
    private final boolean compactMode;

    /**
     * Creates a UUID generator with default settings (non-compact mode).
     */
    public UuidIdGenerator() {
        this(false);
    }

    /**
     * Creates a UUID generator with configurable compact mode.
     *
     * @param compactMode If true, generates UUIDs without hyphens
     */
    public UuidIdGenerator(boolean compactMode) {
        this.compactMode = compactMode;
        log.info("[UUID] Initialized with compactMode={}", compactMode);
    }

    // =========================================================================
    // IdGeneratorCapability Implementation
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * <p>Generates a Version 4 (random) UUID. Version 4 UUIDs are generated
     * using a cryptographically strong random number generator.</p>
     *
     * @return A unique UUID string (e.g., "550e8400-e29b-41d4-a716-446655440000")
     */
    @Override
    public String generate() {
        return formatUuid(UUID.randomUUID());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Generates a UUID with a business prefix for identification.
     * Format: "{prefix}-{uuid}"</p>
     *
     * @param prefix The prefix to prepend (e.g., "BK" for booking)
     * @return A prefixed UUID (e.g., "BK-550e8400-e29b-41d4-a716-446655440000")
     * @throws IllegalArgumentException if prefix is null or empty
     */
    @Override
    public String generate(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            throw new IllegalArgumentException("Prefix must not be null or empty");
        }
        return prefix + "-" + formatUuid(UUID.randomUUID());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Generates multiple UUIDs in batch. Each UUID is independently
     * generated using the random number generator.</p>
     *
     * @param count The number of UUIDs to generate (must be positive)
     * @return A list of UUID strings
     * @throws IllegalArgumentException if count is less than 1
     */
    @Override
    public List<String> generateBatch(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Count must be at least 1, but got " + count);
        }

        List<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(formatUuid(UUID.randomUUID()));
        }
        return ids;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Generates multiple UUIDs with prefix in batch.</p>
     *
     * @param prefix The prefix to prepend to each UUID
     * @param count  The number of UUIDs to generate (must be positive)
     * @return A list of prefixed UUID strings
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
            ids.add(prefixWithSeparator + formatUuid(UUID.randomUUID()));
        }
        return ids;
    }

    // =========================================================================
    // Internal Methods
    // =========================================================================

    /**
     * Formats a UUID according to the compact mode setting.
     *
     * @param uuid The UUID to format
     * @return Formatted UUID string (with or without hyphens)
     */
    private String formatUuid(UUID uuid) {
        if (compactMode) {
            // Remove hyphens from UUID string
            return uuid.toString().replace("-", "");
        }
        return uuid.toString();
    }

    /**
     * Returns whether compact mode is enabled.
     *
     * @return true if generating UUIDs without hyphens
     */
    public boolean isCompactMode() {
        return compactMode;
    }

    /**
     * Returns a string representation for diagnostics.
     *
     * @return Diagnostic string
     */
    @Override
    public String toString() {
        return String.format("UuidIdGenerator[compactMode=%s]", compactMode);
    }
}
