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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;

import io.runtime.orchestrator.bootstrap.RuntimeShellBootstrap;

/**
 * The sample plugins end to end, through the plugin runtime, on PostgreSQL and Kafka (Kafka design §14 K23, §14.5
 * item 3): a member places an order over HTTP; sample-orders writes it and its {@code SampleOrderPlaced} event in one
 * transaction and sends {@code SampleOrderActivity} after the commit; the relay publishes the event to Kafka;
 * sample-shipping prepares one shipment in the order's tenant, records the event in its inbox and shows the shipment
 * over HTTP. When the member refuses the shipment, sample-shipping sends sample-orders' typed command
 * {@code sample-orders.cancel-order} through its own outbox and Kafka, and sample-orders cancels the order once
 * however often it is asked (standard-link plan L4.7).
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
    "brix.infra.kafka.outbox.publish-timeout-ms=10000",
    // The hard cap of every typed query; a host whose plugins declare queries does not start without it (MESSAGE-005).
    "brix.query.max-deadline=5s"
})
@EnabledIfEnvironmentVariable(named = "BRIX_P0_POSTGRES_URL", matches = ".+",
    disabledReason = "needs a throwaway PostgreSQL in BRIX_P0_POSTGRES_URL, _USER and _PASSWORD")
class SamplePluginsEndToEndTest {

    private static final SampleInfrastructure INFRASTRUCTURE = new SampleInfrastructure();
    private static final String SCHEMA = INFRASTRUCTURE.schema();

    @LocalServerPort
    private int port;

    @Autowired
    private RuntimeShellBootstrap runtime;

    @Autowired
    private DataSource dataSource;

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

    @Test
    void anOrderPlacedOverHttpIsShippedOnceInItsTenantThroughTheOutboxAndKafka() throws Exception {
        runtime.start().readyFuture().toCompletableFuture().get(WAIT.toSeconds(), TimeUnit.SECONDS);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // The platform made the messaging tables from its own migrations; the plugins' migrations hold none.
        // sample-orders takes commands (an inbox and a business key table), sample-shipping sends them (an outbox).
        assertEquals(List.of("brix_msg_history_sample_orders_idempotency", "brix_msg_history_sample_orders_inbox",
                "brix_msg_history_sample_orders_outbox", "brix_msg_history_sample_shipping_inbox",
                "brix_msg_history_sample_shipping_outbox", "sample_orders_command_idempotency", "sample_orders_inbox",
                "sample_orders_order", "sample_orders_outbox", "sample_shipping_inbox", "sample_shipping_outbox",
                "sample_shipping_shipment"),
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

        // The member reads the shipment from sample-shipping's own endpoint; an unknown order has none.
        HttpResponse<String> shipment = get("/api/sample-shipping/shipments/" + orderId);
        assertEquals(200, shipment.statusCode(), shipment.body());
        JsonNode view = JSON.readTree(shipment.body());
        assertEquals(orderId, view.get("orderId").asString(), shipment.body());
        assertEquals("sku-1", view.get("sku").asString(), shipment.body());
        assertEquals(2, view.get("quantity").asInt(), shipment.body());
        assertEquals(1L, view.get("orderVersion").asLong(), shipment.body());
        HttpResponse<String> unknown = get("/api/sample-shipping/shipments/1");
        assertEquals(404, unknown.statusCode(), unknown.body());
        assertEquals("SAMPLE_SHIPMENT_NOT_FOUND", JSON.readTree(unknown.body()).get("errorCode").asString());

        // sample-orders shows the order with its shipment, read through sample-shipping's typed query in the member's
        // tenant, for the member (query design §4); sample-orders never touches sample-shipping's tables.
        HttpResponse<String> detail = get("/api/sample-orders/orders/" + orderId);
        assertEquals(200, detail.statusCode(), detail.body());
        JsonNode shown = JSON.readTree(detail.body());
        assertEquals("sku-1", shown.get("sku").asString(), detail.body());
        assertEquals(2, shown.get("quantity").asInt(), detail.body());
        assertEquals("PREPARED", shown.get("shipment").asString(), detail.body());
        HttpResponse<String> noOrder = get("/api/sample-orders/orders/1");
        assertEquals(404, noOrder.statusCode(), noOrder.body());
        assertEquals("PLACED", shown.get("status").asString(), detail.body());

        // The member refuses the shipment: sample-shipping asks sample-orders to cancel the order with its typed
        // command, written to sample-shipping's outbox in the request's transaction, in the member's tenant.
        HttpResponse<String> refusal = post("/api/sample-shipping/shipments/" + orderId + "/refuse", Map.of());
        assertEquals(202, refusal.statusCode(), refusal.body());
        String commandId = JSON.readTree(refusal.body()).get("commandId").asString();
        Map<String, Object> command = jdbc.queryForMap("SELECT message_kind, message_type, tenant_id, scope,"
            + " partition_key, producer_plugin_id FROM sample_shipping_outbox WHERE message_id = ?", commandId);
        assertEquals("COMMAND", command.get("message_kind"));
        assertEquals("sample-orders.cancel-order", command.get("message_type"));
        assertEquals(SampleHost.TENANT, ((Number) command.get("tenant_id")).longValue());
        assertEquals("TENANT", command.get("scope"));
        assertEquals(orderId, command.get("partition_key"));
        assertEquals("sample-shipping", command.get("producer_plugin_id"));

        // The relay published it to the command's topic; sample-orders recorded it in its inbox and cancelled the
        // order in tenant 42, keyed by the sender, the tenant and the order id.
        await("sample-orders cancelled the order", () -> "CANCELLED".equals(jdbc.queryForObject(
            "SELECT status FROM sample_orders_order WHERE id = ?", String.class, Long.parseLong(orderId))));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM sample_orders_inbox WHERE handler_id = ?"
            + " AND message_id = ? AND status = 'PROCESSED'", "sample-orders.cancel-order.v1", commandId));
        assertEquals(List.of(commandId), jdbc.queryForList("SELECT command_id FROM sample_orders_command_idempotency"
            + " WHERE idempotency_scope = ? AND idempotency_key = ?", String.class,
            "sample-shipping|" + SampleHost.TENANT + "|sample-orders.cancel-order", orderId));
        HttpResponse<String> cancelled = get("/api/sample-orders/orders/" + orderId);
        JsonNode cancelledView = JSON.readTree(cancelled.body());
        assertEquals("CANCELLED", cancelledView.get("status").asString(), cancelled.body());
        assertEquals(2L, cancelledView.get("version").asLong(), cancelled.body());

        // Refusing again sends a second command with the same key: sample-orders records it and leaves the order.
        HttpResponse<String> again = post("/api/sample-shipping/shipments/" + orderId + "/refuse", Map.of());
        assertEquals(202, again.statusCode(), again.body());
        String secondId = JSON.readTree(again.body()).get("commandId").asString();
        await("sample-orders took the second command", () -> count(jdbc, "SELECT COUNT(*) FROM sample_orders_inbox"
            + " WHERE handler_id = ? AND message_id = ? AND status = 'PROCESSED'", "sample-orders.cancel-order.v1",
            secondId) == 1);
        assertEquals(List.of(commandId), jdbc.queryForList("SELECT command_id FROM sample_orders_command_idempotency",
            String.class));
        assertEquals(2L, jdbc.queryForObject("SELECT version FROM sample_orders_order WHERE id = ?", Long.class,
            Long.parseLong(orderId)));
        HttpResponse<String> noShipment = post("/api/sample-shipping/shipments/1/refuse", Map.of());
        assertEquals(404, noShipment.statusCode(), noShipment.body());

        // SampleOrderActivity is BEST_EFFORT: it skipped the outbox and was sent once the order committed.
        assertEquals(0, count(jdbc,
            "SELECT COUNT(*) FROM sample_orders_outbox WHERE message_type = 'SampleOrderActivity'"));
        List<String> activity = INFRASTRUCTURE.read(INFRASTRUCTURE.topic("brix.event.SampleOrderActivity"));
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
        return SampleInfrastructure.post(port, path, body);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return SampleInfrastructure.get(port, path);
    }
}
