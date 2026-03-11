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
 * Event serialization exception.
 *
 * <p>Thrown when an event object cannot be serialized to JSON.
 * This exception is used by {@code KafkaEventBusCapability} and {@code OutboxEventPublisher}
 * during event publishing to wrap underlying Jackson serialization errors,
 * providing unified serialization failure semantics.</p>
 *
 * <p>Possible causes include:</p>
 * <ul>
 *   <li>Event object contains non-serializable fields (e.g., circular references)</li>
 *   <li>Missing no-arg constructor or getter methods</li>
 *   <li>Incorrect Jackson serialization configuration</li>
 * </ul>
 *
 * <p>Usage locations:</p>
 * <ul>
 *   <li>{@code KafkaEventBusCapability#serializeEvent} — Kafka event publishing serialization</li>
 *   <li>{@code OutboxEventPublisher#serializePayload} — Outbox pattern event persistence serialization</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.0.0
 * @see io.infra.adapter.kafka.KafkaEventBusCapability
 */
public class EventSerializationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     * 
     * @param message the error message
     */
    public EventSerializationException(String message) {
        super(message);
    }

    /**
     * Constructor with cause.
     * 
     * @param message the error message
     * @param cause   the original exception
     */
    public EventSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
