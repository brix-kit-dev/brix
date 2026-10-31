/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.Test;

import io.runtime.sdk.plugin.AccessDecision;
import io.runtime.sdk.plugin.CallerIdentity;
import io.runtime.sdk.plugin.CancellationSignal;
import io.runtime.sdk.plugin.InvocationScope;
import io.runtime.sdk.plugin.QueryHandler;
import io.runtime.sdk.plugin.QueryInvocation;
import io.runtime.sdk.plugin.TraceContext;
import io.runtime.sdk.query.BrixQueryException;
import io.runtime.sdk.query.QueryType;

class QueryCapabilityContractTest {

    private static final String TRACEPARENT = "00-00000000000000000000000000000001-0000000000000001-01";

    record Request(String id) {
    }

    record Result(String id) {
    }

    @Test
    void queryHandlerHasTheFrozenAbi() throws NoSuchMethodException {
        // Root BRX-RS-MANIFEST-027: one asynchronous method taking the runtime-built invocation.
        List<Method> methods = Arrays.stream(QueryHandler.class.getDeclaredMethods())
            .filter(method -> !method.isSynthetic())
            .toList();
        assertEquals(1, methods.size());
        Method handle = QueryHandler.class.getMethod("handle", QueryInvocation.class);
        assertEquals(CompletionStage.class, handle.getReturnType());
    }

    @Test
    void queryInvocationCarriesTheRootFieldsInOrder() {
        // Root BRX-RS-MANIFEST-028 and appendix A: request, caller, scope, access, trace, deadline, cancellation.
        assertEquals(List.of("request", "caller", "scope", "access", "trace", "deadline", "cancellation"),
            Arrays.stream(QueryInvocation.class.getRecordComponents()).map(RecordComponent::getName).toList());
        assertThrows(NullPointerException.class, () -> new QueryInvocation<>(null, CallerIdentity.service("a"),
            InvocationScope.platform(), AccessDecision.none(), new TraceContext(TRACEPARENT, null), Instant.now(),
            CancellationSignal.none()));
    }

    @Test
    void capabilityOnlyHandsOutTypedClients() {
        // Root §4.7 and BRX-RS-MESSAGE-009: no string target or query name on the plugin surface.
        List<Method> methods = Arrays.stream(QueryCapability.class.getDeclaredMethods())
            .filter(method -> !method.isSynthetic())
            .toList();
        assertEquals(1, methods.size());
        assertEquals("client", methods.get(0).getName());
        assertEquals(List.of(QueryType.class), List.of(methods.get(0).getParameterTypes()));
    }

    @Test
    void queryTypeNamesItsContract() {
        QueryType<Request, Result> always = QueryType.of("sample-shipping.find-shipment", "1.2.0", Request.class,
            Result.class);
        QueryType<Request, Optional<Result>> maybe = QueryType.optional("sample-shipping.find-shipment", "1.2.0",
            Request.class, Result.class);

        assertEquals(1, always.major());
        assertFalse(always.optionalResult());
        assertTrue(maybe.optionalResult());
        assertEquals(Result.class, maybe.resultType());
        assertNotEquals(always, maybe);
        assertEquals(maybe, QueryType.optional("sample-shipping.find-shipment", "1.2.0", Request.class, Result.class));
    }

    @Test
    void queryTypeRefusesMalformedNamesAndVersions() {
        assertThrows(IllegalArgumentException.class, () -> QueryType.of("FindShipment", "1.0.0", Request.class,
            Result.class));
        assertThrows(IllegalArgumentException.class, () -> QueryType.of("sample.find", "1.0", Request.class,
            Result.class));
        assertThrows(IllegalArgumentException.class, () -> QueryType.of("sample.find", "1.0.0", Request.class,
            Optional.class));
    }

    @Test
    void providerMissingHasTheRootWireCode() {
        // Root BRX-RS-ERROR-012: the unique symbol PROVIDER_MISSING, wire code query.provider_missing.
        assertEquals("query.provider_missing", BrixQueryException.Code.PROVIDER_MISSING.wireCode());
        for (BrixQueryException.Code code : BrixQueryException.Code.values()) {
            assertTrue(code.wireCode().startsWith("query."), code.wireCode());
        }
    }

    @Test
    void invalidRequestTakesOnlyAFixedReasonToken() {
        BrixQueryException invalid = BrixQueryException.invalidRequest("order-id-malformed");

        assertTrue(invalid.is(BrixQueryException.Code.INVALID_REQUEST));
        assertEquals(Map.of("reason", "order-id-malformed"), invalid.safeParameters());
        assertThrows(IllegalArgumentException.class, () -> BrixQueryException.invalidRequest("Order 42 is bad"));
    }

    @Test
    void scopeAndCallerComeOnlyInValidShapes() {
        assertTrue(InvocationScope.tenant("100").isTenant());
        assertFalse(InvocationScope.platform().isTenant());
        assertThrows(IllegalArgumentException.class, () -> InvocationScope.tenant(" "));
        assertFalse(CallerIdentity.service("sample-orders").hasPrincipal());
        assertThrows(IllegalArgumentException.class,
            () -> new CallerIdentity("sample-orders", Optional.empty(), Optional.of("actor")));
    }
}
