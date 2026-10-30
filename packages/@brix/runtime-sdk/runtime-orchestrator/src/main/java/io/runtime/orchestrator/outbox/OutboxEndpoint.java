/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.outbox;

import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.Access;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;

import io.runtime.orchestrator.outbox.MessageOperationAudit.MessageOperation;

/**
 * Lists the parked messages of every owner outbox; replays or discards a parked message and republishes a published
 * one, each by its event id (P4 design §3; Kafka design D33, D34, §14 K14, K16).
 *
 * <ul>
 *   <li>{@code GET /actuator/outbox} lists the parked messages without payloads;</li>
 *   <li>{@code POST /actuator/outbox/{eventId}} with {@code {"operator": ..., "reason": ...}} makes a parked message
 *       due again with a fresh attempt budget;</li>
 *   <li>{@code DELETE /actuator/outbox/{eventId}?operator=...&reason=...} discards a parked message: it is never
 *       sent, and the retention removes it;</li>
 *   <li>{@code POST /actuator/outbox/{eventId}/republish} with {@code {"operator": ..., "reason": ...}} sends a
 *       published message again, while it was published within the outbox retention.</li>
 * </ul>
 *
 * <p>Each action is recorded in the platform audit before it takes effect, with the operator and the reason, and is
 * refused (503) when it cannot be recorded; an action that then fails is recorded again with its error code. Every
 * action keeps the event id, so a subscriber that had already committed the event skips it through its inbox (root
 * BRX-RS-MESSAGE-028). The endpoint is served on the management port, which the host does not publish. Reading is
 * allowed by default; a host allows the actions with {@code management.endpoint.outbox.access=unrestricted}.</p>
 */
@WebEndpoint(id = "outbox", defaultAccess = Access.READ_ONLY)
public final class OutboxEndpoint {

    /** Parked messages listed per outbox. */
    static final int LIST_LIMIT = 100;
    /** The second path segment that asks for a republish. */
    public static final String REPUBLISH = "republish";
    /** No outbox holds the message. */
    static final String NOT_FOUND = "OUTBOX_MESSAGE_NOT_FOUND";
    /** The message is not in the state the action needs. */
    static final String WRONG_STATE = "OUTBOX_MESSAGE_STATE";
    /** The message was published before the outbox retention, so subscribers may no longer recognise it. */
    static final String RETENTION_EXCEEDED = "OUTBOX_RETENTION_EXCEEDED";
    /** The message changed between the audit record and the action. */
    static final String CHANGED = "OUTBOX_MESSAGE_CHANGED";
    /** The platform audit could not record the action. */
    static final String AUDIT_UNAVAILABLE = "MESSAGE_AUDIT_UNAVAILABLE";
    /** The operator or the reason is missing, or the path asks for no known action. */
    static final String BAD_REQUEST = "MESSAGE_OPERATION_INVALID";

    static final int STATUS_BAD_REQUEST = 400;
    static final int STATUS_CONFLICT = 409;
    static final int STATUS_UNAVAILABLE = 503;

    private static final String PARKED = "PARKED";
    private static final String PUBLISHED = "PUBLISHED";
    private static final Logger log = LoggerFactory.getLogger(OutboxEndpoint.class);

    private final Supplier<OutboxStoreRegistry> registry;
    private final Supplier<MessageOperationAudit> audit;
    private final Duration retention;
    private final Clock clock;

