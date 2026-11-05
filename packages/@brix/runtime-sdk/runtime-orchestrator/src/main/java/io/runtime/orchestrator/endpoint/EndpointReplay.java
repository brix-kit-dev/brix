/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.idempotency.IdempotencyScopes;
import io.runtime.orchestrator.idempotency.RequestFingerprint;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointResponse;

/**
 * The idempotency step of the Dispatcher, for endpoints that declare {@code idempotency.mode: required} (root
 * BRX-RS-MANIFEST-018, MANIFEST-022; standard-link plan point 10a; idempotency design §5.4).
 *
 * <p>The caller names a key in the {@code Idempotency-Key} header. It is unique for the endpoint, the tenant and the
 * caller. Inside the owner's unit of work, before the handler, the call takes the key:</p>
 * <ul>
 *   <li>the first call goes on to the handler, and its answer is kept with its business write. A call that fails rolls
 *   both back, so nothing is kept and it can be tried again;</li>
 *   <li>a later call with the same content gets the kept answer again, with the {@code Idempotent-Replayed} header,
 *   and the handler does not run;</li>
 *   <li>a later call with other content is answered 422, and one while the first has not finished 409 at once.</li>
 * </ul>
 */
final class EndpointReplay {

    /** The request header that names a call's key. */
    static final String KEY_HEADER = "idempotency-key";
    /** The response header of an answer given again. */
    static final String REPLAYED_HEADER = "Idempotent-Replayed";
    /** The outcome of an answer given again, among those of {@link EndpointMetrics#finished}. */
    static final String REPLAYED = "replayed";
    /** The call names no key, or a key that is not 1 to 128 visible ASCII characters. */
    static final String KEY_REQUIRED = "endpoint.idempotency_key_required";
    /** Another call with the key has not finished. */
    static final String IN_PROGRESS = "endpoint.idempotency_in_progress";
    /** An earlier call holds the key with other content. */
    static final String CONFLICT = "endpoint.idempotency_conflict";
    /** The largest answer the runtime keeps (design D76). */
    static final int MAX_BODY_BYTES = 64 * 1024;

    private static final Logger log = LoggerFactory.getLogger(EndpointReplay.class);
    private static final Pattern KEY = Pattern.compile("[\\x21-\\x7E]{1,128}");

    private final Supplier<OwnerEndpointIdempotency> records;
    private final ObjectMapper json;

    EndpointReplay(Supplier<OwnerEndpointIdempotency> records, ObjectMapper json) {
        this.records = Objects.requireNonNull(records, "records must not be null");
        this.json = Objects.requireNonNull(json, "json must not be null");
    }

    /**
     * A call that named a usable key.
     *
     * @param route its route
     * @param key what it is deduplicated by
     * @param fingerprint the fingerprint of its request
     * @param tenantId its tenant, or {@code null}
     * @param traceId its trace
     */
    record Call(EndpointRoute route, OwnerEndpointIdempotency.Key key, String fingerprint, Long tenantId,
            String traceId) {
    }

    /**
     * An answer given again.
     *
     * @param status the status of the first answer
     * @param body its body, or {@code null}
     */
    record Replayed(int status, Object body) {
    }

    /**
     * Refuses a route whose results cannot be kept here: a host without the owners' table has no replay.
     *
     * @param route a route that replays its results
     * @throws IllegalStateException when the host has no store
     */
    void requireStore(EndpointRoute route) {
        if (records.get() == null) {
            throw new IllegalStateException("Endpoint '" + route.endpointId() + "' replays its results, and this host "
                + "has no " + OwnerEndpointIdempotency.class.getSimpleName());
        }
    }

    /**
     * Admits a call, before its unit of work begins.
     *
     * @param route its route
     * @param key the value of its {@code Idempotency-Key} header
     * @param request the request, as the handler gets it
     * @param tenantId its verified tenant
     * @param actorId its verified caller
     * @param traceId its trace
     * @return the call
     * @throws EndpointHandlingException 400 when the call names no usable key
     */
    Call admit(EndpointRoute route, Optional<String> key, JsonNode request, Optional<String> tenantId,
            Optional<String> actorId, String traceId) {
        String value = key.filter(candidate -> KEY.matcher(candidate).matches())
            .orElseThrow(() -> new EndpointHandlingException(400, KEY_REQUIRED, KEY_REQUIRED));
        String caller = actorId.orElseThrow(() -> new IllegalStateException(
            "Endpoint '" + route.endpointId() + "' replays its results and has no verified caller"));
        String scope = IdempotencyScopes.bounded(tenantId.orElse("platform") + "|" + caller);
        String entry = route.pluginId() + "/" + route.contract().replay().handlerId();
        return new Call(route, new OwnerEndpointIdempotency.Key(entry, scope, value), RequestFingerprint.of(request),
            tenantId.map(EndpointReplay::number).orElse(null), traceId);
    }

