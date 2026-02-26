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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 默认事件分发器实现
 * 
 * <p>线程安全的事件分发器，支持同步和异步事件分发。</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class DefaultEventDispatcher implements EventDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(DefaultEventDispatcher.class);

    /**
     * 订阅 ID 生成器
     */
    private final AtomicLong subscriptionIdGenerator = new AtomicLong(0);

    /**
     * 领域事件订阅 - 事件类型 -> 订阅列表
     */
    private final Map<Class<?>, List<SubscriptionHolder<?>>> domainSubscriptions = new ConcurrentHashMap<>();

    /**
     * 集成事件订阅 - 事件类型 -> 订阅列表
     */
    private final Map<Class<?>, List<SubscriptionHolder<?>>> integrationSubscriptions = new ConcurrentHashMap<>();

    /**
     * 异步执行器
     */
    private final ExecutorService asyncExecutor;

    /**
     * 是否已关闭
     */
    private volatile boolean shutdown = false;

    /**
     * 创建默认事件分发器
     */
    public DefaultEventDispatcher() {
        this(Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r, "event-dispatcher");
                t.setDaemon(true);
                return t;
            }
        ));
    }

    /**
     * 创建默认事件分发器（指定执行器）
     * 
     * @param asyncExecutor 异步执行器
     */
    public DefaultEventDispatcher(ExecutorService asyncExecutor) {
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "Executor cannot be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends DomainEvent> Subscription subscribe(Class<T> eventType, Consumer<T> handler) {
        return subscribe(eventType, null, handler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T extends DomainEvent> Subscription subscribe(Class<T> eventType, String moduleId, Consumer<T> handler) {
        Objects.requireNonNull(eventType, "Event type cannot be null");
        Objects.requireNonNull(handler, "Handler cannot be null");

        SubscriptionHolder<T> holder = new SubscriptionHolder<>(
            generateSubscriptionId(),
            eventType,
            moduleId,
            handler,
            this
        );

        domainSubscriptions.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
            .add(holder);

        logger.debug("Subscribed to domain event: {} (module: {})", eventType.getSimpleName(), moduleId);
        return holder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T extends IntegrationEvent> Subscription subscribeIntegration(Class<T> eventType, Consumer<T> handler) {
        Objects.requireNonNull(eventType, "Event type cannot be null");
        Objects.requireNonNull(handler, "Handler cannot be null");

        SubscriptionHolder<T> holder = new SubscriptionHolder<>(
            generateSubscriptionId(),
            eventType,
            null,
            handler,
            this
        );

        integrationSubscriptions.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
            .add(holder);

        logger.debug("Subscribed to integration event: {}", eventType.getSimpleName());
        return holder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unsubscribe(Subscription subscription) {
        if (subscription == null) return;

        Class<?> eventType = subscription.getEventType();
        
        // 尝试从领域事件订阅移除
        List<SubscriptionHolder<?>> domainSubs = domainSubscriptions.get(eventType);
        if (domainSubs != null) {
            domainSubs.removeIf(s -> s.getId().equals(subscription.getId()));
        }

        // 尝试从集成事件订阅移除
        List<SubscriptionHolder<?>> integrationSubs = integrationSubscriptions.get(eventType);
        if (integrationSubs != null) {
            integrationSubs.removeIf(s -> s.getId().equals(subscription.getId()));
        }

        logger.debug("Unsubscribed: {}", subscription.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unsubscribeAll(String moduleId) {
        if (moduleId == null) return;

        domainSubscriptions.values().forEach(list -> 
            list.removeIf(s -> moduleId.equals(s.getModuleId()))
        );
        integrationSubscriptions.values().forEach(list -> 
            list.removeIf(s -> moduleId.equals(s.getModuleId()))
        );

        logger.debug("Unsubscribed all for module: {}", moduleId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public void dispatch(DomainEvent event) {
        if (shutdown) {
            logger.warn("Dispatcher is shutdown, ignoring event: {}", event.getEventType());
            return;
        }

        Objects.requireNonNull(event, "Event cannot be null");

        Class<?> eventType = event.getClass();
        List<SubscriptionHolder<?>> subscribers = domainSubscriptions.get(eventType);

        if (subscribers == null || subscribers.isEmpty()) {
            logger.trace("No subscribers for event: {}", eventType.getSimpleName());
            return;
        }

        logger.debug("Dispatching event: {} to {} subscribers", eventType.getSimpleName(), subscribers.size());

        for (SubscriptionHolder<?> holder : subscribers) {
            if (holder.isActive()) {
                try {
                    ((SubscriptionHolder<DomainEvent>) holder).getHandler().accept(event);
                } catch (Exception e) {
                    logger.error("Error handling event: {} in subscription: {}", 
                        eventType.getSimpleName(), holder.getId(), e);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public void dispatchAsync(DomainEvent event) {
        if (shutdown) {
            logger.warn("Dispatcher is shutdown, ignoring event: {}", event.getEventType());
            return;
        }

        Objects.requireNonNull(event, "Event cannot be null");

        Class<?> eventType = event.getClass();
        List<SubscriptionHolder<?>> subscribers = domainSubscriptions.get(eventType);

        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }

        logger.debug("Dispatching event async: {} to {} subscribers", eventType.getSimpleName(), subscribers.size());

        for (SubscriptionHolder<?> holder : subscribers) {
            if (holder.isActive()) {
                asyncExecutor.submit(() -> {
                    try {
                        ((SubscriptionHolder<DomainEvent>) holder).getHandler().accept(event);
                    } catch (Exception e) {
                        logger.error("Error handling event async: {} in subscription: {}", 
                            eventType.getSimpleName(), holder.getId(), e);
                    }
                });
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public void dispatchIntegration(IntegrationEvent event) {
        if (shutdown) {
            logger.warn("Dispatcher is shutdown, ignoring event: {}", event.getEventType());
            return;
        }

        Objects.requireNonNull(event, "Event cannot be null");

        Class<?> eventType = event.getClass();
        List<SubscriptionHolder<?>> subscribers = integrationSubscriptions.get(eventType);

        if (subscribers == null || subscribers.isEmpty()) {
            logger.trace("No subscribers for integration event: {}", eventType.getSimpleName());
            return;
        }

        logger.debug("Dispatching integration event: {} to {} subscribers", 
            eventType.getSimpleName(), subscribers.size());

        // 集成事件默认异步分发
        for (SubscriptionHolder<?> holder : subscribers) {
            if (holder.isActive()) {
                asyncExecutor.submit(() -> {
                    try {
                        ((SubscriptionHolder<IntegrationEvent>) holder).getHandler().accept(event);
                    } catch (Exception e) {
                        logger.error("Error handling integration event: {} in subscription: {}", 
                            eventType.getSimpleName(), holder.getId(), e);
                    }
                });
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSubscriberCount(Class<?> eventType) {
        int count = 0;
        
        List<SubscriptionHolder<?>> domainSubs = domainSubscriptions.get(eventType);
        if (domainSubs != null) {
            count += domainSubs.size();
        }
        
        List<SubscriptionHolder<?>> integrationSubs = integrationSubscriptions.get(eventType);
        if (integrationSubs != null) {
            count += integrationSubs.size();
        }
        
        return count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasSubscribers(Class<?> eventType) {
        return getSubscriberCount(eventType) > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        domainSubscriptions.clear();
        integrationSubscriptions.clear();
        logger.info("All subscriptions cleared");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
        shutdown = true;
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        clear();
        logger.info("Event dispatcher shutdown");
    }

    /**
     * 生成订阅 ID
     */
    private String generateSubscriptionId() {
        return "sub-" + subscriptionIdGenerator.incrementAndGet();
    }

    /**
     * 订阅持有者
     */
    private static class SubscriptionHolder<T> implements Subscription {
        
        private final String id;
        private final Class<?> eventType;
        private final String moduleId;
        private final Consumer<T> handler;
        private final DefaultEventDispatcher dispatcher;
        private volatile boolean active = true;

        SubscriptionHolder(String id, Class<?> eventType, String moduleId, 
                          Consumer<T> handler, DefaultEventDispatcher dispatcher) {
            this.id = id;
            this.eventType = eventType;
            this.moduleId = moduleId;
            this.handler = handler;
            this.dispatcher = dispatcher;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Class<?> getEventType() {
            return eventType;
        }

        @Override
        public String getModuleId() {
            return moduleId;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void cancel() {
            active = false;
            dispatcher.unsubscribe(this);
        }

        Consumer<T> getHandler() {
            return handler;
        }
    }
}
