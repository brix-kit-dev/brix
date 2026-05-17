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

/**
 * Interface for distributed unique ID generation.
 *
 * <p>This interface defines the contract for generating globally unique
 * identifiers suitable for distributed systems. Implementations must
 * guarantee uniqueness across all nodes and time.
 *
 * <h3>Design Requirements</h3>
 * <ul>
 *   <li><b>Uniqueness:</b> IDs must be globally unique across all instances</li>
 *   <li><b>Ordering:</b> IDs should be roughly time-ordered for efficient indexing</li>
 *   <li><b>Performance:</b> Generation must be lock-free and high-throughput</li>
 *   <li><b>Distribution:</b> Must work in distributed deployment scenarios</li>
 * </ul>
 *
 * <h3>ID Characteristics (for Snowflake Implementation)</h3>
 * <ul>
 *   <li>64-bit signed long integer</li>
 *   <li>Time-ordered (IDs generated later are larger)</li>
 *   <li>Node-aware (includes worker ID to prevent collisions)</li>
 *   <li>High throughput (~4 million IDs per second per node)</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Autowired
 * private IdGenerator idGenerator;
 *
 * public void createEntity() {
 *     Entity entity = new Entity();
 *     entity.setId(idGenerator.nextId());
 *     // ...
 * }
 * }</pre>
 *
 * <h3>Implementation Notes</h3>
 * <p>The default implementation uses Twitter's Snowflake algorithm.
 * For Kubernetes deployments, worker ID can be derived from pod ordinal
 * or stateful set index.
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see SnowflakeIdGenerator
 */
public interface IdGenerator {

    /**
     * Generates the next unique ID.
     *
     * <p>This method must be thread-safe and can be called concurrently
     * from multiple threads without external synchronization.
     *
     * <p><b>Guarantees:</b>
     * <ul>
     *   <li>Each call returns a unique value</li>
     *   <li>IDs are monotonically increasing within the same node</li>
     *   <li>IDs are globally unique across all nodes</li>
     * </ul>
     *
     * @return a globally unique 64-bit ID
     * @throws IdGenerationException if ID generation fails (e.g., clock moved backwards)
     */
    long nextId();

    /**
     * Generates the next unique ID as a String.
     *
     * <p>Convenience method that converts the numeric ID to its string
     * representation. Useful for JSON serialization or systems that
     * prefer string identifiers.
     *
     * @return a globally unique ID as string
     * @throws IdGenerationException if ID generation fails
     */
    default String nextIdAsString() {
        return String.valueOf(nextId());
    }

    /**
     * Parses the timestamp component from a generated ID.
     *
     * <p>For Snowflake IDs, this extracts the millisecond timestamp
     * embedded in the ID. Useful for debugging or analytics.
     *
     * @param id the generated ID to parse
     * @return the timestamp in milliseconds since epoch
     * @throws IllegalArgumentException if the ID format is invalid
     */
    long parseTimestamp(long id);

    /**
     * Parses the worker ID component from a generated ID.
     *
     * <p>For Snowflake IDs, this extracts the worker/node identifier
     * embedded in the ID. Useful for tracing which node generated an ID.
     *
     * @param id the generated ID to parse
     * @return the worker ID that generated this ID
     * @throws IllegalArgumentException if the ID format is invalid
     */
    long parseWorkerId(long id);

    /**
     * Exception thrown when ID generation fails.
     *
     * <p>This typically occurs when:
     * <ul>
     *   <li>System clock moved backwards (clock skew)</li>
     *   <li>Sequence overflow within the same millisecond</li>
     *   <li>Worker ID configuration is invalid</li>
     * </ul>
     */
    class IdGenerationException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an IdGenerationException with a message.
         *
         * @param message the detail message
         */
        public IdGenerationException(String message) {
            super(message);
        }

        /**
         * Constructs an IdGenerationException with a message and cause.
         *
         * @param message the detail message
         * @param cause the underlying cause
         */
        public IdGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
