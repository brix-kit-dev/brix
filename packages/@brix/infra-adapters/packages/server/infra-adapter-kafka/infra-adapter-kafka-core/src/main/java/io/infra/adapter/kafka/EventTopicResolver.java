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

import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.IntegrationEvent;

/**
 * Event Topic Resolution.
 * 
 * <p>Responsible for mapping event types to Kafka Topics, this is the core component for event routing.
 * Follows unified Topic naming conventions to ensure events are routed correctly.</p>
 * 
 * <h3>Topic Naming Conventions</h3>
 * <table border="1">
 *   <tr>
 *     <th>Event Type</th>
 *     <th>Topic Format</th>
 *     <th>Example</th>
 *   </tr>
 *   <tr>
 *     <td>Domain Event</td>
 *     <td>domain.{moduleId}.{aggregateType}</td>
 *     <td>domain.booking.reservation</td>
 *   </tr>
 *   <tr>
 *     <td>Integration Event</td>
 *     <td>integration.{eventTypeName}</td>
 *     <td>integration.reservation-created</td>
 *   </tr>
 * </table>
 * 
 * <h3>Design Considerations</h3>
 * <ul>
 *   <li><b>Domain Event Isolation</b>: Each module's domain events have independent Topics to avoid interference</li>
 *   <li><b>Integration Event Sharing</b>: Integration events use public Topics for cross-module subscription</li>
 *   <li><b>Naming Consistency</b>: Uses lowercase letters and hyphens, following Kafka best practices</li>
 * </ul>
 * 
 * @author Brix Platform Authors Platform Team
 * @since 3.0.0
 */
public class EventTopicResolver {

    /**
     * Domain event Topic prefix.
     */
    private static final String DOMAIN_TOPIC_PREFIX = "domain";

    /**
     * Integration event Topic prefix.
     */
    private static final String INTEGRATION_TOPIC_PREFIX = "integration";

    /**
     * Topic environment prefix (for multi-environment isolation).
     * 
     * <p>For example: dev-, staging-, prod-</p>
     */
    private final String topicPrefix;

    /**
     * Default constructor (no environment prefix).
     */
    public EventTopicResolver() {
        this("");
    }

    /**
     * Constructor with environment prefix.
     * 
     * @param topicPrefix Topic environment prefix, e.g. "dev-", "staging-"
     */
    public EventTopicResolver(String topicPrefix) {
        this.topicPrefix = topicPrefix != null ? topicPrefix : "";
    }

    /**
     * Resolve the target Topic for a domain event.
     * 
     * <p>Domain event Topic format: {prefix}domain.{moduleId}.{aggregateType}</p>
     * 
     * <h4>Example</h4>
     * <pre>{@code
     * // Without environment prefix
     * resolveDomainTopic(reservationEvent, "booking") 
     *     => "domain.booking.reservation"
     * 
     * // With environment prefix
     * new EventTopicResolver("dev-").resolveDomainTopic(reservationEvent, "booking")
     *     => "dev-domain.booking.reservation"
     * }</pre>
     * 
     * @param event    the domain event
     * @param moduleId the source module ID
     * @return the target Topic name
     */
    public String resolveDomainTopic(DomainEvent event, String moduleId) {
        // Get aggregate root type and convert to lowercase kebab-case format
        String aggregateType = toKebabCase(event.getAggregateType());
        
        // Build Topic: {prefix}domain.{moduleId}.{aggregateType}
        return buildTopic(DOMAIN_TOPIC_PREFIX, moduleId, aggregateType);
    }

    /**
     * Resolve the target Topic for an integration event.
     * 
     * <p>Integration event Topic format: {prefix}integration.{eventTypeName}</p>
     * 
     * <h4>Example</h4>
     * <pre>{@code
     * resolveIntegrationTopic(new ReservationCreatedEvent())
     *     => "integration.reservation-created"
     * }</pre>
     * 
     * @param event the integration event
     * @return the target Topic name
     */
    public String resolveIntegrationTopic(IntegrationEvent event) {
        // Get event type name and convert to lowercase kebab-case format
        String eventTypeName = extractEventTypeName(event.getEventType());
        
        // Build Topic: {prefix}integration.{eventTypeName}
        return buildTopic(INTEGRATION_TOPIC_PREFIX, eventTypeName);
    }

    /**
     * Resolve Topic by event type string (for consumer registration).
     * 
     * @param eventType the event type (fully qualified class name)
     * @return the Topic name
     */
    public String resolveTopicByEventType(String eventType) {
        String eventTypeName = extractEventTypeName(eventType);
        return buildTopic(INTEGRATION_TOPIC_PREFIX, eventTypeName);
    }

    /**
     * Build Topic name.
     * 
     * @param parts Topic components
     * @return the complete Topic name
     */
    private String buildTopic(String... parts) {
        StringBuilder sb = new StringBuilder();
        if (!topicPrefix.isEmpty()) {
            sb.append(topicPrefix);
        }
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(".");
            }
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    /**
     * Extract event name from fully qualified event type.
     * 
     * <p>For example: io.brix.app.booking.event.ReservationCreatedEvent => reservation-created</p>
     * 
     * @param eventType the fully qualified event type
     * @return the event name (kebab-case)
     */
    private String extractEventTypeName(String eventType) {
        // Get simple class name
        String simpleName = eventType;
        int lastDot = eventType.lastIndexOf('.');
        if (lastDot > 0) {
            simpleName = eventType.substring(lastDot + 1);
        }
        
        // Remove "Event" suffix
        if (simpleName.endsWith("Event")) {
            simpleName = simpleName.substring(0, simpleName.length() - 5);
        }
        
        // Convert to kebab-case
        return toKebabCase(simpleName);
    }

    /**
     * Convert camelCase to lowercase hyphenated format (kebab-case).
     * 
     * <p>For example: ReservationCreated => reservation-created</p>
     * 
     * @param input the camelCase string
     * @return the kebab-case formatted string
     */
    private String toKebabCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('-');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
