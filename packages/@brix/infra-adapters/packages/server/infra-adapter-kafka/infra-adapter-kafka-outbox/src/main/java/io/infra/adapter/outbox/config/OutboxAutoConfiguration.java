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
package io.infra.adapter.outbox.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.infra.adapter.kafka.EventTopicResolver;
import io.infra.adapter.kafka.config.KafkaEventBusProperties;
import io.infra.adapter.outbox.OutboxEventPublisher;
import io.infra.adapter.outbox.OutboxEventRepository;

/**
 * Outbox 模式自动配置
 *
 * <p>Spring Boot 自动配置类，当 classpath 同时存在 JPA 和 Kafka 依赖，
 * 且 Kafka 适配器已启用时，自动配置 Outbox 事件发布器。</p>
 *
 * <h3>架构定位</h3>
 * <p>
 * 本配置属于 {@code infra-adapter-outbox} 独立模块（Layer 2.5: Adapter 层）。
 * Outbox 是跨基础设施的模式（需要 DB + MQ 协同），作为 {@code infra-adapter-kafka}
 * 的可选增强模块，独立管理 JPA 实体扫描和 Repository 注册。
 * </p>
 *
 * <h3>激活条件</h3>
 * <ul>
 *   <li>classpath 存在 {@link KafkaTemplate}（spring-kafka 依赖）</li>
 *   <li>classpath 存在 JPA Repository（spring-boot-starter-data-jpa 依赖）</li>
 *   <li>配置 {@code brix.infra.kafka.enabled=true}（默认启用）</li>
 *   <li>存在 {@link EventTopicResolver} Bean（由 kafka 适配器提供）</li>
 * </ul>
 *
 * <h3>配置项</h3>
 * <p>通过 {@code brix.infra.kafka.outbox.*} 前缀配置：</p>
 * <table border="1">
 *   <tr><th>配置项</th><th>说明</th><th>默认值</th></tr>
 *   <tr><td>brix.infra.kafka.outbox.batch-size</td><td>每批处理事件数</td><td>100</td></tr>
 *   <tr><td>brix.infra.kafka.outbox.max-retry-count</td><td>最大重试次数</td><td>5</td></tr>
 *   <tr><td>brix.infra.kafka.outbox.retention-days</td><td>已完成事件保留天数</td><td>7</td></tr>
 *   <tr><td>brix.infra.kafka.outbox.process-interval-ms</td><td>处理间隔（毫秒）</td><td>1000</td></tr>
 *   <tr><td>brix.infra.kafka.outbox.retry-interval-ms</td><td>重试间隔（毫秒）</td><td>30000</td></tr>
 *   <tr><td>brix.infra.kafka.outbox.cleanup-cron</td><td>清理定时 Cron</td><td>0 0 3 * * ?</td></tr>
 * </table>
 *
 * @author Brix Platform Authors
 * @since 3.0.0
 */
@AutoConfiguration
@ConditionalOnClass({KafkaTemplate.class})
@ConditionalOnProperty(name = "brix.infra.kafka.enabled", havingValue = "true", matchIfMissing = true)
@EnableJpaRepositories(basePackages = "io.infra.adapter.outbox")
@EnableScheduling
public class OutboxAutoConfiguration {

    /**
     * 创建 Outbox 事件序列化专用 ObjectMapper（内部使用，不注册为 Spring Bean）
     *
     * <p>避免与 Spring Boot 自动配置的全局 ObjectMapper 冲突。
     * 配置 Java 8 时间模块和 ISO-8601 日期格式。</p>
     *
     * @return ObjectMapper 实例
     */
    private static ObjectMapper createOutboxObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 注册 Java 8 时间模块（支持 Instant、LocalDateTime 等）
        mapper.registerModule(new JavaTimeModule());
        // 日期时间序列化为 ISO-8601 格式（而非时间戳）
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 忽略未知属性（兼容性，避免反序列化失败）
        mapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false);
        return mapper;
    }

    /**
     * 配置 Outbox 事件发布器
     *
     * <p>当存在 {@link OutboxEventRepository} Bean（JPA 自动代理生成）
     * 和 {@link EventTopicResolver} Bean（由 kafka 适配器提供）时自动配置。</p>
     *
     * @param outboxRepository Outbox JPA 仓储
     * @param kafkaTemplate    Kafka 消息模板
     * @param topicResolver    事件 Topic 解析器（来自 infra-adapter-kafka）
     * @param properties       Kafka 配置属性（含 Outbox 子配置）
     * @return OutboxEventPublisher 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({OutboxEventRepository.class, EventTopicResolver.class})
    public OutboxEventPublisher outboxEventPublisher(
            OutboxEventRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            EventTopicResolver topicResolver,
            KafkaEventBusProperties properties) {
        return new OutboxEventPublisher(
                outboxRepository, kafkaTemplate, topicResolver,
                createOutboxObjectMapper(), properties.getOutbox());
    }
}
