/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.outbox;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.Access;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;

/**
 * Lists the parked messages of every owner outbox and replays one by its event id (P4 design §3).
 *
 * <p>{@code GET /actuator/outbox} lists them without payloads; {@code POST /actuator/outbox/{eventId}}
 * makes one due again with a fresh attempt budget. The endpoint is served on the management port,
 * which the host does not publish. Reading is allowed by default; a host allows replays with
 * {@code management.endpoint.outbox.access=unrestricted}. A replay keeps the event id, so a subscriber
 * that had already committed the event skips it through its inbox.</p>
 */
@WebEndpoint(id = "outbox", defaultAccess = Access.READ_ONLY)
public final class OutboxEndpoint {

    /** Parked messages listed per outbox. */
    static final int LIST_LIMIT = 100;

    private static final Logger log = LoggerFactory.getLogger(OutboxEndpoint.class);

    private final Supplier<OutboxStoreRegistry> registry;
    private final Clock clock;

    /**
     * Creates the endpoint.
     *
     * @param registry supplies the owner outboxes; may supply {@code null}
     * @param clock replay clock
     */
    public OutboxEndpoint(Supplier<OutboxStoreRegistry> registry, Clock clock) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
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
     * @return the outbox that held it, or 404 when no outbox holds it parked
     */
    @WriteOperation
    public WebEndpointResponse<Replay> replay(@Selector String eventId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (Map.Entry<String, OutboxMessageStore> store : stores().entrySet()) {
            if (store.getValue().replayParked(eventId, now)) {
                log.info("event=outbox.replayed outbox={} event_id={}", store.getKey(), eventId);
                return new WebEndpointResponse<>(new Replay(store.getKey(), eventId, true), WebEndpointResponse.STATUS_OK);
            }
        }
        return new WebEndpointResponse<>(new Replay(null, eventId, false), WebEndpointResponse.STATUS_NOT_FOUND);
    }

    private Map<String, OutboxMessageStore> stores() {
        OutboxStoreRegistry stores = registry.get();
        return stores == null ? Map.of() : new TreeMap<>(stores.stores());
    }

    /**
     * Parked messages by outbox table.
     *
     * @param outboxes each outbox's parked messages
     */
    public record ParkedMessages(Map<String, List<ParkedOutboxMessage>> outboxes) {
    }

    /**
     * Result of a replay.
     *
     * @param outbox the outbox that held the message, or {@code null}
     * @param eventId the requested event id
     * @param replayed whether a parked message was made due again
     */
    public record Replay(String outbox, String eventId, boolean replayed) {
    }
}
