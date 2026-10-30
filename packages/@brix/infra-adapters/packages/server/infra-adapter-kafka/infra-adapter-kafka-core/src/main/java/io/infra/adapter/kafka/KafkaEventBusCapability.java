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
package io.infra.adapter.kafka;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxTransportException;
import io.runtime.orchestrator.tracing.MessageTracing;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.EventPublishException;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;
import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.EventReliability;
import io.runtime.sdk.event.IntegrationEvent;

/**
 * The host's direct event bus on Kafka, for BEST_EFFORT integration events (root BRX-RS-MESSAGE-019; Kafka design
 * §13 K2).
 * <p>An event published here leaves at once, outside any outbox, so it may be lost: it is written as a canonical
 * envelope marked BEST_EFFORT to the event type's topic {@code brix.event.<eventType>}, through the same sender the
 * outbox relay uses, which creates the topic before the first send. It remains for the modules that are not yet
 * runtime-managed plugins (Kafka design §5): an event type that a plugin manifest declares is refused, whatever its
 * reliability, because this bus takes the producer and tenant the event names. A declared type is published
 * through the event bus the declaring plugin's {@code PluginContext} gives it, which binds them (§14 K21). Domain
 * events stay inside their plugin (root §6.1) and are refused too.</p>
 * <p>Each event is sent in a PRODUCER span, a child of the caller's current span when one is recorded (§13 K8);
 * without a tracing adapter the envelope carries the event's own trace id.</p>
 * <p>The payload is the event's typed {@link IntegrationEvent#payload()}; an event written before typed payloads
 * existed is sent as it serializes. The tenant comes from the event or the caller's tenant context, as before, and
 * must be a numeric tenant id: the envelope is always tenant-scoped.</p>
 *
 * @author Brix Platform Authors
 * @since 3.0.0
 */
