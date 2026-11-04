/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
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

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.MessageOperationAudit;
import io.runtime.orchestrator.outbox.MessageOperationAudit.MessageOperation;

/**
 * Lists the events each subscription parked; replays them one by one or a subscription's at once, and discards them
 * (Kafka design §4.2, D20, D33, §14 K14).
 *
 * <ul>
 *   <li>{@code GET /actuator/events} lists them by subscription, without payloads;</li>
 *   <li>{@code POST /actuator/events/{subscription}/{eventId}} with {@code {"operator": ..., "reason": ...}} delivers
 *       one again through the delivery core and releases it once its subscriber committed;</li>
 *   <li>{@code POST /actuator/events/{subscription}} with the same body does that for the subscription's parked events,
 *       the earliest parked first, up to {@value #LIST_LIMIT}, and stops at the first that fails again;</li>
 *   <li>{@code DELETE /actuator/events/{subscription}/{eventId}?operator=...&reason=...} releases one without delivering
 *       it, also when it cannot be delivered at all.</li>
 * </ul>
 *
 * <p>Each event's replay or discard is recorded in the platform audit before it takes effect, with the operator, the
 * reason and the subscription, and is refused (503) when it cannot be recorded; a replay that then fails is recorded
 * again with its error code. The event id stays the same, so a subscriber that had already committed the event skips it
 * through its inbox (root BRX-RS-MESSAGE-028). The endpoint answers 404 when the subscription does not exist or has not
 * parked the event, and 409 when the subscriber could not take it again. A discarded command is first recorded for
 * its sender, who then reads it as discarded (standard-link plan point 7); when that fails the answer is 409 and the
 * command stays parked. It is served on the management port, which the host does not publish; a host allows the
 * actions with {@code management.endpoint.events.access=unrestricted}.</p>
 */
@WebEndpoint(id = "events", defaultAccess = Access.READ_ONLY)
public final class EventsEndpoint {

    /** Parked events listed, or replayed at once, per subscription. */
    static final int LIST_LIMIT = 100;
    /** A subscription could not take the event again. */
    static final int STATUS_CONFLICT = 409;
    /** The platform audit could not record the action. */
    static final int STATUS_UNAVAILABLE = 503;
    /** The subscription does not exist, or has not parked the event. */
    static final String NOT_FOUND = "EVENT_NOT_PARKED";
    /** The platform audit could not record the action. */
    static final String AUDIT_UNAVAILABLE = "MESSAGE_AUDIT_UNAVAILABLE";
    /** The operator or the reason is missing. */
    static final String BAD_REQUEST = "MESSAGE_OPERATION_INVALID";
    /** The operator or the reason is missing. */
    static final int STATUS_BAD_REQUEST = 400;
    /** The provider could not record a discarded command for its sender (standard-link plan point 7). */
    static final String DISCARD_UNRECORDED = "COMMAND_DISCARD_UNRECORDED";

    private static final Logger log = LoggerFactory.getLogger(EventsEndpoint.class);

    private final Supplier<EventSubscriptionTransport> brokers;
    private final Supplier<EventDelivery> deliveries;
    private final Supplier<MessageOperationAudit> audit;

    /**
     * Creates the endpoint.
     *
     * @param brokers supplies the adapter that consumes from the broker; may supply {@code null}
     * @param deliveries supplies the delivery core; may supply {@code null}
     * @param audit supplies the platform audit of these actions; may supply {@code null}, which refuses them
     */
    public EventsEndpoint(
            Supplier<EventSubscriptionTransport> brokers,
            Supplier<EventDelivery> deliveries,
            Supplier<MessageOperationAudit> audit) {
        this.brokers = Objects.requireNonNull(brokers, "brokers must not be null");
        this.deliveries = Objects.requireNonNull(deliveries, "deliveries must not be null");
        this.audit = Objects.requireNonNull(audit, "audit must not be null");
    }

    /**
     * Lists the parked events of each subscription, the earliest parked first.
     *
     * @return parked events by subscription
     */
    @ReadOperation
    public ParkedEvents parked() {
        EventSubscriptionTransport broker = brokers.get();
        return new ParkedEvents(broker == null ? Map.of() : new TreeMap<>(broker.parked(LIST_LIMIT)));
    }

