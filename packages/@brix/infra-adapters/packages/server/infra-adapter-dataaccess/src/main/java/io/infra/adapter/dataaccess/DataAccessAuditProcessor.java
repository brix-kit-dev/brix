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
package io.infra.adapter.dataaccess;

import io.runtime.sdk.capability.DataAccessCapability.DataAccessRecord;

/**
 * Data Access Audit Processor Interface.
 *
 * <p>Defines the contract for processing data access audit records.
 * Implementations handle the actual persistence or forwarding of
 * audit records to external systems.</p>
 *
 * <h3>Processing Strategies</h3>
 * <ul>
 *   <li><b>Logging</b>: Write to structured logs (default, for ELK/Splunk)</li>
 *   <li><b>Database</b>: Persist to compliance audit table</li>
 *   <li><b>EventBus</b>: Publish as integration events for external processing</li>
 *   <li><b>Hybrid</b>: Combination of multiple strategies</li>
 * </ul>
 *
 * <h3>Non-blocking Contract</h3>
 * <p>Implementations must be non-blocking and should process records
 * asynchronously to avoid impacting business operation performance.</p>
 *
 * @author Brix Team
 * @version 3.1.0
 * @since 3.1.0
 */
public interface DataAccessAuditProcessor {

    /**
     * Processes a data access audit record.
     *
     * <p>This method must be non-blocking. Implementations should
     * queue the record for async processing if needed.</p>
     *
     * @param record    The data access record to process
     * @param principal The principal (user ID) who performed the access
     */
    void process(DataAccessRecord record, String principal);

    /**
     * Shuts down the processor, flushing any pending records.
     *
     * <p>Called during application shutdown to ensure all audit
     * records are persisted before the application terminates.</p>
     */
    void shutdown();

    /**
     * Returns the number of pending audit records in the queue.
     *
     * <p>Useful for monitoring and health checks.</p>
     *
     * @return Number of pending records
     */
    int getPendingCount();
}
