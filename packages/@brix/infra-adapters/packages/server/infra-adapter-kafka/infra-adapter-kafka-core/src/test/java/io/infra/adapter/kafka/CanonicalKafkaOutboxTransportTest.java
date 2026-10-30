/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxTransportException;

@ExtendWith(MockitoExtension.class)
class CanonicalKafkaOutboxTransportTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private KafkaEventBroker broker;

    @Captor
    private ArgumentCaptor<ProducerRecord<String, String>> recordCaptor;

    private ObjectMapper objectMapper;
    private CanonicalKafkaOutboxTransport transport;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().configureForJackson2().build();
        transport = new CanonicalKafkaOutboxTransport(
            kafkaTemplate,
            new KafkaEventTopics("prod-"),
            broker,
            objectMapper,
            Duration.ofSeconds(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesCanonicalEnvelopeWithPartitionKeyAndHeaders() throws Exception {
        mockSendSuccess("prod-brix.event.TenantFirstOwnerAccepted");
        CanonicalOutboxMessage message = message("{\"tenantId\":100,\"memberId\":200}");

        transport.publish(message);

        verify(kafkaTemplate).send(recordCaptor.capture());
        ProducerRecord<String, String> record = recordCaptor.getValue();
        assertThat(record.topic()).isEqualTo("prod-brix.event.TenantFirstOwnerAccepted");
        verify(broker).ensureEventTopics(Set.of("TenantFirstOwnerAccepted"));
        assertThat(record.key()).isEqualTo("tenant-100");
        assertHeader(record, "messageId", "msg-1");
        assertHeader(record, "eventId", "evt-1");
        assertHeader(record, "eventType", "TenantFirstOwnerAccepted");
        assertHeader(record, "tenantId", "100");
        Map<String, Object> envelope = objectMapper.readValue(record.value(), new TypeReference<>() {
        });
        assertThat(envelope.get("messageId")).isEqualTo("msg-1");
        assertThat(envelope.get("eventId")).isEqualTo("evt-1");
        assertThat(envelope.get("partitionKey")).isEqualTo("tenant-100");
        assertThat((Map<String, Object>) envelope.get("payload")).containsEntry("tenantId", 100);
    }

    @Test
    void invalidJsonPayloadIsPermanentTransportFailure() {
        CanonicalOutboxMessage message = message("not-json");

        assertThatThrownBy(() -> transport.publish(message))
            .isInstanceOf(OutboxTransportException.class)
            .satisfies(failure -> {
                OutboxTransportException ex = (OutboxTransportException) failure;
                assertThat(ex.errorCode()).isEqualTo("KAFKA_PAYLOAD_INVALID");
                assertThat(ex.permanent()).isTrue();
            });
    }

    @Test
    @SuppressWarnings("unchecked")
    void brokerFailureIsTransientTransportFailure() {
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        assertThatThrownBy(() -> transport.publish(message("{\"tenantId\":100}")))
            .isInstanceOf(OutboxTransportException.class)
            .satisfies(failure -> {
                OutboxTransportException ex = (OutboxTransportException) failure;
                assertThat(ex.errorCode()).isEqualTo("KAFKA_SEND_FAILED");
                assertThat(ex.permanent()).isFalse();
            });
    }

    @Test
    @SuppressWarnings("unchecked")
    void aRecordKafkaCanNeverTakeIsAPermanentFailure() {
        // Kafka design §14 K13: retrying a record that is too large only holds its key back; it is parked at once.
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new org.apache.kafka.common.errors.RecordTooLargeException("too large"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        assertThatThrownBy(() -> transport.publish(message("{\"tenantId\":100}")))
            .isInstanceOf(OutboxTransportException.class)
            .satisfies(failure -> {
                OutboxTransportException ex = (OutboxTransportException) failure;
                assertThat(ex.errorCode()).isEqualTo("KAFKA_RECORD_REJECTED");
                assertThat(ex.permanent()).isTrue();
            });
        verify(broker, never()).suspect();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aSendThatFailsOnTheConnectionMakesTheBrokerProbeAgain() {
        // Kafka design D21: the relay's next readiness check asks the broker instead of trusting the cached answer.
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new org.apache.kafka.common.errors.TimeoutException("expired"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        assertThatThrownBy(() -> transport.publish(message("{\"tenantId\":100}")))
            .isInstanceOf(OutboxTransportException.class)
            .satisfies(failure -> assertThat(((OutboxTransportException) failure).permanent()).isFalse());
        verify(broker).suspect();
    }

    @Test
    void aCommandGoesToItsCommandTypesTopic() {
        // Kafka design §14 K22: commands travel like events, on brix.command.<commandType>.
        mockSendSuccess("prod-brix.command.TenantFirstOwnerAccepted");
        CanonicalOutboxMessage event = message("{\"tenantId\":100}");
        CanonicalOutboxMessage command = new CanonicalOutboxMessage(event.messageId(), event.eventId(), "COMMAND",
            event.messageType(), event.schemaVersion(), event.reliability(), event.producerPluginId(), event.scope(),
            event.tenantId(), event.partitionKey(), event.occurredAt(), event.correlationId(), event.causationId(),
            event.traceparent(), event.tracestate(), event.payload(), event.attemptCount());

        transport.publish(command);

        verify(kafkaTemplate).send(recordCaptor.capture());
        assertThat(recordCaptor.getValue().topic()).isEqualTo("prod-brix.command.TenantFirstOwnerAccepted");
        verify(broker).ensureCommandTopics(Set.of("TenantFirstOwnerAccepted"));
    }

    @Test
    void theTransportIsReadyWhileTheBrokerAnswers() {
        // Kafka design D21: while Kafka cannot be reached the relay claims nothing and spends no attempts.
        when(broker.reachable()).thenReturn(false, true);

        assertThat(transport.ready()).isFalse();
        assertThat(transport.ready()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aTopicThatCannotBePreparedIsATransientFailure() {
        doThrow(new IllegalStateException("broker unavailable")).when(broker).ensureEventTopics(any());

        assertThatThrownBy(() -> transport.publish(message("{\"tenantId\":100}")))
            .isInstanceOf(OutboxTransportException.class)
            .satisfies(failure -> {
                OutboxTransportException ex = (OutboxTransportException) failure;
                assertThat(ex.errorCode()).isEqualTo("KAFKA_TOPIC_UNAVAILABLE");
                assertThat(ex.permanent()).isFalse();
            });
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    @Test
    void anEventTypeThatIsNoTopicNameIsAPermanentFailure() {
        CanonicalOutboxMessage message = new CanonicalOutboxMessage("msg-1", "evt-1", "EVENT", "Tenant Accepted", "1.0.0",
            "CRITICAL", "platform-tenant", "PLATFORM", null, "k", OffsetDateTime.parse("2026-07-28T01:00:00Z"), "corr-1",
            null, null, null, "{}", 1);

        assertThatThrownBy(() -> transport.publish(message))
            .isInstanceOf(OutboxTransportException.class)
            .satisfies(failure -> {
                OutboxTransportException ex = (OutboxTransportException) failure;
                assertThat(ex.errorCode()).isEqualTo("KAFKA_TOPIC_INVALID");
                assertThat(ex.permanent()).isTrue();
            });
    }

    @SuppressWarnings("unchecked")
    private void mockSendSuccess(String topic) {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition(topic, 0), 0L, 0, 0L, 0, 0);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.completedFuture(new SendResult<>(null, metadata)));
    }

    private static CanonicalOutboxMessage message(String payload) {
        return new CanonicalOutboxMessage(
            "msg-1",
            "evt-1",
            "EVENT",
            "TenantFirstOwnerAccepted",
            "1.0.0",
            "CRITICAL",
            "platform-tenant",
            "TENANT",
            100L,
            "tenant-100",
            OffsetDateTime.parse("2026-07-28T01:00:00Z"),
            "corr-1",
            "cause-1",
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
            "brix=tenant",
            payload,
            1);
    }

    private static void assertHeader(ProducerRecord<String, String> record, String key, String expected) {
        assertThat(record.headers().lastHeader(key)).isNotNull();
        assertThat(new String(record.headers().lastHeader(key).value())).isEqualTo(expected);
    }
}
