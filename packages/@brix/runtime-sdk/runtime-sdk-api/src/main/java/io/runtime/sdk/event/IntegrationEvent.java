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
import java.util.Objects;
import java.util.UUID;

import jakarta.annotation.Nonnull;

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
     * Minimum schema version the runtime SDK accepts on the consumer side.
     *
     * <p><b>Schema evolution protocol (red-line R3.6):</b></p>
     * <ul>
     *   <li><b>MAJOR (multiples of 10)</b>: Breaking change. EventBus consumers
     *       <b>must</b> reject events whose {@link #getSchemaVersion()} is
     *       below the producer's declared {@link SchemaVersion#minCompatible()}.
     *       Adapters log a WARN and skip the event &mdash; never throw, so a
     *       single poison message cannot stop the consumer thread.</li>
     *   <li><b>MINOR (1..9 between MAJORs)</b>: Additive only. Consumers must
     *       remain backward-compatible: unknown fields are ignored, missing
     *       optional fields fall back to documented defaults.</li>
     *   <li>Event classes <b>should</b> be annotated with {@link SchemaVersion}
     *       so the platform can enforce the protocol at build / publish /
     *       consume time without depending on per-instance values that
     *       producers can mutate.</li>
     * </ul>
     *
     * <p>This constant defines the floor of versions the SDK will deserialize.
     * It is intentionally {@code 1} so all historical events remain readable.
     * Future SDK majors may raise this value once a deprecation window has
     * elapsed.</p>
     *
     * @since 3.2.0
     */
    public static final int MIN_SUPPORTED_SCHEMA_VERSION = 1;

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
     * Distributed trace ID for correlating events with OpenTelemetry traces.
     *
     * <p>When set, allows linking event processing to the originating distributed
     * trace in observability tools (Jaeger, Zipkin, Grafana Tempo, etc.).
     * Automatically populated from MDC / OpenTelemetry context by the EventBus
     * adapter at publish time if not explicitly provided by the publisher.</p>
     *
     * <p>Format follows the W3C Trace Context specification (32-char hex string)
     * when sourced from OpenTelemetry, or falls back to a UUID when no active
     * trace context is available.</p>
     *
     * @since 3.2.0
     */
    @Nonnull
    private String traceId;

    /**
     * Tenant ID this event belongs to.
     *
     * <p>Identifies the tenant context in which the event was produced.
     * Automatically populated from {@code TenantContext} by the EventBus adapter
     * if not explicitly set by the publisher.  Consumers <b>must</b> use this
     * field to enforce tenant-level data isolation during event processing.</p>
     *
     * <p>When {@code tenantId} is {@code null} at publish time and
     * {@code TenantContext} has no active tenant, the EventBus adapter will
     * throw {@link IllegalArgumentException} to prevent cross-tenant data leaks.</p>
     *
     * @since 3.2.0
     */
    @Nonnull
    private String tenantId;

    /**
     * Schema version for consumer-side compatibility checking.
     *
     * <p>Consumers should inspect this field to decide whether they can
     * process the event.  When a breaking change is introduced to the event
     * payload, the schema version must be incremented so that older consumers
     * can skip or transform unsupported versions gracefully.</p>
     *
     * <p>Defaults to {@code 1} for all newly created events.</p>
     *
     * @since 3.2.0
     */
    private int schemaVersion = 1;

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
        this.sourceModule = Objects.requireNonNull(sourceModule, "sourceModule cannot be null");
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
        this.sourceModule = Objects.requireNonNull(sourceModule, "sourceModule cannot be null");
    }

    /**
     * Constructor with tenant ID.
     *
     * <p>Preferred constructor for tenant-aware event publishing.
     * If the caller does not know the tenant ID at construction time,
     * use {@link #IntegrationEvent(String)} and let the EventBus adapter
     * populate the tenant ID from {@code TenantContext} automatically.</p>
     *
     * @param sourceModule source module identifier, cannot be null
     * @param tenantId     tenant identifier, may be null (auto-populated by adapter)
     * @since 3.2.0
     */
    protected IntegrationEvent(String sourceModule, String tenantId) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = this.getClass().getName();
        this.timestamp = Instant.now();
        this.version = 1;
        this.sourceModule = Objects.requireNonNull(sourceModule, "sourceModule cannot be null");
        this.tenantId = tenantId;
    }

    /**
     * Constructor with version number and tenant ID.
     *
     * @param sourceModule source module identifier, cannot be null
     * @param version      event version number
     * @param tenantId     tenant identifier, may be null (auto-populated by adapter)
     * @since 3.2.0
     */
    protected IntegrationEvent(String sourceModule, int version, String tenantId) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = this.getClass().getName();
        this.timestamp = Instant.now();
        this.version = version;
        this.sourceModule = Objects.requireNonNull(sourceModule, "sourceModule cannot be null");
        this.tenantId = tenantId;
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

    /**
     * Get distributed trace ID.
     *
     * @return trace identifier for OpenTelemetry correlation
     * @since 3.2.0
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * Set distributed trace ID.
     *
     * <p>Typically called by the EventBus adapter to auto-populate from the
     * current OpenTelemetry span context or MDC at publish time.</p>
     *
     * @param traceId distributed trace identifier
     * @since 3.2.0
     */
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    /**
     * Set distributed trace ID (fluent API).
     *
     * @param traceId distributed trace identifier
     * @return current event instance (supports method chaining)
     * @since 3.2.0
     */
    public IntegrationEvent withTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    /**
     * Get tenant ID.
     *
     * @return tenant identifier, or {@code null} if not yet populated
     * @since 3.2.0
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Set tenant ID.
     *
     * <p>Typically called by the EventBus adapter to auto-populate
     * from {@code TenantContext} when the publisher did not set it explicitly.</p>
     *
     * @param tenantId tenant identifier
     * @since 3.2.0
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * Set tenant ID (fluent API).
     *
     * @param tenantId tenant identifier
     * @return current event instance (supports method chaining)
     * @since 3.2.0
     */
    public IntegrationEvent withTenantId(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    /**
     * Get schema version.
     *
     * @return schema version number (defaults to 1)
     * @since 3.2.0
     */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /**
     * Set schema version.
     *
     * @param schemaVersion schema version number, must be positive
     * @since 3.2.0
     */
    public void setSchemaVersion(int schemaVersion) {
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be >= 1, got: " + schemaVersion);
        }
        this.schemaVersion = schemaVersion;
    }

    /**
     * Set schema version (fluent API).
     *
     * @param schemaVersion schema version number, must be positive
     * @return current event instance (supports method chaining)
     * @since 3.2.0
     */
    public IntegrationEvent withSchemaVersion(int schemaVersion) {
        setSchemaVersion(schemaVersion);
        return this;
    }

    @Override
    public String toString() {
        return String.format(
                "%s[eventId=%s, sourceModule=%s, tenantId=%s, traceId=%s, routingKey=%s, schemaVersion=%d, timestamp=%s]",
                getClass().getSimpleName(), eventId, sourceModule, tenantId, traceId, getRoutingKey(),
                schemaVersion, timestamp);
    }
}
