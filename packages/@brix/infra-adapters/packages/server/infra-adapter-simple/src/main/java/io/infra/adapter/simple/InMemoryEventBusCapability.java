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
package io.infra.adapter.simple;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.micrometer.core.instrument.MeterRegistry;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;
import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.IntegrationEvent;

/**
 * In-Memory Event Bus Capability Implementation
 * 
 * <p>This class is a lightweight in-memory implementation of {@link EventBusCapability},
 * suitable for local development and testing scenarios. Events are delivered through
 * an in-memory publish-subscribe mechanism without requiring external message middleware like Kafka.</p>
 * 
 * <h3>Key Features</h3>
 * <ul>
 *   <li><b>Sync/Async Publishing</b>: Supports both synchronous and asynchronous event publishing modes</li>
 *   <li><b>Type-Safe Subscription</b>: Precise matching based on event type</li>
 *   <li><b>Event History</b>: Optionally retains recent events for debugging</li>
 *   <li><b>Thread Safety</b>: All operations are thread-safe</li>
 * </ul>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * InMemoryEventBusCapability eventBus = new InMemoryEventBusCapability();
 * 
 * // Subscribe to events (for testing)
 * eventBus.subscribe(OrderCreatedEvent.class, event -> {
 *     System.out.println("Received order: " + event.getOrderId());
 * });
 * 
 * // Publish event
 * eventBus.publish(new OrderCreatedEvent("ORDER-001"));
 * }</pre>
 * 
 * <h3>Limitations</h3>
 * <ul>
 *   <li>Events are only delivered within the current JVM process, cross-process communication is not supported</li>
 *   <li>All subscriptions and event history are lost after process restart</li>
 *   <li>Message persistence and reliable delivery are not guaranteed</li>
 * </ul>
 * 
 * @author Brix Team
 * @since 3.0.0
 * @see EventBusCapability
 */
@Capability(
    type = EventBusCapability.class,
    name = "in-memory-event-bus",
    description = "In-memory event bus implementation, suitable for development and single-node deployment",
    level = CapabilityLevel.STANDARD,
    aliases = {"simpleEventBus", "inMemoryEventBus"}
)
public class InMemoryEventBusCapability implements EventBusCapability {

    private static final Logger log = LoggerFactory.getLogger(InMemoryEventBusCapability.class);

    /**
     * Domain event subscribers mapping
     * Key: Event type (Class)
     * Value: List of subscribers
     */
    private final Map<Class<? extends DomainEvent>, List<Consumer<DomainEvent>>> domainSubscribers 
        = new ConcurrentHashMap<>();

    /**
     * Integration event subscribers mapping
     * Key: Event type (Class)
     * Value: List of subscribers
     */
    private final Map<Class<? extends IntegrationEvent>, List<Consumer<IntegrationEvent>>> integrationSubscribers 
        = new ConcurrentHashMap<>();

    /**
     * Event history records (for testing and debugging)
     */
    private final BlockingQueue<Object> eventHistory;

    /**
     * Bounded async executor for event dispatching.
     *
     * <p>When async mode is enabled, events are dispatched using a bounded
     * {@link ThreadPoolExecutor} with Core=2, Max=8, Queue=1024 and CallerRunsPolicy.
     * This prevents unbounded thread creation under high event throughput.</p>
     */
    private final ExecutorService executor;

    /**
     * Whether to use async mode for event publishing
     */
    private final boolean asyncMode;

    /**
     * Maximum event history size
     */
    private final int maxHistorySize;

    /**
     * Micrometer meter registry for event metrics (R3.3).
     * Optional — when {@code null}, metrics are not recorded.
     *
     * @since 3.2.0
     */
    private final MeterRegistry meterRegistry;

    /** Counter metric name: total published events. */
    private static final String METRIC_PUBLISHED_TOTAL = "brix.event.published.total";

    /** Counter metric name: total consumed events. */
    private static final String METRIC_CONSUMED_TOTAL = "brix.event.consumed.total";

    /**
     * Creates an in-memory event bus (default configuration)
     * 
     * <p>Uses synchronous mode, retains last 1000 events in history.</p>
     */
    public InMemoryEventBusCapability() {
        this(false, 1000, null);
    }

    /** Core thread pool size for async event dispatching. */
    private static final int ASYNC_CORE_POOL_SIZE = 2;

    /** Maximum thread pool size for async event dispatching. */
    private static final int ASYNC_MAX_POOL_SIZE = 8;

    /** Keep-alive time in seconds for idle threads beyond the core pool size. */
    private static final long ASYNC_KEEP_ALIVE_SECONDS = 60L;

    /** Bounded work queue capacity for async event dispatching tasks. */
    private static final int ASYNC_QUEUE_CAPACITY = 1024;

    /**
     * Creates an in-memory event bus
     * 
     * @param asyncMode      Whether to use async mode
     * @param maxHistorySize Maximum event history size (0 means no retention)
     */
    public InMemoryEventBusCapability(boolean asyncMode, int maxHistorySize) {
        this(asyncMode, maxHistorySize, null);
    }

