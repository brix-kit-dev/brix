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

import java.util.Map;
import java.util.Optional;
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
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import io.runtime.orchestrator.event.conformance.RecordingMessageTracing;
import io.runtime.orchestrator.tracing.MessageTracing;
import io.runtime.sdk.capability.EventPublishException;
import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.EventReliability;
import io.runtime.sdk.event.IntegrationEvent;

/**
 * The direct bus carries BEST_EFFORT integration events as canonical envelopes on {@code brix.event.<eventType>}
 * and refuses what must go through an outbox (Kafka design §13 K2; root BRX-RS-MESSAGE-019, MESSAGE-022).
 */
@ExtendWith(MockitoExtension.class)
class KafkaEventBusCapabilityTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private KafkaEventBroker broker;

    @Captor
    private ArgumentCaptor<ProducerRecord<String, String>> recordCaptor;

    private final ObjectMapper json = JsonMapper.builder().configureForJackson2()
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private Map<String, EventReliability> declared = Map.of();
    private KafkaEventBusCapability bus;

    /** A legacy event that defines no typed payload. */
    public static final class MessageSentEvent extends IntegrationEvent {

        private final String messageId;

        MessageSentEvent(String tenantId, String messageId) {
            super("app-messenger", 1, tenantId);
            this.messageId = messageId;
        }

        public String getMessageId() {
            return messageId;
        }

        @Override
        public String getRoutingKey() {
            return "message:" + messageId;
        }
    }

    /** An event with a typed payload. */
    public static final class AvatarUploadedEvent extends IntegrationEvent {

        AvatarUploadedEvent() {
            super("app-storage");
        }

        @Override
        public String getRoutingKey() {
            return "user:7";
        }

        @Override
        public Object payload() {
            return Map.of("userId", 7, "objectKey", "avatars/7.png");
        }
    }

    @BeforeEach
    void setUp() {
        bus = new KafkaEventBusCapability(kafkaTemplate, new KafkaEventTopics("prod-"), broker, json, "enterprise-shell",
            () -> Optional.of("910001"), eventType -> Optional.ofNullable(declared.get(eventType)), null,
            MessageTracing.NOOP);
    }

    @Test
    void aBestEffortEventIsACanonicalEnvelopeOnItsTypesTopic() throws Exception {
        sendSucceeds();
        MessageSentEvent event = new MessageSentEvent("910001", "m-1");
        event.setTraceId(TRACE_ID);

        bus.publishIntegration(event);

        verify(broker).ensureEventTopics(Set.of("MessageSent"));
        verify(kafkaTemplate).send(recordCaptor.capture());
        ProducerRecord<String, String> record = recordCaptor.getValue();
        assertThat(record.topic()).isEqualTo("prod-brix.event.MessageSent");
        assertThat(record.key()).isEqualTo("message:m-1");
        Map<String, Object> envelope = json.readValue(record.value(), new TypeReference<>() {
        });
        assertThat(envelope).containsEntry("eventId", event.getEventId())
            .containsEntry("eventType", "MessageSent")
            .containsEntry("reliability", "BEST_EFFORT")
            .containsEntry("producerPluginId", "app-messenger")
            .containsEntry("scope", "TENANT")
            .containsEntry("tenantId", 910001)
            .containsEntry("schemaVersion", "1.0.0")
            .containsEntry("partitionKey", "message:m-1");
        assertThat((String) envelope.get("traceparent")).startsWith("00-" + TRACE_ID + "-").endsWith("-01");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");
        assertThat(payload).as("a legacy event is sent as it serializes").containsEntry("messageId", "m-1");
    }

    @Test
    void aTypedPayloadIsSentAsThePayload() throws Exception {
        sendSucceeds();

        AvatarUploadedEvent event = new AvatarUploadedEvent();

        bus.publishIntegration(event);

        verify(kafkaTemplate).send(recordCaptor.capture());
        Map<String, Object> envelope = json.readValue(recordCaptor.getValue().value(), new TypeReference<>() {
        });
        assertThat(envelope.get("payload")).isEqualTo(Map.of("userId", 7, "objectKey", "avatars/7.png"));
        assertThat(envelope).as("the caller's tenant fills an event that carries none")
            .containsEntry("scope", "TENANT").containsEntry("tenantId", 910001);
        assertThat((String) envelope.get("traceparent")).as("a trace id made up as a UUID still starts a trace")
            .startsWith("00-" + event.getTraceId().replace("-", "") + "-");
    }

    @Test
    void anEventIsSentInItsProducerSpan() throws Exception {
        // Kafka design §13 K8: with a tracing adapter the envelope carries the PRODUCER span's context.
        sendSucceeds();
        RecordingMessageTracing tracing = new RecordingMessageTracing();
        bus = new KafkaEventBusCapability(kafkaTemplate, new KafkaEventTopics(""), broker, json, "enterprise-shell",
            () -> Optional.of("910001"), eventType -> Optional.empty(), null, tracing);

        bus.publishIntegration(new MessageSentEvent("910001", "m-1"));

        verify(kafkaTemplate).send(recordCaptor.capture());
        Map<String, Object> envelope = json.readValue(recordCaptor.getValue().value(), new TypeReference<>() {
        });
        RecordingMessageTracing.Recorded span = tracing.spans(RecordingMessageTracing.PRODUCER).get(0);
        assertThat(envelope.get("traceparent")).isEqualTo(span.traceparent());
        assertThat(span.failed()).isFalse();
    }

    @Test
    void anEventNeedsATenant() {
        bus = new KafkaEventBusCapability(kafkaTemplate, new KafkaEventTopics(""), broker, json, "enterprise-shell",
            Optional::empty, eventType -> Optional.empty(), null, MessageTracing.NOOP);

        assertThatThrownBy(() -> bus.publishIntegration(new AvatarUploadedEvent()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tenantId");
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    @Test
    void anEventTypeAManifestDeclaresCriticalGoesThroughItsOwnersBus() {
        declared = Map.of("MessageSent", EventReliability.CRITICAL);

        assertThatThrownBy(() -> bus.publishIntegration(new MessageSentEvent("910001", "m-1")))
            .isInstanceOf(EventPublishException.class)
            .hasMessageContaining("PluginContext");
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    @Test
    void anEventTypeAPluginDeclaresIsRefusedEvenWhenBestEffort() {
        // Kafka design §14 K21: this bus takes the producer and tenant the event names, so it cannot carry a type a
        // plugin declares; that plugin's own bus binds them.
        declared = Map.of("MessageSent", EventReliability.BEST_EFFORT);

        assertThatThrownBy(() -> bus.publishIntegration(new MessageSentEvent("910001", "m-1")))
            .isInstanceOf(EventPublishException.class)
            .hasMessageContaining("PluginContext");
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    @Test
    void aTenantThatIsNoTenantIdIsRefused() {
        assertThatThrownBy(() -> bus.publishIntegration(new MessageSentEvent("default", "m-1")))
            .isInstanceOf(EventPublishException.class)
            .hasMessageContaining("default");
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    @Test
    void aTopicThatCannotBePreparedFailsThePublish() {
        doThrow(new IllegalStateException("broker unavailable")).when(broker).ensureEventTopics(any());

        assertThatThrownBy(() -> bus.publishIntegration(new MessageSentEvent("910001", "m-1")))
            .isInstanceOf(EventPublishException.class)
            .hasMessageContaining("KAFKA_TOPIC_UNAVAILABLE");
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aSendTheBrokerRefusesFailsThePublish() {
        CompletableFuture<SendResult<String, String>> refused = new CompletableFuture<>();
        refused.completeExceptionally(new IllegalStateException("broker refused"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(refused);

        assertThatThrownBy(() -> bus.publishIntegration(new MessageSentEvent("910001", "m-1")))
            .isInstanceOf(EventPublishException.class)
            .hasMessageContaining("KAFKA_SEND_FAILED");
    }

    @Test
    void aDomainEventStaysInsideItsPlugin() {
        DomainEvent event = new DomainEvent() {
            @Override
            public String getAggregateId() {
                return "booking-1";
            }

            @Override
            public String getAggregateType() {
                return "Booking";
            }
        };

        assertThatThrownBy(() -> bus.publish(event)).isInstanceOf(EventPublishException.class);
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    @SuppressWarnings("unchecked")
    private void sendSucceeds() {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition("t", 0), 0L, 0, 0L, 0, 0);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.completedFuture(new SendResult<>(null, metadata)));
    }
}
