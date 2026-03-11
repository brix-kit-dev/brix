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
 * Outbox Pattern Auto-Configuration.
 *
 * <p>Spring Boot auto-configuration class. When classpath contains both JPA and Kafka dependencies,
 * and Kafka adapter is enabled, automatically configures the Outbox event publisher.</p>
 *
 * <h3>Architecture Position</h3>
 * <p>
 * This configuration belongs to the {@code infra-adapter-outbox} standalone module (Layer 2.5: Adapter Layer).
 * Outbox is a cross-infrastructure pattern (requires DB + MQ coordination), serving as an optional
 * enhancement module for {@code infra-adapter-kafka}, independently managing JPA entity scanning
 * and Repository registration.
 * </p>
 *
 * <h3>Activation Conditions</h3>
 * <ul>
 *   <li>classpath contains {@link KafkaTemplate} (spring-kafka dependency)</li>
 *   <li>classpath contains JPA Repository (spring-boot-starter-data-jpa dependency)</li>
 *   <li>configured {@code brix.infra.kafka.enabled=true} (default enabled)</li>
 *   <li>exists {@link EventTopicResolver} Bean (provided by kafka adapter)</li>
 * </ul>
 *
 * <h3>Configuration Items</h3>
 * <p>Configured via {@code brix.infra.kafka.outbox.*} prefix:</p>
 * <table border="1">
 *   <tr><th>Configuration</th><th>Description</th><th>Default</th></tr>
 *   <tr><td>brix.infra.kafka.outbox.batch-size</td><td>Events per batch</td><td>100</td></tr>
 *   <tr><td>brix.infra.kafka.outbox.max-retry-count</td><td>Maximum retry count</td><td>5</td></tr>
 *   <tr><td>brix.infra.kafka.outbox.retention-days</td><td>Completed event retention days</td><td>7</td></tr>
 *   <tr><td>brix.infra.kafka.outbox.process-interval-ms</td><td>Processing interval (ms)</td><td>1000</td></tr>
 *   <tr><td>brix.infra.kafka.outbox.retry-interval-ms</td><td>Retry interval (ms)</td><td>30000</td></tr>
 *   <tr><td>brix.infra.kafka.outbox.cleanup-cron</td><td>Cleanup scheduled Cron</td><td>0 0 3 * * ?</td></tr>
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
     * Create Outbox event serialization dedicated ObjectMapper (internal use, not registered as Spring Bean).
     *
     * <p>Avoids conflicts with Spring Boot auto-configured global ObjectMapper.
     * Configures Java 8 time module and ISO-8601 date format.</p>
     *
     * @return ObjectMapper instance
     */
    private static ObjectMapper createOutboxObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Register Java 8 time module (supports Instant, LocalDateTime, etc.)
        mapper.registerModule(new JavaTimeModule());
        // Serialize datetime in ISO-8601 format (not timestamps)
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Ignore unknown properties (compatibility, avoids deserialization failures)
        mapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false);
        return mapper;
    }

    /**
     * Configure Outbox event publisher.
     *
     * <p>Auto-configures when {@link OutboxEventRepository} Bean (JPA auto-proxied)
     * and {@link EventTopicResolver} Bean (provided by kafka adapter) exist.</p>
     *
     * @param outboxRepository Outbox JPA repository
     * @param kafkaTemplate    Kafka message template
     * @param topicResolver    Event Topic resolver (from infra-adapter-kafka)
     * @param properties       Kafka configuration properties (includes Outbox sub-configuration)
     * @return OutboxEventPublisher instance
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
