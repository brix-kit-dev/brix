/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.runtime.sdk.capability.CommandCapability;
import io.runtime.sdk.capability.CommandSubmission;
import io.runtime.sdk.event.EventScope;
import io.runtime.sdk.plugin.AccessDecision;
import io.runtime.sdk.plugin.CommandInvocation;
import io.runtime.sdk.plugin.IdempotencyContext;
import io.runtime.sdk.plugin.TraceContext;

/**
 * Typed commands in the SDK (root BRX-RS-MANIFEST-027, MANIFEST-028, A-22; standard-link plan L4.2).
 */
class CommandTypeTest {

    record Cancel(String orderId) {
    }

    @Test
    void aCommandTypeNamesItsContract() {
        CommandType<Cancel> type = CommandType.of("sample-orders.cancel-order", "1.2.0", Cancel.class);

        assertEquals("sample-orders.cancel-order", type.commandType());
        assertEquals("1.2.0", type.version());
        assertEquals(1, type.major());
        assertEquals(Cancel.class, type.requestType());
        assertEquals(type, CommandType.of("sample-orders.cancel-order", "1.2.0", Cancel.class));
    }

    @Test
    void anIllegalNameOrVersionIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> CommandType.of("has space", "1.0.0", Cancel.class));
        assertThrows(IllegalArgumentException.class, () -> CommandType.of("a.b", "1.0", Cancel.class));
    }

    @Test
    void theInvocationCarriesTheFrozenMembers() {
        // Root §5.3: command, access, trace, deadline, cancellation, idempotency; the attempt count is extra.
        List<String> names = Arrays.stream(CommandInvocation.class.getRecordComponents())
            .map(RecordComponent::getName).toList();

        assertEquals(List.of("command", "access", "trace", "deadline", "cancellation", "idempotency",
            "deliveryAttempt"), names);
    }

    @Test
    void anInvocationBuiltTheOldWayHasNoAccessAndTheEnvelopesKey() {
        CommandInvocation<Cancel> invocation = new CommandInvocation<>(envelope(null),
            new TraceContext("00-0123456789abcdef0123456789abcdef-0123456789abcdef-01", null), Instant.EPOCH,
            () -> false, 1);

        assertEquals(AccessDecision.none(), invocation.access());
        assertEquals(new IdempotencyContext("order", "o-7"), invocation.idempotency());
        assertEquals(null, invocation.command().actorId());
    }

    @Test
    void anEnvelopeCarriesTheUserItWasSentFor() {
        assertEquals("u-1", envelope("u-1").actorId());
        assertEquals(null, envelope(" ").actorId());
    }

    @Test
    void aCapabilityWithoutTypedClientsSaysSo() {
        CommandCapability legacy = new CommandCapability() {
            @Override
            @SuppressWarnings("removal")
            public <C> CommandSubmission submit(CommandRequest<C> request) {
                throw new AssertionError("not called");
            }
        };

        assertThrows(UnsupportedOperationException.class,
            () -> legacy.client(CommandType.of("sample-orders.cancel-order", "1.0.0", Cancel.class)));
    }

    @Test
    void anIdempotencyContextNeedsAScopeAndAKey() {
        assertThrows(IllegalArgumentException.class, () -> new IdempotencyContext(" ", "o-7"));
        assertThrows(IllegalArgumentException.class, () -> new IdempotencyContext("order", null));
    }

    private static CommandEnvelope<Cancel> envelope(String actorId) {
        return new CommandEnvelope<>("c-1", "sample-orders.cancel-order", "1.0.0", Instant.EPOCH, "sample-shipping",
            EventScope.PLATFORM, null, "c-1", null, "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01", null,
            "o-7", "order", "o-7", new Cancel("o-7"), actorId);
    }
}
