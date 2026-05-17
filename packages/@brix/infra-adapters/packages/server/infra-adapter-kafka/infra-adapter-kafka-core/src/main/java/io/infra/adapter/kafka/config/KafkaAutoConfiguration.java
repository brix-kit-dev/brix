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
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
import io.infra.adapter.kafka.metrics.KafkaConsumerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.runtime.sdk.capability.EventBusCapability;

/**
 * Kafka Event Bus Auto-Configuration.
 * 
 * <p>Spring Boot auto-configuration class, responsible for initializing Kafka-related Beans.
 * Takes effect automatically when Kafka dependencies exist in classpath and Kafka connection info is configured.</p>
 * 
 * <h3>Configuration Items</h3>
 * <table border="1">
 *   <tr><th>Configuration</th><th>Description</th><th>Default</th></tr>
 *   <tr><td>brix.runtime.kafka.enabled</td><td>Whether to enable</td><td>true</td></tr>
 *   <tr><td>brix.runtime.kafka.bootstrap-servers</td><td>Kafka address</td><td>localhost:9092</td></tr>
 *   <tr><td>brix.runtime.kafka.topic-prefix</td><td>Topic prefix</td><td></td></tr>
 *   <tr><td>brix.runtime.module-id</td><td>Current module ID</td><td>unknown</td></tr>
 * </table>
 * 
 * <h3>Conditional Assembly</h3>
 * <ul>
 *   <li>Requires KafkaTemplate class to be present (spring-kafka dependency)</li>
 *   <li>Configured brix.runtime.kafka.enabled=true (default)</li>
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
     * Configure Kafka Producer Factory.
     * 
     * <p>Creates Kafka producer factory with serializer and producer parameter configuration.</p>
     * 
     * @param properties Kafka configuration properties
     * @return ProducerFactory instance
     */
    @Bean
    @ConditionalOnMissingBean
    public ProducerFactory<String, String> kafkaProducerFactory(KafkaEventBusProperties properties) {
        Map<String, Object> configs = new HashMap<>();
        
        // Kafka connection configuration
        configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        
        // Serialization configuration
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        
        // Reliability configuration (read from config properties)
        KafkaEventBusProperties.ProducerProperties producer = properties.getProducer();
        configs.put(ProducerConfig.ACKS_CONFIG, producer.getAcks());
        configs.put(ProducerConfig.RETRIES_CONFIG, producer.getRetries());
        configs.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);  // Idempotent producer
        
        // Performance configuration (read from config properties)
        configs.put(ProducerConfig.BATCH_SIZE_CONFIG, producer.getBatchSize());
        configs.put(ProducerConfig.LINGER_MS_CONFIG, producer.getLingerMs());
        configs.put(ProducerConfig.BUFFER_MEMORY_CONFIG, producer.getBufferMemory());
        
        return new DefaultKafkaProducerFactory<>(configs);
    }

    /**
     * Configure KafkaTemplate.
     * 
     * @param producerFactory Producer factory
     * @return KafkaTemplate instance
     */
    @Bean
    @ConditionalOnMissingBean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * Configure Kafka Admin (for Topic management).
     * 
     * @param properties Kafka configuration properties
     * @return KafkaAdmin instance
     */
    @Bean
    @ConditionalOnMissingBean
    public KafkaAdmin kafkaAdmin(KafkaEventBusProperties properties) {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        return new KafkaAdmin(configs);
    }

    /**
     * Create event serialization dedicated ObjectMapper (internal use, not registered as Spring Bean).
     * 
     * <p>Avoids conflicts with Spring Boot auto-configured global ObjectMapper.
     * Each adapter module uses an independent ObjectMapper instance.</p>
     * 
     * @return ObjectMapper instance
     */
    private static ObjectMapper createEventObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Register Java 8 time module
        mapper.registerModule(new JavaTimeModule());
        
        // Serialize datetime in ISO-8601 format
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Ignore unknown properties (compatibility)
        mapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, 
                false);
        
        return mapper;
    }

    /**
     * Configure Topic resolver.
     * 
     * @param properties Kafka configuration properties
     * @return EventTopicResolver instance
     */
    @Bean
    @ConditionalOnMissingBean
    public EventTopicResolver eventTopicResolver(KafkaEventBusProperties properties) {
        return new EventTopicResolver(properties.getTopicPrefix());
    }

    /**
     * Configure EventBusCapability implementation.
     * 
     * <p>This is the core capability implementation Bean, modules obtain this implementation through RuntimeContext.</p>
     *
     * <p>The {@code tenantIdProvider} is injected optionally.  When {@code platform-common}
     * is on the classpath, its auto-configuration registers a
     * {@code Supplier<Optional<String>>} bean backed by {@code TenantContext}.
     * If absent (e.g., in a pure unit-test setup), the adapter falls back to requiring
     * callers to set tenantId on events explicitly.</p>
     * 
     * @param kafkaTemplate    Kafka template
     * @param topicResolver    Topic resolver
     * @param moduleId         Current module ID
     * @param tenantIdProvider Optional tenant ID supplier from platform-common
     * @return EventBusCapability instance
     */
    @Bean
    @ConditionalOnMissingBean(EventBusCapability.class)
    public KafkaEventBusCapability kafkaEventBusCapability(
            KafkaTemplate<String, String> kafkaTemplate,
            EventTopicResolver topicResolver,
            @Value("${brix.infra.module-id:unknown}") String moduleId,
            @Autowired(required = false) Supplier<Optional<String>> tenantIdProvider) {
        return new KafkaEventBusCapability(
                kafkaTemplate, topicResolver, createEventObjectMapper(), moduleId, tenantIdProvider);
    }

    /**
     * Configures Kafka health indicator for Actuator.
     *
     * <p>Reports Kafka broker connectivity status at /actuator/health endpoint.</p>
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

    /**
     * Configures consumer-side Micrometer metrics for event observability.
     *
     * <p>Activates only when a {@link MeterRegistry} bean is present (typically via
     * {@code spring-boot-starter-actuator}). Provides counters for consumed events,
     * retry attempts, and dead-letter queue routing — fulfilling Architecture Red
     * Line 3 (all cross-plugin communication must be observable).</p>
     *
     * @param meterRegistry the Micrometer meter registry
     * @return KafkaConsumerMetrics instance
     * @since 3.2.0
     */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(KafkaConsumerMetrics.class)
    public KafkaConsumerMetrics kafkaConsumerMetrics(MeterRegistry meterRegistry) {
        return new KafkaConsumerMetrics(meterRegistry);
    }

}
