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

import java.time.Instant;
import java.util.UUID;

import io.runtime.sdk.event.IntegrationEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Outbox Event Entity.
 *
 * <p>Used to implement the Outbox pattern, ensuring transactional consistency of event publishing.
 * Events are first written to the Outbox table, then sent to Kafka by an asynchronous task.</p>
 *
 * <h3>Architecture Position</h3>
 * <p>
 * This class belongs to the {@code infra-adapter-outbox} standalone module (Layer 2.5: Adapter Layer).
 * Outbox is a cross-infrastructure pattern (requires DB + MQ coordination), so it was separated from
 * {@code infra-adapter-kafka} to avoid introducing JPA dependencies into the Kafka adapter.
 * </p>
 *
 * <h3>Outbox Pattern Description</h3>
 * <p>The Outbox pattern solves distributed transaction problems:</p>
 * <ol>
 *   <li>Business operation and event writing to Outbox are completed in the same database transaction</li>
 *   <li>Background scheduled task reads unsent events from Outbox</li>
 *   <li>After successful send to Kafka, mark event as processed</li>
 * </ol>
 *
 * <h3>Table Structure</h3>
 * <pre>{@code
 * CREATE TABLE event_outbox (
 *     id UUID PRIMARY KEY,
 *     event_id VARCHAR(64) NOT NULL,
 *     event_type VARCHAR(255) NOT NULL,
 *     payload TEXT NOT NULL,
 *     topic VARCHAR(255) NOT NULL,
 *     routing_key VARCHAR(255),
 *     tenant_id VARCHAR(128) NOT NULL,
 *     trace_id VARCHAR(128) NOT NULL,
 *     schema_version INT NOT NULL,
 *     correlation_id VARCHAR(128),
 *     status VARCHAR(32) NOT NULL,
 *     created_at TIMESTAMP NOT NULL,
 *     processed_at TIMESTAMP,
 *     retry_count INT DEFAULT 0,
 *     error_message TEXT,
 *     source_module VARCHAR(128)
 * );
 * }</pre>
 *
 * @author Brix Platform Authors
 * @since 3.0.0
 */
@Entity
@Table(name = "event_outbox", indexes = {
        @Index(name = "idx_outbox_status", columnList = "status"),
        @Index(name = "idx_outbox_tenant_status", columnList = "tenant_id,status"),
        @Index(name = "idx_outbox_created", columnList = "created_at"),
        @Index(name = "idx_outbox_event_id", columnList = "event_id", unique = true)
})
public class OutboxEvent {

    /**
     * Event Status Enumeration.
     *
     * <p>Describes the status transition of Outbox events throughout their lifecycle:
     * {@code PENDING → PROCESSING → COMPLETED} or
     * {@code PENDING → PROCESSING → (retry) PENDING → ... → FAILED}</p>
     */
    public enum Status {
        /** Pending - waiting to be sent to Kafka */
        PENDING,

        /** Processing - currently being sent to Kafka */
        PROCESSING,

        /** Completed - successfully sent to Kafka */
        COMPLETED,

        /** Failed - send failed and could not be routed to DLQ */
        FAILED,

        /** Dead-lettered - exceeded maximum retry count and was routed to DLQ */
        DEAD_LETTERED
    }

