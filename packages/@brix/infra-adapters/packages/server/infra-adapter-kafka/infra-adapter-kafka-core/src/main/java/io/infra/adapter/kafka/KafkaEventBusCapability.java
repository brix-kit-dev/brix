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

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.EventPublishException;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;
import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.IntegrationEvent;

/**
 * Kafka-based Event Bus Capability Implementation.
 * 
 * <p>This class implements {@link EventBusCapability} as a Full Product Host implementation,
 * providing event publishing capabilities based on Apache Kafka. Modules publish events through
 * this implementation without being aware of Kafka's existence.</p>
 * 
 * <h3>Core Features</h3>
 * <ul>
 *   <li><b>Event Routing</b>: Automatically resolves Topics based on {@link EventTopicResolver}</li>
 *   <li><b>Message Ordering</b>: Uses aggregateId/eventId as partition key to ensure ordering</li>
 *   <li><b>Event Tracing</b>: Automatically adds tracing Headers (traceId, spanId)</li>
 *   <li><b>Serialization</b>: Unified JSON format</li>
 * </ul>
 * 
 * <h3>Message Format</h3>
 * <p>All event messages contain the following Headers:</p>
 * <ul>
 *   <li>eventId - Event unique identifier</li>
 *   <li>eventType - Event type (fully qualified class name)</li>
 *   <li>timestamp - Event timestamp</li>
 *   <li>sourceModule - Source module ID</li>
 *   <li>traceId - Trace ID (if available)</li>
 * </ul>
 * 
 * <h3>Thread Safety</h3>
 * <p>This class is thread-safe and can be used concurrently by multiple threads.</p>
 * 
 * @author Brix Platform Authors Platform Team
 * @since 3.0.0
 * @see EventBusCapability
 * @see EventTopicResolver
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

    /**
     * Event ID Header name.
     */
    private static final String HEADER_EVENT_ID = "eventId";

    /**
     * Event type Header name.
     */
    private static final String HEADER_EVENT_TYPE = "eventType";

    /**
     * Timestamp Header name.
     */
    private static final String HEADER_TIMESTAMP = "timestamp";

    /**
     * Source module Header name.
     */
    private static final String HEADER_SOURCE_MODULE = "sourceModule";

    /**
     * Publish timeout in seconds.
     */
    private static final int PUBLISH_TIMEOUT_SECONDS = 10;

    // ==================== Micrometer Metric Names (R3.3 / R3.7) ====================

    /** Counter: total number of events successfully published. */
    private static final String METRIC_PUBLISHED_TOTAL = "brix.event.published.total";

    /** Counter: total number of events that failed to publish. */
    private static final String METRIC_PUBLISH_ERROR = "brix.event.publish.error";

    /** Timer: duration of publish operations. */
    private static final String METRIC_PUBLISH_DURATION = "brix.event.publish.duration";

    /** Counter: total number of events successfully consumed. */
    private static final String METRIC_CONSUMED_TOTAL = "brix.event.consumed.total";

    /** Counter: event consumption retry attempts. */
    private static final String METRIC_RETRY_COUNT = "brix.event.retry.count";

    /** Counter: events sent to the dead-letter queue. */
    private static final String METRIC_DLQ_COUNT = "brix.event.dlq.count";

    /**
     * Kafka message template.
     * 
     * <p>Injected by Spring, used to send messages to Kafka.</p>
     */
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Topic resolver.
     * 
     * <p>Resolves target Topic based on event type.</p>
     */
    private final EventTopicResolver topicResolver;

    /**
     * JSON serializer.
     * 
     * <p>Unified JSON serialization using Jackson.</p>
     */
    private final ObjectMapper objectMapper;

    /**
     * Current module ID.
     * 
     * <p>Used to identify the event source.</p>
     */
    private final String currentModuleId;

    /**
     * Supplier that resolves the current tenant ID from the runtime context.
     *
     * <p>Injected by Spring auto-configuration.  Typically backed by
     * {@code TenantContext.getTenantId()} from {@code platform-common}.
     * Returns {@code Optional.empty()} when no tenant context is available
     * (e.g., system-level background jobs).</p>
     *
     * @since 3.2.0
     */
    private final Supplier<Optional<String>> tenantIdProvider;

    /**
     * Micrometer meter registry for publishing event metrics (R3.3 / R3.7).
     *
     * <p>Optional dependency — when {@code null}, metrics are simply not recorded.
     * This keeps the adapter functional in environments without Actuator/Micrometer.</p>
     *
     * @since 3.2.0
     */
    private final MeterRegistry meterRegistry;

    /**
     * Constructor.
     * 
     * @param kafkaTemplate    Kafka message template, injected by Spring
     * @param topicResolver    Topic resolver
     * @param objectMapper     JSON serializer
     * @param currentModuleId  Current module ID, used to identify event source
     * @param tenantIdProvider Supplier resolving the current tenant ID from runtime context;
     *                         may be {@code null}, in which case events must carry tenantId explicitly
     */
    public KafkaEventBusCapability(
            KafkaTemplate<String, String> kafkaTemplate,
            EventTopicResolver topicResolver,
            ObjectMapper objectMapper,
            String currentModuleId,
            Supplier<Optional<String>> tenantIdProvider) {
        this(kafkaTemplate, topicResolver, objectMapper, currentModuleId, tenantIdProvider, null);
    }

    /**
     * Constructor with Micrometer support.
     *
     * @param kafkaTemplate    Kafka message template, injected by Spring
     * @param topicResolver    Topic resolver
     * @param objectMapper     JSON serializer
     * @param currentModuleId  Current module ID, used to identify event source
     * @param tenantIdProvider Supplier resolving the current tenant ID from runtime context
     * @param meterRegistry    Micrometer registry for event metrics (optional, may be {@code null})
     * @since 3.2.0
     */
    public KafkaEventBusCapability(
            KafkaTemplate<String, String> kafkaTemplate,
            EventTopicResolver topicResolver,
            ObjectMapper objectMapper,
            String currentModuleId,
            Supplier<Optional<String>> tenantIdProvider,
            MeterRegistry meterRegistry) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate cannot be null");
        this.topicResolver = Objects.requireNonNull(topicResolver, "topicResolver cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.currentModuleId = Objects.requireNonNull(currentModuleId, "currentModuleId cannot be null");
        this.tenantIdProvider = tenantIdProvider != null ? tenantIdProvider : Optional::empty;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Publish a domain event.
     * 
     * <p>Domain events propagate within modules, sent to module-specific Topics.
     * Message key uses aggregateId to ensure events from the same aggregate root are processed in order.</p>
     * 
     * <h4>Topic Naming Rule</h4>
     * <p>domain.{moduleId}.{aggregateType}</p>
     * <p>For example: domain.booking.reservation</p>
     * 
     * @param event the domain event to publish, cannot be null
     * @throws IllegalArgumentException if event is null
     * @throws EventPublishException    if publishing fails
     */
    @Override
    public void publish(DomainEvent event) {
        // Parameter validation
        Objects.requireNonNull(event, "Domain event cannot be null");

        String eventTypeName = event.getClass().getSimpleName();
        Timer.Sample sample = startTimerSample();

        // Resolve Topic
        String topic = topicResolver.resolveDomainTopic(event, currentModuleId);
        
        // Get message key (aggregate ID, ensures event ordering for same aggregate root)
        String key = event.getAggregateId();
        
        // Serialize event
        String payload = serializeEvent(event);
        
        // Build message Headers
        Headers headers = buildHeaders(event);
        
        // Create Producer Record
        ProducerRecord<String, String> record = new ProducerRecord<>(
                topic,
                null,  // partition - Kafka auto-selects based on key
                key,
                payload,
                headers
        );
        
        // Synchronously wait for send result, ensuring caller can perceive publish failures
        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(record);
        
        try {
            SendResult<String, String> result = future.get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            // R3.3: Record publish success metric
            recordPublishSuccess(eventTypeName, currentModuleId, sample);
            if (log.isDebugEnabled()) {
                log.debug("Domain event published successfully: eventId={}, topic={}, partition={}, offset={}",
                        event.getEventId(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            recordPublishError(eventTypeName, e);
            throw new EventPublishException(event.getEventId(),
                    "Domain event publishing interrupted: eventId=" + event.getEventId(), e);
        } catch (ExecutionException e) {
            recordPublishError(eventTypeName, e.getCause());
            throw new EventPublishException(event.getEventId(),
                    "Domain event publishing failed: eventId=" + event.getEventId() + ", topic=" + topic, e.getCause());
        } catch (TimeoutException e) {
            recordPublishError(eventTypeName, e);
            throw new EventPublishException(event.getEventId(),
                    "Domain event publishing timed out: eventId=" + event.getEventId() + ", topic=" + topic, e);
        }
    }

    /**
     * Publish an integration event.
     * 
     * <p>Integration events are used for cross-module communication, sent to public integration Topics.
     * Runtime Shell routes events to corresponding modules based on subscription declarations in Manifest.</p>
     * 
     * <h4>Topic Naming Rule</h4>
     * <p>integration.{eventType}</p>
     * <p>For example: integration.reservation-created</p>
     * 
     * <h4>Delivery Guarantee</h4>
     * <ul>
     *   <li>At-Least-Once delivery</li>
     *   <li>Consumers must implement idempotent processing</li>
     * </ul>
     * 
     * @param event the integration event to publish, cannot be null
     * @throws IllegalArgumentException if event is null
     * @throws EventPublishException    if publishing fails
     */
    @Override
    public void publishIntegration(IntegrationEvent event) {
        // Parameter validation
        Objects.requireNonNull(event, "Integration event cannot be null");

        IntegrationEventMetadataSupport.enrich(event, tenantIdProvider);

        String eventTypeName = event.getClass().getSimpleName();
        Timer.Sample sample = startTimerSample();

        // Resolve Topic
        String topic = topicResolver.resolveIntegrationTopic(event);
        
        // Get message key (event ID or routing key)
        String key = event.getRoutingKey() != null ? event.getRoutingKey() : event.getEventId();
        
        // Serialize event
        String payload = serializeEvent(event);
        
        // Build message Headers
        Headers headers = buildIntegrationHeaders(event);
        
        // Create Producer Record
        ProducerRecord<String, String> record = new ProducerRecord<>(
                topic,
                null,  // partition
                key,
                payload,
                headers
        );
        
        // Synchronously wait for send result, ensuring caller can perceive publish failures
        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(record);
        
        try {
            SendResult<String, String> result = future.get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            // R3.3: Record publish success metric
            recordPublishSuccess(eventTypeName, event.getSourceModule(), sample);
            log.info("Integration event published successfully: eventId={}, type={}, traceId={}, topic={}, partition={}, offset={}",
                    event.getEventId(),
                    event.getEventType(),
                    event.getTraceId(),
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            recordPublishError(eventTypeName, e);
            throw new EventPublishException(event.getEventId(),
                    "Integration event publishing interrupted: eventId=" + event.getEventId(), e);
        } catch (ExecutionException e) {
            recordPublishError(eventTypeName, e.getCause());
            throw new EventPublishException(event.getEventId(),
                    "Integration event publishing failed: eventId=" + event.getEventId() + ", topic=" + topic, e.getCause());
        } catch (TimeoutException e) {
            recordPublishError(eventTypeName, e);
            throw new EventPublishException(event.getEventId(),
                    "Integration event publishing timed out: eventId=" + event.getEventId() + ", topic=" + topic, e);
        }
    }

    /**
     * Serialize event to JSON string.
     * 
     * @param event the event to serialize
     * @return JSON string
     * @throws EventSerializationException if serialization fails
     */
    private String serializeEvent(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new EventSerializationException(
                    "Event serialization failed: " + event.getClass().getName(), e);
        }
    }

    /**
     * Build message Headers for domain events.
     * 
     * @param event the domain event
     * @return Kafka Headers
     */
    private Headers buildHeaders(DomainEvent event) {
        RecordHeaders headers = new RecordHeaders();
        
        // Add event metadata
        addHeader(headers, HEADER_EVENT_ID, event.getEventId());
        addHeader(headers, HEADER_EVENT_TYPE, event.getEventType());
        addHeader(headers, HEADER_TIMESTAMP, String.valueOf(event.getTimestamp().toEpochMilli()));
        addHeader(headers, HEADER_SOURCE_MODULE, currentModuleId);
        
        // Add aggregate root information
        addHeader(headers, "aggregateId", event.getAggregateId());
        addHeader(headers, "aggregateType", event.getAggregateType());
        
        return headers;
    }

    /**
     * Build message Headers for integration events.
     *
     * <p>Includes all base event metadata plus tenant ID and schema version
     * (R3.6 compliance) so that consumers can filter and validate events
     * at the header level without full payload deserialization.</p>
     * 
     * @param event the integration event
     * @return Kafka Headers
     */
    private Headers buildIntegrationHeaders(IntegrationEvent event) {
        return IntegrationEventMetadataSupport.buildHeaders(event);
    }

    /**
     * Add Header if value is not null.
     * 
     * @param headers Header collection
     * @param key     Header key
     * @param value   Header value
     */
    private void addHeader(RecordHeaders headers, String key, String value) {
        if (value != null) {
            headers.add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }

    // ==================== R3.3 / R3.7: Micrometer Metrics Helpers ====================

    /**
     * Starts a timer sample for measuring publish duration.
     * Returns {@code null} when no MeterRegistry is configured.
     */
    private Timer.Sample startTimerSample() {
        return meterRegistry != null ? Timer.start(meterRegistry) : null;
    }

    /**
     * Records a successful event publish (counter + timer).
     *
     * @param eventType    simple class name of the event
     * @param sourceModule module that published the event
     * @param sample       timer sample started before publishing (may be {@code null})
     */
    private void recordPublishSuccess(String eventType, String sourceModule, Timer.Sample sample) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(METRIC_PUBLISHED_TOTAL,
                "eventType", eventType,
                "sourceModule", sourceModule
        ).increment();

        if (sample != null) {
            sample.stop(meterRegistry.timer(METRIC_PUBLISH_DURATION,
                    "eventType", eventType));
        }
    }

    /**
     * Records a failed event publish attempt.
     *
     * @param eventType simple class name of the event
     * @param cause     the exception that caused the failure
     */
    private void recordPublishError(String eventType, Throwable cause) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(METRIC_PUBLISH_ERROR,
                "eventType", eventType,
                "errorType", cause.getClass().getSimpleName()
        ).increment();
    }

    /**
     * Records a successful event consumption.
     *
     * <p>Intended to be called by consumer-side infrastructure (e.g., KafkaListener wrappers)
     * when an event is processed successfully.</p>
     *
     * @param eventType    simple class name of the consumed event
     * @param sourceModule source module of the event
     */
    public void recordConsumeSuccess(String eventType, String sourceModule) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(METRIC_CONSUMED_TOTAL,
                "eventType", eventType,
                "sourceModule", sourceModule
        ).increment();
    }

    /**
     * Records an event consumption retry attempt (R3.7).
     *
     * @param eventType simple class name of the retried event
     * @param attempt   retry attempt number (1-based)
     */
    public void recordConsumeRetry(String eventType, int attempt) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(METRIC_RETRY_COUNT,
                "eventType", eventType,
                "attempt", String.valueOf(attempt)
        ).increment();
    }

    /**
     * Records an event sent to the dead-letter queue (R3.7).
     *
     * @param eventType simple class name of the DLQ'd event
     * @param cause     the exception that caused permanent failure
     */
    public void recordDlq(String eventType, Throwable cause) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(METRIC_DLQ_COUNT,
                "eventType", eventType,
                "errorType", cause.getClass().getSimpleName()
        ).increment();
    }
}
