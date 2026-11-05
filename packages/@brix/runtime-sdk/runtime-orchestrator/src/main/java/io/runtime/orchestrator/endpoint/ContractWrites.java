/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.plugin.EndpointHandlingException;
import tools.jackson.databind.ObjectMapper;

/**
 * The one write of an operational endpoint's call that replays its results (root BRX-RS-HOST-020; idempotency design
 * §5.6, D80, D81, D84).
 *
 * <p>The handler of such an endpoint runs in no transaction, so the record goes with its write. The L2B proxy of the
 * write contract comes here in the owner's unit of work: the first call takes the key and keeps the contract's result
 * with the owner's writes, and a later one gets that result while the owner writes nothing. A call that fails rolls
 * everything back, so it leaves no key.</p>
 *
 * <p>The call reaches the proxy on the handler's thread, as the owner's unit of work and tenant scope do.</p>
 */
public final class ContractWrites {

    private static final Logger log = LoggerFactory.getLogger(ContractWrites.class);
    private static final ThreadLocal<Write> CURRENT = new ThreadLocal<>();

    private final Supplier<OwnerEndpointIdempotency> records;
    private final ObjectMapper json;

    /**
     * Creates the writes.
     *
     * @param records the owners' records
     * @param json writes and reads the contracts' results
     */
    public ContractWrites(Supplier<OwnerEndpointIdempotency> records, ObjectMapper json) {
        this.records = Objects.requireNonNull(records, "records must not be null");
        this.json = Objects.requireNonNull(json, "json must not be null");
    }

    /** The write of one call, on its way to the owner. */
    static final class Write {

        private final EndpointReplay.Call call;
        private boolean reached;
        private boolean repeated;

        Write(EndpointReplay.Call call) {
            this.call = Objects.requireNonNull(call, "call must not be null");
        }

        /** Whether the handler reached the owner's write. */
        boolean reached() {
            return reached;
        }

        /** Whether an earlier call had made the write, so the owner did not. */
        boolean repeated() {
            return repeated;
        }
    }

    /**
     * Runs a handler with the write of its call at hand.
     *
     * @param write the write
     * @param handler the handler
     * @param <T> result type
     * @return the handler's result
     */
    static <T> T within(Write write, Supplier<T> handler) {
        CURRENT.set(write);
        try {
            return handler.get();
        } finally {
            CURRENT.remove();
        }
    }

    /**
     * Runs a replayable write of an owner, in the owner's unit of work.
     *
     * @param ownerId the owner
     * @param method the contract's method
     * @param target calls the owner
     * @return the write's result, kept or given again; without a call that replays its results, what the owner returned
     * @throws EndpointHandlingException 409 while another call with the key runs, 422 when it holds other content
     */
    public Object write(String ownerId, Method method, Supplier<Object> target) {
        Write write = CURRENT.get();
        if (write == null || !ownerId.equals(write.call.route().contract().replay().writeOwner())) {
            return target.get();
        }
        EndpointReplay.Call call = write.call;
        if (write.reached) {
            // Idempotency design D84: the record goes with one write, and a second one would have none.
            throw new IllegalStateException("Endpoint '" + call.route().endpointId() + "' wrote twice through its write "
                + "contract; a call that replays its results has one write");
        }
        write.reached = true;
        OwnerEndpointIdempotency store = records.get();
        OwnerEndpointIdempotency.Reservation reservation = store.reserve(ownerId, call.key(), call.fingerprint(),
            call.tenantId(), call.traceId(), call.route().contract().replay().retention());
        return switch (reservation.state()) {
            case IN_PROGRESS -> throw EndpointReplay.refused(call, 409, EndpointReplay.IN_PROGRESS);
            case HELD -> {
                OwnerEndpointIdempotency.Held held = reservation.held();
                if (!held.fingerprint().equals(call.fingerprint())) {
                    throw EndpointReplay.refused(call, 422, EndpointReplay.CONFLICT);
                }
                write.repeated = true;
                yield read(held.writeResult(), method);
            }
            case TAKEN -> {
                Object result = target.get();
                store.keepWriteResult(ownerId, call.key(), text(result, call));
                yield result;
            }
        };
    }

    private Object read(String result, Method method) {
        if (result == null) {
            return null;
        }
        return json.readerFor(json.constructType(method.getGenericReturnType())).readValue(result);
    }

    private String text(Object result, EndpointReplay.Call call) {
        if (result == null) {
            return null;
        }
        String text = json.writeValueAsString(result);
        if (text.getBytes(StandardCharsets.UTF_8).length > EndpointReplay.MAX_BODY_BYTES) {
            // Design D76, D82: a defect of the contract. The owner's writes roll back, so nothing happened without a
            // result to give again.
            log.error("event=endpoint.idempotency_answer_too_large plugin={} endpoint={} limit={}",
                call.route().pluginId(), call.route().endpointId(), EndpointReplay.MAX_BODY_BYTES);
            throw new IllegalStateException("Endpoint '" + call.route().endpointId() + "' wrote a result of more than the "
                + EndpointReplay.MAX_BODY_BYTES + " bytes the runtime keeps for replay");
        }
        return text;
    }
}
