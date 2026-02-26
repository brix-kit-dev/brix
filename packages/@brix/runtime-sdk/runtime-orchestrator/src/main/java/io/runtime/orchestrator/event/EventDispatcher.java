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
 * 事件分发器
 * 
 * <p>负责事件的分发和路由。接收来自 EventBusCapability 的事件，
 * 并分发给订阅的模块处理器。</p>
 * 
 * <h3>核心职责</h3>
 * <ul>
 *   <li>管理事件订阅关系</li>
 *   <li>分发领域事件和集成事件</li>
 *   <li>支持同步和异步分发</li>
 *   <li>事件过滤和转换</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 订阅事件
 * dispatcher.subscribe(OrderCreatedEvent.class, event -> {
 *     processNewOrder(event);
 * });
 * 
 * // 发布事件
 * dispatcher.dispatch(new OrderCreatedEvent(orderId));
 * 
 * // 异步发布
 * dispatcher.dispatchAsync(new OrderCreatedEvent(orderId));
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public interface EventDispatcher {

    /**
     * 订阅领域事件
     * 
     * @param eventType 事件类型
     * @param handler 事件处理器
     * @param <T> 事件类型
     * @return 订阅句柄，用于取消订阅
     */
    <T extends DomainEvent> Subscription subscribe(Class<T> eventType, Consumer<T> handler);

    /**
     * 订阅领域事件（指定模块）
     * 
     * @param eventType 事件类型
     * @param moduleId 订阅模块 ID
     * @param handler 事件处理器
     * @param <T> 事件类型
     * @return 订阅句柄
     */
    <T extends DomainEvent> Subscription subscribe(Class<T> eventType, String moduleId, Consumer<T> handler);

    /**
     * 订阅集成事件
     * 
     * @param eventType 事件类型
     * @param handler 事件处理器
     * @param <T> 事件类型
     * @return 订阅句柄
     */
    <T extends IntegrationEvent> Subscription subscribeIntegration(Class<T> eventType, Consumer<T> handler);

    /**
     * 取消订阅
     * 
     * @param subscription 订阅句柄
     */
    void unsubscribe(Subscription subscription);

    /**
     * 取消模块的所有订阅
     * 
     * @param moduleId 模块 ID
     */
    void unsubscribeAll(String moduleId);

    /**
     * 同步分发领域事件
     * 
     * <p>事件将同步发送给所有订阅者，在当前线程执行</p>
     * 
     * @param event 领域事件
     */
    void dispatch(DomainEvent event);

    /**
     * 异步分发领域事件
     * 
     * <p>事件将异步发送给所有订阅者</p>
     * 
     * @param event 领域事件
     */
    void dispatchAsync(DomainEvent event);

    /**
     * 分发集成事件
     * 
     * <p>集成事件默认异步分发</p>
     * 
     * @param event 集成事件
     */
    void dispatchIntegration(IntegrationEvent event);

    /**
     * 获取指定事件类型的订阅者数量
     * 
     * @param eventType 事件类型
     * @return 订阅者数量
     */
    int getSubscriberCount(Class<?> eventType);

    /**
     * 检查是否有指定事件类型的订阅者
     * 
     * @param eventType 事件类型
     * @return 如果有订阅者返回 true
     */
    boolean hasSubscribers(Class<?> eventType);

    /**
     * 清空所有订阅
     */
    void clear();

    /**
     * 关闭分发器
     * 
     * <p>释放资源，停止异步分发</p>
     */
    void shutdown();

    /**
     * 订阅句柄
     * 
     * <p>用于标识和取消订阅</p>
     */
    interface Subscription {
        
        /**
         * 获取订阅 ID
         * 
         * @return 订阅 ID
         */
        String getId();

        /**
         * 获取事件类型
         * 
         * @return 事件类型
         */
        Class<?> getEventType();

        /**
         * 获取订阅模块 ID
         * 
         * @return 模块 ID，如果没有指定则返回 null
         */
        String getModuleId();

        /**
         * 是否活跃
         * 
         * @return 如果订阅活跃返回 true
         */
        boolean isActive();

        /**
         * 取消订阅
         */
        void cancel();
    }
}
