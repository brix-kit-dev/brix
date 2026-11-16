/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import io.runtime.sdk.plugin.TraceContext;

/**
 * A delivery runs with its trace on the thread's MDC, and the thread gets back what its MDC held, however the delivery
 * ends (root BRX-RS-ERROR-013, ERROR-017 item 7; standard-link plan D133).
 */
class DeliveryTracesTest {

    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    private static final String OUTER_TRACEPARENT = "00-11111111111111111111111111111111-2222222222222222-01";

    @AfterEach
    void clearTheThread() {
        MDC.clear();
    }

    @Test
    void theDeliveryRunsWithItsTraceAndTheThreadGetsBackWhatItHad() {
        MDC.put(DeliveryTraces.TRACEPARENT, OUTER_TRACEPARENT);
        MDC.put(DeliveryTraces.TRACE_ID, "11111111111111111111111111111111");
        List<String> seen = new ArrayList<>();

        DeliveryTraces.within(new TraceContext(TRACEPARENT, "vendor=1"),
            () -> seen.addAll(List.of(MDC.get("traceparent"), MDC.get("traceId"))));

        assertEquals(List.of(TRACEPARENT, "4bf92f3577b34da6a3ce929d0e0e4736"), seen);
        assertEquals(OUTER_TRACEPARENT, MDC.get("traceparent"));
        assertEquals("11111111111111111111111111111111", MDC.get("traceId"));
    }

    @Test
    void aThreadWithoutATraceHasNoneAfterADeliveryThatFailed() {
        IllegalStateException failure = new IllegalStateException("the handler failed");

        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> DeliveryTraces.within(new TraceContext(TRACEPARENT, null), () -> {
                throw failure;
            })));

        assertNull(MDC.get("traceparent"));
        assertNull(MDC.get("traceId"));
    }

    @Test
    void onlyAWellFormedTraceparentCarriesADelivery() {
        assertTrue(DeliveryTraces.wellFormed(TRACEPARENT));
        assertFalse(DeliveryTraces.wellFormed(null));
        assertFalse(DeliveryTraces.wellFormed(""));
        assertFalse(DeliveryTraces.wellFormed("not-a-traceparent"));
        assertFalse(DeliveryTraces.wellFormed(" " + TRACEPARENT), "no padding");
        assertFalse(DeliveryTraces.wellFormed("01-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"), "version 00");
        assertFalse(DeliveryTraces.wellFormed("00-4BF92F3577B34DA6A3CE929D0E0E4736-00f067aa0ba902b7-01"), "lower case");
        assertFalse(DeliveryTraces.wellFormed("00-00000000000000000000000000000000-00f067aa0ba902b7-01"), "trace id");
        assertFalse(DeliveryTraces.wellFormed("00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01"), "span id");
    }

    @Test
    void aDeliveryWhoseTraceIsNotWellFormedDoesNotRun() {
        List<String> ran = new ArrayList<>();

        assertThrows(IllegalArgumentException.class,
            () -> DeliveryTraces.within(new TraceContext("not-a-traceparent", null), () -> ran.add("ran")));

        assertTrue(ran.isEmpty());
        assertNull(MDC.get("traceparent"));
        assertNull(MDC.get("traceId"));
    }
}
