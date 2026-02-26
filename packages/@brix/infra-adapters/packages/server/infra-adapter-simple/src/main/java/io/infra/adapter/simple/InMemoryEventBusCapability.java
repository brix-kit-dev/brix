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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;
import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.IntegrationEvent;

/**
 * 基于内存的事件总线能力实现
 * 
 * <p>本类是 {@link EventBusCapability} 的轻量级内存实现，适用于本地开发和测试场景。
 * 事件通过内存中的发布-订阅机制传递，无需依赖 Kafka 等外部消息中间件。</p>
 * 
 * <h3>核心特性</h3>
 * <ul>
 *   <li><b>同步/异步发布</b>：支持同步和异步两种事件发布模式</li>
 *   <li><b>类型安全订阅</b>：基于事件类型进行精确匹配</li>
 *   <li><b>事件历史</b>：可选保留最近的事件用于调试</li>
 *   <li><b>线程安全</b>：所有操作都是线程安全的</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * InMemoryEventBusCapability eventBus = new InMemoryEventBusCapability();
 * 
 * // 订阅事件（测试用）
 * eventBus.subscribe(OrderCreatedEvent.class, event -> {
 *     System.out.println("收到订单: " + event.getOrderId());
 * });
 * 
 * // 发布事件
 * eventBus.publish(new OrderCreatedEvent("ORDER-001"));
 * }</pre>
 * 
 * <h3>限制说明</h3>
 * <ul>
 *   <li>事件仅在当前 JVM 进程内传递，不支持跨进程通信</li>
 *   <li>进程重启后所有订阅关系和事件历史都会丢失</li>
 *   <li>不保证消息的持久化和可靠投递</li>
 * </ul>
 * 
 * @author Brix Team
 * @since 3.0.0
 * @see EventBusCapability
 */
@Capability(
    type = EventBusCapability.class,
    name = "in-memory-event-bus",
    description = "基于内存的事件总线实现，适用于开发和单机部署",
    level = CapabilityLevel.STANDARD,
    aliases = {"simpleEventBus", "inMemoryEventBus"}
)
public class InMemoryEventBusCapability implements EventBusCapability {

    private static final Logger log = LoggerFactory.getLogger(InMemoryEventBusCapability.class);

    /**
     * 领域事件订阅者映射
     * Key: 事件类型（Class）
     * Value: 订阅者列表
     */
    private final Map<Class<? extends DomainEvent>, List<Consumer<DomainEvent>>> domainSubscribers 
        = new ConcurrentHashMap<>();

    /**
     * 集成事件订阅者映射
     * Key: 事件类型（Class）
     * Value: 订阅者列表
     */
    private final Map<Class<? extends IntegrationEvent>, List<Consumer<IntegrationEvent>>> integrationSubscribers 
        = new ConcurrentHashMap<>();

    /**
     * 事件历史记录（用于测试和调试）
     */
    private final BlockingQueue<Object> eventHistory;

    /**
     * 异步执行器
     */
    private final ExecutorService executor;

    /**
     * 是否使用异步模式发布事件
     */
    private final boolean asyncMode;

    /**
     * 最大事件历史数量
     */
    private final int maxHistorySize;

    /**
     * 创建内存事件总线（默认配置）
     * 
     * <p>使用同步模式，保留最近 1000 条事件历史。</p>
     */
    public InMemoryEventBusCapability() {
        this(false, 1000);
    }