@Capability(
    type = EventBusCapability.class,
    name = "kafka-event-bus",
    description = "Apache Kafka-based Event Bus Capability Implementation",
    level = CapabilityLevel.CORE,
    aliases = {"eventBus", "kafkaEventBus"}
)
public class KafkaEventBusCapability implements EventBusCapability {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventBusCapability.class);

    /** How long a direct publish waits for the broker. */
    static final Duration PUBLISH_TIMEOUT = Duration.ofSeconds(10);

    private static final String METRIC_PUBLISHED_TOTAL = "brix.event.published.total";
    private static final String METRIC_PUBLISH_ERROR = "brix.event.publish.error";
    private static final String METRIC_PUBLISH_DURATION = "brix.event.publish.duration";
    private static final Pattern TRACE_ID = Pattern.compile("[0-9a-f]{32}");
    private static final String INVALID_TRACE_ID = "0".repeat(32);

    private final CanonicalKafkaOutboxTransport sender;
    private final ObjectMapper objectMapper;
    private final String currentModuleId;
    private final Supplier<Optional<String>> tenantIdProvider;
    private final Function<String, Optional<EventReliability>> declaredReliability;
    private final MeterRegistry meterRegistry;
    private final MessageTracing tracing;

    /**
     * Creates the direct bus.
     *
     * @param kafkaTemplate Kafka template
     * @param topics topic names
     * @param broker the broker's reachability and topics
     * @param objectMapper writes payloads and envelopes
     * @param currentModuleId producer id used when an event names no source module
     * @param tenantIdProvider the caller's tenant, for events that carry none; may be {@code null}
     * @param declaredReliability the reliability a plugin manifest declares for an event type
     * @param meterRegistry publish metrics; may be {@code null}
     * @param tracing the PRODUCER span each event is sent in
     */
    public KafkaEventBusCapability(
            KafkaTemplate<String, String> kafkaTemplate,
            KafkaEventTopics topics,
            KafkaEventBroker broker,
            ObjectMapper objectMapper,
            String currentModuleId,
            Supplier<Optional<String>> tenantIdProvider,
            Function<String, Optional<EventReliability>> declaredReliability,
            MeterRegistry meterRegistry,
            MessageTracing tracing) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.sender = new CanonicalKafkaOutboxTransport(kafkaTemplate, topics, broker, objectMapper, PUBLISH_TIMEOUT);
        this.currentModuleId = Objects.requireNonNull(currentModuleId, "currentModuleId cannot be null");
        this.tenantIdProvider = tenantIdProvider != null ? tenantIdProvider : Optional::empty;
        this.declaredReliability = Objects.requireNonNull(declaredReliability, "declaredReliability cannot be null");
        this.meterRegistry = meterRegistry;
        this.tracing = Objects.requireNonNull(tracing, "tracing cannot be null");
    }

    /**
     * Refuses a domain event: domain events stay inside their plugin (root §6.1).
     *
     * @param event the domain event
     * @throws EventPublishException always
     */
    @Override
    public void publish(DomainEvent event) {
        Objects.requireNonNull(event, "Domain event cannot be null");
        throw new EventPublishException(event.getEventId(),
            "Domain events stay inside their plugin; Kafka carries integration events only (root §6.1)", null);
    }

    /**
     * Publishes a BEST_EFFORT integration event to its event type's topic.
     *
     * @param event the integration event
     * @throws IllegalArgumentException when neither the event nor the caller's context names a tenant
     * @throws EventPublishException when a manifest declares the type CRITICAL or STANDARD, when the event's tenant is
     *     not a numeric tenant id, or when the broker does not take the event
     */
    @Override
    public void publishIntegration(IntegrationEvent event) {
        Objects.requireNonNull(event, "Integration event cannot be null");
        IntegrationEventMetadataSupport.enrich(event, tenantIdProvider);
        String eventType = event.eventTypeId();
        Optional<EventReliability> declared = declaredReliability.apply(eventType);
        if (declared.isPresent()) {
            // The direct bus takes the producer and tenant the event names; a type a plugin declares goes through
            // that plugin's own bus, which binds them (multi-tenant §10; Kafka design §14 K21).
            throw new EventPublishException(event.getEventId(), eventType + " is declared " + declared.get()
                + " by a plugin manifest: publish it through the event bus that plugin's PluginContext gives it,"
                + " not the host's direct bus", null);
        }
        CanonicalOutboxMessage message = envelope(event, eventType);
        Timer.Sample sample = meterRegistry != null ? Timer.start(meterRegistry) : null;
        try (MessageTracing.MessageSpan span = tracing.producer(message)) {
            // Without a recorded span the event still carries its own trace id (root BRX-RS-OTEL-002).
            CanonicalOutboxMessage traced = span.traceparent() == null
                ? message.withTraceContext(traceparent(event.getTraceId()), null)
                : message.withTraceContext(span.traceparent(), span.tracestate());
            try {
                sender.publish(traced);
            } catch (OutboxTransportException failure) {
                span.failed(failure);
                recordPublishError(eventType, failure);
                throw new EventPublishException(event.getEventId(), "Integration event publishing failed: eventId="
                    + event.getEventId() + ", type=" + eventType + ", code=" + failure.errorCode(), failure);
            }
        }
        recordPublishSuccess(eventType, message.producerPluginId(), sample);
        log.info("event=event.published_direct type={} event_id={} producer={} reliability=BEST_EFFORT",
            eventType, event.getEventId(), message.producerPluginId());
    }

    private CanonicalOutboxMessage envelope(IntegrationEvent event, String eventType) {
        String eventId = event.getEventId();
        Long tenantId = tenant(event);
        try {
            return new CanonicalOutboxMessage(
                eventId,
                eventId,
                "EVENT",
                eventType,
                event.getSchemaVersion() + ".0.0",
                EventReliability.BEST_EFFORT.name(),
                textOr(event.getSourceModule(), currentModuleId),
                "TENANT",
                tenantId,
                textOr(event.getRoutingKey(), eventId),
                OffsetDateTime.ofInstant(event.getTimestamp(), ZoneOffset.UTC),
                textOr(event.getCorrelationId(), eventId),
                event.getCausationId(),
                null,
                null,
                payload(event),
                0);
        } catch (IllegalArgumentException invalid) {
            throw new EventPublishException(eventId, "Integration event is not a valid envelope: " + invalid.getMessage(),
                invalid);
        }
    }

    private static Long tenant(IntegrationEvent event) {
        String tenantId = event.getTenantId();
        try {
            return Long.valueOf(tenantId.trim());
        } catch (NumberFormatException notATenant) {
            throw new EventPublishException(event.getEventId(),
                "Integration event tenant '" + tenantId + "' is not a numeric tenant id", notATenant);
        }
    }

    /** The typed payload when the event defines one, else the event as legacy publishers serialized it. */
    private String payload(IntegrationEvent event) {
        Object payload;
        try {
            payload = event.payload();
        } catch (UnsupportedOperationException legacy) {
            payload = event;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new EventSerializationException("Event serialization failed: " + event.getClass().getName(), e);
        }
    }

    /**
     * A W3C traceparent continuing the event's trace with a new span, or {@code null} without a valid trace id. A
     * trace id in UUID form, which events get when no trace is active, is read without its dashes.
     */
    private static String traceparent(String eventTraceId) {
        String traceId = eventTraceId == null ? null : eventTraceId.replace("-", "").toLowerCase(Locale.ROOT);
        if (traceId == null || !TRACE_ID.matcher(traceId).matches() || INVALID_TRACE_ID.equals(traceId)) {
            return null;
        }
        byte[] span = new byte[8];
        ThreadLocalRandom.current().nextBytes(span);
        span[0] |= 1;
        return "00-" + traceId + "-" + HexFormat.of().formatHex(span) + "-01";
    }

    private static String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void recordPublishSuccess(String eventType, String sourceModule, Timer.Sample sample) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(METRIC_PUBLISHED_TOTAL, "eventType", eventType, "sourceModule", sourceModule).increment();
        if (sample != null) {
            sample.stop(meterRegistry.timer(METRIC_PUBLISH_DURATION, "eventType", eventType));
        }
    }

    private void recordPublishError(String eventType, OutboxTransportException failure) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(METRIC_PUBLISH_ERROR, "eventType", eventType, "code", failure.errorCode()).increment();
    }
}
