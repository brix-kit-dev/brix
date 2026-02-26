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
package io.infra.adapter.kafka.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.infra.adapter.kafka.EventTopicResolver;
import io.infra.adapter.kafka.KafkaEventBusCapability;
import io.infra.adapter.kafka.health.KafkaHealthIndicator;
import io.runtime.sdk.capability.EventBusCapability;

/**
 * Kafka 事件总线自动配置
 * 
 * <p>Spring Boot 自动配置类，负责初始Kafka 相关Bean
 * classpath 中存Kafka 依赖且配置了 Kafka 连接信息时自动生效。</p>
 * 
 * <h3>配置。</h3>
 * <table border="1">
 *   <tr><th>配置</th><th>说明</th><th>默认</th></tr>
 *   <tr><td>shinwa.runtime.kafka.enabled</td><td>是否启用</td><td>true</td></tr>
 *   <tr><td>shinwa.runtime.kafka.bootstrap-servers</td><td>Kafka 地址</td><td>localhost:9092</td></tr>
 *   <tr><td>shinwa.runtime.kafka.topic-prefix</td><td>Topic 前缀</td><td></td></tr>
 *   <tr><td>shinwa.runtime.module-id</td><td>当前模块 ID</td><td>unknown</td></tr>
 * </table>
 * 
 * <h3>条件装配</h3>
 * <ul>
 *   <li>需KafkaTemplate 类存在（spring-kafka 依赖。</li>
 *   <li>配置 shinwa.runtime.kafka.enabled=true（默认）</li>
 * </ul>
 * 
 * @author Brix Platform Authors Platform Team
 * @since 3.0.0
 */
@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(name = "brix.infra.kafka.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KafkaEventBusProperties.class)
@EnableScheduling
public class KafkaAutoConfiguration {

    /**
     * 配置 Kafka Producer Factory
     * 
     * <p>创建 Kafka 生产者工厂，配置序列化器和生产者参。</p>
     * 
     * @param properties Kafka 配置属性
     * @return ProducerFactory 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ProducerFactory<String, String> kafkaProducerFactory(KafkaEventBusProperties properties) {
        Map<String, Object> configs = new HashMap<>();
        
        // Kafka 连接配置
        configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        
        // 序列化配置
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        
        // 可靠性配置（从配置属性读取）
        KafkaEventBusProperties.ProducerProperties producer = properties.getProducer();
        configs.put(ProducerConfig.ACKS_CONFIG, producer.getAcks());
        configs.put(ProducerConfig.RETRIES_CONFIG, producer.getRetries());
        configs.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);  // 幂等性生产者
        
        // 性能配置（从配置属性读取）
        configs.put(ProducerConfig.BATCH_SIZE_CONFIG, producer.getBatchSize());
        configs.put(ProducerConfig.LINGER_MS_CONFIG, producer.getLingerMs());
        configs.put(ProducerConfig.BUFFER_MEMORY_CONFIG, producer.getBufferMemory());
        
        return new DefaultKafkaProducerFactory<>(configs);
    }

    /**
     * 配置 KafkaTemplate
     * 
     * @param producerFactory Producer 工厂
     * @return KafkaTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * 配置 Kafka Admin（用。Topic 管理。
     * 
     * @param properties Kafka 配置属性
     * @return KafkaAdmin 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public KafkaAdmin kafkaAdmin(KafkaEventBusProperties properties) {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        return new KafkaAdmin(configs);
    }

    /**
     * 创建事件序列化专用 ObjectMapper（内部使用，不注册为 Spring Bean）
     * 
     * <p>避免与 Spring Boot 自动配置的全局 ObjectMapper 冲突。
     * 每个适配器模块使用独立的 ObjectMapper 实例。</p>
     * 
     * @return ObjectMapper 实例
     */
    private static ObjectMapper createEventObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // 注册 Java 8 时间模块
        mapper.registerModule(new JavaTimeModule());
        
        // 日期时间序列化为 ISO-8601 格式
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // 忽略未知属性（兼容性）
        mapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, 
                false);
        
        return mapper;
    }

    /**
     * 配置 Topic 解析
     * 
     * @param properties Kafka 配置属性
     * @return EventTopicResolver 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public EventTopicResolver eventTopicResolver(KafkaEventBusProperties properties) {
        return new EventTopicResolver(properties.getTopicPrefix());
    }

    /**
     * 配置 EventBusCapability 实现
     * 
     * <p>这是核心的能力实现Bean，模块通过 RuntimeContext 获取此实现</p>
     * 
     * @param kafkaTemplate Kafka 模板
     * @param topicResolver Topic 解析
     * @param objectMapper  JSON 序列化器
     * @param moduleId      当前模块 ID
     * @return EventBusCapability 实例
     */
    @Bean
    @ConditionalOnMissingBean(EventBusCapability.class)
    public EventBusCapability kafkaEventBusCapability(
            KafkaTemplate<String, String> kafkaTemplate,
            EventTopicResolver topicResolver,
            @Value("${brix.infra.module-id:unknown}") String moduleId) {
        return new KafkaEventBusCapability(kafkaTemplate, topicResolver, createEventObjectMapper(), moduleId);
    }

    /**
     * Configures Kafka health indicator for Actuator.
     *
     * <p>Reports Kafka broker connectivity status at /actuator/health endpoint.</p>
     *
     * <p>配置 Kafka 健康指示器，在 /actuator/health 端点报告 Broker 连通性状态。</p>
     *
     * @param kafkaAdmin Kafka Admin client for cluster metadata
     * @param properties Kafka configuration properties
     * @return KafkaHealthIndicator instance
     */
    @Bean
    @ConditionalOnMissingBean(KafkaHealthIndicator.class)
    public KafkaHealthIndicator kafkaHealthIndicator(
            KafkaAdmin kafkaAdmin,
            KafkaEventBusProperties properties) {
        return new KafkaHealthIndicator(kafkaAdmin, properties.getHealth().getTimeoutSeconds());
    }

}