    /**
     * 创建内存事件总线
     * 
     * @param asyncMode      是否使用异步模式
     * @param maxHistorySize 最大事件历史数量（0 表示不保留）
     */
    public InMemoryEventBusCapability(boolean asyncMode, int maxHistorySize) {
        this.asyncMode = asyncMode;
        this.maxHistorySize = maxHistorySize;
        this.eventHistory = maxHistorySize > 0 
            ? new LinkedBlockingQueue<>(maxHistorySize) 
            : null;
        this.executor = asyncMode 
            ? Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "inmemory-eventbus");
                t.setDaemon(true);
                return t;
            }) 
            : null;
    }

    /**
     * 发布领域事件
     * 
     * <p>领域事件在模块内部传播，通知所有订阅该事件类型的消费者。</p>
     * 
     * @param event 要发布的领域事件，不能为 null
     * @throws IllegalArgumentException 如果事件为 null
     */
    @Override
    public void publish(DomainEvent event) {
        Objects.requireNonNull(event, "领域事件不能为空");

        log.debug("发布领域事件: type={}, eventId={}", 
            event.getClass().getSimpleName(), event.getEventId());

        // 记录事件历史
        recordHistory(event);

        // 获取订阅者
        @SuppressWarnings("unchecked")
        List<Consumer<DomainEvent>> subscribers = domainSubscribers.get(event.getClass());
        
        if (subscribers == null || subscribers.isEmpty()) {
            log.debug("领域事件无订阅者: {}", event.getClass().getSimpleName());
            return;
        }

        // 分发事件
        dispatchToSubscribers(event, subscribers);
    }

    /**
     * 发布集成事件
     * 
     * <p>集成事件用于跨模块/跨系统通信，通知所有订阅该事件类型的消费者。</p>
     * 
     * @param event 要发布的集成事件，不能为 null
     * @throws IllegalArgumentException 如果事件为 null
     */
    @Override
    public void publishIntegration(IntegrationEvent event) {
        Objects.requireNonNull(event, "集成事件不能为空");

        log.debug("发布集成事件: type={}, eventId={}", 
            event.getClass().getSimpleName(), event.getEventId());

        // 记录事件历史
        recordHistory(event);

        // 获取订阅者
        @SuppressWarnings("unchecked")
        List<Consumer<IntegrationEvent>> subscribers = integrationSubscribers.get(event.getClass());
        
        if (subscribers == null || subscribers.isEmpty()) {
            log.debug("集成事件无订阅者: {}", event.getClass().getSimpleName());
            return;
        }

        // 分发事件
        dispatchToSubscribers(event, subscribers);
    }

    /**
     * 订阅领域事件（测试用）
     * 
     * <p>注意：这是内存实现特有的方法，用于测试场景。
     * 正式环境中应通过 module-manifest.yaml 声明订阅关系。</p>
     * 
     * @param eventType 事件类型
     * @param handler   事件处理器
     * @param <T>       事件类型
     * @return 取消订阅的 Runnable
     */
    public <T extends DomainEvent> Runnable subscribeDomain(
            Class<T> eventType, 
            Consumer<T> handler) {
        Objects.requireNonNull(eventType, "事件类型不能为空");
        Objects.requireNonNull(handler, "处理器不能为空");

        @SuppressWarnings("unchecked")
        Consumer<DomainEvent> wrappedHandler = (Consumer<DomainEvent>) handler;

        domainSubscribers
            .computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
            .add(wrappedHandler);

        log.info("注册领域事件订阅: {}", eventType.getSimpleName());

        // 返回取消订阅的 Runnable
        return () -> {
            List<Consumer<DomainEvent>> list = domainSubscribers.get(eventType);
            if (list != null) {
                list.remove(wrappedHandler);
            }
        };
    }

    /**
     * 订阅集成事件（测试用）
     * 
     * <p>注意：这是内存实现特有的方法，用于测试场景。
     * 正式环境中应通过 module-manifest.yaml 声明订阅关系。</p>
     * 
     * @param eventType 事件类型
     * @param handler   事件处理器
     * @param <T>       事件类型
     * @return 取消订阅的 Runnable
     */
    public <T extends IntegrationEvent> Runnable subscribeIntegration(
            Class<T> eventType, 
            Consumer<T> handler) {
        Objects.requireNonNull(eventType, "事件类型不能为空");
        Objects.requireNonNull(handler, "处理器不能为空");

        @SuppressWarnings("unchecked")
        Consumer<IntegrationEvent> wrappedHandler = (Consumer<IntegrationEvent>) handler;

        integrationSubscribers
            .computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
            .add(wrappedHandler);

        log.info("注册集成事件订阅: {}", eventType.getSimpleName());

        // 返回取消订阅的 Runnable
        return () -> {
            List<Consumer<IntegrationEvent>> list = integrationSubscribers.get(eventType);
            if (list != null) {
                list.remove(wrappedHandler);
            }
        };
    }

    /**
     * 获取事件历史记录（测试用）
     * 
     * @return 事件历史列表（不可变）
     */
    public List<Object> getEventHistory() {
        if (eventHistory == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(eventHistory);
    }

    /**
     * 清空事件历史
     */
    public void clearHistory() {
        if (eventHistory != null) {
            eventHistory.clear();
        }
    }

    /**
     * 清空所有订阅
     */
    public void clearSubscribers() {
        domainSubscribers.clear();
        integrationSubscribers.clear();
        log.info("已清空所有事件订阅");
    }

    /**
     * 关闭事件总线
     * 
     * <p>释放异步执行器资源。</p>
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
        log.info("内存事件总线已关闭");
    }

    // ==================== 私有方法 ====================

    /**
     * 记录事件到历史
     */
    private void recordHistory(Object event) {
        if (eventHistory != null) {
            // 如果队列满了，移除最旧的
            while (!eventHistory.offer(event)) {
                eventHistory.poll();
            }
        }
    }

    /**
     * 分发事件到订阅者
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
     * 调用事件处理器
     */
    private <T> void invokeHandler(T event, Consumer<T> handler) {
        try {
            handler.accept(event);
        } catch (Exception e) {
            log.error("事件处理器执行失败: eventType={}, error={}", 
                event.getClass().getSimpleName(), e.getMessage(), e);
        }
    }
}
