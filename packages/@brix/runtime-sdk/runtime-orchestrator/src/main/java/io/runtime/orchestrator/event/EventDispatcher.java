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
package io.runtime.orchestrator.event;

import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.IntegrationEvent;

import java.util.function.Consumer;

/**
 * Event Dispatcher.
 * 
 * <p>Responsible for event dispatch and routing. Receives events from EventBusCapability
 * and dispatches them to subscribed module handlers.</p>
 * 
 * <h3>Core Responsibilities</h3>
 * <ul>
 *   <li>Manages event subscriptions</li>
 *   <li>Dispatches domain events and integration events</li>
 *   <li>Supports synchronous and asynchronous dispatch</li>
 *   <li>Event filtering and transformation</li>
 * </ul>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * // Subscribe to event
 * dispatcher.subscribe(OrderCreatedEvent.class, event -> {
 *     processNewOrder(event);
 * });
 * 
 * // Dispatch event
 * dispatcher.dispatch(new OrderCreatedEvent(orderId));
 * 
 * // Async dispatch
 * dispatcher.dispatchAsync(new OrderCreatedEvent(orderId));
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public interface EventDispatcher {

    /**
     * Subscribes to domain event.
     * 
     * @param eventType event type
     * @param handler event handler
     * @param <T> event type
     * @return subscription handle, used for unsubscribing
     */
    <T extends DomainEvent> Subscription subscribe(Class<T> eventType, Consumer<T> handler);

    /**
     * Subscribes to domain event (for specific module).
     * 
     * @param eventType event type
     * @param moduleId subscribing module ID
     * @param handler event handler
     * @param <T> event type
     * @return subscription handle
     */
    <T extends DomainEvent> Subscription subscribe(Class<T> eventType, String moduleId, Consumer<T> handler);

    /**
     * Subscribes to integration event.
     * 
     * @param eventType event type
     * @param handler event handler
     * @param <T> event type
     * @return subscription handle
     */
    <T extends IntegrationEvent> Subscription subscribeIntegration(Class<T> eventType, Consumer<T> handler);

    /**
     * Unsubscribes.
     * 
     * @param subscription subscription handle
     */
    void unsubscribe(Subscription subscription);

    /**
     * Unsubscribes all subscriptions for a module.
     * 
     * @param moduleId module ID
     */
    void unsubscribeAll(String moduleId);

    /**
     * Synchronously dispatches domain event.
     * 
     * <p>Event will be synchronously sent to all subscribers, executed in current thread</p>
     * 
     * @param event domain event
     */
    void dispatch(DomainEvent event);

    /**
     * Asynchronously dispatches domain event.
     * 
     * <p>Event will be asynchronously sent to all subscribers</p>
     * 
     * @param event domain event
     */
    void dispatchAsync(DomainEvent event);

    /**
     * Dispatches integration event.
     * 
     * <p>Integration events are dispatched asynchronously by default</p>
     * 
     * @param event integration event
     */
    void dispatchIntegration(IntegrationEvent event);

    /**
     * Gets subscriber count for specified event type.
     * 
     * @param eventType event type
     * @return subscriber count
     */
    int getSubscriberCount(Class<?> eventType);

    /**
     * Checks if there are subscribers for specified event type.
     * 
     * @param eventType event type
     * @return true if there are subscribers
     */
    boolean hasSubscribers(Class<?> eventType);

    /**
     * Clears all subscriptions.
     */
    void clear();

    /**
     * Shuts down the dispatcher.
     * 
     * <p>Releases resources, stops async dispatch</p>
     */
    void shutdown();

    /**
     * Subscription handle.
     * 
     * <p>Used for identifying and canceling subscriptions</p>
     */
    interface Subscription {
        
        /**
         * Gets subscription ID.
         * 
         * @return subscription ID
         */
        String getId();

        /**
         * Gets event type.
         * 
         * @return event type
         */
        Class<?> getEventType();

        /**
         * Gets subscribing module ID.
         * 
         * @return module ID, returns null if not specified
         */
        String getModuleId();

        /**
         * Checks if subscription is active.
         * 
         * @return true if subscription is active
         */
        boolean isActive();

        /**
         * Cancels subscription.
         */
        void cancel();
    }
}
