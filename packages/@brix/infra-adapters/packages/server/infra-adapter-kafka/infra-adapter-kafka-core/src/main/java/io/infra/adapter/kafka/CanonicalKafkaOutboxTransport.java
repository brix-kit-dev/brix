/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.InvalidRecordException;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.RecordBatchTooLargeException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.RetriableException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxTransport;
import io.runtime.orchestrator.outbox.OutboxTransportException;

/**
 * Kafka transport for canonical outbox messages.
 * <p>This L2C adapter maps the already validated canonical envelope to a Kafka
 * record on the event type's topic {@code brix.event.<eventType>} (Kafka design D18), or a command on its command
 * type's topic {@code brix.command.<commandType>} (§14 K22), keyed by the
 * partition key. It does not own Outbox state, does not decide reliability, and does
 * not rewrite message or event identity.</p>
 * <p>It is ready while the broker answers, so the relay claims nothing while Kafka cannot be reached and
 * waiting spends no attempts (D21); a send that times out still counts as an attempt, and makes the
 * broker probe again before the relay's next readiness check. A record Kafka can never take (too large,
 * an invalid topic or record) fails permanently, so the relay parks it at once instead of holding its key
 * back for every retry. The producer gives up on a record within the publish timeout
 * ({@code delivery.timeout.ms}), and the transport waits a little longer than that, so a send reported as
 * failed is not still under way. The broker does not create topics by itself, so the transport makes sure
 * an event type's topic exists before its first send.</p>
 *
 * @author Brix Platform Authors
 * @since 3.0.10
 */
public final class CanonicalKafkaOutboxTransport implements OutboxTransport {

    private static final String ERROR_PAYLOAD_INVALID = "KAFKA_PAYLOAD_INVALID";
    private static final String ERROR_TOPIC_INVALID = "KAFKA_TOPIC_INVALID";
    private static final String ERROR_TOPIC_UNAVAILABLE = "KAFKA_TOPIC_UNAVAILABLE";
    /** A topic exists with fewer partitions than the production profile needs: the message waits for it. */
    private static final String ERROR_TOPIC_PARTITIONS = "KAFKA_TOPIC_PARTITIONS";
    private static final String ERROR_INTERRUPTED = "KAFKA_SEND_INTERRUPTED";
    private static final String ERROR_FAILED = "KAFKA_SEND_FAILED";
    private static final String ERROR_TIMEOUT = "KAFKA_SEND_TIMEOUT";
    private static final String ERROR_REJECTED = "KAFKA_RECORD_REJECTED";
    private static final String ERROR_KIND_UNSUPPORTED = "KAFKA_KIND_UNSUPPORTED";

    /** How much longer than the producer's own delivery timeout the transport waits for its verdict. */
    static final Duration VERDICT_MARGIN = Duration.ofSeconds(5);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaEventTopics topics;
    private final KafkaEventBroker broker;
    private final CanonicalEnvelopeCodec envelopes;
    private final Duration publishTimeout;

    /**
     * Creates a canonical Kafka transport.
     *
     * @param kafkaTemplate Kafka template
     * @param topics topic names
     * @param broker the broker's reachability and topics
     * @param objectMapper JSON mapper
     * @param publishTimeout broker send timeout
     */
    public CanonicalKafkaOutboxTransport(
            KafkaTemplate<String, String> kafkaTemplate,
            KafkaEventTopics topics,
            KafkaEventBroker broker,
            ObjectMapper objectMapper,
            Duration publishTimeout) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate must not be null");
        this.topics = Objects.requireNonNull(topics, "topics must not be null");
        this.broker = Objects.requireNonNull(broker, "broker must not be null");
        this.envelopes = new CanonicalEnvelopeCodec(Objects.requireNonNull(objectMapper, "objectMapper must not be null"));
        if (publishTimeout == null || publishTimeout.isZero() || publishTimeout.isNegative()) {
            throw new IllegalArgumentException("publishTimeout must be positive");
        }
        this.publishTimeout = publishTimeout;
    }

    /** Ready while the broker answers; until then the relay waits without claiming. */
    @Override
    public boolean ready() {
        return broker.reachable();
    }

    @Override
    public void publish(CanonicalOutboxMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        boolean command = "COMMAND".equals(message.messageKind());
        if (!command && !"EVENT".equals(message.messageKind())) {
            throw new OutboxTransportException(ERROR_KIND_UNSUPPORTED,
                "Kafka carries canonical events and commands, not " + message.messageKind(), null, true);
        }
        String topic;
        try {
            topic = command ? topics.commandTopic(message.messageType()) : topics.eventTopic(message.messageType());
        } catch (IllegalArgumentException invalid) {
            throw new OutboxTransportException(ERROR_TOPIC_INVALID, invalid.getMessage(), invalid, true);
        }
        try {
            if (command) {
                broker.ensureCommandTopics(Set.of(message.messageType()));
            } else {
                broker.ensureEventTopics(Set.of(message.messageType()));
            }
        } catch (KafkaTopicPartitionsException tooFew) {
            // A configuration the operator fixes, not a failure of this message: it waits without spending an attempt
            // (standard-link plan point 5).
            throw OutboxTransportException.unavailable(ERROR_TOPIC_PARTITIONS, "Kafka topic " + topic
                + " has too few partitions", tooFew);
        } catch (IllegalStateException unavailable) {
            throw new OutboxTransportException(ERROR_TOPIC_UNAVAILABLE, "Kafka topic " + topic + " is not available",
                unavailable);
        }
        ProducerRecord<String, String> record = new ProducerRecord<>(
            topic,
            null,
            message.partitionKey(),
            writeEnvelope(message),
            headers(message));
        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(record);
        try {
            future.get(publishTimeout.plus(VERDICT_MARGIN).toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OutboxTransportException(ERROR_INTERRUPTED, "Kafka outbox publish interrupted", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (neverAccepted(cause)) {
                throw new OutboxTransportException(ERROR_REJECTED, "Kafka cannot take this record: "
                    + cause.getClass().getSimpleName(), cause, true);
            }
            if (cause instanceof RetriableException) {
                broker.suspect();
            }
            throw new OutboxTransportException(ERROR_FAILED, "Kafka outbox publish failed", cause);
        } catch (TimeoutException ex) {
            broker.suspect();
            throw new OutboxTransportException(ERROR_TIMEOUT, "Kafka outbox publish timed out", ex);
        }
    }

    private static boolean neverAccepted(Throwable cause) {
        return cause instanceof RecordTooLargeException
            || cause instanceof RecordBatchTooLargeException
            || cause instanceof InvalidTopicException
            || cause instanceof InvalidRecordException
            || cause instanceof SerializationException;
    }

    private String writeEnvelope(CanonicalOutboxMessage message) {
        try {
            return envelopes.write(message);
        } catch (JacksonException ex) {
            throw new OutboxTransportException(ERROR_PAYLOAD_INVALID, "Canonical outbox payload is not valid JSON", ex,
                true);
        }
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
