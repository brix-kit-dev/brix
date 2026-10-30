/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javax.sql.DataSource;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import io.runtime.orchestrator.bootstrap.RuntimeShellBootstrap;

/**
 * The sample plugins end to end, through the plugin runtime, on PostgreSQL and Kafka (Kafka design §14 K23, §14.5
 * item 3): a member places an order over HTTP; sample-orders writes it and its {@code SampleOrderPlaced} event in one
 * transaction and sends {@code SampleOrderActivity} after the commit; the relay publishes the event to Kafka;
 * sample-shipping prepares one shipment in the order's tenant and records the event in its inbox.
 *
 * <p>The host is the auto-configurations and the properties below; the plugins bring only their manifests, their
 * code and their business tables. Each run gets its own PostgreSQL schema and topic prefix, so nothing is shared.</p>
 */
@SpringBootTest(classes = SampleHost.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.jpa.open-in-view=false",
    "brix.capability.required[0]=io.runtime.sdk.capability.EventBusCapability",
    "brix.capability.required[1]=io.runtime.sdk.capability.IdGeneratorCapability",
    "brix.runtime-shell.required-plugins[0]=sample-orders",
    "brix.runtime-shell.required-plugins[1]=sample-shipping",
    "brix.infra.database.migration.fail-on-error=true",
    "brix.outbox.relay.enabled=true",
    "brix.outbox.relay.persistence.enabled=true",
    "brix.outbox.relay.owner=sample-e2e",
    "brix.outbox.relay.transport=kafka",
    "brix.outbox.relay.poll-delay=100ms",
    "brix.outbox.relay.retry-backoff-base=200ms",
    "brix.outbox.relay.retry-jitter-max=50ms",
    "brix.infra.kafka.outbox.publish-timeout-ms=10000"
})
@EnabledIfEnvironmentVariable(named = "BRIX_P0_POSTGRES_URL", matches = ".+",
    disabledReason = "needs a throwaway PostgreSQL in BRIX_P0_POSTGRES_URL, _USER and _PASSWORD")
class SamplePluginsEndToEndTest {

    private static final String RUN = Long.toHexString(System.nanoTime() & 0xffffffL);
    private static final String SCHEMA = "sample_e2e_" + RUN;
    private static final String TOPICS = "sample-e2e-" + RUN + "-";
    private static final Duration WAIT = Duration.ofSeconds(60);
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static EmbeddedKafkaBroker kafka;

    @LocalServerPort
    private int port;

    @Autowired
    private RuntimeShellBootstrap runtime;

    @Autowired
    private DataSource dataSource;

    @BeforeAll
    static void startInfrastructure() throws SQLException {
        try (Connection connection = DriverManager.getConnection(System.getenv("BRIX_P0_POSTGRES_URL"),
                System.getenv("BRIX_P0_POSTGRES_USER"), System.getenv("BRIX_P0_POSTGRES_PASSWORD"));
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
        }
        kafka = new EmbeddedKafkaKraftBroker(1, 3).brokerProperties(Map.of(
            "auto.create.topics.enable", "false",
            "group.initial.rebalance.delay.ms", "0"));
        kafka.afterPropertiesSet();
    }

