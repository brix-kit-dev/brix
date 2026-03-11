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

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builder implementation for {@link DataAccessCapability.DataAccessRecord}
 *
 * <p>Provides a fluent API for constructing immutable DataAccessRecord instances.
 * This builder ensures all required fields are validated before building.</p>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * DataAccessRecord record = DataAccessRecord.builder()
 *     .operation(DataOperation.READ)
 *     .resourceType("booking")
 *     .resourceId("booking-123")
 *     .recordCount(50)
 *     .durationMs(125)
 *     .metadata("query", "findByStatus")
 *     .build();
 * }</pre>
 *
 * <p>[Data Access Record Builder]</p>
 * <p>Used to build immutable data access record objects with fluent API.</p>
 *
 * @author Runtime SDK Team
 * @since 3.1.0
 * @see DataAccessCapability.DataAccessRecord
 */
public class DataAccessRecordBuilder implements DataAccessCapability.DataAccessRecord.Builder {

    private DataAccessCapability.DataOperation operation;
    private String resourceType;
    private Set<String> resourceIds = new HashSet<>();
    private int recordCount;
    private Instant timestamp;
    private long durationMs;
    private Map<String, String> metadata = new HashMap<>();

    /**
     * Creates a new builder instance
     */
    public DataAccessRecordBuilder() {
        this.timestamp = Instant.now();
    }

    @Override
    public DataAccessCapability.DataAccessRecord.Builder operation(
            DataAccessCapability.DataOperation operation) {
        this.operation = Objects.requireNonNull(operation, "Operation must not be null");
        return this;
    }

    @Override
    public DataAccessCapability.DataAccessRecord.Builder resourceType(String resourceType) {
        this.resourceType = Objects.requireNonNull(resourceType, "Resource type must not be null");
        return this;
    }

    @Override
    public DataAccessCapability.DataAccessRecord.Builder resourceIds(Set<String> resourceIds) {
        if (resourceIds != null) {
            this.resourceIds = new HashSet<>(resourceIds);
        }
        return this;
    }

    @Override
    public DataAccessCapability.DataAccessRecord.Builder resourceId(String resourceId) {
        if (resourceId != null && !resourceId.isBlank()) {
            this.resourceIds.add(resourceId);
        }
        return this;
    }

    @Override
    public DataAccessCapability.DataAccessRecord.Builder recordCount(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Record count must be non-negative");
        }
        this.recordCount = count;
        return this;
    }

    @Override
    public DataAccessCapability.DataAccessRecord.Builder timestamp(Instant timestamp) {
        this.timestamp = Objects.requireNonNull(timestamp, "Timestamp must not be null");
        return this;
    }

    @Override
    public DataAccessCapability.DataAccessRecord.Builder durationMs(long durationMs) {
        if (durationMs < 0) {
            throw new IllegalArgumentException("Duration must be non-negative");
        }
        this.durationMs = durationMs;
        return this;
    }

    @Override
    public DataAccessCapability.DataAccessRecord.Builder metadata(String key, String value) {
        if (key != null && !key.isBlank() && value != null) {
            this.metadata.put(key, value);
        }
        return this;
    }

    @Override
    public DataAccessCapability.DataAccessRecord build() {
        // Validate required fields
        Objects.requireNonNull(operation, "Operation is required");
        Objects.requireNonNull(resourceType, "Resource type is required");

        return new ImmutableDataAccessRecord(
            operation,
            resourceType,
            Collections.unmodifiableSet(new HashSet<>(resourceIds)),
            recordCount,
            timestamp,
            durationMs,
            Collections.unmodifiableMap(new HashMap<>(metadata))
        );
    }

    // =========================================================================
    // Immutable Implementation
    // =========================================================================

    /**
     * Immutable implementation of DataAccessRecord
     *
     * <p>[Immutable Data Access Record Implementation]</p>
     * <p>All fields are immutable, guaranteeing thread safety.</p>
     */
    private static final class ImmutableDataAccessRecord 
            implements DataAccessCapability.DataAccessRecord {

        private final DataAccessCapability.DataOperation operation;
        private final String resourceType;
        private final Set<String> resourceIds;
        private final int recordCount;
        private final Instant timestamp;
        private final long durationMs;
        private final Map<String, String> metadata;

        ImmutableDataAccessRecord(
                DataAccessCapability.DataOperation operation,
                String resourceType,
                Set<String> resourceIds,
                int recordCount,
                Instant timestamp,
                long durationMs,
                Map<String, String> metadata) {
            this.operation = operation;
            this.resourceType = resourceType;
            this.resourceIds = resourceIds;
            this.recordCount = recordCount;
            this.timestamp = timestamp;
            this.durationMs = durationMs;
            this.metadata = metadata;
        }

        @Override
        public DataAccessCapability.DataOperation getOperation() {
            return operation;
        }

        @Override
        public String getResourceType() {
            return resourceType;
        }

        @Override
        public Set<String> getResourceIds() {
            return resourceIds;
        }

        @Override
        public int getRecordCount() {
            return recordCount;
        }

        @Override
        public Instant getTimestamp() {
            return timestamp;
        }

        @Override
        public long getDurationMs() {
            return durationMs;
        }

        @Override
        public Map<String, String> getMetadata() {
            return metadata;
        }

        @Override
        public String toString() {
            return String.format(
                "DataAccessRecord[operation=%s, resourceType=%s, recordCount=%d, durationMs=%d]",
                operation, resourceType, recordCount, durationMs
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ImmutableDataAccessRecord that = (ImmutableDataAccessRecord) o;
            return recordCount == that.recordCount &&
                   durationMs == that.durationMs &&
                   operation == that.operation &&
                   resourceType.equals(that.resourceType) &&
                   resourceIds.equals(that.resourceIds) &&
                   timestamp.equals(that.timestamp);
        }

        @Override
        public int hashCode() {
            return Objects.hash(operation, resourceType, resourceIds, 
                              recordCount, timestamp, durationMs);
        }
    }
}
