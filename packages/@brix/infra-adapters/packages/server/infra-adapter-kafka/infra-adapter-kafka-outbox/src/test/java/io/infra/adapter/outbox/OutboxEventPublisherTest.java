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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.infra.adapter.kafka.EventTopicResolver;
import io.infra.adapter.kafka.config.KafkaEventBusProperties;
import io.runtime.sdk.event.IntegrationEvent;

class OutboxEventPublisherTest {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final EventTopicResolver topicResolver = mock(EventTopicResolver.class);
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final KafkaEventBusProperties.OutboxProperties properties = new KafkaEventBusProperties.OutboxProperties();

    @Test
    void saveForLaterPersistsRequiredEventMetadata() {
        TestIntegrationEvent event = new TestIntegrationEvent(null)
                .withCorrelation("correlation-1")
                .withRouting("order-1");
        event.setSchemaVersion(2);

        when(repository.existsByEventId(event.getEventId())).thenReturn(false);
        when(topicResolver.resolveIntegrationTopic(event)).thenReturn("integration.order-created");

        OutboxEventPublisher publisher = new OutboxEventPublisher(
                repository,
                kafkaTemplate,
                topicResolver,
                objectMapper,
                properties,
                () -> Optional.of("tenant-1"),
                null);

        publisher.saveForLater(event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo("tenant-1");
        assertThat(saved.getTraceId()).isNotBlank();
        assertThat(saved.getSchemaVersion()).isEqualTo(2);
        assertThat(saved.getCorrelationId()).isEqualTo("correlation-1");
        assertThat(saved.getRoutingKey()).isEqualTo("order-1");
    }

    @Test
    void processOutboxRoutesMaxRetryFailureToDlqWithMetadataHeaders() {
        properties.setMaxRetryCount(1);
        properties.setDlqTopicSuffix(".DLQ");

        TestIntegrationEvent event = new TestIntegrationEvent("tenant-1")
                .withCorrelation("correlation-1")
                .withRouting("order-1");
        event.setTraceId("trace-1");
        OutboxEvent outboxEvent = OutboxEvent.from(event, "{\"event\":\"payload\"}", "integration.order-created");

        CompletableFuture<SendResult<String, String>> failedSend = new CompletableFuture<>();
        failedSend.completeExceptionally(new RuntimeException("broker unavailable"));

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> successfulDlqSend =
                CompletableFuture.completedFuture(mock(SendResult.class));

        when(repository.findPendingEvents(anyInt())).thenReturn(List.of(outboxEvent));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failedSend, successfulDlqSend);

        OutboxEventPublisher publisher = new OutboxEventPublisher(
                repository,
                kafkaTemplate,
                topicResolver,
                objectMapper,
                properties,
                () -> Optional.of("tenant-1"),
                null);

        publisher.processOutbox();

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEvent.Status.DEAD_LETTERED);

        ArgumentCaptor<ProducerRecord<String, String>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, org.mockito.Mockito.times(2)).send(recordCaptor.capture());
        ProducerRecord<String, String> dlqRecord = recordCaptor.getAllValues().get(1);
        assertThat(dlqRecord.topic()).isEqualTo("integration.order-created.DLQ");
        assertThat(dlqRecord.headers().lastHeader("tenantId")).isNotNull();
        assertThat(dlqRecord.headers().lastHeader("traceId")).isNotNull();
        assertThat(dlqRecord.headers().lastHeader("schemaVersion")).isNotNull();
        assertThat(dlqRecord.headers().lastHeader("dlqOriginalTopic")).isNotNull();
    }

    private static final class TestIntegrationEvent extends IntegrationEvent {
        private String routingKey;

        private TestIntegrationEvent(String tenantId) {
            super("test-module", tenantId);
        }

        TestIntegrationEvent withRouting(String routingKey) {
            this.routingKey = routingKey;
            return this;
        }

        TestIntegrationEvent withCorrelation(String correlationId) {
            withCorrelationId(correlationId);
            return this;
        }

        @Override
        public String getRoutingKey() {
            return routingKey;
        }
    }
}