    @AfterAll
    static void stopInfrastructure() throws SQLException {
        if (kafka != null) {
            kafka.destroy();
        }
        try (Connection connection = DriverManager.getConnection(System.getenv("BRIX_P0_POSTGRES_URL"),
                System.getenv("BRIX_P0_POSTGRES_USER"), System.getenv("BRIX_P0_POSTGRES_PASSWORD"));
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    @DynamicPropertySource
    static void connections(DynamicPropertyRegistry registry) {
        String url = System.getenv("BRIX_P0_POSTGRES_URL");
        registry.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
        registry.add("spring.datasource.username", () -> System.getenv("BRIX_P0_POSTGRES_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("BRIX_P0_POSTGRES_PASSWORD"));
        registry.add("brix.infra.kafka.bootstrap-servers", () -> kafka.getBrokersAsString());
        registry.add("brix.infra.kafka.topic-prefix", () -> TOPICS);
    }

    @Test
    void anOrderPlacedOverHttpIsShippedOnceInItsTenantThroughTheOutboxAndKafka() throws Exception {
        runtime.start().readyFuture().toCompletableFuture().get(WAIT.toSeconds(), TimeUnit.SECONDS);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // The platform made the messaging tables from its own migrations; the plugins' migrations hold none.
        assertEquals(List.of("brix_msg_history_sample_orders_outbox", "brix_msg_history_sample_shipping_inbox",
                "sample_orders_order", "sample_orders_outbox", "sample_shipping_inbox", "sample_shipping_shipment"),
            jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = ?"
                + " AND table_name LIKE 'sample%' OR table_schema = ? AND table_name LIKE 'brix_msg%'"
                + " ORDER BY table_name", String.class, SCHEMA, SCHEMA));

        HttpResponse<String> placed = post("/api/sample-orders/orders", Map.of("sku", "sku-1", "quantity", 2));
        assertEquals(201, placed.statusCode(), placed.body());
        String orderId = JSON.readTree(placed.body()).get("orderId").asString();

        // SampleOrderPlaced went through the outbox, in the order's tenant, and the relay published it.
        await("the relay published SampleOrderPlaced", () -> "PUBLISHED".equals(jdbc.query(
            "SELECT status FROM sample_orders_outbox WHERE message_type = 'SampleOrderPlaced'",
            rs -> rs.next() ? rs.getString(1) : null)));
        Map<String, Object> outbox = jdbc.queryForMap("SELECT event_id, tenant_id, partition_key, scope, reliability,"
            + " producer_plugin_id FROM sample_orders_outbox WHERE message_type = 'SampleOrderPlaced'");
        assertEquals(SampleHost.TENANT, ((Number) outbox.get("tenant_id")).longValue());
        assertEquals("order." + orderId, outbox.get("partition_key"));
        assertEquals("TENANT", outbox.get("scope"));
        assertEquals("STANDARD", outbox.get("reliability"));
        assertEquals("sample-orders", outbox.get("producer_plugin_id"));

        // sample-shipping prepared the shipment in tenant 42 and recorded the event in its inbox, in one transaction.
        await("sample-shipping prepared the shipment", () -> count(jdbc,
            "SELECT COUNT(*) FROM sample_shipping_shipment WHERE order_id = ?", orderId) == 1);
        assertEquals(SampleHost.TENANT, jdbc.queryForObject(
            "SELECT tenant_id FROM sample_shipping_shipment WHERE order_id = ?", Long.class, orderId));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM sample_shipping_inbox WHERE handler_id = ?"
            + " AND message_id = ? AND status = 'PROCESSED'", "sample-shipping.prepare-shipment.v1",
            outbox.get("event_id")));

        // SampleOrderActivity is BEST_EFFORT: it skipped the outbox and was sent once the order committed.
        assertEquals(0, count(jdbc,
            "SELECT COUNT(*) FROM sample_orders_outbox WHERE message_type = 'SampleOrderActivity'"));
        List<String> activity = read(TOPICS + "brix.event.SampleOrderActivity");
        assertEquals(1, activity.size(), activity.toString());
        JsonNode envelope = JSON.readTree(activity.get(0));
        assertTrue(activity.get(0).contains(orderId), activity.get(0));
        assertEquals("BEST_EFFORT", envelope.get("reliability").asString(), activity.get(0));

        // A request the handler refuses changes nothing.
        HttpResponse<String> refused = post("/api/sample-orders/orders", Map.of("sku", "sku-2", "quantity", 0));
        assertEquals(400, refused.statusCode(), refused.body());
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM sample_orders_order"));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM sample_shipping_shipment"));
    }

    private HttpResponse<String> post(String path, Map<String, Object> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
            .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static List<String> read(String topic) {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBrokersAsString());
        configs.put(ConsumerConfig.GROUP_ID_CONFIG, "sample-e2e-reader-" + UUID.randomUUID());
        configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<String> values = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(configs)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + WAIT.toNanos();
            while (values.isEmpty() && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    values.add(record.value());
                }
            }
            // Anything else sent with it arrives in the same polls.
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofSeconds(1))) {
                values.add(record.value());
            }
        }
        return values;
    }

    private static int count(JdbcTemplate jdbc, String sql, Object... arguments) {
        Integer count = jdbc.queryForObject(sql, Integer.class, arguments);
        return count == null ? 0 : count;
    }

    private static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("timed out waiting until " + what);
            }
            Thread.sleep(100);
        }
    }
}
