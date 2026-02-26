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
 * {@link EventTopicResolver} 单元测试
 *
 * <p>验证事件 Topic 解析逻辑：命名规范、前缀处理、
 * CamelCase 到 kebab-case 转换、Event 后缀移除。</p>
 *
 * @author Brix Team
 * @since 3.0.0
 */
@DisplayName("EventTopicResolver 测试")
class EventTopicResolverTest {

    // ==================== 无前缀场景 ====================

    @Nested
    @DisplayName("无环境前缀")
    class WithoutPrefixTests {

        private EventTopicResolver resolver;

        @BeforeEach
        void setUp() {
            resolver = new EventTopicResolver();
        }

        @Test
        @DisplayName("resolveDomainTopic - 应生成正确的领域事件 Topic")
        void resolveDomainTopic_shouldBuildCorrectTopic() {
            DomainEvent event = createDomainEvent("Reservation");

            String topic = resolver.resolveDomainTopic(event, "booking");

            assertThat(topic).isEqualTo("domain.booking.reservation");
        }

        @Test
        @DisplayName("resolveDomainTopic - 驼峰聚合类型应转为 kebab-case")
        void resolveDomainTopic_shouldConvertCamelCaseToKebab() {
            DomainEvent event = createDomainEvent("FlightBooking");

            String topic = resolver.resolveDomainTopic(event, "travel");

            assertThat(topic).isEqualTo("domain.travel.flight-booking");
        }

        @Test
        @DisplayName("resolveIntegrationTopic - 应生成正确的集成事件 Topic")
        void resolveIntegrationTopic_shouldBuildCorrectTopic() {
            IntegrationEvent event = createIntegrationEvent(
                "com.shinwa.app.booking.event.ReservationCreatedEvent"
            );

            String topic = resolver.resolveIntegrationTopic(event);

            assertThat(topic).isEqualTo("integration.reservation-created");
        }

        @Test
        @DisplayName("resolveIntegrationTopic - 无包名时应直接使用简单类名")
        void resolveIntegrationTopic_shouldHandleSimpleClassName() {
            IntegrationEvent event = createIntegrationEvent("OrderPlacedEvent");

            String topic = resolver.resolveIntegrationTopic(event);

            assertThat(topic).isEqualTo("integration.order-placed");
        }

        @Test
        @DisplayName("resolveIntegrationTopic - 非 Event 后缀不应被移除")
        void resolveIntegrationTopic_shouldNotRemoveNonEventSuffix() {
            IntegrationEvent event = createIntegrationEvent("UserNotification");

            String topic = resolver.resolveIntegrationTopic(event);

            assertThat(topic).isEqualTo("integration.user-notification");
        }

        @Test
        @DisplayName("resolveTopicByEventType - 应从完整类名解析 Topic")
        void resolveTopicByEventType_shouldExtractFromFqcn() {
            String topic = resolver.resolveTopicByEventType(
                "com.shinwa.app.messenger.event.MessageSentEvent"
            );

            assertThat(topic).isEqualTo("integration.message-sent");
        }
    }

    // ==================== 有前缀场景 ====================

    @Nested
    @DisplayName("有环境前缀")
    class WithPrefixTests {

        private EventTopicResolver resolver;

        @BeforeEach
        void setUp() {
            resolver = new EventTopicResolver("dev-");
        }

        @Test
        @DisplayName("resolveDomainTopic - 应包含环境前缀")
        void resolveDomainTopic_shouldPrependPrefix() {
            DomainEvent event = createDomainEvent("Reservation");

            String topic = resolver.resolveDomainTopic(event, "booking");

            assertThat(topic).isEqualTo("dev-domain.booking.reservation");
        }

        @Test
        @DisplayName("resolveIntegrationTopic - 应包含环境前缀")
        void resolveIntegrationTopic_shouldPrependPrefix() {
            IntegrationEvent event = createIntegrationEvent("OrderCreatedEvent");

            String topic = resolver.resolveIntegrationTopic(event);

            assertThat(topic).isEqualTo("dev-integration.order-created");
        }
    }

    // ==================== 构造函数边界场景 ====================

    @Nested
    @DisplayName("构造函数边界场景")
    class ConstructorEdgeCaseTests {

        @Test
        @DisplayName("空字符串前缀应等同于无前缀")
        void emptyPrefix_shouldBeEquivalentToNoPrefix() {
            EventTopicResolver resolver = new EventTopicResolver("");
            DomainEvent event = createDomainEvent("Order");

            String topic = resolver.resolveDomainTopic(event, "shop");

            assertThat(topic).isEqualTo("domain.shop.order");
        }

        @Test
        @DisplayName("null 前缀应安全处理为空字符串")
        void nullPrefix_shouldBeTreatedAsEmpty() {
            EventTopicResolver resolver = new EventTopicResolver(null);
            DomainEvent event = createDomainEvent("Payment");

            String topic = resolver.resolveDomainTopic(event, "billing");

            assertThat(topic).isEqualTo("domain.billing.payment");
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建模拟的领域事件
     */
    private DomainEvent createDomainEvent(String aggregateType) {
        DomainEvent event = Mockito.mock(DomainEvent.class);
        Mockito.when(event.getAggregateType()).thenReturn(aggregateType);
        Mockito.when(event.getAggregateId()).thenReturn("test-id");
        return event;
    }

    /**
     * 创建模拟的集成事件
     */
    private IntegrationEvent createIntegrationEvent(String eventType) {
        IntegrationEvent event = Mockito.mock(IntegrationEvent.class);
        Mockito.when(event.getEventType()).thenReturn(eventType);
        return event;
    }
}
