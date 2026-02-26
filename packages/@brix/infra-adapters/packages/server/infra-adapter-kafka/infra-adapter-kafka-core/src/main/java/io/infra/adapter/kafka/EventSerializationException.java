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

/**
 * 事件序列化异常。
 *
 * <p>当事件对象无法被序列化为 JSON 时抛出此异常。
 * 该异常被 {@code KafkaEventBusCapability} 和 {@code OutboxEventPublisher}
 * 在事件发布过程中使用，用于封装底层 Jackson 序列化错误，
 * 提供统一的序列化失败语义。</p>
 *
 * <p>可能的原因包括：</p>
 * <ul>
 *   <li>事件对象包含无法序列化的字段（如循环引用）</li>
 *   <li>缺少无参构造函数或 getter 方法</li>
 *   <li>Jackson 序列化配置不正确</li>
 * </ul>
 *
 * <p>使用位置：</p>
 * <ul>
 *   <li>{@code KafkaEventBusCapability#serializeEvent} — Kafka 事件发布序列化</li>
 *   <li>{@code OutboxEventPublisher#serializePayload} — Outbox 模式事件持久化序列化</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.0.0
 * @see io.infra.adapter.kafka.KafkaEventBusCapability
 */
public class EventSerializationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造函数
     * 
     * @param message 错误消息
     */
    public EventSerializationException(String message) {
        super(message);
    }

    /**
     * 带原因的构造函数
     * 
     * @param message 错误消息
     * @param cause   原始异常
     */
    public EventSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