    /**
     * Delivers the subscription's parked events again, the earliest parked first, and stops at the first that fails.
     *
     * @param subscription the subscription's name, as listed
     * @param operator who asks
     * @param reason why
     * @param security who the management port authenticated
     * @return each event's outcome; 404 when the subscription does not exist, 409 when an event failed again
     */
    @WriteOperation
    public WebEndpointResponse<Batch> replayAll(
            @Selector String subscription, String operator, String reason, SecurityContext security) {
        if (blank(operator) || blank(reason)) {
            return new WebEndpointResponse<>(new Batch(subscription, List.of()), STATUS_BAD_REQUEST);
        }
        Optional<Target> target = target(subscription);
        if (target.isEmpty()) {
            return new WebEndpointResponse<>(new Batch(subscription, List.of()), WebEndpointResponse.STATUS_NOT_FOUND);
        }
        List<ParkedEvent> parked = target.get().broker().parked(LIST_LIMIT)
            .getOrDefault(target.get().subscription().name(), List.of());
        List<Outcome> outcomes = new ArrayList<>();
        int status = WebEndpointResponse.STATUS_OK;
        for (ParkedEvent event : parked) {
            WebEndpointResponse<Outcome> replayed = replay(target.get(), event.eventId(), operator, reason, security);
            outcomes.add(replayed.getBody());
            if (replayed.getStatus() != WebEndpointResponse.STATUS_OK) {
                status = replayed.getStatus() == WebEndpointResponse.STATUS_NOT_FOUND ? STATUS_CONFLICT
                    : replayed.getStatus();
                break;
            }
        }
        return new WebEndpointResponse<>(new Batch(subscription, List.copyOf(outcomes)), status);
    }

    /**
     * Delivers one parked event of the subscription again.
     *
     * @param subscription the subscription's name, as listed
     * @param eventId the event id, as listed
     * @param operator who asks
     * @param reason why
     * @param security who the management port authenticated
     * @return the outcome; 404 when the subscription has not parked the event, 409 when it could not take it again
     */
    @WriteOperation
    public WebEndpointResponse<Outcome> replay(
            @Selector String subscription,
            @Selector String eventId,
            String operator,
            String reason,
            SecurityContext security) {
        if (blank(operator) || blank(reason)) {
            return invalid(subscription, eventId);
        }
        Optional<Target> target = target(subscription);
        if (target.isEmpty()) {
            return notFound(subscription, eventId);
        }
        return replay(target.get(), eventId, operator, reason, security);
    }

    /**
     * Releases one parked event of the subscription without delivering it.
     *
     * @param subscription the subscription's name, as listed
     * @param eventId the event id, as listed
     * @param operator who asks
     * @param reason why
     * @param security who the management port authenticated
     * @return the outcome; 404 when the subscription has not parked the event
     */
    @DeleteOperation
    public WebEndpointResponse<Outcome> discard(
            @Selector String subscription,
            @Selector String eventId,
            String operator,
            String reason,
            SecurityContext security) {
        if (blank(operator) || blank(reason)) {
            return invalid(subscription, eventId);
        }
        Optional<Target> target = target(subscription);
        Optional<ParkedEvent> parked = target.flatMap(found ->
            found.broker().parkedEvent(found.subscription(), eventId));
        if (parked.isEmpty()) {
            return notFound(subscription, eventId);
        }
        Optional<MessageOperation> operation = recorded(MessageOperationAudit.DISCARDED, subscription, eventId,
            operator, reason, security);
        if (operation.isEmpty()) {
            return refused(subscription, eventId);
        }
        if (target.get().subscription().command() && !discardedCommand(target.get(), eventId, parked.get())) {
            return failed(operation.get(), DISCARD_UNRECORDED);
        }
        target.get().broker().release(target.get().subscription(), eventId);
        log.info("event=event.discarded subscription={} event_id={} operator={}", subscription, eventId,
            operation.get().operator());
        return new WebEndpointResponse<>(new Outcome(subscription, eventId, true, null), WebEndpointResponse.STATUS_OK);
    }

    /**
     * Records a discarded command for its sender before the broker lets it go (standard-link plan point 7); false when
     * the provider could not record it, and the command stays parked.
     */
    private boolean discardedCommand(Target target, String commandId, ParkedEvent parked) {
        Optional<CanonicalOutboxMessage> command = target.broker().parkedMessage(target.subscription(), commandId);
        if (command.isEmpty()) {
            return true;
        }
        try {
            target.delivery().discardedCommand(target.subscription().pluginId(), target.subscription().handlerId(),
                command.get(), parked.errorCode());
            return true;
        } catch (RuntimeException failure) {
            log.warn("event=event.discard_unrecorded subscription={} event_id={} code={}", target.subscription().name(),
                commandId, DISCARD_UNRECORDED, failure);
            return false;
        }
    }

    /** The event subscriptions and the consumers of the provided commands (Kafka design §14 K22). */
    static List<ConsumedSubscription> consumed(EventDelivery delivery) {
        List<ConsumedSubscription> consumed = new ArrayList<>();
        for (EventSubscribers.DeclaredSubscriber declared : delivery.subscriptions()) {
            consumed.add(new ConsumedSubscription(declared.pluginId(), declared.subscription().handlerId(),
                declared.subscription().eventType()));
        }
        consumed.addAll(delivery.commandProvisions());
        return consumed;
    }

