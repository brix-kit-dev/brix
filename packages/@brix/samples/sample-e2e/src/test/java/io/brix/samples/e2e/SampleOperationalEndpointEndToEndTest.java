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

import java.net.http.HttpResponse;
import java.sql.SQLException;
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

import io.runtime.orchestrator.bootstrap.RuntimeShellBootstrap;
import tools.jackson.databind.JsonNode;

/**
 * A platform operator's typed endpoint, end to end on PostgreSQL (idempotency design D78; root BRX-RS-HOST-014,
 * HOST-019, HOST-020): the runtime holds it to its schemas and its declared status as it does a plugin's endpoint, and
 * the write happens in sample-orders' own unit of work, in the tenant the command names.
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
    void anOperatorChangesAnOrderThroughTheOwnersContract() throws Exception {
        runtime.start().readyFuture().toCompletableFuture().get(WAIT.toSeconds(), TimeUnit.SECONDS);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        HttpResponse<String> placed = SampleInfrastructure.place(port, Map.of("sku", "sku-1", "quantity", 3));
        assertEquals(201, placed.statusCode(), placed.body());
        String orderId = JSON.readTree(placed.body()).get("orderId").asString();

        // The declared status, and a response the runtime checked against its schema.
        HttpResponse<String> adjusted = adjust(SampleHost.TENANT, orderId, Map.of("delta", 2));
        assertEquals(201, adjusted.statusCode(), adjusted.body());
        JsonNode answer = JSON.readTree(adjusted.body());
        assertEquals(orderId, answer.get("orderId").asString());
        assertEquals(5, answer.get("quantity").asInt());
        assertEquals(2, answer.get("version").asInt());
        assertEquals(5, quantity(jdbc, orderId));

        // A request the schema refuses never reaches the handler.
        HttpResponse<String> invalid = adjust(SampleHost.TENANT, orderId, Map.of("delta", "two"));
        assertEquals(400, invalid.statusCode(), invalid.body());
        assertEquals("endpoint.invalid_request", JSON.readTree(invalid.body()).get("errorCode").asString());
        HttpResponse<String> nothing = adjust(SampleHost.TENANT, orderId, Map.of());
        assertEquals(400, nothing.statusCode(), nothing.body());

        // The owner's refusal rolls its unit of work back, and its code reaches the operator.
        HttpResponse<String> tooMany = adjust(SampleHost.TENANT, orderId, Map.of("delta", 5000));
        assertEquals(409, tooMany.statusCode(), tooMany.body());
        assertEquals("SAMPLE_ORDER_QUANTITY_INVALID", JSON.readTree(tooMany.body()).get("errorCode").asString());

        // The unit of work is bound to the tenant the command names: another tenant does not see the order.
        HttpResponse<String> elsewhere = adjust(SampleHost.TENANT + 1, orderId, Map.of("delta", 1));
        assertEquals(404, elsewhere.statusCode(), elsewhere.body());
        assertEquals("SAMPLE_ORDER_NOT_FOUND", JSON.readTree(elsewhere.body()).get("errorCode").asString());

        // A member's token is not a platform token.
        HttpResponse<String> member = SampleInfrastructure.post(port, path(SampleHost.TENANT, orderId), Map.of("delta", 1));
        assertEquals(403, member.statusCode(), member.body());

        assertEquals(5, quantity(jdbc, orderId));
    }

    private HttpResponse<String> adjust(long tenantId, String orderId, Map<String, Object> body) throws Exception {
        return SampleInfrastructure.postAsOperator(port, path(tenantId, orderId), body);
    }

    private static String path(long tenantId, String orderId) {
        return "/api/platform/sample-orders/" + tenantId + "/orders/" + orderId + "/quantity-adjustments";
    }

    private static int quantity(JdbcTemplate jdbc, String orderId) {
        return SampleInfrastructure.count(jdbc, "SELECT quantity FROM sample_orders_order WHERE id = ?",
            Long.valueOf(orderId));
    }
}
