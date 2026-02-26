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
import java.util.Set;

/**
 * Data Access Capability Contract
 *
 * <p>Provides data access authorization and auditing capability for plugins.
 * This capability enables fine-grained data access control and compliance
 * auditing without plugins being aware of the underlying authorization system.</p>
 *
 * <h3>Core Responsibilities</h3>
 * <ul>
 *   <li>Verify data access authorization based on scopes</li>
 *   <li>Record data access for compliance auditing</li>
 *   <li>Support multi-tenant data isolation</li>
 *   <li>Enable row-level and field-level security</li>
 * </ul>
 *
 * <h3>Authorization Model</h3>
 * <p>Uses {@link DataScope} for fine-grained access control:</p>
 * <ul>
 *   <li>DEPARTMENT: Department-level isolation</li>
 *   <li>ORGANIZATION: Organization-level isolation</li>
 *   <li>REGION: Region-based access control</li>
 *   <li>SELF: User's own data only</li>
 *   <li>ALL: Full access</li>
 * </ul>
 *
 * <h3>Design Constraints</h3>
 * <ul>
 *   <li><b>Policy Transparent</b>: Plugins don't know the authorization policy details</li>
 *   <li><b>Audit Required</b>: All sensitive data access must be recorded</li>
 *   <li><b>Fail-Safe</b>: Default to deny if authorization cannot be determined</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Inject
 * private DataAccessCapability dataAccess;
 *
 * public List<Booking> findBookings(BookingQuery query) {
 *     // Check authorization before query
 *     DataScope scope = DataScope.department("sales");
 *     if (!dataAccess.isAuthorized(scope)) {
 *         throw new UnauthorizedException("No access to booking data");
 *     }
 *
 *     // Execute query
 *     List<Booking> bookings = repository.find(query);
 *
 *     // Audit data access
 *     dataAccess.auditAccess(DataAccessRecord.builder()
 *         .operation(DataOperation.READ)
 *         .resourceType("booking")
 *         .recordCount(bookings.size())
 *         .build());
 *
 *     return bookings;
 * }
 * }</pre>
 *
 * <h3>Implementation Notes</h3>
 * <p>This interface is implemented by the Host layer:</p>
 * <ul>
 *   <li>Standalone Host: Integration with RBAC/ABAC policy engine</li>
 *   <li>Embedded Host: Delegated to customer's authorization system</li>
 * </ul>
 *
 * <p>【数据访问能力契约】</p>
 * <p>提供数据访问授权和审计能力：</p>
 * <ul>
 *   <li>isAuthorized(scope): 检查当前用户是否有权访问指定数据范围</li>
 *   <li>getAuthorizedScopes(): 获取当前用户所有授权的数据范围</li>
 *   <li>auditAccess(record): 记录数据访问日志，用于合规审计</li>
 * </ul>
 *
 * @author Runtime SDK Team
 * @since 3.1.0
 * @see DataScope
 */
public interface DataAccessCapability {

    /**
     * Check if current principal is authorized to access the specified scope
     *
     * <p>Verifies whether the current authenticated user has permission to
     * access data within the given scope. This check considers:</p>
     * <ul>
     *   <li>User's roles and permissions</li>
     *   <li>Tenant membership</li>
     *   <li>Data ownership</li>
     *   <li>Time-based access rules</li>
     * </ul>
     *
     * <h4>Authorization Decision</h4>
     * <ul>
     *   <li>Returns {@code true} if access is explicitly granted</li>
     *   <li>Returns {@code false} if access is denied or cannot be determined</li>
     * </ul>
     *
     * @param scope The data scope to check authorization for
     * @return {@code true} if authorized, {@code false} otherwise
     * @throws IllegalArgumentException if scope is null
     */
    boolean isAuthorized(DataScope scope);

    /**
     * Check authorization for multiple scopes
     *
     * <p>Batch authorization check for scenarios requiring access to
     * multiple data scopes. More efficient than multiple single checks.</p>
     *
     * @param scopes The set of data scopes to check
     * @return {@code true} if ALL scopes are authorized, {@code false} otherwise
     * @throws IllegalArgumentException if scopes is null or empty
     */
    boolean isAuthorizedAll(Set<DataScope> scopes);

