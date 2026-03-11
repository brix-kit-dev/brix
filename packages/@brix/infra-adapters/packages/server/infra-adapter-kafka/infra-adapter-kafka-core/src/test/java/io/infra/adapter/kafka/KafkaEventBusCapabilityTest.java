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

import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Captor;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.runtime.sdk.capability.EventPublishException;
import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.IntegrationEvent;

/**
 * Unit tests for {@link KafkaEventBusCapability}
 *
 * <p>Uses Mockito to mock KafkaTemplate, validating event publishing
 * topic routing, headers construction, serialization, and exception handling.</p>
 *
 * @author Brix Team
 * @since 3.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaEventBusCapability Tests")
class KafkaEventBusCapabilityTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Captor
    private ArgumentCaptor<ProducerRecord<String, String>> recordCaptor;

    private ObjectMapper objectMapper;
    private EventTopicResolver topicResolver;
    private KafkaEventBusCapability eventBus;

    private static final String MODULE_ID = "booking";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        topicResolver = new EventTopicResolver();
        eventBus = new KafkaEventBusCapability(kafkaTemplate, topicResolver, objectMapper, MODULE_ID);
    }

    // ==================== Constructor Validation ====================

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorTests {

        @Test
        @DisplayName("kafkaTemplate null should throw NPE")
        void shouldThrow_whenKafkaTemplateNull() {
            assertThatThrownBy(() ->
                new KafkaEventBusCapability(null, topicResolver, objectMapper, MODULE_ID))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("topicResolver null should throw NPE")
        void shouldThrow_whenTopicResolverNull() {
            assertThatThrownBy(() ->
                new KafkaEventBusCapability(kafkaTemplate, null, objectMapper, MODULE_ID))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("objectMapper null should throw NPE")
        void shouldThrow_whenObjectMapperNull() {
            assertThatThrownBy(() ->
                new KafkaEventBusCapability(kafkaTemplate, topicResolver, null, MODULE_ID))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("currentModuleId null should throw NPE")
        void shouldThrow_whenModuleIdNull() {
            assertThatThrownBy(() ->
                new KafkaEventBusCapability(kafkaTemplate, topicResolver, objectMapper, null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    // ==================== publish(DomainEvent) ====================

    @Nested
    @DisplayName("publish(DomainEvent)")
    class PublishDomainEventTests {

        @Test
        @DisplayName("Should send domain event to correct topic with correct key")
        @SuppressWarnings("unchecked")
        void shouldSendToCorrectTopic() {
            TestDomainEvent event = new TestDomainEvent("order-123", "Order");
            mockSendSuccess("domain.booking.order");

            eventBus.publish(event);

            verify(kafkaTemplate).send(recordCaptor.capture());
            ProducerRecord<String, String> record = recordCaptor.getValue();

            assertThat(record.topic()).isEqualTo("domain.booking.order");
            assertThat(record.key()).isEqualTo("order-123");
            assertThat(record.value()).contains("order-123");
        }

        @Test
        @DisplayName("Should include standard Kafka headers")
        @SuppressWarnings("unchecked")
        void shouldIncludeStandardHeaders() {
            TestDomainEvent event = new TestDomainEvent("agg-1", "Reservation");
            mockSendSuccess("domain.booking.reservation");

            eventBus.publish(event);

            verify(kafkaTemplate).send(recordCaptor.capture());
            ProducerRecord<String, String> record = recordCaptor.getValue();

            assertThat(record.headers().lastHeader("eventId")).isNotNull();
            assertThat(record.headers().lastHeader("eventType")).isNotNull();
            assertThat(record.headers().lastHeader("timestamp")).isNotNull();
            assertThat(record.headers().lastHeader("sourceModule")).isNotNull();
            assertThat(record.headers().lastHeader("aggregateId")).isNotNull();
            assertThat(record.headers().lastHeader("aggregateType")).isNotNull();
        }

        @Test
        @DisplayName("Null event should throw NPE")
        void shouldThrow_whenEventNull() {
            assertThatThrownBy(() -> eventBus.publish((DomainEvent) null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Send failure should throw EventPublishException")
        @SuppressWarnings("unchecked")
        void shouldThrowEventPublishException_whenSendFails() {
            TestDomainEvent event = new TestDomainEvent("fail-1", "Order");
            CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("broker down"));
            when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

            assertThatThrownBy(() -> eventBus.publish(event))
                .isInstanceOf(EventPublishException.class)
                .hasMessageContaining("Domain event publishing failed");
        }
    }

    // ==================== publishIntegration(IntegrationEvent) ====================

    @Nested
    @DisplayName("publishIntegration(IntegrationEvent)")
    class PublishIntegrationEventTests {

        @Test
        @DisplayName("Should send integration event to correct integration topic")
        @SuppressWarnings("unchecked")
        void shouldSendToIntegrationTopic() {
            TestIntegrationEvent event = new TestIntegrationEvent("routing-key-1");
            // eventType = full class name, topic resolver extracts simple name,
            // removes "Event" suffix and converts to kebab-case
            String expectedTopic = topicResolver.resolveIntegrationTopic(event);
            mockSendSuccess(expectedTopic);

            eventBus.publishIntegration(event);

            verify(kafkaTemplate).send(recordCaptor.capture());
            ProducerRecord<String, String> record = recordCaptor.getValue();

            assertThat(record.topic()).isEqualTo(expectedTopic);
            assertThat(record.key()).isEqualTo("routing-key-1");
        }

        @Test
        @DisplayName("Should use eventId as key when routingKey is null")
        @SuppressWarnings("unchecked")
        void shouldUseEventIdAsKey_whenRoutingKeyNull() {
            TestIntegrationEvent event = new TestIntegrationEvent(null);
            mockSendSuccess(topicResolver.resolveIntegrationTopic(event));

            eventBus.publishIntegration(event);

            verify(kafkaTemplate).send(recordCaptor.capture());
            ProducerRecord<String, String> record = recordCaptor.getValue();

            assertThat(record.key()).isEqualTo(event.getEventId());
        }

        @Test
        @DisplayName("Should include standard integration event headers (including sourceModule)")
        @SuppressWarnings("unchecked")
        void shouldIncludeIntegrationHeaders() {
            TestIntegrationEvent event = new TestIntegrationEvent("key-1");
            mockSendSuccess(topicResolver.resolveIntegrationTopic(event));

            eventBus.publishIntegration(event);

            verify(kafkaTemplate).send(recordCaptor.capture());
            ProducerRecord<String, String> record = recordCaptor.getValue();

            assertThat(record.headers().lastHeader("eventId")).isNotNull();
            assertThat(record.headers().lastHeader("eventType")).isNotNull();
            assertThat(record.headers().lastHeader("sourceModule")).isNotNull();
            assertThat(record.headers().lastHeader("routingKey")).isNotNull();
        }

        @Test
        @DisplayName("Null event should throw NPE")
        void shouldThrow_whenEventNull() {
            assertThatThrownBy(() -> eventBus.publishIntegration(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Send failure should throw EventPublishException")
        @SuppressWarnings("unchecked")
        void shouldThrowEventPublishException_whenSendFails() {
            TestIntegrationEvent event = new TestIntegrationEvent("key-2");
            CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("network error"));
            when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

            assertThatThrownBy(() -> eventBus.publishIntegration(event))
                .isInstanceOf(EventPublishException.class)
                .hasMessageContaining("Integration event publishing failed");
        }
    }

    // ==================== Helper Methods and Test Subclasses ====================

    @SuppressWarnings("unchecked")
    private void mockSendSuccess(String topic) {
        RecordMetadata metadata = new RecordMetadata(
            new TopicPartition(topic, 0), 0L, 0, 0L, 0, 0);
        SendResult<String, String> sendResult = new SendResult<>(null, metadata);
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);
    }

    /**
     * Test domain event
     */
    static class TestDomainEvent extends DomainEvent {

        private final String aggregateId;
        private final String aggregateType;

        TestDomainEvent(String aggregateId, String aggregateType) {
            this.aggregateId = aggregateId;
            this.aggregateType = aggregateType;
        }

        @Override
        public String getAggregateId() { return aggregateId; }

        @Override
        public String getAggregateType() { return aggregateType; }
    }

    /**
     * Test integration event
     */
    static class TestIntegrationEvent extends IntegrationEvent {

        private final String routingKey;

        TestIntegrationEvent(String routingKey) {
            super("test-module");
            this.routingKey = routingKey;
        }

        @Override
        public String getRoutingKey() { return routingKey; }

        @Override
        public String getSourceModule() { return "test-module"; }
    }
}
