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
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
            .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
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
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("timed out waiting until " + what);
            }
            Thread.sleep(100);
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getenv("BRIX_P0_POSTGRES_URL"),
            System.getenv("BRIX_P0_POSTGRES_USER"), System.getenv("BRIX_P0_POSTGRES_PASSWORD"));
    }
}
