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
package io.brix.architecture.guard;

/**
 * Architecture Layer Constants
 *
 * <p>Defines package patterns for each layer in Brix Platform architecture,
 * used for ArchUnit rule matching.</p>
 *
 * <h2>Layer Hierarchy</h2>
 * <pre>
 *  ┌───────────────────────┐
 *  │    Plugin Layer       │  io.brix.app.**
 *  ├───────────────────────┤
 *  │  Runtime SDK API      │  io.runtime.sdk.**
 *  ├───────────────────────┤
 *  │  Orchestrator Layer   │  io.runtime.orchestrator.**
 *  ├───────────────────────┤
 *  │  Infrastructure       │  io.brix.infra.adapter.**
 *  └───────────────────────┘
 * </pre>
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 */
public final class ArchitectureLayers {

    private ArchitectureLayers() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== Infrastructure Adapter Packages ====================

    /** Root package for all infrastructure adapters */
    public static final String INFRA_ADAPTER_ROOT = "io.brix.infra.adapter..";

    /** Kafka adapter package */
    public static final String INFRA_ADAPTER_KAFKA = "io.brix.infra.adapter.kafka..";

    /** Redis adapter package */
    public static final String INFRA_ADAPTER_REDIS = "io.brix.infra.adapter.redis..";

    /** Webhook adapter package */
    public static final String INFRA_ADAPTER_WEBHOOK = "io.brix.infra.adapter.webhook..";

    /** OpenTelemetry adapter package */
    public static final String INFRA_ADAPTER_OTEL = "io.brix.infra.adapter.otel..";

    // ==================== Middleware Client Packages ====================

    /** Spring Kafka */
    public static final String SPRING_KAFKA = "org.springframework.kafka..";

    /** Apache Kafka */
    public static final String APACHE_KAFKA = "org.apache.kafka..";

    /** Spring Data Redis */
    public static final String SPRING_DATA_REDIS = "org.springframework.data.redis..";

    /** Jedis */
    public static final String JEDIS = "redis.clients.jedis..";

    /** Lettuce */
    public static final String LETTUCE = "io.lettuce.core..";

    /** Spring AMQP / RabbitMQ */
    public static final String SPRING_AMQP = "org.springframework.amqp..";

    // ==================== HTTP Client Packages ====================

    /** Spring RestTemplate */
    public static final String REST_TEMPLATE = "org.springframework.web.client..";

    /** Spring WebClient */
    public static final String WEB_CLIENT = "org.springframework.web.reactive.function.client..";

    /** OpenFeign */
    public static final String OPEN_FEIGN = "org.springframework.cloud.openfeign..";

    /** OkHttp */
    public static final String OKHTTP = "com.squareup.okhttp3..";

    /** JDK HttpClient (java.net.http package) */
    public static final String JDK_HTTP_CLIENT = "java.net.http..";

    // ==================== Spring Container APIs ====================

    /** Spring ApplicationContext */
    public static final String APPLICATION_CONTEXT = "org.springframework.context.ApplicationContext";

    /** Spring BeanFactory */
    public static final String BEAN_FACTORY = "org.springframework.beans.factory.BeanFactory";

    // ==================== Spring Events ====================

    /** Spring ApplicationEventPublisher */
    public static final String APPLICATION_EVENT_PUBLISHER = "org.springframework.context.ApplicationEventPublisher";

    /** Spring ApplicationEvent */
    public static final String APPLICATION_EVENT = "org.springframework.context.ApplicationEvent";

    /** Spring EventListener annotation */
    public static final String EVENT_LISTENER = "org.springframework.context.event.EventListener";
}
