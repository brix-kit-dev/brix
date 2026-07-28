/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxTransport;
import io.runtime.orchestrator.outbox.OutboxTransportException;

/**
 * Kafka transport for canonical outbox messages.
 *
 * <p>This L2C adapter maps the already validated canonical envelope to a Kafka
 * record. It does not own Outbox state, does not decide reliability, and does
 * not rewrite message or event identity.</p>
 *
 * @author Brix Platform Authors
 * @since 3.0.10
 */
public final class CanonicalKafkaOutboxTransport implements OutboxTransport {

    private static final String ERROR_PAYLOAD_INVALID = "KAFKA_PAYLOAD_INVALID";
    private static final String ERROR_INTERRUPTED = "KAFKA_SEND_INTERRUPTED";
    private static final String ERROR_FAILED = "KAFKA_SEND_FAILED";
    private static final String ERROR_TIMEOUT = "KAFKA_SEND_TIMEOUT";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final EventTopicResolver topicResolver;
    private final ObjectMapper objectMapper;
    private final Duration publishTimeout;

    /**
     * Creates a canonical Kafka transport.
     *
     * @param kafkaTemplate Kafka template
     * @param topicResolver topic resolver
     * @param objectMapper JSON mapper
     * @param publishTimeout broker send timeout
     */
    public CanonicalKafkaOutboxTransport(
            KafkaTemplate<String, String> kafkaTemplate,
            EventTopicResolver topicResolver,
            ObjectMapper objectMapper,
            Duration publishTimeout) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate must not be null");
        this.topicResolver = Objects.requireNonNull(topicResolver, "topicResolver must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        if (publishTimeout == null || publishTimeout.isZero() || publishTimeout.isNegative()) {
            throw new IllegalArgumentException("publishTimeout must be positive");
        }
        this.publishTimeout = publishTimeout;
    }

    @Override
    public void publish(CanonicalOutboxMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        String topic = topicResolver.resolveTopicByEventType(message.messageType());
        ProducerRecord<String, String> record = new ProducerRecord<>(
            topic,
            null,
            message.partitionKey(),
            writeEnvelope(message),
            headers(message));
        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(record);
        try {
            future.get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OutboxTransportException(ERROR_INTERRUPTED, "Kafka outbox publish interrupted", ex);
        } catch (ExecutionException ex) {
            throw new OutboxTransportException(ERROR_FAILED, "Kafka outbox publish failed", ex.getCause());
        } catch (TimeoutException ex) {
            throw new OutboxTransportException(ERROR_TIMEOUT, "Kafka outbox publish timed out", ex);
        }
    }

    private String writeEnvelope(CanonicalOutboxMessage message) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("messageId", message.messageId());
            envelope.put("eventId", message.eventId());
            envelope.put("messageKind", message.messageKind());
            envelope.put("eventType", message.messageType());
            envelope.put("schemaVersion", message.schemaVersion());
            envelope.put("reliability", message.reliability());
            envelope.put("producerPluginId", message.producerPluginId());
            envelope.put("scope", message.scope());
            envelope.put("tenantId", message.tenantId());
            envelope.put("occurredAt", message.occurredAt());
            envelope.put("correlationId", message.correlationId());
            envelope.put("causationId", message.causationId());
            envelope.put("traceparent", message.traceparent());
            envelope.put("tracestate", message.tracestate());
            envelope.put("partitionKey", message.partitionKey());
            envelope.put("payload", payloadNode(message));
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            throw new OutboxTransportException(ERROR_PAYLOAD_INVALID, "Canonical outbox payload is not valid JSON", ex,
                true);
        }
    }

    private JsonNode payloadNode(CanonicalOutboxMessage message) throws JsonProcessingException {
        return objectMapper.readTree(message.payload());
    }

    private Headers headers(CanonicalOutboxMessage message) {
        RecordHeaders headers = new RecordHeaders();
        add(headers, "messageId", message.messageId());
        add(headers, "eventId", message.eventId());
        add(headers, "messageKind", message.messageKind());
        add(headers, "eventType", message.messageType());
        add(headers, "schemaVersion", message.schemaVersion());
        add(headers, "reliability", message.reliability());
        add(headers, "producerPluginId", message.producerPluginId());
        add(headers, "scope", message.scope());
        add(headers, "tenantId", message.tenantId() == null ? null : String.valueOf(message.tenantId()));
        add(headers, "correlationId", message.correlationId());
        add(headers, "causationId", message.causationId());
        add(headers, "traceparent", message.traceparent());
        add(headers, "tracestate", message.tracestate());
        return headers;
    }

    private static void add(Headers headers, String key, String value) {
        if (value != null && !value.isBlank()) {
            headers.add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
