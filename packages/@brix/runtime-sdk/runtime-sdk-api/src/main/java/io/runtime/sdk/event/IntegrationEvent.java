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
 * Integration Event Base Class
 * 
 * <p>Integration events are used for asynchronous communication across modules and systems,
 * serving as the core decoupling mechanism between modules.
 * Unlike DomainEvent, IntegrationEvent defines integration contracts requiring stricter version management.</p>
 * 
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><b>Contract Stability</b>: Event structure changes require version evolution</li>
 *   <li><b>Backward Compatibility</b>: New fields should have defaults, removed fields must be deprecated first</li>
 *   <li><b>Idempotent Consumption</b>: Consumers must support repeated consumption</li>
 * </ul>
 * 
 * <h3>Difference from DomainEvent</h3>
 * <table border="1">
 *   <tr><th>Feature</th><th>DomainEvent</th><th>IntegrationEvent</th></tr>
 *   <tr><td>Scope</td><td>Internal module</td><td>Cross-module/cross-system</td></tr>
 *   <tr><td>Semantics</td><td>Business semantics</td><td>Integration contract</td></tr>
 *   <tr><td>Version Management</td><td>Relaxed</td><td>Strict</td></tr>
 *   <tr><td>Schema Requirements</td><td>Optional</td><td>Must define</td></tr>
 * </table>
 * 
 * <h3>Event Routing</h3>
 * <p>Runtime Shell handles converting DomainEvent to IntegrationEvent and routing to subscribers.
 * Modules only need to declare publishes/subscribes in manifest, no routing details required.</p>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * public class UserCreatedIntegrationEvent extends IntegrationEvent {
 *     private final String userId;
 *     private final String email;
 *     
 *     public UserCreatedIntegrationEvent(String userId, String email) {
 *         super("user-service");  // Source service identifier
 *         this.userId = userId;
 *         this.email = email;
 *     }
 *     
 *     @Override
 *     public String getRoutingKey() {
 *         return "user." + userId;  // Used for event partitioning
 *     }
 * }
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see DomainEvent
 */
public abstract class IntegrationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique event identifier
     * 
     * <p>Used for idempotent consumption and event tracing, consumers should deduplicate using this ID</p>
     */
    private final String eventId;

    /**
     * Event type
     * 
     * <p>Fully qualified class name, used to determine target type during deserialization</p>
     */
    private final String eventType;

    /**
     * Event occurrence timestamp
     */
    private final Instant timestamp;

    /**
     * Event version number
     * 
     * <p>Used for schema evolution, consumers decide processing logic based on version</p>
     */
    private final int version;

    /**
     * Source module identifier
     * 
     * <p>Module ID that published this event, e.g., "brix-app-identity"</p>
     */
    private final String sourceModule;

    /**
     * Correlation ID
     * 
     * <p>Used for distributed tracing, spans the entire request chain</p>
     */
    private String correlationId;

    /**
     * Causation ID
     * 
     * <p>Event ID that triggered this event, used to build event causation chain</p>
     */
    private String causationId;

    /**
     * Constructor
     * 
     * @param sourceModule source module identifier, cannot be empty
     */
    protected IntegrationEvent(String sourceModule) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = this.getClass().getName();
        this.timestamp = Instant.now();
        this.version = 1;
        this.sourceModule = sourceModule;
    }

    /**
     * Constructor with version number
     * 
     * @param sourceModule source module identifier
     * @param version event version number
     */
    protected IntegrationEvent(String sourceModule, int version) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = this.getClass().getName();
        this.timestamp = Instant.now();
        this.version = version;
        this.sourceModule = sourceModule;
    }

    /**
     * Get routing key
     * 
     * <p>Used for message partitioning, ensures ordered consumption of related events.
     * Recommended format: {aggregateType}.{aggregateId}, e.g., "user.12345"</p>
     * 
     * @return routing key, cannot be null
     */
    public abstract String getRoutingKey();

    /**
     * Get target module list
     * 
     * <p>Optional: specifies target modules the event should route to.
     * If empty array is returned, Runtime Shell routes automatically based on subscriptions.</p>
     * 
     * @return array of target module IDs, defaults to empty (automatic routing)
     */
    public String[] getTargetModules() {
        return new String[0];
    }

    /**
     * Get event metadata
     * 
     * @return metadata map
     */
    public Map<String, String> getMetadata() {
        return Map.of();
    }

    // ==================== Getter/Setter Methods ====================

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int getVersion() {
        return version;
    }

    public String getSourceModule() {
        return sourceModule;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Set correlation ID
     * 
     * <p>Usually set automatically by Runtime Shell, modules can also set manually to correlate with specific requests</p>
     * 
     * @param correlationId correlation ID
     * @return current event instance (supports method chaining)
     */
    public IntegrationEvent withCorrelationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    public String getCausationId() {
        return causationId;
    }

    /**
     * Set causation ID
     * 
     * @param causationId event ID that triggered this event
     * @return current event instance
     */
    public IntegrationEvent withCausationId(String causationId) {
        this.causationId = causationId;
        return this;
    }

    @Override
    public String toString() {
        return String.format("%s[eventId=%s, sourceModule=%s, routingKey=%s, timestamp=%s]",
                getClass().getSimpleName(), eventId, sourceModule, getRoutingKey(), timestamp);
    }
}
