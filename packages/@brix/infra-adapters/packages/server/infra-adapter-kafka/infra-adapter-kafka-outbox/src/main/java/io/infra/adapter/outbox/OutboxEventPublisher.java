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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.infra.adapter.kafka.EventSerializationException;
import io.infra.adapter.kafka.EventTopicResolver;
import io.infra.adapter.kafka.IntegrationEventMetadataSupport;
import io.infra.adapter.kafka.config.KafkaEventBusProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.runtime.sdk.event.IntegrationEvent;

/**
 * Outbox Pattern Event Publisher.
 *
 * <p>Implements the Outbox pattern to ensure transactional consistency of event publishing.
 * Core flow:</p>
 * <ol>
 *   <li>Business code calls {@link #saveForLater} to save event to Outbox table</li>
 *   <li>Scheduled task {@link #processOutbox} reads and sends pending events</li>
 *   <li>Mark event as completed after successful send</li>
 * </ol>
 *
 * <h3>Architecture Position</h3>
 * <p>
 * This class belongs to the {@code infra-adapter-outbox} standalone module (Layer 2.5: Adapter Layer).
 * Outbox is a cross-infrastructure pattern (requires DB + MQ coordination), so it was separated from
 * {@code infra-adapter-kafka}. This module depends on {@code infra-adapter-kafka} to reuse
 * {@link EventTopicResolver} and {@link KafkaEventBusProperties.OutboxProperties}.
 * </p>
 *
 * <h3>Transaction Guarantee</h3>
 * <p>Outbox records are written in the same database transaction as business data,
 * ensuring "business operation success → event recorded" consistency.</p>
 *
 * <h3>Configuration Externalization</h3>
 * <p>All scheduled task parameters are externalized through {@link KafkaEventBusProperties.OutboxProperties},
 * configurable in {@code application.yml} via {@code brix.infra.kafka.outbox.*} prefix.</p>
 *
 * <h3>Failure Recovery</h3>
 * <ul>
 *   <li>Failed events are automatically retried (maximum retry count configurable)</li>
 *   <li>Events exceeding retry count are marked as FAILED, requiring manual intervention</li>
 * </ul>
 *
 * @author Brix Platform Authors
 * @since 3.0.0
 */
