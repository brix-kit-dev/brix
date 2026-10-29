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

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import io.infra.adapter.kafka.CanonicalKafkaOutboxTransport;
import io.infra.adapter.kafka.KafkaEventBroker;
import io.infra.adapter.kafka.KafkaEventBusCapability;
import io.infra.adapter.kafka.KafkaEventSettings;
import io.infra.adapter.kafka.KafkaEventSubscriptionTransport;
import io.infra.adapter.kafka.KafkaEventTopics;
import io.infra.adapter.kafka.health.KafkaHealthIndicator;
import io.micrometer.core.instrument.MeterRegistry;
import io.runtime.orchestrator.event.EventSubscriptionTransport;
import io.runtime.orchestrator.outbox.OutboxTransport;
import io.runtime.orchestrator.plugin.PluginRuntimeManager;
import io.runtime.orchestrator.tracing.MessageTracing;
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
 *   <tr><td>brix.infra.kafka.enabled</td><td>Whether to enable</td><td>true</td></tr>
 *   <tr><td>brix.infra.kafka.bootstrap-servers</td><td>Kafka address</td><td>localhost:9092</td></tr>
 *   <tr><td>brix.infra.kafka.topic-prefix</td><td>Topic prefix</td><td></td></tr>
 *   <tr><td>brix.infra.module-id</td><td>Producer id of direct-bus events that name no source module</td><td>unknown</td></tr>
 * </table>
 * 
 * <h3>Conditional Assembly</h3>
 * <ul>
 *   <li>Requires KafkaTemplate class to be present (spring-kafka dependency)</li>
 *   <li>Configured brix.infra.kafka.enabled=true (default)</li>
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
        return JsonMapper.builder()
                // Keep the Jackson 2 defaults the event wire format was built on
                // (Java time types are supported natively by Jackson 3)
                .configureForJackson2()
                // Serialize datetime in ISO-8601 format
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                // Ignore unknown properties (compatibility)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
    }

    /**
     * The direct bus for BEST_EFFORT integration events (Kafka design §13 K2): canonical envelopes on
     * {@code brix.event.<eventType>}; an event type a plugin manifest declares CRITICAL or STANDARD is refused.
     *
     * <p>The {@code tenantIdProvider} is injected optionally. When {@code platform-common} is on the classpath, its
     * auto-configuration registers a {@code Supplier<Optional<String>>} bean backed by {@code TenantContext}.</p>
     *
     * @param kafkaTemplate Kafka template
     * @param topics topic names
     * @param broker the broker's reachability and topics
     * @param moduleId producer id for events that name no source module
     * @param tenantIdProvider optional tenant ID supplier from platform-common
     * @param plugins the plugin runtime, for the reliability each manifest declares
     * @param meterRegistry publish metrics, when the host has Micrometer
     * @param tracing the PRODUCER span of each event, when a tracing adapter records one
     * @return the direct bus
     */
    @Bean
    @ConditionalOnMissingBean(EventBusCapability.class)
    public KafkaEventBusCapability kafkaEventBusCapability(
            KafkaTemplate<String, String> kafkaTemplate,
            KafkaEventTopics topics,
            KafkaEventBroker broker,
            @Value("${brix.infra.module-id:unknown}") String moduleId,
            @Autowired(required = false) Supplier<Optional<String>> tenantIdProvider,
            ObjectProvider<PluginRuntimeManager> plugins,
            ObjectProvider<MeterRegistry> meterRegistry,
            ObjectProvider<MessageTracing> tracing) {
        return new KafkaEventBusCapability(kafkaTemplate, topics, broker, createEventObjectMapper(), moduleId,
            tenantIdProvider, eventType -> Optional.ofNullable(plugins.getIfAvailable())
                .flatMap(runtime -> runtime.eventReliability(eventType)),
            meterRegistry.getIfAvailable(), tracing.getIfAvailable(() -> MessageTracing.NOOP));
    }

    /**
     * Names the canonical event bus's topics and consumer groups (Kafka design D18, D19); the direct bus uses the
     * same topics.
     *
     * @param properties Kafka configuration properties
     * @return the names
     */
    @Bean
    @ConditionalOnMissingBean
    public KafkaEventTopics kafkaEventTopics(KafkaEventBusProperties properties) {
        return new KafkaEventTopics(properties.getTopicPrefix());
    }

    /**
     * Probes the broker and creates the canonical event bus's topics from the manifests (Kafka design §4.4); the
     * direct bus has its topics created the same way, so nothing relies on the broker creating topics by itself.
     *
     * @param properties Kafka configuration properties
     * @param topics topic names
     * @return the broker view; it closes its admin client with the context
     */
    @Bean
    @ConditionalOnMissingBean
    public KafkaEventBroker kafkaEventBroker(KafkaEventBusProperties properties, KafkaEventTopics topics) {
        return new KafkaEventBroker(Admin.create(clientConfigs(properties)), topics, eventSettings(properties),
            Clock.systemUTC());
    }

    /**
     * Configures the canonical Outbox transport used by the L2B Relay when {@code brix.outbox.relay.transport=kafka};
     * by default the relay delivers events in process to the subscribers' inboxes (P4 design D11).
     *
     * @param kafkaTemplate Kafka template
     * @param topics topic names
     * @param broker the broker's reachability and topics
     * @param properties Kafka configuration properties
     * @return canonical outbox transport
     */
    @Bean
    @ConditionalOnMissingBean(OutboxTransport.class)
    @ConditionalOnProperty(prefix = "brix.outbox.relay", name = "transport", havingValue = "kafka")
    public OutboxTransport kafkaOutboxTransport(
            KafkaTemplate<String, String> kafkaTemplate,
            KafkaEventTopics topics,
            KafkaEventBroker broker,
            KafkaEventBusProperties properties) {
        return new CanonicalKafkaOutboxTransport(
            kafkaTemplate,
            topics,
            broker,
            createEventObjectMapper(),
            Duration.ofMillis(properties.getOutbox().getPublishTimeoutMs()));
    }

    /**
     * Consumes the declared subscriptions from Kafka for the runtime's subscription group, with the same switch as
     * the outbox transport: events sent to Kafka are always received from it (Kafka design §4.2, §4.3).
     *
     * @param broker the broker's reachability and topics
     * @param topics topic names
     * @param properties Kafka configuration properties
     * @param kafkaTemplate writes parking records
     * @return the subscription transport
     */
    @Bean
    @ConditionalOnMissingBean(EventSubscriptionTransport.class)
    @ConditionalOnProperty(prefix = "brix.outbox.relay", name = "transport", havingValue = "kafka")
    public KafkaEventSubscriptionTransport kafkaEventSubscriptionTransport(
            KafkaEventBroker broker,
            KafkaEventTopics topics,
            KafkaEventBusProperties properties,
            KafkaTemplate<String, String> kafkaTemplate) {
        return new KafkaEventSubscriptionTransport(broker, topics, eventSettings(properties), clientConfigs(properties),
            kafkaTemplate, createEventObjectMapper(), Clock.systemUTC());
    }

    private static Map<String, Object> clientConfigs(KafkaEventBusProperties properties) {
        return Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
    }

    private static KafkaEventSettings eventSettings(KafkaEventBusProperties properties) {
        KafkaEventBusProperties.EventsProperties events = properties.getEvents();
        return new KafkaEventSettings(events.getPartitions(), events.getReplicationFactor(), events.getRetention(),
            events.getMaxPollRecords(), events.getPollTimeout(), events.getReadyCacheTtl(), events.getAdminTimeout(),
            events.getSendTimeout());
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

}
