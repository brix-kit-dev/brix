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

/**
 * Event Bus Capability Contract
 *
 * <p>Defines the standard interface for module event publishing, one of the
 * core capabilities of the Runtime Shell. Modules use this interface to publish
 * events without knowing the underlying transport (Kafka/RabbitMQ/HTTP/In-Memory).</p>
 *
 * <h3>Core Responsibilities</h3>
 * <ul>
 *   <li>Publish domain events (within module)</li>
 *   <li>Publish integration events (cross-module/cross-system)</li>
 *   <li>Event persistence guarantee (Outbox pattern)</li>
 * </ul>
 *
 * <h3>Event Types</h3>
 * <table border="1">
 *   <tr><th>Type</th><th>Scope</th><th>Purpose</th></tr>
 *   <tr><td>DomainEvent</td><td>Within Module</td><td>Business events in DDD</td></tr>
 *   <tr><td>IntegrationEvent</td><td>Cross-Module/System</td><td>Decoupled communication between modules</td></tr>
 * </table>
 *
 * <h3>Design Constraints</h3>
 * <ul>
 *   <li><b>No Subscribe Method</b>: Subscriptions are declared in module-manifest.yaml</li>
 *   <li><b>Infrastructure Transparent</b>: Modules don't know if events go to Kafka/HTTP/Memory</li>
 *   <li><b>At-Least-Once Delivery</b>: Consumers must implement idempotent handling</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Inject
 * private EventBusCapability eventBus;
 *
 * public void createReservation(ReservationCommand command) {
 *     // Business logic...
 *     Reservation reservation = // ...
 *
 *     // Publish domain event
 *     eventBus.publish(new ReservationCreatedEvent(reservation.getId()));
 *
 *     // Or publish integration event (to notify other modules)
 *     eventBus.publishIntegration(
 *         new ReservationCreatedIntegrationEvent(reservation.getId(), command.getCustomerId())
 *     );
 * }
 * }</pre>
 *
 * <h3>Implementation Notes</h3>
 * <p>This interface is implemented by the Host layer, with different implementations:</p>
 * <ul>
 *   <li>Full Product Host: Kafka + Outbox pattern</li>
 *   <li>Embedded Host: HTTP Webhook / In-Memory queue</li>
 * </ul>
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see DomainEvent
 * @see IntegrationEvent
 */
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
     *   <li>At-Least-Once delivery</li>
     *   <li>Uses Outbox pattern for transactional consistency</li>
     *   <li>Consumers must implement idempotent handling</li>
     * </ul>
     *
     * <h4>Ordering Guarantees</h4>
     * <p>Events with the same routingKey are delivered in order via Kafka Partition Key.</p>
     *
     * @param event The integration event to publish, must not be null
     * @throws IllegalArgumentException if event is null
     * @throws EventPublishException if publishing fails
     */
    void publishIntegration(IntegrationEvent event);
}