    /**
     * Check authorization for any of the given scopes
     *
     * <p>Returns true if the user is authorized for at least one
     * of the specified scopes.</p>
     *
     * @param scopes The set of data scopes to check
     * @return {@code true} if ANY scope is authorized, {@code false} otherwise
     * @throws IllegalArgumentException if scopes is null or empty
     */
    boolean isAuthorizedAny(Set<DataScope> scopes);

    /**
     * Get all authorized data scopes for current principal
     *
     * <p>Returns the complete set of data scopes that the current user
     * is authorized to access. Useful for building dynamic queries
     * that automatically filter data based on authorization.</p>
     *
     * @return Set of authorized scopes, never null (may be empty)
     */
    Set<DataScope> getAuthorizedScopes();

    /**
     * Get authorized scopes filtered by type
     *
     * <p>Returns authorized scopes of a specific type, useful for
     * scenarios where only certain scope types are relevant.</p>
     *
     * <p>Common scope types: DEPARTMENT, ORGANIZATION, REGION, SELF, ALL</p>
     *
     * @param scopeType The type of scopes to retrieve (e.g., "DEPARTMENT", "ORGANIZATION")
     * @return Set of authorized scopes of the specified type
     * @throws IllegalArgumentException if scopeType is null or blank
     */
    Set<DataScope> getAuthorizedScopes(String scopeType);

    /**
     * Record data access for auditing
     *
     * <p>Records a data access event for compliance and security auditing.
     * This should be called after any significant data operation.</p>
     *
     * <h4>Audit Record Contents</h4>
     * <ul>
     *   <li>Operation type (READ/CREATE/UPDATE/DELETE)</li>
     *   <li>Resource type and identifiers</li>
     *   <li>Number of records affected</li>
     *   <li>Timestamp and duration</li>
     *   <li>User and tenant context</li>
     * </ul>
     *
     * <h4>Asynchronous Processing</h4>
     * <p>Audit recording is typically asynchronous to avoid impacting
     * business operation performance.</p>
     *
     * @param record The data access record to audit
     * @throws IllegalArgumentException if record is null
     */
    void auditAccess(DataAccessRecord record);

    // =========================================================================
    // Inner Types
    // =========================================================================

    /**
     * Data Operation Type Enumeration
     *
     * <p>Defines the types of data operations for auditing.</p>
     */
    enum DataOperation {
        /**
         * Read/Query operation
         */
        READ,

        /**
         * Create/Insert operation
         */
        CREATE,

        /**
         * Update/Modify operation
         */
        UPDATE,

        /**
         * Delete/Remove operation
         */
        DELETE,

        /**
         * Export/Download operation
         */
        EXPORT
    }

    /**
     * Data Access Record for Auditing
     *
     * <p>Immutable record capturing details of a data access event.</p>
     *
     * <p>【数据访问记录】</p>
     * <p>用于合规审计的数据访问记录，包含操作类型、资源信息、影响记录数等。</p>
     */
    interface DataAccessRecord {

        /**
         * Get the operation type
         *
         * @return The data operation performed
         */
        DataOperation getOperation();

        /**
         * Get the resource type accessed
         *
         * @return Resource type identifier (e.g., "booking")
         */
        String getResourceType();

        /**
         * Get the specific resource identifiers accessed
         *
         * @return Set of resource IDs, may be empty for bulk operations
         */
        Set<String> getResourceIds();

        /**
         * Get the number of records affected
         *
         * @return Number of records read/created/updated/deleted
         */
        int getRecordCount();

        /**
         * Get the operation timestamp
         *
         * @return When the operation occurred
         */
        Instant getTimestamp();

        /**
         * Get operation duration in milliseconds
         *
         * @return Duration of the operation
         */
        long getDurationMs();

        /**
         * Get additional metadata
         *
         * @return Key-value metadata, may be empty
         */
        java.util.Map<String, String> getMetadata();

        /**
         * Builder for creating DataAccessRecord instances
         *
         * @return A new builder instance
         */
        static Builder builder() {
            return new DataAccessRecordBuilder();
        }

        /**
         * Builder interface for DataAccessRecord
         */
        interface Builder {
            Builder operation(DataOperation operation);
            Builder resourceType(String resourceType);
            Builder resourceIds(Set<String> resourceIds);
            Builder resourceId(String resourceId);
            Builder recordCount(int count);
            Builder timestamp(Instant timestamp);
            Builder durationMs(long durationMs);
            Builder metadata(String key, String value);
            DataAccessRecord build();
        }
    }
}
