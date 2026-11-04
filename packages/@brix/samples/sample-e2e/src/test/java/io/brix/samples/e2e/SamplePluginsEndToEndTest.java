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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
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

import io.brix.samples.orders.server.Heartbeat;
import io.runtime.orchestrator.bootstrap.RuntimeShellBootstrap;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.plugin.PluginRuntimeManager;
import io.runtime.orchestrator.task.RuntimeTaskScheduler;
import io.runtime.orchestrator.task.TaskMetrics;
import io.runtime.orchestrator.task.TaskProperties;
import io.runtime.orchestrator.task.TaskRunClaims;

/**
 * The sample plugins end to end, through the plugin runtime, on PostgreSQL and Kafka (Kafka design §14 K23, §14.5
 * item 3): a member places an order over HTTP; sample-orders writes it and its {@code SampleOrderPlaced} event in one
 * transaction and sends {@code SampleOrderActivity} after the commit; the relay publishes the event to Kafka;
 * sample-shipping prepares one shipment in the order's tenant, records the event in its inbox and shows the shipment
 * over HTTP. When the member refuses the shipment, sample-shipping sends sample-orders' typed command
 * {@code sample-orders.cancel-order} through its own outbox and Kafka, and sample-orders cancels the order once
 * however often it is asked (standard-link plan L4.7). sample-orders' heartbeat task runs once per trigger across
 * hosts (L6.7).
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
    // As many partitions per event and command topic as a production host has at least (standard-link plan point 5).
    "brix.infra.kafka.events.partitions=12",
    // The hard cap of every typed query; a host whose plugins declare queries does not start without it (MESSAGE-005).
    "brix.query.max-deadline=5s",
    // sample-orders' heartbeat is due every two seconds, more often than a host allows by default.
    "brix.tasks.min-interval=1s"
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

    @Autowired
    private PluginRuntimeManager plugins;

    @Autowired
    private OwnerPersistenceProvider owners;

    @Autowired
    private TaskRunClaims claims;

    @Autowired
    private RuntimeTaskScheduler scheduler;

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
        // sample-orders takes commands (an inbox, a business key table, and an outcome table for commands that took
        // no key: standard-link plan point 7), sample-shipping sends them (an outbox). Its task's triggers are claimed
        // in a task-run table.
        assertEquals(List.of("brix_msg_history_sample_orders_command_outcome",
                "brix_msg_history_sample_orders_idempotency", "brix_msg_history_sample_orders_inbox",
                "brix_msg_history_sample_orders_outbox", "brix_msg_history_sample_orders_task_run",
                "brix_msg_history_sample_shipping_inbox", "brix_msg_history_sample_shipping_outbox",
                "sample_orders_command_idempotency", "sample_orders_command_outcome", "sample_orders_inbox",
                "sample_orders_order", "sample_orders_outbox",
                "sample_orders_task_run", "sample_shipping_inbox", "sample_shipping_outbox", "sample_shipping_shipment"),
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
        assertEquals("sample-shipping.shipment_not_found", JSON.readTree(unknown.body()).get("errorCode").asString());

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
        assertEquals("sample-orders.order_not_found", JSON.readTree(noOrder.body()).get("errorCode").asString());
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
        // Standard-link plan point 7: the refusal only said queued; sample-shipping reads by the command id that
        // sample-orders ran it.
        HttpResponse<String> cancellation = get("/api/sample-shipping/cancellations/" + commandId);
        assertEquals(200, cancellation.statusCode(), cancellation.body());
        assertEquals("SUCCEEDED", JSON.readTree(cancellation.body()).get("status").asString(), cancellation.body());
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
        // The second command reads as a duplicate of the first, which holds the order's key; a command id
        // sample-shipping never sent tells nothing.
        JsonNode duplicate = JSON.readTree(get("/api/sample-shipping/cancellations/" + secondId).body());
        assertEquals("DUPLICATE", duplicate.get("status").asString(), duplicate.toString());
        assertEquals(commandId, duplicate.get("originalCommandId").asString(), duplicate.toString());
        assertEquals(List.of(secondId), jdbc.queryForList("SELECT command_id FROM sample_orders_command_outcome"
            + " WHERE outcome = 'DUPLICATE' AND holder_command_id = ?", String.class, commandId));
        JsonNode neverSent = JSON.readTree(get("/api/sample-shipping/cancellations/never-sent").body());
        assertEquals("UNKNOWN", neverSent.get("status").asString(), neverSent.toString());

        // Standard-link plan point 8: refusing again with another reason reuses the order's key with other content.
        // sample-orders takes that command as a conflict, without running it, and the order keeps its first reason.
        HttpResponse<String> damaged = post("/api/sample-shipping/shipments/" + orderId + "/refuse",
            Map.of("reason", "DAMAGED"));
        assertEquals(202, damaged.statusCode(), damaged.body());
        String thirdId = JSON.readTree(damaged.body()).get("commandId").asString();
        await("sample-orders took the third command", () -> count(jdbc, "SELECT COUNT(*) FROM sample_orders_inbox"
            + " WHERE handler_id = ? AND message_id = ?", "sample-orders.cancel-order.v1", thirdId) == 1);
        JsonNode conflict = JSON.readTree(get("/api/sample-shipping/cancellations/" + thirdId).body());
        assertEquals("CONFLICT", conflict.get("status").asString(), conflict.toString());
        assertEquals("command.idempotency_conflict", conflict.get("errorCode").asString(), conflict.toString());
        assertEquals(commandId, conflict.get("originalCommandId").asString(), conflict.toString());
        assertEquals("SHIPPING_REFUSED", jdbc.queryForObject("SELECT cancel_reason FROM sample_orders_order"
            + " WHERE id = ?", String.class, Long.parseLong(orderId)));
        assertEquals(2L, jdbc.queryForObject("SELECT version FROM sample_orders_order WHERE id = ?", Long.class,
            Long.parseLong(orderId)));
        assertEquals("1.1.0", jdbc.queryForObject("SELECT schema_version FROM sample_shipping_outbox"
            + " WHERE message_id = ?", String.class, thirdId), "sent with the contract's new minor version");
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM sample_orders_command_idempotency"
            + " WHERE request_fingerprint ~ '^[0-9a-f]{64}$'"), "the key keeps the first command's fingerprint");
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
        // A request that breaks its schema never reaches the handler: the runtime names the path and the rule.
        HttpResponse<String> refused = post("/api/sample-orders/orders", Map.of("sku", "sku-2", "quantity", 0));
        assertEquals(400, refused.statusCode(), refused.body());
        JsonNode invalid = JSON.readTree(refused.body());
        assertEquals("endpoint.invalid_request", invalid.get("errorCode").asString(), refused.body());
        assertEquals("minimum", invalid.get("violations").get(0).get("rule").asString(), refused.body());
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM sample_orders_order"));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM sample_shipping_shipment"));

        // sample-orders' heartbeat runs once per trigger across hosts (standard-link plan L6.7): this host's scheduler
        // and another one on the same database, as a second host's would be, claim each trigger in
        // sample_orders_task_run, and only the one whose claim commits first runs it.
        TaskProperties otherHost = new TaskProperties();
        otherHost.setInstanceId("sample-e2e-other-host");
        otherHost.setMinInterval(Duration.ofSeconds(1));
        List<String> competed = new CopyOnWriteArrayList<>();
        RuntimeTaskScheduler other = new RuntimeTaskScheduler(otherHost, plugins::taskProviders, owners, () -> claims,
            new TaskMetrics() {
                @Override
                public void finished(String moduleId, String taskId, String outcome, Duration took, Instant at) {
                    competed.add("ran");
                }

                @Override
                public void skipped(String moduleId, String taskId, String reason) {
                    competed.add(reason);
                }
            }, Clock.systemUTC());
        String heartbeats = "SELECT COUNT(*) FROM sample_orders_task_run"
            + " WHERE task_id = 'sample-orders.heartbeat' AND status = 'SUCCEEDED'";
        int before = count(jdbc, heartbeats);
        other.start();
        try {
            await("three more heartbeats ran while both hosts competed", () -> count(jdbc, heartbeats) >= before + 3
                && competed.size() >= 3);
        } finally {
            other.stop();
        }
        assertTrue(competed.stream().allMatch(outcome -> "ran".equals(outcome) || "claimed".equals(outcome)),
            competed.toString());
        Heartbeat heartbeat = (Heartbeat) plugins.taskProviders().tasks().stream()
            .filter(provider -> "sample-orders.heartbeat".equals(provider.declaration().taskId()))
            .findFirst().orElseThrow().task();
        List<String> ran = heartbeat.triggers();
        assertEquals(ran.size(), Set.copyOf(ran).size(), "no trigger ran twice: " + ran);
        List<String> succeeded = jdbc.queryForList("SELECT trigger_id FROM sample_orders_task_run"
            + " WHERE task_id = 'sample-orders.heartbeat' AND status = 'SUCCEEDED'", String.class);
        assertTrue(ran.containsAll(succeeded), ran + " ran, " + succeeded + " recorded");
        assertTrue(Set.of(scheduler.instanceId(), "sample-e2e-other-host").containsAll(
            jdbc.queryForList("SELECT DISTINCT claimed_by FROM sample_orders_task_run", String.class)));
    }

    private HttpResponse<String> post(String path, Map<String, Object> body) throws Exception {
        return SampleInfrastructure.post(port, path, body);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return SampleInfrastructure.get(port, path);
    }
}
