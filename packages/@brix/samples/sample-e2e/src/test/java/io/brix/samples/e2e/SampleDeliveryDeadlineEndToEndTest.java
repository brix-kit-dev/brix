/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.e2e;

import static io.brix.samples.e2e.SampleInfrastructure.JSON;
import static io.brix.samples.e2e.SampleInfrastructure.WAIT;
import static io.brix.samples.e2e.SampleInfrastructure.await;
import static io.brix.samples.e2e.SampleInfrastructure.count;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.runtime.orchestrator.bootstrap.RuntimeShellBootstrap;
import io.runtime.orchestrator.event.MicrometerEventSubscriptionMetrics;

/**
 * A delivery's unit of work lasts no longer than its deadline, end to end on PostgreSQL and Kafka (standard-link plan
 * point 3; root BRX-RS-DATA-011, DATA-028). While another transaction holds a lock that sample-shipping's handler, or
 * sample-orders' command handler, needs for its write, the write waits; at the deadline the statement stops, the unit
 * of work rolls back with the inbox record, and the attempt counts. Once the lock is gone, the message is delivered
 * again and takes effect once.
 *
 * <p>A host gives its handlers half its relay lease: the 4 s lease here gives them 2 s.</p>
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
    "brix.outbox.relay.lease-duration=4s",
    "brix.infra.kafka.outbox.publish-timeout-ms=3000",
    "brix.query.max-deadline=5s",
    "brix.tasks.min-interval=1s"
})
@EnabledIfEnvironmentVariable(named = "BRIX_P0_POSTGRES_URL", matches = ".+",
    disabledReason = "needs a throwaway PostgreSQL in BRIX_P0_POSTGRES_URL, _USER and _PASSWORD")
class SampleDeliveryDeadlineEndToEndTest {

    private static final SampleInfrastructure INFRASTRUCTURE = new SampleInfrastructure();
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    /**
     * How soon a held-up attempt must fail: the 2 s deadline stops it. Without the deadline, the owner's 30 s
     * transaction timeout ({@code brix.persistence.transaction-timeout}) would.
     */
    private static final Duration AT_THE_DEADLINE = Duration.ofSeconds(10);

    @LocalServerPort
    private int port;

    @Autowired
    private RuntimeShellBootstrap runtime;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MeterRegistry meters;

    @BeforeAll
    static void startInfrastructure() throws SQLException {
        INFRASTRUCTURE.start();
    }

    @AfterAll
    static void stopInfrastructure() throws SQLException {
        INFRASTRUCTURE.stop();
    }

    @DynamicPropertySource
    static void connections(DynamicPropertyRegistry registry) {
        INFRASTRUCTURE.register(registry);
    }

    @BeforeEach
    void ready() throws Exception {
        // Both cases share the host, which starts once.
        if (STARTED.compareAndSet(false, true)) {
            runtime.start().readyFuture().toCompletableFuture().get(WAIT.toSeconds(), TimeUnit.SECONDS);
        }
    }

    @Test
    void aSubscriberHeldUpPastItsDeadlineRollsBackAndPreparesTheShipmentOnceTheLockIsGone() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        double before = retries("SampleOrderPlaced");
        String orderId;
        try (Connection other = INFRASTRUCTURE.connection()) {
            // Another transaction keeps every write out of the shipments; reads still pass.
            other.setAutoCommit(false);
            try (Statement statement = other.createStatement()) {
                statement.execute("LOCK TABLE sample_shipping_shipment IN SHARE MODE");
            }
            HttpResponse<String> placed = SampleInfrastructure.place(port, Map.of("sku", "sku-1", "quantity", 2));
            assertEquals(201, placed.statusCode(), placed.body());
            orderId = JSON.readTree(placed.body()).get("orderId").asString();

            // The handler's insert waited for the lock until the deadline, then its unit of work rolled back with the
            // inbox record, and the attempt counted as a failure to retry.
            await("a delivery of SampleOrderPlaced failed at its deadline", AT_THE_DEADLINE,
                () -> retries("SampleOrderPlaced") > before);
            assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM sample_shipping_shipment WHERE order_id = ?", orderId));
            assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM sample_shipping_inbox WHERE handler_id = ?",
                "sample-shipping.prepare-shipment.v1"));
            other.rollback();
        }

        // The lock is gone: the event is delivered again and takes effect once.
        await("sample-shipping prepared the shipment", () -> count(jdbc,
            "SELECT COUNT(*) FROM sample_shipping_shipment WHERE order_id = ?", orderId) == 1);
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM sample_shipping_inbox WHERE handler_id = ?"
            + " AND status = 'PROCESSED'", "sample-shipping.prepare-shipment.v1"));
    }

    @Test
    void aCommandProviderHeldUpPastItsDeadlineRollsBackAndCancelsTheOrderOnceTheLockIsGone() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        HttpResponse<String> placed = SampleInfrastructure.place(port, Map.of("sku", "sku-2", "quantity", 1));
        assertEquals(201, placed.statusCode(), placed.body());
        String orderId = JSON.readTree(placed.body()).get("orderId").asString();
        await("sample-shipping prepared the shipment", () -> count(jdbc,
            "SELECT COUNT(*) FROM sample_shipping_shipment WHERE order_id = ?", orderId) == 1);

        double before = retries("sample-orders.cancel-order");
        String commandId;
        try (Connection other = INFRASTRUCTURE.connection()) {
            // Another transaction holds the order's row: sample-orders can read the order but not change it.
            other.setAutoCommit(false);
            try (Statement statement = other.createStatement()) {
                statement.execute("SELECT id FROM sample_orders_order WHERE id = " + Long.parseLong(orderId)
                    + " FOR UPDATE");
            }
            HttpResponse<String> refusal = post("/api/sample-shipping/shipments/" + orderId + "/refuse", Map.of());
            assertEquals(202, refusal.statusCode(), refusal.body());
            commandId = JSON.readTree(refusal.body()).get("commandId").asString();

            // The update waited for the row until the deadline; the inbox record, the business key and the update
            // rolled back together, and the attempt counted.
            await("a delivery of the command failed at its deadline", AT_THE_DEADLINE,
                () -> retries("sample-orders.cancel-order") > before);
            assertEquals("PLACED", jdbc.queryForObject("SELECT status FROM sample_orders_order WHERE id = ?",
                String.class, Long.parseLong(orderId)));
            assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM sample_orders_inbox WHERE message_id = ?", commandId));
            assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM sample_orders_command_idempotency WHERE command_id = ?",
                commandId));
            other.rollback();
        }

        // The row is free again: the command is delivered again and cancels the order once.
        await("sample-orders cancelled the order", () -> "CANCELLED".equals(jdbc.queryForObject(
            "SELECT status FROM sample_orders_order WHERE id = ?", String.class, Long.parseLong(orderId))));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM sample_orders_inbox WHERE handler_id = ?"
            + " AND message_id = ? AND status = 'PROCESSED'", "sample-orders.cancel-order.v1", commandId));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM sample_orders_command_idempotency WHERE command_id = ?",
            commandId));
        assertEquals(2L, jdbc.queryForObject("SELECT version FROM sample_orders_order WHERE id = ?", Long.class,
            Long.parseLong(orderId)));
    }

    /** Deliveries of a message type that failed and wait for a retry, across its subscriptions. */
    private double retries(String type) {
        return meters.find(MicrometerEventSubscriptionMetrics.RETRIES).tag("type", type).counters().stream()
            .mapToDouble(Counter::count)
            .sum();
    }

    private HttpResponse<String> post(String path, Map<String, Object> body) throws Exception {
        return SampleInfrastructure.post(port, path, body);
    }
}