    /**
     * Creates the endpoint.
     *
     * @param registry supplies the owner outboxes; may supply {@code null}
     * @param audit supplies the platform audit of these actions; may supply {@code null}, which refuses them
     * @param retention how long the outboxes keep published messages
     * @param clock action clock
     */
    public OutboxEndpoint(
            Supplier<OutboxStoreRegistry> registry,
            Supplier<MessageOperationAudit> audit,
            Duration retention,
            Clock clock) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.audit = Objects.requireNonNull(audit, "audit must not be null");
        this.retention = Objects.requireNonNull(retention, "retention must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Lists the parked messages of each outbox, the earliest parked first.
     *
     * @return parked messages by outbox table
     */
    @ReadOperation
    public ParkedMessages parked() {
        Map<String, List<ParkedOutboxMessage>> parked = new TreeMap<>();
        stores().forEach((outbox, store) -> parked.put(outbox, store.listParked(LIST_LIMIT)));
        return new ParkedMessages(parked);
    }

    /**
     * Makes the parked message with this event id due again.
     *
     * @param eventId event id of the parked message
     * @param operator who asks
     * @param reason why
     * @param security who the management port authenticated
     * @return the outcome; 404 when no outbox holds the message, 409 when it is not parked
     */
    @WriteOperation
    public WebEndpointResponse<Outcome> replay(
            @Selector String eventId, String operator, String reason, SecurityContext security) {
        return act(MessageOperationAudit.REPLAYED, eventId, operator, reason, security,
            (store, now) -> store.replayParked(eventId, now));
    }

    /**
     * Discards the parked message with this event id: it is never sent.
     *
     * @param eventId event id of the parked message
     * @param operator who asks
     * @param reason why
     * @param security who the management port authenticated
     * @return the outcome; 404 when no outbox holds the message, 409 when it is not parked
     */
    @DeleteOperation
    public WebEndpointResponse<Outcome> discard(
            @Selector String eventId, String operator, String reason, SecurityContext security) {
        return act(MessageOperationAudit.DISCARDED, eventId, operator, reason, security,
            (store, now) -> store.discardParked(eventId, now));
    }

    /**
     * Sends the published message with this event id again.
     *
     * @param eventId event id of the published message
     * @param action {@value #REPUBLISH}
     * @param operator who asks
     * @param reason why
     * @param security who the management port authenticated
     * @return the outcome; 404 when no outbox holds the message, 409 when it is not published or was published
     *         before the outbox retention
     */
    @WriteOperation
    public WebEndpointResponse<Outcome> republish(
            @Selector String eventId, @Selector String action, String operator, String reason, SecurityContext security) {
        if (!REPUBLISH.equals(action)) {
            return respond(new Outcome(null, eventId, false, BAD_REQUEST), STATUS_BAD_REQUEST);
        }
        return act(MessageOperationAudit.REPUBLISHED, eventId, operator, reason, security,
            (store, now) -> store.republish(eventId, now, now.minus(retention)));
    }

    private WebEndpointResponse<Outcome> act(
            String action,
            String eventId,
            String operator,
            String reason,
            SecurityContext security,
            BiPredicate<OutboxMessageStore, OffsetDateTime> perform) {
        if (operator == null || operator.isBlank() || reason == null || reason.isBlank()) {
            return respond(new Outcome(null, eventId, false, BAD_REQUEST), STATUS_BAD_REQUEST);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        Optional<Located> found = locate(eventId);
        if (found.isEmpty()) {
            return respond(new Outcome(null, eventId, false, NOT_FOUND), WebEndpointResponse.STATUS_NOT_FOUND);
        }
        String outbox = found.get().outbox();
        OutboxMessageState stored = found.get().stored();
        boolean republish = MessageOperationAudit.REPUBLISHED.equals(action);
        if (!(republish ? PUBLISHED : PARKED).equals(stored.status())) {
            return respond(new Outcome(outbox, eventId, false, WRONG_STATE), STATUS_CONFLICT);
        }
        if (republish && (stored.publishedAt() == null || stored.publishedAt().isBefore(now.minus(retention)))) {
            return respond(new Outcome(outbox, eventId, false, RETENTION_EXCEEDED), STATUS_CONFLICT);
        }
        MessageOperation operation;
        try {
            operation = MessageOperation.of(action, eventId, outbox, operator, principal(security), reason);
        } catch (IllegalArgumentException invalid) {
            return respond(new Outcome(outbox, eventId, false, BAD_REQUEST), STATUS_BAD_REQUEST);
        }
        MessageOperationAudit recorder = Objects.requireNonNullElse(audit.get(), MessageOperationAudit.NONE);
        try {
            recorder.record(operation);
        } catch (RuntimeException failure) {
            log.warn("event=outbox.operation_refused action={} outbox={} event_id={} code={}", action, outbox, eventId,
                AUDIT_UNAVAILABLE, failure);
            return respond(new Outcome(outbox, eventId, false, AUDIT_UNAVAILABLE), STATUS_UNAVAILABLE);
        }
        if (!perform.test(found.get().store(), now)) {
            recordFailure(recorder, operation.failed(CHANGED));
            return respond(new Outcome(outbox, eventId, false, CHANGED), STATUS_CONFLICT);
        }
        log.info("event=outbox.{} outbox={} event_id={} operator={}", verb(action), outbox, eventId, operation.operator());
        return respond(new Outcome(outbox, eventId, true, null), WebEndpointResponse.STATUS_OK);
    }

    private Optional<Located> locate(String eventId) {
        for (Map.Entry<String, OutboxMessageStore> store : stores().entrySet()) {
            Optional<OutboxMessageState> stored = store.getValue().find(eventId);
            if (stored.isPresent()) {
                return Optional.of(new Located(store.getKey(), store.getValue(), stored.get()));
            }
        }
        return Optional.empty();
    }

    private Map<String, OutboxMessageStore> stores() {
        OutboxStoreRegistry stores = registry.get();
        return stores == null ? Map.of() : new TreeMap<>(stores.stores());
    }

    /** Records that an action failed after its request was recorded; the action's own outcome stands either way. */
    private static void recordFailure(MessageOperationAudit recorder, MessageOperation failure) {
        try {
            recorder.record(failure);
        } catch (RuntimeException unrecorded) {
            log.warn("event=message.operation_failure_unaudited action={} message_id={} place={} code={}",
                failure.action(), failure.messageId(), failure.place(), failure.errorCode(), unrecorded);
        }
    }

    /** The name the management port authenticated, if any. */
    private static String principal(SecurityContext security) {
        Principal principal = security == null ? null : security.getPrincipal();
        return principal == null ? null : principal.getName();
    }

    private static String verb(String action) {
        return switch (action) {
            case MessageOperationAudit.REPLAYED -> "replayed";
            case MessageOperationAudit.DISCARDED -> "discarded";
            default -> "republished";
        };
    }

    private static WebEndpointResponse<Outcome> respond(Outcome outcome, int status) {
        return new WebEndpointResponse<>(outcome, status);
    }

    private record Located(String outbox, OutboxMessageStore store, OutboxMessageState stored) {
    }

    /**
     * Parked messages by outbox table.
     *
     * @param outboxes each outbox's parked messages
     */
    public record ParkedMessages(Map<String, List<ParkedOutboxMessage>> outboxes) {
    }

    /**
     * Outcome of an action.
     *
     * @param outbox the outbox that holds the message, or {@code null}
     * @param eventId the requested event id
     * @param done whether the action took effect
     * @param errorCode why it did not, or {@code null}
     */
    public record Outcome(String outbox, String eventId, boolean done, String errorCode) {
    }
}