    /**
     * Runs a call in the owner's unit of work.
     *
     * @param call the admitted call
     * @param handler runs the handler and checks its response
     * @return the handler's response, or the {@link Replayed} answer of the call that holds the key
     */
    Object run(Call call, Supplier<Object> handler) {
        OwnerEndpointIdempotency store = records.get();
        String ownerId = call.route().pluginId();
        OwnerEndpointIdempotency.Reservation reservation = store.reserve(ownerId, call.key(), call.fingerprint(),
            call.tenantId(), call.traceId(), call.route().contract().replay().retention());
        return switch (reservation.state()) {
            case IN_PROGRESS -> throw refused(call, 409, IN_PROGRESS);
            case HELD -> replay(call, reservation.held());
            case TAKEN -> {
                Object answer = handler.get();
                keep(call, store, (EndpointResponse) answer);
                yield answer;
            }
        };
    }

    /**
     * Runs a call whose record goes with its write through an internal contract (idempotency design §5.6, D81). Its
     * handler runs in no unit of work: the write takes the key in the owner's, and the answer is kept afterwards.
     *
     * @param call the admitted call
     * @param boundary gives the runtime short transactions of the owner
     * @param deadline the call's deadline
     * @param handler runs the handler and checks its response
     * @return the handler's response, or the {@link Replayed} answer
     */
    Object throughContract(Call call, EndpointInvocationBoundary boundary, Instant deadline, Supplier<Object> handler) {
        OwnerEndpointIdempotency store = records.get();
        String ownerId = call.route().contract().replay().writeOwner();
        Optional<OwnerEndpointIdempotency.Held> held = boundary.inOwner(call.route(), ownerId, deadline,
            () -> store.find(ownerId, call.key()));
        // Root BRX-RS-MANIFEST-022: a conflict and a replay come before the handler. A call that stopped after the owner
        // committed has no answer yet, so its handler runs again, and the contract gives its result again.
        if (held.isPresent() && (held.get().answered() || !held.get().fingerprint().equals(call.fingerprint()))) {
            return replay(call, held.get());
        }
        ContractWrites.Write write = new ContractWrites.Write(call);
        EndpointResponse answer = (EndpointResponse) ContractWrites.within(write, handler);
        if (!write.reached()) {
            log.error("event=endpoint.idempotency_key_unused plugin={} endpoint={} trace_id={}",
                call.route().pluginId(), call.route().endpointId(), call.traceId());
            return answer;
        }
        keepAnswer(call, boundary, deadline, store, answer);
        if (!write.repeated()) {
            return answer;
        }
        log.info("event=endpoint.idempotency_resumed plugin={} endpoint={} trace_id={}", call.route().pluginId(),
            call.route().endpointId(), call.traceId());
        return new Replayed(answer.status(), answer.body());
    }

    /**
     * Design D82: the owner has committed, so the answer goes back whatever happens here. Without a kept answer, the next
     * call with the key takes the way of a call that stopped.
     */
    private void keepAnswer(Call call, EndpointInvocationBoundary boundary, Instant deadline,
            OwnerEndpointIdempotency store, EndpointResponse answer) {
        String ownerId = call.route().contract().replay().writeOwner();
        String body = answer.body() == null ? null : json.writeValueAsString(answer.body());
        if (tooLarge(body)) {
            log.error("event=endpoint.idempotency_answer_too_large plugin={} endpoint={} limit={}",
                call.route().pluginId(), call.route().endpointId(), MAX_BODY_BYTES);
            return;
        }
        try {
            boundary.inOwner(call.route(), ownerId, deadline, () -> {
                store.complete(ownerId, call.key(), answer.status(), body);
                return Boolean.TRUE;
            });
        } catch (RuntimeException notKept) {
            log.warn("event=endpoint.idempotency_answer_not_kept plugin={} endpoint={} trace_id={}",
                call.route().pluginId(), call.route().endpointId(), call.traceId(), notKept);
        }
    }

    private static boolean tooLarge(String body) {
        return body != null && body.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES;
    }

    private Replayed replay(Call call, OwnerEndpointIdempotency.Held held) {
        if (!held.fingerprint().equals(call.fingerprint())) {
            throw refused(call, 422, CONFLICT);
        }
        log.info("event=endpoint.idempotency_replayed plugin={} endpoint={} trace_id={} first_trace_id={}",
            call.route().pluginId(), call.route().endpointId(), call.traceId(), held.traceId());
        return new Replayed(held.status(), held.body() == null ? null : json.readTree(held.body()));
    }

    private void keep(Call call, OwnerEndpointIdempotency store, EndpointResponse answer) {
        String body = answer.body() == null ? null : json.writeValueAsString(answer.body());
        if (tooLarge(body)) {
            // Design D76: a defect of the endpoint. Its business write rolls back, so nothing happened without an answer
            // to replay.
            log.error("event=endpoint.idempotency_answer_too_large plugin={} endpoint={} limit={}",
                call.route().pluginId(), call.route().endpointId(), MAX_BODY_BYTES);
            throw new IllegalStateException("Endpoint '" + call.route().endpointId() + "' answered more than the "
                + MAX_BODY_BYTES + " bytes the runtime keeps for replay");
        }
        store.complete(call.route().pluginId(), call.key(), answer.status(), body);
    }

    static EndpointHandlingException refused(Call call, int status, String code) {
        log.warn("event=endpoint.idempotency_refused plugin={} endpoint={} code={} trace_id={}",
            call.route().pluginId(), call.route().endpointId(), code, call.traceId());
        return new EndpointHandlingException(status, code, code);
    }

    private static Long number(String tenantId) {
        try {
            return Long.valueOf(tenantId);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