    /** Primary key ID (database auto-generated UUID) */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Event unique identifier.
     *
     * <p>Corresponds to {@link IntegrationEvent#getEventId()}, used for idempotency checking.</p>
     */
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    /**
     * Event type.
     *
     * <p>Corresponds to {@link IntegrationEvent#getEventType()} (usually fully qualified class name)</p>
     */
    @Column(name = "event_type", nullable = false, length = 255)
    private String eventType;

    /** Event payload (serialized data in JSON format) */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    /** Target Kafka Topic */
    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    /** Routing key (used for Kafka Partition Key, ensures events with same routing key are ordered) */
    @Column(name = "routing_key", length = 255)
    private String routingKey;

    /** Tenant identifier required for cross-plugin tenant isolation. */
    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    /** Distributed trace identifier for observability correlation. */
    @Column(name = "trace_id", nullable = false, length = 128)
    private String traceId;

    /** Event schema version used by consumers for compatibility checks. */
    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    /** Optional correlation ID linking a workflow of integration events. */
    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    /** Current event status */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private Status status = Status.PENDING;

    /** Creation time (time when event was written to Outbox) */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Processing completion time (time of successful send or final failure) */
    @Column(name = "processed_at")
    private Instant processedAt;

    /** Retry count (cumulative count of send failures) */
    @Column(name = "retry_count")
    private int retryCount = 0;

    /** Error message (exception info from last send failure) */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** Source module ID (identifies which business module produced the event) */
    @Column(name = "source_module", length = 128)
    private String sourceModule;

    // ==================== Constructors ====================

    /**
     * JPA default constructor.
     *
     * <p>Only for JPA framework reflection calls, business code should use {@link #from(IntegrationEvent, String, String)} factory method.</p>
     */
    protected OutboxEvent() {
    }

    /**
     * Create Outbox record from integration event.
     *
     * <p>Factory method that extracts key information from IntegrationEvent to create Outbox entity.
     * Created with {@link Status#PENDING} status, waiting for scheduled task processing.</p>
     *
     * @param event   integration event (provides eventId, eventType, routingKey, sourceModule)
     * @param payload serialized JSON payload
     * @param topic   target Kafka Topic (resolved by EventTopicResolver)
     * @return Outbox event entity (PENDING status)
     */
    public static OutboxEvent from(IntegrationEvent event, String payload, String topic) {
        OutboxEvent outbox = new OutboxEvent();
        outbox.eventId = event.getEventId();
        outbox.eventType = event.getEventType();
        outbox.payload = payload;
        outbox.topic = topic;
        outbox.routingKey = event.getRoutingKey();
        outbox.sourceModule = event.getSourceModule();
        outbox.tenantId = event.getTenantId();
        outbox.traceId = event.getTraceId();
        outbox.schemaVersion = event.getSchemaVersion();
        outbox.correlationId = event.getCorrelationId();
        outbox.status = Status.PENDING;
        outbox.createdAt = Instant.now();
        return outbox;
    }

    // ==================== Business Methods (State Transitions) ====================

    /**
     * Mark as processing.
     *
     * <p>Called when scheduled task retrieves event from database and prepares to send.</p>
     */
    public void markProcessing() {
        this.status = Status.PROCESSING;
    }

    /**
     * Mark as completed.
     *
     * <p>Called after event is successfully sent to Kafka, also records processing completion time.</p>
     */
    public void markCompleted() {
        this.status = Status.COMPLETED;
        this.processedAt = Instant.now();
    }

    /**
     * Mark as failed.
     *
     * <p>Called when event exceeds maximum retry count and still cannot be sent, requires manual intervention.</p>
     *
     * @param errorMessage failure reason description
     */
    public void markFailed(String errorMessage) {
        this.status = Status.FAILED;
        this.errorMessage = errorMessage;
        this.processedAt = Instant.now();
    }

    /**
     * Mark as dead-lettered after successful DLQ routing.
     *
     * @param errorMessage failure reason description
     */
    public void markDeadLettered(String errorMessage) {
        this.status = Status.DEAD_LETTERED;
        this.errorMessage = errorMessage;
        this.processedAt = Instant.now();
    }

    /**
     * Increment retry count.
     *
     * <p>Called on each send failure, used to determine if maximum retry limit is reached.</p>
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }

    /**
     * Reset to pending status (for retry).
     *
     * <p>When send fails but maximum retry count is not exceeded, reset status to wait for next scheduled task processing.</p>
     */
    public void resetToPending() {
        this.status = Status.PENDING;
    }

    // ==================== Getters ====================

    public UUID getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public String getTopic() {
        return topic;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getTraceId() {
        return traceId;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getSourceModule() {
        return sourceModule;
    }

    @Override
    public String toString() {
        return "OutboxEvent{" +
                "id=" + id +
                ", eventId='" + eventId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", topic='" + topic + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", status=" + status +
                ", retryCount=" + retryCount +
                '}';
    }
}
