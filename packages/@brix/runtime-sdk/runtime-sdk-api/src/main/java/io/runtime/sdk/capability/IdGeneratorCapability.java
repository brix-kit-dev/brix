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

import java.util.List;

import io.runtime.sdk.annotation.Since;

/**
 * ID Generator Capability Contract
 *
 * <p>Provides distributed unique ID generation capability for plugins.
 * Plugins use this interface to generate unique identifiers without knowing
 * the underlying implementation (Snowflake/UUID/Database Sequence).</p>
 *
 * <h3>Core Responsibilities</h3>
 * <ul>
 *   <li>Generate globally unique identifiers</li>
 *   <li>Support prefix-based ID generation for business categorization</li>
 *   <li>Support batch ID generation for performance optimization</li>
 *   <li>Ensure uniqueness across distributed systems</li>
 * </ul>
 *
 * <h3>ID Characteristics</h3>
 * <table border="1">
 *   <tr><th>Property</th><th>Guarantee</th></tr>
 *   <tr><td>Uniqueness</td><td>Globally unique across all nodes</td></tr>
 *   <tr><td>Ordering</td><td>Roughly time-ordered (Snowflake-based implementations)</td></tr>
 *   <tr><td>Length</td><td>Implementation-specific, typically 16-32 characters</td></tr>
 * </table>
 *
 * <h3>Design Constraints</h3>
 * <ul>
 *   <li><b>Infrastructure Transparent</b>: Plugins don't know if IDs are from Snowflake/UUID/Sequence</li>
 *   <li><b>Stateless</b>: No state dependency between ID generation calls</li>
 *   <li><b>Thread-Safe</b>: Safe for concurrent use from multiple threads</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Inject
 * private IdGeneratorCapability idGenerator;
 *
 * public Booking createBooking(BookingCommand command) {
 *     // Generate unique booking ID
 *     String bookingId = idGenerator.generate();
 *
 *     // Or with business prefix
 *     String prefixedId = idGenerator.generate("BK");
 *     // Result: "BK-1234567890123456"
 *
 *     return new Booking(bookingId, command);
 * }
 *
 * public List<OrderItem> createOrderItems(List<ItemCommand> commands) {
 *     // Batch generation for performance
 *     List<String> ids = idGenerator.generateBatch(commands.size());
 *     // ...
 * }
 * }</pre>
 *
 * <h3>Implementation Notes</h3>
 * <p>This interface is implemented by the Host layer:</p>
 * <ul>
 *   <li>Standalone Host: Snowflake algorithm with worker ID</li>
 *   <li>Embedded Host: UUID or customer-provided ID generator</li>
 * </ul>
 *
 * <p><b>ID Generation Capability Contract</b></p>
 * <p>Provides distributed unique ID generation capability. Plugins use this interface to generate unique identifiers:</p>
 * <ul>
 *   <li>generate(): Generate a single unique ID</li>
 *   <li>generate(prefix): Generate an ID with business prefix</li>
 *   <li>generateBatch(count): Batch generate IDs for performance optimization</li>
 * </ul>
 *
 * @author Runtime SDK Team
 * @since 3.1.0
 */
@Since("3.1.0")
public interface IdGeneratorCapability {

    /**
     * Generate a unique identifier
     *
     * <p>Generates a globally unique ID suitable for use as a primary key
     * or business identifier. The format depends on Host implementation.</p>
     *
     * <h4>ID Format Examples</h4>
     * <ul>
     *   <li>Snowflake: "1234567890123456789" (19 digits)</li>
     *   <li>UUID: "550e8400-e29b-41d4-a716-446655440000"</li>
     *   <li>ULID: "01ARZ3NDEKTSV4RRFFQ69G5FAV"</li>
     * </ul>
     *
     * @return A unique identifier string, never null or empty
     */
    String generate();

    /**
     * Generate a unique identifier with prefix
     *
     * <p>Generates a unique ID with a business-meaningful prefix for
     * easier identification and categorization.</p>
     *
     * <h4>Prefix Format</h4>
     * <p>The prefix is prepended to the ID with a separator (typically "-"):</p>
     * <pre>
     * generate("BK")  → "BK-1234567890123456"
     * generate("ORD") → "ORD-1234567890123456"
     * generate("USR") → "USR-1234567890123456"
     * </pre>
     *
     * @param prefix The prefix to prepend (typically 2-4 uppercase letters)
     * @return A unique identifier with prefix, never null or empty
     * @throws IllegalArgumentException if prefix is null or empty
     */
    String generate(String prefix);

    /**
     * Generate multiple unique identifiers in batch
     *
     * <p>Batch generation is more efficient than calling {@link #generate()}
     * multiple times, especially for Snowflake-based implementations.</p>
     *
     * <h4>Performance Consideration</h4>
     * <p>For large batches (>1000), consider splitting into smaller chunks
     * to avoid blocking other ID generation requests.</p>
     *
     * @param count The number of IDs to generate, must be positive
     * @return A list of unique identifiers, size equals count
     * @throws IllegalArgumentException if count is less than 1
     */
    List<String> generateBatch(int count);

    /**
     * Generate multiple unique identifiers with prefix in batch
     *
     * <p>Combines batch generation with prefix for business scenarios
     * that require multiple prefixed IDs.</p>
     *
     * @param prefix The prefix to prepend to each ID
     * @param count The number of IDs to generate, must be positive
     * @return A list of unique identifiers with prefix, size equals count
     * @throws IllegalArgumentException if prefix is null/empty or count is less than 1
     */
    List<String> generateBatch(String prefix, int count);
}
