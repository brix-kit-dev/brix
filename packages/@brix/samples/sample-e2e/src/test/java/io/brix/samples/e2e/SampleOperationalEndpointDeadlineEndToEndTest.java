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
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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

/**
 * An operator's change whose handler answers after its deadline, end to end on PostgreSQL (root BRX-RS-DATA-011,
 * RESILIENCE-001; standard-link plan queue item 6, D122). The owner committed the change in its own unit of work in
 * time, so the call's 504 leaves the outcome unknown to the caller; a repeat with the same key gets the kept result
 * without the owner writing again. The host's endpoint deadline is 4 s here, which caps {@code interactive-write}.
 */
@SpringBootTest(classes = SampleHost.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.jpa.open-in-view=false",
    "brix.capability.required[0]=io.runtime.sdk.capability.EventBusCapability",
    "brix.capability.required[1]=io.runtime.sdk.capability.IdGeneratorCapability",
    "brix.runtime-shell.required-plugins[0]=sample-orders",
    "brix.runtime-shell.required-plugins[1]=sample-shipping",
    "brix.runtime-shell.required-operational-modules[0]=sample-operations",
    "brix.runtime-shell.endpoint-deadline-millis=4000",
    "brix.infra.database.migration.fail-on-error=true",
    "brix.outbox.relay.enabled=true",
    "brix.outbox.relay.persistence.enabled=true",
    "brix.outbox.relay.owner=sample-e2e",
    "brix.outbox.relay.transport=kafka",
    "brix.outbox.relay.poll-delay=100ms",
    "brix.outbox.relay.retry-backoff-base=200ms",
    "brix.outbox.relay.retry-jitter-max=50ms",
    "brix.infra.kafka.outbox.publish-timeout-ms=3000",
    "brix.query.max-deadline=4s",
    "brix.tasks.min-interval=1s"
})
@EnabledIfEnvironmentVariable(named = "BRIX_P0_POSTGRES_URL", matches = ".+",
    disabledReason = "needs a throwaway PostgreSQL in BRIX_P0_POSTGRES_URL, _USER and _PASSWORD")
class SampleOperationalEndpointDeadlineEndToEndTest {

    private static final SampleInfrastructure INFRASTRUCTURE = new SampleInfrastructure();
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final Duration PAST_THE_DEADLINE = Duration.ofSeconds(5);

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
        if (STARTED.compareAndSet(false, true)) {
            runtime.start().readyFuture().toCompletableFuture().get(WAIT.toSeconds(), TimeUnit.SECONDS);
        }
        SampleOperationsModule.FAIL_AFTER_THE_WRITE.set(false);
        SampleOperationsModule.PAUSE_AFTER_THE_WRITE.set(Duration.ZERO);
    }

    @AfterEach
    void calm() {
        SampleOperationsModule.PAUSE_AFTER_THE_WRITE.set(Duration.ZERO);
    }

    @Test
    void aChangeAnsweredAfterTheDeadlineIs504AndARepeatGoesOnFromThere() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String orderId = placedOrder();
        String key = UUID.randomUUID().toString();
        SampleOperationsModule.PAUSE_AFTER_THE_WRITE.set(PAST_THE_DEADLINE);

        // The owner committed in time; the handler answered after the deadline: 504, and no answer is kept.
        HttpResponse<String> late = adjust(orderId, key);
        assertEquals(504, late.statusCode(), late.body());
        assertEquals("endpoint.deadline_exceeded", JSON.readTree(late.body()).get("errorCode").asString());
        assertEquals(4, quantity(jdbc, orderId));
        assertEquals(1, SampleInfrastructure.count(jdbc, "SELECT COUNT(*) FROM sample_orders_endpoint_idempotency"
            + " WHERE idempotency_key = ? AND result_status IS NULL AND write_result ->> 'quantity' = '4'", key));

        // The repeat runs the handler again; the contract gives the kept result, and the owner writes nothing.
        SampleOperationsModule.PAUSE_AFTER_THE_WRITE.set(Duration.ZERO);
        int handled = SampleOperationsModule.HANDLED.get();
        HttpResponse<String> resumed = adjust(orderId, key);
        assertEquals(201, resumed.statusCode(), resumed.body());
        assertEquals("true", resumed.headers().firstValue("Idempotent-Replayed").orElse(null));
        assertEquals(4, JSON.readTree(resumed.body()).get("quantity").asInt());
        assertEquals(handled + 1, SampleOperationsModule.HANDLED.get());
        assertEquals(4, quantity(jdbc, orderId));
    }

    /** Places an order of three items as the member, and returns its id. */
    private String placedOrder() throws Exception {
        HttpResponse<String> placed = SampleInfrastructure.place(port, Map.of("sku", "sku-1", "quantity", 3));
        assertEquals(201, placed.statusCode(), placed.body());
        return JSON.readTree(placed.body()).get("orderId").asString();
    }

    private HttpResponse<String> adjust(String orderId, String key) throws Exception {
        return SampleInfrastructure.postAsOperator(port, "/api/platform/sample-orders/" + SampleHost.TENANT + "/orders/"
            + orderId + "/quantity-adjustments", Map.of("delta", 1), key);
    }

    private static int quantity(JdbcTemplate jdbc, String orderId) {
        return SampleInfrastructure.count(jdbc, "SELECT quantity FROM sample_orders_order WHERE id = ?",
            Long.valueOf(orderId));
    }
}