    private WebEndpointResponse<Outcome> replay(
            Target target, String eventId, String operator, String reason, SecurityContext security) {
        String name = target.subscription().name();
        if (target.broker().parkedEvent(target.subscription(), eventId).isEmpty()) {
            return notFound(name, eventId);
        }
        Optional<MessageOperation> operation = recorded(MessageOperationAudit.REPLAYED, name, eventId, operator,
            reason, security);
        if (operation.isEmpty()) {
            return refused(name, eventId);
        }
        Optional<CanonicalOutboxMessage> message = target.broker().parkedMessage(target.subscription(), eventId);
        if (message.isEmpty()) {
            return failed(operation.get(), SubscriptionDelivery.ENVELOPE_INVALID);
        }
        try {
            target.delivery().deliverTo(target.subscription().pluginId(), target.subscription().handlerId(),
                message.get());
        } catch (EventDeliveryException failure) {
            log.warn("event=event.replay_failed subscription={} event_id={} code={}", name, eventId,
                failure.errorCode(), failure);
            return failed(operation.get(), failure.errorCode());
        }
        target.broker().release(target.subscription(), eventId);
        log.info("event=event.replayed subscription={} type={} event_id={} operator={}", name,
            message.get().messageType(), eventId, operation.get().operator());
        return new WebEndpointResponse<>(new Outcome(name, eventId, true, null), WebEndpointResponse.STATUS_OK);
    }

    /** Records the action before it takes effect; empty when it cannot be recorded, and the action must not happen. */
    private Optional<MessageOperation> recorded(
            String action, String subscription, String eventId, String operator, String reason, SecurityContext security) {
        MessageOperation operation = MessageOperation.of(action, eventId, subscription, operator, principal(security),
            reason);
        try {
            Objects.requireNonNullElse(audit.get(), MessageOperationAudit.NONE).record(operation);
            return Optional.of(operation);
        } catch (RuntimeException failure) {
            log.warn("event=event.operation_refused action={} subscription={} event_id={} code={}", action,
                subscription, eventId, AUDIT_UNAVAILABLE, failure);
            return Optional.empty();
        }
    }

    private WebEndpointResponse<Outcome> failed(MessageOperation operation, String errorCode) {
        MessageOperationAudit recorder = Objects.requireNonNullElse(audit.get(), MessageOperationAudit.NONE);
        try {
            recorder.record(operation.failed(errorCode));
        } catch (RuntimeException unrecorded) {
            log.warn("event=message.operation_failure_unaudited action={} message_id={} place={} code={}",
                operation.action(), operation.messageId(), operation.place(), errorCode, unrecorded);
        }
        return new WebEndpointResponse<>(new Outcome(operation.place(), operation.messageId(), false, errorCode),
            STATUS_CONFLICT);
    }

    private Optional<Target> target(String subscription) {
        EventSubscriptionTransport broker = brokers.get();
        EventDelivery delivery = deliveries.get();
        if (broker == null || delivery == null) {
            return Optional.empty();
        }
        return consumed(delivery).stream()
            .filter(consumed -> consumed.name().equals(subscription))
            .findFirst()
            .map(consumed -> new Target(broker, delivery, consumed));
    }

    private static String principal(SecurityContext security) {
        java.security.Principal principal = security == null ? null : security.getPrincipal();
        return principal == null ? null : principal.getName();
    }

    private static WebEndpointResponse<Outcome> notFound(String subscription, String eventId) {
        return new WebEndpointResponse<>(new Outcome(subscription, eventId, false, NOT_FOUND),
            WebEndpointResponse.STATUS_NOT_FOUND);
    }

    private static WebEndpointResponse<Outcome> refused(String subscription, String eventId) {
        return new WebEndpointResponse<>(new Outcome(subscription, eventId, false, AUDIT_UNAVAILABLE),
            STATUS_UNAVAILABLE);
    }

    private static WebEndpointResponse<Outcome> invalid(String subscription, String eventId) {
        return new WebEndpointResponse<>(new Outcome(subscription, eventId, false, BAD_REQUEST), STATUS_BAD_REQUEST);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record Target(EventSubscriptionTransport broker, EventDelivery delivery, ConsumedSubscription subscription) {
    }

    /**
     * Parked events by subscription.
     *
     * @param subscriptions each subscription's parked events
     */
    public record ParkedEvents(Map<String, List<ParkedEvent>> subscriptions) {
    }

    /**
     * Outcome of an action on one parked event.
     *
     * @param subscription the subscription's name
     * @param eventId the event id
     * @param done whether the event was replayed and released, or discarded
     * @param errorCode why not, or {@code null}
     */
    public record Outcome(String subscription, String eventId, boolean done, String errorCode) {
    }

    /**
     * Outcome of replaying a subscription's parked events.
     *
     * @param subscription the subscription's name
     * @param events each event's outcome, in the order they were replayed
     */
    public record Batch(String subscription, List<Outcome> events) {
    }
}
