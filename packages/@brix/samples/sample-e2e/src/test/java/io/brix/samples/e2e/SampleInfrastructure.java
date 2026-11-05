/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.e2e;

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
import java.util.function.BooleanSupplier;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.test.context.DynamicPropertyRegistry;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * What one end-to-end test class runs against: its own PostgreSQL schema in the throwaway database of
 * {@code BRIX_P0_POSTGRES_URL}, an embedded Kafka broker with its own topic prefix, and the HTTP calls of a member.
 * Nothing is shared between runs.
 */
final class SampleInfrastructure {

    static final Duration WAIT = Duration.ofSeconds(60);
    static final ObjectMapper JSON = JsonMapper.builder().build();

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private final String schema;
    private final String topics;
    private EmbeddedKafkaBroker kafka;

    SampleInfrastructure() {
        String run = Long.toHexString(System.nanoTime() & 0xffffffL);
        this.schema = "sample_e2e_" + run;
        this.topics = "sample-e2e-" + run + "-";
    }

    String schema() {
        return schema;
    }

    String topic(String name) {
        return topics + name;
    }

    void start() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        kafka = new EmbeddedKafkaKraftBroker(1, 3).brokerProperties(Map.of(
            "auto.create.topics.enable", "false",
            "group.initial.rebalance.delay.ms", "0"));
        kafka.afterPropertiesSet();
    }

    void stop() throws SQLException {
        if (kafka != null) {
            kafka.destroy();
        }
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    void register(DynamicPropertyRegistry registry) {
        String url = System.getenv("BRIX_P0_POSTGRES_URL");
        registry.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema);
        registry.add("spring.datasource.username", () -> System.getenv("BRIX_P0_POSTGRES_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("BRIX_P0_POSTGRES_PASSWORD"));
        registry.add("brix.infra.kafka.bootstrap-servers", () -> kafka.getBrokersAsString());
        registry.add("brix.infra.kafka.topic-prefix", () -> topics);
    }

    static HttpResponse<String> post(int port, String path, Map<String, Object> body) throws Exception {
        return HTTP.send(request(port, path, body).build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Sends a request with a key, as a client of an endpoint that replays its results does. */
    static HttpResponse<String> post(int port, String path, Map<String, Object> body, String idempotencyKey)
            throws Exception {
        return HTTP.send(request(port, path, body).header("Idempotency-Key", idempotencyKey).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    /** Sends a request as a platform operator, without a key. */
    static HttpResponse<String> postAsOperator(int port, String path, Map<String, Object> body) throws Exception {
        return HTTP.send(request(port, path, body).header(SampleHost.OPERATOR_HEADER, "9001").build(),
            HttpResponse.BodyHandlers.ofString());
    }

    /** Sends a request as a platform operator, with a key. */
    static HttpResponse<String> postAsOperator(int port, String path, Map<String, Object> body, String idempotencyKey)
            throws Exception {
        return HTTP.send(request(port, path, body).header(SampleHost.OPERATOR_HEADER, "9001")
            .header("Idempotency-Key", idempotencyKey).build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Places an order under a key of its own: sample-orders' endpoint requires one (standard-link plan point 10a). */
    static HttpResponse<String> place(int port, Map<String, Object> order) throws Exception {
        return post(port, "/api/sample-orders/orders", order, UUID.randomUUID().toString());
    }

    private static HttpRequest.Builder request(int port, String path, Map<String, Object> body) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
    }

    static HttpResponse<String> get(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    List<String> read(String topic) {
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

    static int count(JdbcTemplate jdbc, String sql, Object... arguments) {
        Integer count = jdbc.queryForObject(sql, Integer.class, arguments);
        return count == null ? 0 : count;
    }

    static void await(String what, BooleanSupplier condition) throws InterruptedException {
        await(what, WAIT, condition);
    }

    static void await(String what, Duration within, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + within.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("timed out waiting until " + what);
            }
            Thread.sleep(100);
        }
    }

    /**
     * Opens a connection of its own to this run's schema, outside the host's pool, as another application's
     * transaction does.
     */
    Connection connection() throws SQLException {
        Connection connection = connect();
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
        }
        return connection;
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getenv("BRIX_P0_POSTGRES_URL"),
            System.getenv("BRIX_P0_POSTGRES_USER"), System.getenv("BRIX_P0_POSTGRES_PASSWORD"));
    }
}
