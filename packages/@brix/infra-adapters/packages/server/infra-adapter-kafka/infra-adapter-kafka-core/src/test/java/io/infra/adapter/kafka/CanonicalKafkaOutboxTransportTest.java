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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxTransportException;

@ExtendWith(MockitoExtension.class)
class CanonicalKafkaOutboxTransportTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Captor
    private ArgumentCaptor<ProducerRecord<String, String>> recordCaptor;

    private ObjectMapper objectMapper;
    private CanonicalKafkaOutboxTransport transport;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        transport = new CanonicalKafkaOutboxTransport(
            kafkaTemplate,
            new EventTopicResolver("prod-"),
            objectMapper,
            Duration.ofSeconds(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesCanonicalEnvelopeWithPartitionKeyAndHeaders() throws Exception {
        mockSendSuccess("prod-integration.tenant-first-owner-accepted");
        CanonicalOutboxMessage message = message("{\"tenantId\":100,\"memberId\":200}");

        transport.publish(message);

        verify(kafkaTemplate).send(recordCaptor.capture());
        ProducerRecord<String, String> record = recordCaptor.getValue();
        assertThat(record.topic()).isEqualTo("prod-integration.tenant-first-owner-accepted");
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
