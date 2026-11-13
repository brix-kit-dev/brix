/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.e2e;

import static io.brix.samples.e2e.SampleInfrastructure.JSON;
import static io.brix.samples.e2e.SampleInfrastructure.WAIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

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
import tools.jackson.databind.JsonNode;

/**
 * A platform operator's typed endpoint, end to end on PostgreSQL (idempotency design §5.6, D78 to D85; root
 * BRX-RS-HOST-014, HOST-019, HOST-020, MANIFEST-018): the runtime holds it to its schemas and its declared status as it
 * does a plugin's endpoint, and the write happens in sample-orders' own unit of work, in the tenant the command names.
 * The key goes with that write, so a repeated call changes nothing, and a call that stopped after the owner committed
 * goes on from there. What the contract changes it announces through sample-orders' own outbox, with the write
 * (standard-link plan queue item 8, D125), and sample-shipping's shipment follows it.
 */
@SpringBootTest(classes = SampleHost.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.jpa.open-in-view=false",
    "brix.capability.required[0]=io.runtime.sdk.capability.EventBusCapability",
    "brix.capability.required[1]=io.runtime.sdk.capability.IdGeneratorCapability",
    "brix.runtime-shell.required-plugins[0]=sample-orders",
    "brix.runtime-shell.required-plugins[1]=sample-shipping",
    "brix.runtime-shell.required-operational-modules[0]=sample-operations",
    "brix.infra.database.migration.fail-on-error=true",
    "brix.outbox.relay.enabled=true",
    "brix.outbox.relay.persistence.enabled=true",
    "brix.outbox.relay.owner=sample-e2e",
    "brix.outbox.relay.transport=kafka",
    "brix.outbox.relay.poll-delay=100ms",
    "brix.outbox.relay.retry-backoff-base=200ms",
    "brix.outbox.relay.retry-jitter-max=50ms",
    "brix.infra.kafka.outbox.publish-timeout-ms=3000",
    "brix.query.max-deadline=5s",
    "brix.tasks.min-interval=1s"
})
@EnabledIfEnvironmentVariable(named = "BRIX_P0_POSTGRES_URL", matches = ".+",
    disabledReason = "needs a throwaway PostgreSQL in BRIX_P0_POSTGRES_URL, _USER and _PASSWORD")
class SampleOperationalEndpointEndToEndTest {

    private static final SampleInfrastructure INFRASTRUCTURE = new SampleInfrastructure();
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final String ENTRY = "sample-operations/sample-operations.adjust-quantity.v1";
    private static final String ADJUSTED = "SampleOrderQuantityAdjusted";

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

    @BeforeEach
    void ready() throws Exception {
        // The cases share the host, which starts once.
        if (STARTED.compareAndSet(false, true)) {
            runtime.start().readyFuture().toCompletableFuture().get(WAIT.toSeconds(), TimeUnit.SECONDS);
        }
        SampleOperationsModule.FAIL_AFTER_THE_WRITE.set(false);
    }

    @Test
    void anOperatorChangesAnOrderThroughTheOwnersContract() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String orderId = placedOrder();

        // The declared status, and a response the runtime checked against its schema.
        HttpResponse<String> adjusted = adjust(SampleHost.TENANT, orderId, Map.of("delta", 2), key());
        assertEquals(201, adjusted.statusCode(), adjusted.body());
        JsonNode answer = JSON.readTree(adjusted.body());
        assertEquals(orderId, answer.get("orderId").asString());
        assertEquals(5, answer.get("quantity").asInt());
        assertEquals(2, answer.get("version").asInt());
        assertEquals(5, quantity(jdbc, orderId));

        // A request the schema refuses never reaches the handler.
        HttpResponse<String> invalid = adjust(SampleHost.TENANT, orderId, Map.of("delta", "two"), key());
        assertEquals(400, invalid.statusCode(), invalid.body());
        assertEquals("endpoint.invalid_request", JSON.readTree(invalid.body()).get("errorCode").asString());
        HttpResponse<String> nothing = adjust(SampleHost.TENANT, orderId, Map.of(), key());
        assertEquals(400, nothing.statusCode(), nothing.body());

        // The owner's refusal rolls its unit of work back, and its code reaches the operator.
        HttpResponse<String> tooMany = adjust(SampleHost.TENANT, orderId, Map.of("delta", 5000), key());
        assertEquals(409, tooMany.statusCode(), tooMany.body());
        assertEquals("SAMPLE_ORDER_QUANTITY_INVALID", JSON.readTree(tooMany.body()).get("errorCode").asString());

        // The unit of work is bound to the tenant the command names: another tenant does not see the order.
        HttpResponse<String> elsewhere = adjust(SampleHost.TENANT + 1, orderId, Map.of("delta", 1), key());
        assertEquals(404, elsewhere.statusCode(), elsewhere.body());
        assertEquals("SAMPLE_ORDER_NOT_FOUND", JSON.readTree(elsewhere.body()).get("errorCode").asString());

