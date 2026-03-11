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
package io.infra.adapter.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbox Event Repository Interface.
 *
 * <p>Spring Data JPA repository, providing CRUD and batch operation capabilities for Outbox events.</p>
 *
 * <h3>Architecture Position</h3>
 * <p>
 * This interface belongs to the {@code infra-adapter-outbox} standalone module (Layer 2.5: Adapter Layer).
 * Implementation is auto-proxied by Spring Data JPA, no manual SQL writing required.
 * </p>
 *
 * <h3>Core Queries</h3>
 * <ul>
 *   <li>{@link #findPendingEvents(int)} - Query events pending to be sent (for scheduled task use)</li>
 *   <li>{@link #findRetryableEvents(int, int)} - Query retryable failed events</li>
 *   <li>{@link #markAsProcessing(List)} - Batch mark as processing (prevent concurrent duplicate processing)</li>
 *   <li>{@link #deleteCompletedBefore(Instant)} - Clean up completed historical events</li>
 * </ul>
 *
 * @author Brix Platform Authors
 * @since 3.0.0
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Find Outbox record by event ID.
     *
     * @param eventId event unique identifier
     * @return Optional wrapper of Outbox event
     */
    Optional<OutboxEvent> findByEventId(String eventId);

    /**
     * Check if specified event ID already exists (idempotency check).
     *
     * @param eventId event unique identifier
     * @return true if already exists
     */
    boolean existsByEventId(String eventId);

    /**
     * Query pending events (PENDING status), ordered by creation time ascending.
     *
     * <p>Used by {@link OutboxEventPublisher#processOutbox()} scheduled task.</p>
     *
     * @param limit maximum query count (batch size)
     * @return list of pending events
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PENDING' ORDER BY e.createdAt ASC LIMIT :limit")
    List<OutboxEvent> findPendingEvents(@Param("limit") int limit);

    /**
     * Query retryable failed events (FAILED status and not exceeding maximum retry count).
     *
     * <p>Used by {@link OutboxEventPublisher#retryFailedEvents()} scheduled task.</p>
     *
     * @param maxRetryCount maximum retry count limit
     * @param limit         maximum query count (batch size)
     * @return list of retryable events
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'FAILED' AND e.retryCount < :maxRetryCount ORDER BY e.createdAt ASC LIMIT :limit")
    List<OutboxEvent> findRetryableEvents(@Param("maxRetryCount") int maxRetryCount, @Param("limit") int limit);

    /**
     * Batch mark events as processing.
     *
     * <p>Uses optimistic lock semantics: only updates records with current status PENDING,
     * preventing multiple scheduled task instances from concurrently processing the same batch of events.</p>
     *
     * @param ids list of event IDs
     * @return actual number of records updated
     */
    @Modifying
    @Query("UPDATE OutboxEvent e SET e.status = 'PROCESSING' WHERE e.id IN :ids AND e.status = 'PENDING'")
    int markAsProcessing(@Param("ids") List<UUID> ids);

    /**
     * Delete completed events before specified time.
     *
     * <p>Used by {@link OutboxEventPublisher#cleanupOldEvents()} scheduled task,
     * prevents Outbox table from growing indefinitely.</p>
     *
     * @param before cutoff time (delete completed events before this time)
     * @return actual number of records deleted
     */
    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = 'COMPLETED' AND e.processedAt < :before")
    int deleteCompletedBefore(@Param("before") Instant before);

    /**
     * Count events by status (for monitoring and alerting).
     *
     * @param status event status
     * @return count of events with that status
     */
    long countByStatus(OutboxEvent.Status status);
}
