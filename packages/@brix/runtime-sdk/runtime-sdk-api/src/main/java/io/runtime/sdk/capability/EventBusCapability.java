/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.sdk.capability;

import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.IntegrationEvent;

import io.runtime.sdk.annotation.Since;

/**
 * Event Bus Capability Contract
 *
 * <p>Publishes a plugin's integration events without the plugin knowing the transport (outbox and relay, Kafka or
 * in-process delivery). The plugin declares each event type it publishes in {@code META-INF/brix/plugin-manifest.yaml}
 * under {@code events.publishes}, with its schema and its reliability; the runtime chooses the path from that
 * declaration, binds the producer and the tenant from the calling unit of work, and refuses an event the manifest
 * does not declare (root BRX-RS-MESSAGE-017 to MESSAGE-022; Kafka design §14 K20, K21).</p>
 *
 * <h3>Reliability</h3>
 * <table border="1">
 *   <tr><th>Manifest {@code reliability}</th><th>Path</th><th>Promise</th></tr>
 *   <tr><td>CRITICAL, STANDARD</td><td>The plugin's own outbox, written in its current unit of work</td>
 *       <td>Sent once the business change commits, at least once; never sent when it rolls back</td></tr>
 *   <tr><td>BEST_EFFORT</td><td>Sent directly after the unit of work commits (at once outside one)</td>
 *       <td>No retry, no promise of delivery</td></tr>
 * </table>
 *
 * <h3>Ordering</h3>
 * <p>Subscribers see the events of one event type with the same partition key in the order they were published.
 * Nothing else is ordered: not across event types, not across keys. A subscriber that needs the order of one
 * aggregate across several event types compares an aggregate version the payloads carry (Kafka design D31).</p>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * // In the plugin's configure(...): the factory runs once the runtime has started the plugin.
 * bootstrap.bindEndpointHandlerFactory("orders.place.v1", context -> new PlaceOrder(
 *     context.require(PersistenceCapability.class), context.require(EventBusCapability.class)));
 *
 * // In the handler: the order and its event commit together.
 * persistence.unitOfWork().required(() -> {
 *     orders.save(order);
 *     eventBus.publishIntegration(new OrderPlaced(order.id(), order.tenantId(), order.version()));
 *     return order;
 * });
 * }</pre>
 *
 * <p>Subscriptions are not made through this interface: a subscriber declares them under
 * {@code events.subscribes} and binds a {@link io.runtime.sdk.plugin.EventHandler} factory.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see DomainEvent
 * @see IntegrationEvent
 */
@Since("3.0.0")
public interface EventBusCapability {

    /**
     * Publish domain event
     *
     * <p>Domain events propagate within the module, used for event-driven
     * architecture in Domain-Driven Design. The Runtime Shell may convert
     * specific domain events to integration events and route to other modules.</p>
     *
     * <h4>Delivery Guarantees</h4>
     * <ul>
     *   <li>Within module: sync/async depends on Host configuration</li>
     *   <li>Cross-module: determined by Runtime Shell whether to convert to IntegrationEvent</li>
     * </ul>
     *
     * @param event The domain event to publish, must not be null
     * @throws IllegalArgumentException if event is null
     * @throws EventPublishException if publishing fails
     */
    void publish(DomainEvent event);

    /**
     * Publish integration event
     *
     * <p>Integration events are used for cross-module, cross-system communication,
     * the core mechanism for module decoupling. The Runtime Shell routes events
     * to corresponding modules based on subscription declarations in manifests.</p>
     *
     * <h4>Delivery Guarantees</h4>
     * <ul>
     *   <li>CRITICAL and STANDARD events require transactional outbox and persistent inbox</li>
     *   <li>BEST_EFFORT events may be delivered without an at-least-once promise</li>
     *   <li>Runtime Shell must fail fast when the manifest declaration and publish path disagree</li>
     * </ul>
     *
     * <h4>Ordering Guarantees</h4>
     * <p>Events of one event type with the same partition key reach each subscriber in publish order; there is no
     * ordering across event types or keys (root BRX-RS-MESSAGE-027; Kafka design D31).</p>
     *
     * @param event The integration event to publish, must not be null
     * @throws IllegalArgumentException if event is null
     * @throws EventPublishException if publishing fails
     */
    void publishIntegration(IntegrationEvent event);
}