        // A member's token is not a platform token.
        HttpResponse<String> member = SampleInfrastructure.post(port, path(SampleHost.TENANT, orderId), Map.of("delta", 1),
            key());
        assertEquals(403, member.statusCode(), member.body());

        assertEquals(5, quantity(jdbc, orderId));
    }

    @Test
    void theShipmentFollowsTheChangeThroughTheOwnersOutbox() throws Exception {
        // Standard-link plan queue item 8, D125.
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String orderId = placedOrder();
        SampleInfrastructure.await("sample-shipping prepared the shipment", () -> shipped(jdbc, orderId, 3, 1));

        HttpResponse<String> adjusted = adjust(SampleHost.TENANT, orderId, Map.of("delta", 2), key());
        assertEquals(201, adjusted.statusCode(), adjusted.body());

        // The contract's event went into sample-orders' outbox with the change, in the order's tenant, and the relay
        // published it.
        assertEquals(1, adjustments(jdbc, orderId));
        SampleInfrastructure.await("the relay published SampleOrderQuantityAdjusted", () -> "PUBLISHED".equals(
            jdbc.queryForObject("SELECT status FROM sample_orders_outbox WHERE message_type = ? AND partition_key = ?",
                String.class, ADJUSTED, "order." + orderId)));
        Map<String, Object> outbox = jdbc.queryForMap("SELECT event_id, tenant_id, scope, reliability,"
            + " producer_plugin_id FROM sample_orders_outbox WHERE message_type = ? AND partition_key = ?", ADJUSTED,
            "order." + orderId);
        assertEquals(SampleHost.TENANT, ((Number) outbox.get("tenant_id")).longValue());
        assertEquals("TENANT", outbox.get("scope"));
        assertEquals("STANDARD", outbox.get("reliability"));
        assertEquals("sample-orders", outbox.get("producer_plugin_id"));

        // sample-shipping's shipment followed it, and its inbox recorded the event.
        SampleInfrastructure.await("the shipment follows the change", () -> shipped(jdbc, orderId, 5, 2));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM sample_shipping_inbox WHERE handler_id = ? AND message_id = ?"
            + " AND status = 'PROCESSED'", "sample-shipping.follow-quantity.v1", outbox.get("event_id")));

        // A change the owner refuses rolls back with its unit of work, event and all; the shipment stays.
        HttpResponse<String> tooMany = adjust(SampleHost.TENANT, orderId, Map.of("delta", 5000), key());
        assertEquals(409, tooMany.statusCode(), tooMany.body());
        assertEquals(1, adjustments(jdbc, orderId));
        assertTrue(shipped(jdbc, orderId, 5, 2));
    }

    @Test
    void aChangeIsReadWithTheDigitsTheOperatorWrote() throws Exception {
        // Standard-link plan queue item 7, D123, D124.
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String orderId = placedOrder();

        // What the host cannot read as JSON is answered as it was.
        assertEquals(400, adjustText(orderId, "application/json", "{\"delta\": ").statusCode());
        assertEquals(415, adjustText(orderId, "text/plain", "{\"delta\": 1}").statusCode());
        assertEquals(3, quantity(jdbc, orderId));
        // A whole number written with a fraction of zero is the whole number, as it was.
        HttpResponse<String> whole = adjustText(orderId, "application/json", "{\"delta\": 2.0}");
        assertEquals(201, whole.statusCode(), whole.body());
        assertEquals(5, quantity(jdbc, orderId));

        // 2 as a double, so the change used to be made.
        HttpResponse<String> notWhole = adjustText(orderId, "application/json", "{\"delta\": 2.0000000000000001}");
        assertEquals(400, notWhole.statusCode(), notWhole.body());
        assertViolation(notWhole, "/delta", "type");
        // Eleven characters, a billion digits written out: refused before anything works on it.
        HttpResponse<String> tooLong = adjustText(orderId, "application/json", "{\"delta\": 1e999999999}");
        assertEquals(400, tooLong.statusCode(), tooLong.body());
        assertViolation(tooLong, "/delta", "type");
        assertEquals(5, quantity(jdbc, orderId));
        // Only the change that was made is announced.
        assertEquals(1, adjustments(jdbc, orderId));
    }

    @Test
    void aRepeatedChangeIsMadeOnce() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String orderId = placedOrder();
        String key = key();
        HttpResponse<String> first = adjust(SampleHost.TENANT, orderId, Map.of("delta", 2), key);
        assertEquals(201, first.statusCode(), first.body());
        int handled = SampleOperationsModule.HANDLED.get();

        // The first answer again, before the handler: the owner writes nothing.
        HttpResponse<String> again = adjust(SampleHost.TENANT, orderId, Map.of("delta", 2), key);
        assertEquals(201, again.statusCode(), again.body());
        assertEquals("true", again.headers().firstValue("Idempotent-Replayed").orElse(null));
        assertEquals(JSON.readTree(first.body()), JSON.readTree(again.body()));
        assertEquals(handled, SampleOperationsModule.HANDLED.get());
        assertEquals(5, quantity(jdbc, orderId));

        // Other content under the key, and no key at all, are refused before the handler.
        HttpResponse<String> other = adjust(SampleHost.TENANT, orderId, Map.of("delta", 1), key);
        assertEquals(422, other.statusCode(), other.body());
        assertEquals("endpoint.idempotency_conflict", JSON.readTree(other.body()).get("errorCode").asString());
        HttpResponse<String> none = SampleInfrastructure.postAsOperator(port, path(SampleHost.TENANT, orderId),
            Map.of("delta", 1));
        assertEquals(400, none.statusCode(), none.body());
        assertEquals("endpoint.idempotency_key_required", JSON.readTree(none.body()).get("errorCode").asString());
        assertEquals(handled, SampleOperationsModule.HANDLED.get());
        assertEquals(5, quantity(jdbc, orderId));

        // The owner's table holds the record: the contract's result and the endpoint's answer, under the operator's scope.
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM sample_orders_endpoint_idempotency WHERE entry_id = ?"
            + " AND idempotency_key = ? AND result_status = 201 AND idempotency_scope = 'platform|9001'"
            + " AND write_result ->> 'quantity' = '5' AND result_body ->> 'orderId' = ?", ENTRY, key, orderId));
        // The repeats announced nothing: the one event is the first call's.
        assertEquals(1, adjustments(jdbc, orderId));
    }

    @Test
    void aChangeThatStoppedAfterTheOwnerCommittedGoesOnFromThere() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String orderId = placedOrder();
        String key = key();
        SampleOperationsModule.FAIL_AFTER_THE_WRITE.set(true);

        // The owner committed, then the handler failed: the change stands, with its result and no answer.
        HttpResponse<String> stopped = adjust(SampleHost.TENANT, orderId, Map.of("delta", 1), key);
        assertEquals(503, stopped.statusCode(), stopped.body());
        assertEquals(4, quantity(jdbc, orderId));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM sample_orders_endpoint_idempotency WHERE idempotency_key = ?"
            + " AND result_status IS NULL AND write_result ->> 'quantity' = '4'", key));
        // Its event committed with it, so the shipment follows a change the operator has no answer for yet.
        assertEquals(1, adjustments(jdbc, orderId));
        SampleInfrastructure.await("the shipment follows the committed change", () -> shipped(jdbc, orderId, 4, 2));

        // Other content is still a conflict, before the handler.
        HttpResponse<String> other = adjust(SampleHost.TENANT, orderId, Map.of("delta", 2), key);
        assertEquals(422, other.statusCode(), other.body());

        // The handler runs again and the contract gives its result again; the owner writes nothing.
        SampleOperationsModule.FAIL_AFTER_THE_WRITE.set(false);
        int handled = SampleOperationsModule.HANDLED.get();
        HttpResponse<String> resumed = adjust(SampleHost.TENANT, orderId, Map.of("delta", 1), key);
        assertEquals(201, resumed.statusCode(), resumed.body());
        assertEquals("true", resumed.headers().firstValue("Idempotent-Replayed").orElse(null));
        assertEquals(4, JSON.readTree(resumed.body()).get("quantity").asInt());
        assertEquals(handled + 1, SampleOperationsModule.HANDLED.get());
        assertEquals(4, quantity(jdbc, orderId));

        // From now on the answer comes before the handler.
        HttpResponse<String> again = adjust(SampleHost.TENANT, orderId, Map.of("delta", 1), key);
        assertEquals("true", again.headers().firstValue("Idempotent-Replayed").orElse(null));
        assertEquals(JSON.readTree(resumed.body()), JSON.readTree(again.body()));
        assertEquals(handled + 1, SampleOperationsModule.HANDLED.get());
        // The contract's kept result came back without the contract running again, so nothing was announced twice.
        assertEquals(1, adjustments(jdbc, orderId));
    }

    @Test
    void aChangeTheOwnerRefusesLeavesItsKeyFree() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String orderId = placedOrder();
        String key = key();

        HttpResponse<String> refused = adjust(SampleHost.TENANT, orderId, Map.of("delta", 5000), key);
        assertEquals(409, refused.statusCode(), refused.body());
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM sample_orders_endpoint_idempotency WHERE idempotency_key = ?", key));
        assertEquals(0, adjustments(jdbc, orderId));

        // Nothing holds the key, so other content under it is a new call.
        HttpResponse<String> changed = adjust(SampleHost.TENANT, orderId, Map.of("delta", 1), key);
        assertEquals(201, changed.statusCode(), changed.body());
        assertEquals(4, quantity(jdbc, orderId));
        assertEquals(1, adjustments(jdbc, orderId));
    }

    @Test
    void aSecondCallWhileTheFirstRunsIsToldAtOnce() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String orderId = placedOrder();
        String key = key();
        try (Connection holder = INFRASTRUCTURE.connection()) {
            holder.setAutoCommit(false);
            try (Statement statement = holder.createStatement()) {
                statement.execute("LOCK TABLE sample_orders_order IN ACCESS EXCLUSIVE MODE");
            }
            // The first call waits for the order table in the owner's transaction, holding the key.
            CompletableFuture<HttpResponse<String>> running = CompletableFuture.supplyAsync(() -> {
                try {
                    return adjust(SampleHost.TENANT, orderId, Map.of("delta", 1), key);
                } catch (Exception failed) {
                    throw new IllegalStateException(failed);
                }
            });
            SampleInfrastructure.await("the first call took its key", () -> count(jdbc,
                "SELECT COUNT(*) FROM pg_locks WHERE locktype = 'advisory' AND granted") == 1);
            long started = System.nanoTime();
            HttpResponse<String> whileRunning = adjust(SampleHost.TENANT, orderId, Map.of("delta", 1), key);
            assertEquals(409, whileRunning.statusCode(), whileRunning.body());
            assertEquals("endpoint.idempotency_in_progress", JSON.readTree(whileRunning.body()).get("errorCode").asString());
            assertTrue(Duration.ofNanos(System.nanoTime() - started).toSeconds() < 5, "it did not wait for the first");
            holder.rollback();
            assertEquals(201, running.get(30, TimeUnit.SECONDS).statusCode());
        }
        assertEquals(4, quantity(jdbc, orderId));
        assertEquals("true", adjust(SampleHost.TENANT, orderId, Map.of("delta", 1), key).headers()
            .firstValue("Idempotent-Replayed").orElse(null));
        assertEquals(4, quantity(jdbc, orderId));
    }

    /** Places an order of three items as the member, and returns its id. */
    private String placedOrder() throws Exception {
        HttpResponse<String> placed = SampleInfrastructure.place(port, Map.of("sku", "sku-1", "quantity", 3));
        assertEquals(201, placed.statusCode(), placed.body());
        return JSON.readTree(placed.body()).get("orderId").asString();
    }

    private HttpResponse<String> adjust(long tenantId, String orderId, Map<String, Object> body, String key)
            throws Exception {
        return SampleInfrastructure.postAsOperator(port, path(tenantId, orderId), body, key);
    }

    private HttpResponse<String> adjustText(String orderId, String contentType, String text) throws Exception {
        return SampleInfrastructure.postTextAsOperator(port, path(SampleHost.TENANT, orderId), contentType, text, key());
    }

    private static void assertViolation(HttpResponse<String> answer, String path, String rule) {
        JsonNode body = JSON.readTree(answer.body());
        assertEquals("endpoint.invalid_request", body.get("errorCode").asString(), answer.body());
        assertEquals(1, body.get("violations").size(), answer.body());
        assertEquals(path, body.get("violations").get(0).get("path").asString(), answer.body());
        assertEquals(rule, body.get("violations").get(0).get("rule").asString(), answer.body());
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private static String path(long tenantId, String orderId) {
        return "/api/platform/sample-orders/" + tenantId + "/orders/" + orderId + "/quantity-adjustments";
    }

    private static int count(JdbcTemplate jdbc, String sql, Object... arguments) {
        return SampleInfrastructure.count(jdbc, sql, arguments);
    }

    private static int quantity(JdbcTemplate jdbc, String orderId) {
        return count(jdbc, "SELECT quantity FROM sample_orders_order WHERE id = ?", Long.valueOf(orderId));
    }

    /** How many {@code SampleOrderQuantityAdjusted} events sample-orders' outbox holds for the order. */
    private static int adjustments(JdbcTemplate jdbc, String orderId) {
        return count(jdbc, "SELECT COUNT(*) FROM sample_orders_outbox WHERE message_type = ? AND partition_key = ?",
            ADJUSTED, "order." + orderId);
    }

    /** Whether sample-shipping's shipment of the order shows this quantity and this version of the order. */
    private static boolean shipped(JdbcTemplate jdbc, String orderId, int quantity, long version) {
        return count(jdbc, "SELECT COUNT(*) FROM sample_shipping_shipment WHERE order_id = ? AND quantity = ?"
            + " AND order_version = ?", orderId, quantity, version) == 1;
    }
}
