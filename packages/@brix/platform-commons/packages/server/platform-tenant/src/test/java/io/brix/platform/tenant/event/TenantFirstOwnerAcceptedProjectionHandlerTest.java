/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import io.runtime.sdk.event.EventEnvelope;
import io.runtime.sdk.event.EventReliability;
import io.runtime.sdk.event.EventScope;
import io.runtime.sdk.plugin.CancellationSignal;
import io.runtime.sdk.plugin.EventInvocation;
import io.runtime.sdk.plugin.PublisherDecision;
import io.runtime.sdk.plugin.TraceContext;

class TenantFirstOwnerAcceptedProjectionHandlerTest {

    private static final TenantFirstOwnerAcceptedPayload ACCEPTED = new TenantFirstOwnerAcceptedPayload(110L, 7L, 8L, 410L);

    @Test
    void writesTheProjectionUnderTheDeliveredEventId() {
        List<String> written = new ArrayList<>();

        CompletionStage<Void> handled = new TenantFirstOwnerAcceptedProjectionHandler(
            (eventId, payload) -> written.add(eventId + " " + payload)).handle(delivery(ACCEPTED));

        assertTrue(handled.toCompletableFuture().isDone());
        assertEquals(List.of("event-1 " + ACCEPTED), written);
    }

    @Test
    void aFailedWriteFailsTheDeliverySoTheRuntimeRollsItBack() {
        TenantFirstOwnerAcceptedProjectionHandler handler = new TenantFirstOwnerAcceptedProjectionHandler((eventId, payload) -> {
            throw new IllegalStateException("projection unavailable");
        });

        assertThrows(IllegalStateException.class, () -> handler.handle(delivery(ACCEPTED)));
    }

    @Test
    void thePayloadDecodesFromTheOutboxJsonAndRejectsAMissingField() {
        ObjectMapper json = new ObjectMapper();

        assertEquals(ACCEPTED, json.readValue(
            "{\"tenantId\":110,\"memberId\":7,\"profileId\":8,\"invitationId\":410}", TenantFirstOwnerAcceptedPayload.class));
        assertThrows(JacksonException.class, () -> json.readValue(
            "{\"tenantId\":110,\"memberId\":7,\"profileId\":8}", TenantFirstOwnerAcceptedPayload.class));
    }

    private static EventInvocation<TenantFirstOwnerAcceptedPayload> delivery(TenantFirstOwnerAcceptedPayload payload) {
        String traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
        return new EventInvocation<>(
            new EventEnvelope<>("event-1", TenantFirstOwnerAcceptedEvent.EVENT_TYPE, "1.0.0", Instant.now(),
                "platform-tenant", EventScope.TENANT, "110", "correlation-1", null, traceparent, "110", payload,
                EventReliability.CRITICAL),
            new PublisherDecision("platform-tenant", null), new TraceContext(traceparent, null),
            Instant.now().plusSeconds(30), CancellationSignal.none(), 1);
    }
}
