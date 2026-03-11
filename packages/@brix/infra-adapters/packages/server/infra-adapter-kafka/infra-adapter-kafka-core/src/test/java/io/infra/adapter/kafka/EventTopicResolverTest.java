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

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.IntegrationEvent;

/**
 * Unit tests for {@link EventTopicResolver}
 *
 * <p>Validates event topic resolution logic: naming conventions, prefix handling,
 * CamelCase to kebab-case conversion, and Event suffix removal.</p>
 *
 * @author Brix Team
 * @since 3.0.0
 */
@DisplayName("EventTopicResolver Tests")
class EventTopicResolverTest {

    // ==================== Without Prefix Scenarios ====================

    @Nested
    @DisplayName("Without Environment Prefix")
    class WithoutPrefixTests {

        private EventTopicResolver resolver;

        @BeforeEach
        void setUp() {
            resolver = new EventTopicResolver();
        }

        @Test
        @DisplayName("resolveDomainTopic - should build correct domain event topic")
        void resolveDomainTopic_shouldBuildCorrectTopic() {
            DomainEvent event = createDomainEvent("Reservation");

            String topic = resolver.resolveDomainTopic(event, "booking");

            assertThat(topic).isEqualTo("domain.booking.reservation");
        }

        @Test
        @DisplayName("resolveDomainTopic - camelCase aggregate type should convert to kebab-case")
        void resolveDomainTopic_shouldConvertCamelCaseToKebab() {
            DomainEvent event = createDomainEvent("FlightBooking");

            String topic = resolver.resolveDomainTopic(event, "travel");

            assertThat(topic).isEqualTo("domain.travel.flight-booking");
        }

        @Test
        @DisplayName("resolveIntegrationTopic - should build correct integration event topic")
        void resolveIntegrationTopic_shouldBuildCorrectTopic() {
            IntegrationEvent event = createIntegrationEvent(
                "io.brix.app.booking.event.ReservationCreatedEvent"
            );

            String topic = resolver.resolveIntegrationTopic(event);

            assertThat(topic).isEqualTo("integration.reservation-created");
        }

        @Test
        @DisplayName("resolveIntegrationTopic - should use simple class name when no package name")
        void resolveIntegrationTopic_shouldHandleSimpleClassName() {
            IntegrationEvent event = createIntegrationEvent("OrderPlacedEvent");

            String topic = resolver.resolveIntegrationTopic(event);

            assertThat(topic).isEqualTo("integration.order-placed");
        }

        @Test
        @DisplayName("resolveIntegrationTopic - non-Event suffix should not be removed")
        void resolveIntegrationTopic_shouldNotRemoveNonEventSuffix() {
            IntegrationEvent event = createIntegrationEvent("UserNotification");

            String topic = resolver.resolveIntegrationTopic(event);

            assertThat(topic).isEqualTo("integration.user-notification");
        }

        @Test
        @DisplayName("resolveTopicByEventType - should extract topic from FQCN")
        void resolveTopicByEventType_shouldExtractFromFqcn() {
            String topic = resolver.resolveTopicByEventType(
                "io.brix.app.messenger.event.MessageSentEvent"
            );

            assertThat(topic).isEqualTo("integration.message-sent");
        }
    }

    // ==================== With Prefix Scenarios ====================

    @Nested
    @DisplayName("With Environment Prefix")
    class WithPrefixTests {

        private EventTopicResolver resolver;

        @BeforeEach
        void setUp() {
            resolver = new EventTopicResolver("dev-");
        }

        @Test
        @DisplayName("resolveDomainTopic - should include environment prefix")
        void resolveDomainTopic_shouldPrependPrefix() {
            DomainEvent event = createDomainEvent("Reservation");

            String topic = resolver.resolveDomainTopic(event, "booking");

            assertThat(topic).isEqualTo("dev-domain.booking.reservation");
        }

        @Test
        @DisplayName("resolveIntegrationTopic - should include environment prefix")
        void resolveIntegrationTopic_shouldPrependPrefix() {
            IntegrationEvent event = createIntegrationEvent("OrderCreatedEvent");

            String topic = resolver.resolveIntegrationTopic(event);

            assertThat(topic).isEqualTo("dev-integration.order-created");
        }
    }

    // ==================== Constructor Edge Cases ====================

    @Nested
    @DisplayName("Constructor Edge Cases")
    class ConstructorEdgeCaseTests {

        @Test
        @DisplayName("Empty string prefix should be equivalent to no prefix")
        void emptyPrefix_shouldBeEquivalentToNoPrefix() {
            EventTopicResolver resolver = new EventTopicResolver("");
            DomainEvent event = createDomainEvent("Order");

            String topic = resolver.resolveDomainTopic(event, "shop");

            assertThat(topic).isEqualTo("domain.shop.order");
        }

        @Test
        @DisplayName("Null prefix should be safely treated as empty string")
        void nullPrefix_shouldBeTreatedAsEmpty() {
            EventTopicResolver resolver = new EventTopicResolver(null);
            DomainEvent event = createDomainEvent("Payment");

            String topic = resolver.resolveDomainTopic(event, "billing");

            assertThat(topic).isEqualTo("domain.billing.payment");
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a mock domain event
     */
    private DomainEvent createDomainEvent(String aggregateType) {
        DomainEvent event = Mockito.mock(DomainEvent.class);
        Mockito.when(event.getAggregateType()).thenReturn(aggregateType);
        Mockito.when(event.getAggregateId()).thenReturn("test-id");
        return event;
    }

    /**
     * Creates a mock integration event
     */
    private IntegrationEvent createIntegrationEvent(String eventType) {
        IntegrationEvent event = Mockito.mock(IntegrationEvent.class);
        Mockito.when(event.getEventType()).thenReturn(eventType);
        return event;
    }
}