    /**
     * Creates an in-memory event bus with Micrometer support (R3.3).
     *
     * @param asyncMode      Whether to use async mode
     * @param maxHistorySize Maximum event history size (0 means no retention)
     * @param meterRegistry  Micrometer registry (optional, may be {@code null})
     * @since 3.2.0
     */
    public InMemoryEventBusCapability(boolean asyncMode, int maxHistorySize, MeterRegistry meterRegistry) {
        this.asyncMode = asyncMode;
        this.maxHistorySize = maxHistorySize;
        this.meterRegistry = meterRegistry;
        this.eventHistory = maxHistorySize > 0 
            ? new LinkedBlockingQueue<>(maxHistorySize) 
            : null;

        // Phase 0.1 security fix: bounded thread pool prevents resource exhaustion.
        // CallerRunsPolicy ensures no events are silently dropped when the queue is full.
        if (asyncMode) {
            AtomicInteger threadCounter = new AtomicInteger(0);
            this.executor = new ThreadPoolExecutor(
                ASYNC_CORE_POOL_SIZE,
                ASYNC_MAX_POOL_SIZE,
                ASYNC_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(ASYNC_QUEUE_CAPACITY),
                r -> {
                    Thread t = new Thread(r, "inmemory-eventbus-" + threadCounter.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
            );
        } else {
            this.executor = null;
        }
    }

    /**
     * Publishes a domain event
     * 
     * <p>Domain events propagate within the module, notifying all consumers subscribed to this event type.</p>
     * 
     * @param event Domain event to publish, cannot be null
     * @throws IllegalArgumentException if event is null
     */
    @Override
    public void publish(DomainEvent event) {
        Objects.requireNonNull(event, "Domain event cannot be null");

        log.debug("Publishing domain event: type={}, eventId={}", 
            event.getClass().getSimpleName(), event.getEventId());

        // Record event history
        recordHistory(event);

        // R3.3: Record publish metric
        recordPublishMetric(event.getClass().getSimpleName(), "domain");

        // Get subscribers
        @SuppressWarnings("unchecked")
        List<Consumer<DomainEvent>> subscribers = domainSubscribers.get(event.getClass());
        
        if (subscribers == null || subscribers.isEmpty()) {
            log.debug("No subscribers for domain event: {}", event.getClass().getSimpleName());
            return;
        }

        // Dispatch event
        dispatchToSubscribers(event, subscribers);
    }

    /**
     * Publishes an integration event
     * 
     * <p>Integration events are used for cross-module/cross-system communication,
     * notifying all consumers subscribed to this event type.</p>
     * 
     * @param event Integration event to publish, cannot be null
     * @throws IllegalArgumentException if event is null
     */
    @Override
    public void publishIntegration(IntegrationEvent event) {
        Objects.requireNonNull(event, "Integration event cannot be null");
        ensureTraceId(event);

        // R3.6: Validate tenantId presence for integration events.
        // In production, the Kafka adapter auto-populates from TenantContext.
        // For the in-memory adapter (dev/test), we log a warning instead of
        // throwing, to keep the local development experience frictionless.
        if (event.getTenantId() == null || event.getTenantId().isBlank()) {
            log.warn("Integration event published without tenantId: eventId={}, type={}. "
                    + "In production, this would be auto-populated from TenantContext.",
                    event.getEventId(), event.getClass().getSimpleName());
        }

        log.debug("Publishing integration event: type={}, eventId={}, tenantId={}", 
            event.getClass().getSimpleName(), event.getEventId(), event.getTenantId());
        log.info("event.published name={} sourcePlugin={} tenantId={} traceId={} eventId={}",
            event.getEventType(),
            event.getSourceModule(),
            event.getTenantId(),
            event.getTraceId(),
            event.getEventId());

        // Record event history
        recordHistory(event);

        // R3.3: Record publish metric
        recordPublishMetric(event.getClass().getSimpleName(), "integration");

        // Get subscribers
        @SuppressWarnings("unchecked")
        List<Consumer<IntegrationEvent>> subscribers = integrationSubscribers.get(event.getClass());
        
        if (subscribers == null || subscribers.isEmpty()) {
            log.debug("No subscribers for integration event: {}", event.getClass().getSimpleName());
            return;
        }

        // Dispatch event
        dispatchToSubscribers(event, subscribers);
    }

    /**
     * Subscribes to domain events (for testing)
     * 
     * <p>Note: This is a method specific to the in-memory implementation, used for testing scenarios.
     * In production environments, subscriptions should be declared via module-manifest.yaml.</p>
     * 
     * @param eventType Event type
     * @param handler   Event handler
     * @param <T>       Event type
     * @return Runnable to unsubscribe
     */
    public <T extends DomainEvent> Runnable subscribeDomain(
            Class<T> eventType, 
            Consumer<T> handler) {
        Objects.requireNonNull(eventType, "Event type cannot be null");
        Objects.requireNonNull(handler, "Handler cannot be null");

        @SuppressWarnings("unchecked")
        Consumer<DomainEvent> wrappedHandler = (Consumer<DomainEvent>) handler;

        domainSubscribers
            .computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
            .add(wrappedHandler);

        log.info("Registered domain event subscription: {}", eventType.getSimpleName());

        // Return Runnable to unsubscribe
        return () -> {
            List<Consumer<DomainEvent>> list = domainSubscribers.get(eventType);
            if (list != null) {
                list.remove(wrappedHandler);
            }
        };
    }

    /**
     * Subscribes to integration events (for testing)
     * 
     * <p>Note: This is a method specific to the in-memory implementation, used for testing scenarios.
     * In production environments, subscriptions should be declared via module-manifest.yaml.</p>
     * 
     * @param eventType Event type
     * @param handler   Event handler
     * @param <T>       Event type
     * @return Runnable to unsubscribe
     */
    public <T extends IntegrationEvent> Runnable subscribeIntegration(
            Class<T> eventType, 
            Consumer<T> handler) {
        Objects.requireNonNull(eventType, "Event type cannot be null");
        Objects.requireNonNull(handler, "Handler cannot be null");

        @SuppressWarnings("unchecked")
        Consumer<IntegrationEvent> wrappedHandler = (Consumer<IntegrationEvent>) handler;

        integrationSubscribers
            .computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
            .add(wrappedHandler);

        log.info("Registered integration event subscription: {}", eventType.getSimpleName());

        // Return Runnable to unsubscribe
        return () -> {
            List<Consumer<IntegrationEvent>> list = integrationSubscribers.get(eventType);
            if (list != null) {
                list.remove(wrappedHandler);
            }
        };
    }

    /**
     * Gets event history records (for testing)
     * 
     * @return Immutable list of event history
     */
    public List<Object> getEventHistory() {
        if (eventHistory == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(eventHistory);
    }

    /**
     * Clears event history
     */
    public void clearHistory() {
        if (eventHistory != null) {
            eventHistory.clear();
        }
    }

    /**
     * Clears all subscriptions
     */
    public void clearSubscribers() {
        domainSubscribers.clear();
        integrationSubscribers.clear();
        log.info("All event subscriptions cleared");
    }

    /**
     * Shuts down the event bus
     * 
     * <p>Releases async executor resources.</p>
     */
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("In-memory event bus closed");
    }

    // ==================== Private Methods ====================

    /**
     * Records event to history
     */
    private void recordHistory(Object event) {
        if (eventHistory != null) {
            // If queue is full, remove the oldest
            while (!eventHistory.offer(event)) {
                eventHistory.poll();
            }
        }
    }

    /**
     * Dispatches event to subscribers
     */
    private <T> void dispatchToSubscribers(T event, List<Consumer<T>> subscribers) {
        for (Consumer<T> subscriber : subscribers) {
            if (asyncMode && executor != null) {
                executor.submit(() -> invokeHandler(event, subscriber));
            } else {
                invokeHandler(event, subscriber);
            }
        }
    }

    /**
     * Invokes event handler
     */
    private <T> void invokeHandler(T event, Consumer<T> handler) {
        try {
            handler.accept(event);
            // R3.3: Record consume success metric
            if (meterRegistry != null) {
                meterRegistry.counter(METRIC_CONSUMED_TOTAL,
                        "eventType", event.getClass().getSimpleName()
                ).increment();
            }
            if (event instanceof IntegrationEvent integrationEvent) {
                log.info("event.consumed name={} sourcePlugin={} tenantId={} traceId={} eventId={}",
                        integrationEvent.getEventType(),
                        integrationEvent.getSourceModule(),
                        integrationEvent.getTenantId(),
                        integrationEvent.getTraceId(),
                        integrationEvent.getEventId());
            }
        } catch (Exception e) {
            log.error("Event handler execution failed: eventType={}, error={}", 
                event.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    /**
     * Records an event publish metric (R3.3 compliance).
     *
     * @param eventType simple class name of the event
     * @param category  "domain" or "integration"
     * @since 3.2.0
     */
    private void recordPublishMetric(String eventType, String category) {
        if (meterRegistry != null) {
            meterRegistry.counter(METRIC_PUBLISHED_TOTAL,
                    "eventType", eventType,
                    "category", category
            ).increment();
        }
    }

    /**
     * Ensures the integration event carries a traceId (P0-1 compliance).
     * Resolution order: existing value → MDC traceId → random UUID.
     */
    private void ensureTraceId(IntegrationEvent event) {
        if (event.getTraceId() != null && !event.getTraceId().isEmpty()) {
            return;
        }
        String mdcTraceId = MDC.get("traceId");
        event.setTraceId(mdcTraceId != null ? mdcTraceId : UUID.randomUUID().toString());
    }
}
