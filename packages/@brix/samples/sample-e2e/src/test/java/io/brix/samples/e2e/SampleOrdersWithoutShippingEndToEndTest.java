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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;

import io.runtime.orchestrator.bootstrap.RuntimeShellBootstrap;

/**
 * sample-orders in a host without sample-shipping (query design §4.2, Q9): nothing provides the optional typed query
 * {@code sample-shipping.find-shipment}, yet sample-orders starts and the host becomes ready (root BRX-RS-LIFE-002),
 * and the order detail says its shipment is unavailable rather than missing (root BRX-RS-RESILIENCE-005).
 *
 * <p>The build runs this class in an execution of its own, without sample-shipping's server and core artifacts on the
 * class path; sample-orders keeps sample-shipping-api, the contract it was compiled against.</p>
 */
@SpringBootTest(classes = SampleHost.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.jpa.open-in-view=false",
    "brix.capability.required[0]=io.runtime.sdk.capability.EventBusCapability",
    "brix.capability.required[1]=io.runtime.sdk.capability.IdGeneratorCapability",
    "brix.runtime-shell.required-plugins[0]=sample-orders",
    "brix.infra.database.migration.fail-on-error=true",
    "brix.outbox.relay.enabled=true",
    "brix.outbox.relay.persistence.enabled=true",
    "brix.outbox.relay.owner=sample-e2e",
    "brix.outbox.relay.transport=kafka",
    "brix.outbox.relay.poll-delay=100ms",
    "brix.query.max-deadline=5s"
})
@EnabledIfEnvironmentVariable(named = "BRIX_P0_POSTGRES_URL", matches = ".+",
    disabledReason = "needs a throwaway PostgreSQL in BRIX_P0_POSTGRES_URL, _USER and _PASSWORD")
@EnabledIf(value = "withoutShipping", disabledReason = "runs only where sample-shipping is not on the class path")
class SampleOrdersWithoutShippingEndToEndTest {

    private static final SampleInfrastructure INFRASTRUCTURE = new SampleInfrastructure();

    @LocalServerPort
    private int port;

    @Autowired
    private RuntimeShellBootstrap runtime;

    static boolean withoutShipping() {
        try {
            Class.forName("io.brix.samples.shipping.server.SampleShippingPlugin", false,
                SampleOrdersWithoutShippingEndToEndTest.class.getClassLoader());
            return false;
        } catch (ClassNotFoundException absent) {
            return true;
        }
    }

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
    void anOrderIsShownWithItsShipmentUnavailableWhenNoPluginProvidesTheQuery() throws Exception {
        runtime.start().readyFuture().toCompletableFuture().get(WAIT.toSeconds(), TimeUnit.SECONDS);

        HttpResponse<String> placed = SampleInfrastructure.post(port, "/api/sample-orders/orders",
            Map.of("sku", "sku-1", "quantity", 2));
        assertEquals(201, placed.statusCode(), placed.body());
        String orderId = JSON.readTree(placed.body()).get("orderId").asString();

        HttpResponse<String> detail = SampleInfrastructure.get(port, "/api/sample-orders/orders/" + orderId);
        assertEquals(200, detail.statusCode(), detail.body());
        JsonNode shown = JSON.readTree(detail.body());
        assertEquals("sku-1", shown.get("sku").asString(), detail.body());
        assertEquals("UNAVAILABLE", shown.get("shipment").asString(), detail.body());
    }
}
