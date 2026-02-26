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
 * 事件 Topic 解析
 * 
 * <p>负责将事件类型映射到 Kafka Topic，是事件路由的核心组件
 * 遵循统一Topic 命名规范，确保事件能被正确路由。</p>
 * 
 * <h3>Topic 命名规范</h3>
 * <table border="1">
 *   <tr>
 *     <th>事件类型</th>
 *     <th>Topic 格式</th>
 *     <th>示例</th>
 *   </tr>
 *   <tr>
 *     <td>领域事件</td>
 *     <td>domain.{moduleId}.{aggregateType}</td>
 *     <td>domain.booking.reservation</td>
 *   </tr>
 *   <tr>
 *     <td>集成事件</td>
 *     <td>integration.{eventTypeName}</td>
 *     <td>integration.reservation-created</td>
 *   </tr>
 * </table>
 * 
 * <h3>设计考量</h3>
 * <ul>
 *   <li><b>领域事件隔离</b>：每个模块的领域事件独立 Topic，避免干。</li>
 *   <li><b>集成事件共享</b>：集成事件使用公Topic，便于跨模块订阅</li>
 *   <li><b>命名一致。</b>：使用小写字母和连字符，符合 Kafka 最佳实现</li>
 * </ul>
 * 
 * @author Brix Platform Authors Platform Team
 * @since 3.0.0
 */
public class EventTopicResolver {

    /**
     * 领域事件 Topic 前缀
     */
    private static final String DOMAIN_TOPIC_PREFIX = "domain";

    /**
     * 集成事件 Topic 前缀
     */
    private static final String INTEGRATION_TOPIC_PREFIX = "integration";

    /**
     * Topic 环境前缀（用于多环境隔离
     * 
     * <p>例如：dev-, staging-, prod-</p>
     */
    private final String topicPrefix;

    /**
     * 默认构造函数（无环境前缀
     */
    public EventTopicResolver() {
        this("");
    }

    /**
     * 带环境前缀的构造函数
     * 
     * @param topicPrefix Topic 环境前缀，如 "dev-", "staging-"
     */
    public EventTopicResolver(String topicPrefix) {
        this.topicPrefix = topicPrefix != null ? topicPrefix : "";
    }

    /**
     * 解析领域事件的目Topic
     * 
     * <p>领域事件 Topic 格式：{prefix}domain.{moduleId}.{aggregateType}</p>
     * 
     * <h4>示例</h4>
     * <pre>{@code
     * // 无环境前缀
     * resolveDomainTopic(reservationEvent, "booking") 
     *     => "domain.booking.reservation"
     * 
     * // 有环境前缀
     * new EventTopicResolver("dev-").resolveDomainTopic(reservationEvent, "booking")
     *     => "dev-domain.booking.reservation"
     * }</pre>
     * 
     * @param event    领域事件
     * @param moduleId 来源模块 ID
     * @return 目标 Topic 名称
     */
    public String resolveDomainTopic(DomainEvent event, String moduleId) {
        // 获取聚合根类型并转换为小写连字符格式
        String aggregateType = toKebabCase(event.getAggregateType());
        
        // 构建 Topic：{prefix}domain.{moduleId}.{aggregateType}
        return buildTopic(DOMAIN_TOPIC_PREFIX, moduleId, aggregateType);
    }

    /**
     * 解析集成事件的目Topic
     * 
     * <p>集成事件 Topic 格式：{prefix}integration.{eventTypeName}</p>
     * 
     * <h4>示例</h4>
     * <pre>{@code
     * resolveIntegrationTopic(new ReservationCreatedEvent())
     *     => "integration.reservation-created"
     * }</pre>
     * 
     * @param event 集成事件
     * @return 目标 Topic 名称
     */
    public String resolveIntegrationTopic(IntegrationEvent event) {
        // 获取事件类型名称并转换为小写连字符格
        String eventTypeName = extractEventTypeName(event.getEventType());
        
        // 构建 Topic：{prefix}integration.{eventTypeName}
        return buildTopic(INTEGRATION_TOPIC_PREFIX, eventTypeName);
    }

    /**
     * 根据事件类型字符串解Topic（用于消费者注册）
     * 
     * @param eventType 事件类型（完整类名）
     * @return Topic 名称
     */
    public String resolveTopicByEventType(String eventType) {
        String eventTypeName = extractEventTypeName(eventType);
        return buildTopic(INTEGRATION_TOPIC_PREFIX, eventTypeName);
    }

    /**
     * 构建 Topic 名称
     * 
     * @param parts Topic 组成部分
     * @return 完整 Topic 名称
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
     * 从完整事件类型中提取事件名称
     * 
     * <p>例如：com.shinwa.app.booking.event.ReservationCreatedEvent => reservation-created</p>
     * 
     * @param eventType 完整事件类型
     * @return 事件名称（kebab-case
     */
    private String extractEventTypeName(String eventType) {
        // 获取简单类名
        String simpleName = eventType;
        int lastDot = eventType.lastIndexOf('.');
        if (lastDot > 0) {
            simpleName = eventType.substring(lastDot + 1);
        }
        
        // 移除 "Event" 后缀
        if (simpleName.endsWith("Event")) {
            simpleName = simpleName.substring(0, simpleName.length() - 5);
        }
        
        // 转换kebab-case
        return toKebabCase(simpleName);
    }

    /**
     * 将驼峰命名转换为小写连字符格式（kebab-case
     * 
     * <p>例如：ReservationCreated => reservation-created</p>
     * 
     * @param input 驼峰命名字符
     * @return kebab-case 格式字符
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
