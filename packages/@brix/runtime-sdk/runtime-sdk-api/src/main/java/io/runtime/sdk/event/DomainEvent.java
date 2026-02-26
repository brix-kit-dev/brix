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
package io.runtime.sdk.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Domain Event Base Class
 * 
 * <p>Domain events represent business events occurring within a module, used for event-driven
 * architecture within modules. All domain events must extend this class and follow these rules:</p>
 * 
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><b>Immutability</b>: Events cannot be modified once created</li>
 *   <li><b>Self-describing</b>: Events contain complete contextual information</li>
 *   <li><b>Serializable</b>: Support JSON serialization for transport</li>
 * </ul>
 * 
 * <h3>Naming Convention</h3>
 * <ul>
 *   <li>Class name format: {AggregateRoot}{Action}Event, e.g., ReservationCreatedEvent</li>
 *   <li>Use past tense to indicate events that have occurred</li>
 * </ul>
 * 
 * <h3>Difference from IntegrationEvent</h3>
 * <ul>
 *   <li>DomainEvent: Internal module events, business semantics</li>
 *   <li>IntegrationEvent: Cross-module/cross-system events, integration contracts</li>
 * </ul>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * public class ReservationCreatedEvent extends DomainEvent {
 *     private final String reservationId;
 *     private final String customerId;
 *     
 *     public ReservationCreatedEvent(String reservationId, String customerId) {
 *         super();
 *         this.reservationId = reservationId;
 *         this.customerId = customerId;
 *     }
 *     
 *     @Override
 *     public String getAggregateId() {
 *         return reservationId;
 *     }
 *     
 *     @Override
 *     public String getAggregateType() {
 *         return "Reservation";
 *     }
 * }
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see IntegrationEvent
 */
public abstract class DomainEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique event identifier
     * 
     * <p>System-generated UUID used for:</p>
     * <ul>
     *   <li>Event deduplication (idempotent processing)</li>
     *   <li>Event tracing</li>
     *   <li>Audit logging</li>
     * </ul>
     */
    private final String eventId;

    /**
     * Event type
     * 
     * <p>Defaults to fully qualified class name, e.g., io.brix.app.booking.event.ReservationCreatedEvent</p>
     */
    private final String eventType;

    /**
     * Event occurrence timestamp
     * 
     * <p>Uses UTC time, precise to milliseconds</p>
     */
    private final Instant timestamp;

    /**
     * Event version number
     * 
     * <p>Used for event schema evolution, defaults to 1</p>
     */
    private final int version;

    /**
     * Default constructor
     * 
     * <p>Auto-generates eventId and timestamp</p>
     */
    protected DomainEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = this.getClass().getName();
        this.timestamp = Instant.now();
        this.version = 1;
    }

    /**
     * Constructor with version number
     * 
     * @param version event version number, used for schema evolution
     */
    protected DomainEvent(int version) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = this.getClass().getName();
        this.timestamp = Instant.now();
        this.version = version;
    }

    /**
     * Get aggregate root ID
     * 
     * <p>Used for event partitioning (Partition Key), ensures ordered processing of events
     * from the same aggregate root</p>
     * 
     * @return unique aggregate root identifier, cannot be null
     */
    public abstract String getAggregateId();

    /**
     * Get aggregate root type
     * 
     * <p>e.g., "Reservation", "User", "Contract"</p>
     * 
     * @return aggregate root type name, cannot be null
     */
    public abstract String getAggregateType();

    /**
     * Get event metadata
     * 
     * <p>Optional additional information, such as:</p>
     * <ul>
     *   <li>correlationId: Correlation ID for request chain tracing</li>
     *   <li>causationId: Causation ID identifying the triggering event</li>
     *   <li>userId: Operating user ID</li>
     *   <li>tenantId: Tenant ID</li>
     * </ul>
     * 
     * @return metadata map, returns empty Map by default
     */
    public Map<String, String> getMetadata() {
        return Map.of();
    }

    // ==================== Getter Methods ====================

    /**
     * Get unique event identifier
     * 
     * @return event ID in UUID format
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * Get event type
     * 
     * @return fully qualified event class name
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * Get event occurrence time
     * 
     * @return UTC timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Get event version number
     * 
     * @return version number, defaults to 1
     */
    public int getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return String.format("%s[eventId=%s, aggregateId=%s, timestamp=%s]",
                getClass().getSimpleName(), eventId, getAggregateId(), timestamp);
    }
}