public class OutboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    /** Outbox JPA repository */
    private final OutboxEventRepository outboxRepository;

    /** Kafka message template */
    private final KafkaTemplate<String, String> kafkaTemplate;

    /** Event Topic resolver (reused from infra-adapter-kafka) */
    private final EventTopicResolver topicResolver;

    /** JSON serializer */
    private final ObjectMapper objectMapper;

    /** Outbox configuration properties (externalized via brix.infra.kafka.outbox.*) */
    private final KafkaEventBusProperties.OutboxProperties outboxConfig;

    /** Micrometer counter for processed events (nullable when Micrometer is absent) */
    private final Counter processedCounter;

    /** Micrometer counter for retried events (nullable when Micrometer is absent) */
    private final Counter retriedCounter;

    /** Micrometer counter for dead-lettered events (nullable when Micrometer is absent) */
    private final Counter deadLetteredCounter;

    /** Supplier that resolves the current tenant ID from runtime context. */
    private final Supplier<Optional<String>> tenantIdProvider;

    /**
     * Construct Outbox event publisher.
     *
     * @param outboxRepository Outbox JPA repository
     * @param kafkaTemplate    Kafka message template
     * @param topicResolver    Event Topic resolver
     * @param objectMapper     JSON serializer
     * @param outboxConfig     Outbox externalized configuration properties
     */
    public OutboxEventPublisher(
            OutboxEventRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            EventTopicResolver topicResolver,
            ObjectMapper objectMapper,
            KafkaEventBusProperties.OutboxProperties outboxConfig) {
        this(outboxRepository, kafkaTemplate, topicResolver, objectMapper, outboxConfig, Optional::empty, null);
    }

    /**
     * Construct Outbox event publisher with optional Micrometer metrics.
     *
     * <p>When {@code meterRegistry} is provided, the following metrics are registered:</p>
     * <ul>
     *   <li>{@code brix.outbox.pending} — gauge: current count of PENDING events</li>
     *   <li>{@code brix.outbox.processed} — counter: total successfully processed events</li>
     *   <li>{@code brix.outbox.retried} — counter: total retried events</li>
     * </ul>
     *
     * @param outboxRepository Outbox JPA repository
     * @param kafkaTemplate    Kafka message template
     * @param topicResolver    Event Topic resolver
     * @param objectMapper     JSON serializer
     * @param outboxConfig     Outbox externalized configuration properties
     * @param meterRegistry    Micrometer registry (nullable)
     * @since 3.2.0
     */
    public OutboxEventPublisher(
            OutboxEventRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            EventTopicResolver topicResolver,
            ObjectMapper objectMapper,
            KafkaEventBusProperties.OutboxProperties outboxConfig,
            MeterRegistry meterRegistry) {
        this(outboxRepository, kafkaTemplate, topicResolver, objectMapper, outboxConfig, Optional::empty, meterRegistry);
    }

    /**
     * Construct Outbox event publisher with tenant metadata and optional Micrometer metrics.
     *
     * @param outboxRepository Outbox JPA repository
     * @param kafkaTemplate    Kafka message template
     * @param topicResolver    Event Topic resolver
     * @param objectMapper     JSON serializer
     * @param outboxConfig     Outbox externalized configuration properties
     * @param tenantIdProvider current tenant resolver, may be {@code null}
     * @param meterRegistry    Micrometer registry, may be {@code null}
     */
    public OutboxEventPublisher(
            OutboxEventRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            EventTopicResolver topicResolver,
            ObjectMapper objectMapper,
            KafkaEventBusProperties.OutboxProperties outboxConfig,
            Supplier<Optional<String>> tenantIdProvider,
            MeterRegistry meterRegistry) {
        this.outboxRepository = Objects.requireNonNull(outboxRepository);
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate);
        this.topicResolver = Objects.requireNonNull(topicResolver);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.outboxConfig = Objects.requireNonNull(outboxConfig);
        this.tenantIdProvider = tenantIdProvider != null ? tenantIdProvider : Optional::empty;

        if (meterRegistry != null) {
            meterRegistry.gauge("brix.outbox.pending",
                    outboxRepository, repo -> repo.countByStatus(OutboxEvent.Status.PENDING));
            this.processedCounter = meterRegistry.counter("brix.outbox.processed");
            this.retriedCounter = meterRegistry.counter("brix.outbox.retried");
            this.deadLetteredCounter = meterRegistry.counter("brix.outbox.dead_lettered");
        } else {
            this.processedCounter = null;
            this.retriedCounter = null;
            this.deadLetteredCounter = null;
        }
    }

    /**
     * Save event to Outbox (for transactional publishing).
     *
     * <p>This method should be called within business transaction to ensure event record is committed with business data.</p>
     *
     * <h4>Usage Example</h4>
     * <pre>{@code
     * @Transactional
     * public void createReservation(ReservationCommand cmd) {
     *     // Save business data
     *     Reservation reservation = repository.save(new Reservation(cmd));
     *
     *     // Save event to Outbox (same transaction as business data)
     *     outboxPublisher.saveForLater(new ReservationCreatedEvent(reservation.getId()));
     * }
     * }</pre>
     *
     * @param event the integration event to publish
     * @throws EventSerializationException if event serialization fails
     */
    @Transactional
    public void saveForLater(IntegrationEvent event) {
        Objects.requireNonNull(event, "Event cannot be null");

        IntegrationEventMetadataSupport.enrich(event, tenantIdProvider);

        // Idempotency check: if event already exists, return directly
        if (outboxRepository.existsByEventId(event.getEventId())) {
            log.warn("Event already exists, skipping save: eventId={}", event.getEventId());
            return;
        }

        try {
            // Serialize event to JSON
            String payload = objectMapper.writeValueAsString(event);

            // Resolve target Topic via EventTopicResolver
            String topic = topicResolver.resolveIntegrationTopic(event);

            // Create Outbox record (PENDING status)
            OutboxEvent outboxEvent = OutboxEvent.from(event, payload, topic);

            // Save to database (same transaction as business operation)
            outboxRepository.save(outboxEvent);

            log.debug("Event saved to Outbox: eventId={}, type={}",
                    event.getEventId(), event.getEventType());

        } catch (JsonProcessingException e) {
            throw new EventSerializationException("Event serialization failed: " + event.getEventType(), e);
        }
    }

    /**
     * Process pending events in Outbox.
     *
     * <p>Scheduled task, executes at interval configured by {@code brix.infra.kafka.outbox.process-interval-ms}.
     * Default: polls every 1 second.</p>
     *
     * <h4>Processing Flow</h4>
     * <ol>
     *   <li>Query events with PENDING status (ordered by creation time ascending, limited by batch size)</li>
     *   <li>Batch mark as PROCESSING (optimistic lock prevents concurrent duplicate processing)</li>
     *   <li>Send to Kafka one by one (synchronous confirmation)</li>
     *   <li>Mark COMPLETED on success, increment retry count on failure</li>
     * </ol>
     */
    @Scheduled(fixedDelayString = "${brix.infra.kafka.outbox.process-interval-ms:1000}")
    @Transactional
    public void processOutbox() {
        int batchSize = outboxConfig.getBatchSize();
        // Query pending events
        List<OutboxEvent> events = outboxRepository.findPendingEvents(batchSize);

        if (events.isEmpty()) {
            return;
        }

        log.debug("Starting to process Outbox events, count: {}", events.size());

        // Batch mark as processing (prevent concurrent duplicate processing)
        List<UUID> ids = events.stream()
                .map(OutboxEvent::getId)
                .collect(Collectors.toList());
        outboxRepository.markAsProcessing(ids);

        // Send to Kafka one by one
        for (OutboxEvent event : events) {
            try {
                sendToKafka(event);
                event.markCompleted();
                outboxRepository.save(event);
                if (processedCounter != null) {
                    processedCounter.increment();
                }

                log.info("event.published name={} sourcePlugin={} tenantId={} traceId={} eventId={}",
                        event.getEventType(),
                        event.getSourceModule(),
                        event.getTenantId(),
                        event.getTraceId(),
                        event.getEventId());
                log.debug("Outbox event sent successfully: eventId={}", event.getEventId());

            } catch (Exception e) {
                handleSendFailure(event, e);
            }
        }
    }

    /**
     * Process failed events that need retry.
     *
     * <p>Scheduled task, executes at interval configured by {@code brix.infra.kafka.outbox.retry-interval-ms}.
     * Default: polls every 30 seconds. Resets FAILED status events (not exceeding max retry count) to PENDING.</p>
     */
    @Scheduled(fixedDelayString = "${brix.infra.kafka.outbox.retry-interval-ms:30000}")
    @Transactional
    public void retryFailedEvents() {
        int maxRetry = outboxConfig.getMaxRetryCount();
        int batchSize = outboxConfig.getBatchSize();
        List<OutboxEvent> events = outboxRepository.findRetryableEvents(maxRetry, batchSize);

        if (events.isEmpty()) {
            return;
        }

        log.info("Starting to retry failed events, count: {}", events.size());

        for (OutboxEvent event : events) {
            event.incrementRetryCount();
            event.resetToPending();
            outboxRepository.save(event);
            if (retriedCounter != null) {
                retriedCounter.increment();
            }
        }
    }

    /**
     * Clean up old completed events.
     *
     * <p>Scheduled task, executes according to Cron expression configured by {@code brix.infra.kafka.outbox.cleanup-cron}.
     * Default: 3 AM daily. Cleans completed events older than retention days to prevent Outbox table from growing indefinitely.</p>
     */
    @Scheduled(cron = "${brix.infra.kafka.outbox.cleanup-cron:0 0 3 * * ?}")
    @Transactional
    public void cleanupOldEvents() {
        int retentionDays = outboxConfig.getRetentionDays();
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = outboxRepository.deleteCompletedBefore(cutoff);

        if (deleted > 0) {
            log.info("Cleaned up completed Outbox events: {} records (retention days: {})", deleted, retentionDays);
        }
    }

    // ==================== Internal Methods ====================

    /**
     * Send event to Kafka.
     *
     * <p>Builds ProducerRecord with event metadata Headers, sends synchronously
     * to accurately determine send result in Outbox scenario.</p>
     *
     * @param event Outbox event entity
     */
    private void sendToKafka(OutboxEvent event) {
        // Build Kafka message Headers with event metadata
        RecordHeaders headers = buildHeaders(event);

        // Create ProducerRecord (use routingKey as Partition Key to ensure ordering)
        ProducerRecord<String, String> record = new ProducerRecord<>(
                event.getTopic(),
                null,
                event.getRoutingKey(),
                event.getPayload(),
                headers
        );

        // Synchronous send (Outbox scenario requires confirmation of send result)
        kafkaTemplate.send(record).join();
    }

    /**
     * Handle send failure.
     *
     * <p>Based on configured maximum retry count, determines event status:
     * mark as FAILED (requires manual intervention) if limit exceeded, otherwise reset to PENDING for next retry.</p>
     *
     * @param event failed Outbox event
     * @param e     send exception
     */
    private void handleSendFailure(OutboxEvent event, Exception e) {
        event.incrementRetryCount();
        int maxRetry = outboxConfig.getMaxRetryCount();

        if (event.getRetryCount() >= maxRetry) {
            try {
                sendToDlq(event, e);
                event.markDeadLettered(failureMessage(e));
                if (deadLetteredCounter != null) {
                    deadLetteredCounter.increment();
                }
                log.error("Outbox event send failed and was routed to DLQ: eventId={}, retries={}, topic={}",
                        event.getEventId(), event.getRetryCount(), resolveDlqTopic(event));
            } catch (Exception dlqFailure) {
                event.markFailed("DLQ publish failed: " + failureMessage(dlqFailure)
                        + "; original failure: " + failureMessage(e));
                log.error("Outbox event send failed and DLQ routing failed: eventId={}, retries={}",
                        event.getEventId(), event.getRetryCount(), dlqFailure);
            }
        } else {
            // Reset to pending, wait for next retry
            event.resetToPending();
            log.warn("Outbox event send failed, will retry: eventId={}, retryCount={}, error={}",
                    event.getEventId(), event.getRetryCount(), e.getMessage());
        }

        outboxRepository.save(event);
    }

    private void sendToDlq(OutboxEvent event, Exception cause) {
        RecordHeaders headers = buildHeaders(event);
        addHeader(headers, "dlqOriginalTopic", event.getTopic());
        addHeader(headers, "dlqReason", failureMessage(cause));
        addHeader(headers, "dlqFailedAt", Instant.now().toString());

        ProducerRecord<String, String> record = new ProducerRecord<>(
                resolveDlqTopic(event),
                null,
                event.getRoutingKey(),
                event.getPayload(),
                headers
        );

        kafkaTemplate.send(record).join();
    }

    private RecordHeaders buildHeaders(OutboxEvent event) {
        RecordHeaders headers = new RecordHeaders();
        addHeader(headers, IntegrationEventMetadataSupport.HEADER_EVENT_ID, event.getEventId());
        addHeader(headers, IntegrationEventMetadataSupport.HEADER_EVENT_TYPE, event.getEventType());
        addHeader(headers, IntegrationEventMetadataSupport.HEADER_TIMESTAMP,
                event.getCreatedAt() != null ? String.valueOf(event.getCreatedAt().toEpochMilli()) : null);
        addHeader(headers, IntegrationEventMetadataSupport.HEADER_SOURCE_MODULE, event.getSourceModule());
        addHeader(headers, IntegrationEventMetadataSupport.HEADER_TENANT_ID, event.getTenantId());
        addHeader(headers, IntegrationEventMetadataSupport.HEADER_TRACE_ID, event.getTraceId());
        addHeader(headers, IntegrationEventMetadataSupport.HEADER_SCHEMA_VERSION, String.valueOf(event.getSchemaVersion()));
        addHeader(headers, IntegrationEventMetadataSupport.HEADER_CORRELATION_ID, event.getCorrelationId());
        addHeader(headers, IntegrationEventMetadataSupport.HEADER_ROUTING_KEY, event.getRoutingKey());
        return headers;
    }

    private void addHeader(RecordHeaders headers, String key, String value) {
        if (value != null && !value.isBlank()) {
            headers.add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String resolveDlqTopic(OutboxEvent event) {
        String suffix = outboxConfig.getDlqTopicSuffix();
        return event.getTopic() + (suffix == null || suffix.isBlank() ? ".DLQ" : suffix);
    }

    private String failureMessage(Exception exception) {
        return exception.getMessage() != null ? exception.getMessage() : exception.getClass().getName();
    }
}
