/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

/**
 * A subscription a broker consumes for (Kafka design §4.2): the subscriber plugin, its handler id and the
 * event type it receives; or, for a command (§14 K22), the providing plugin, its handler id and the command type.
 * The runtime takes it from the manifests; plugins never see it.
 *
 * @param pluginId subscriber or provider plugin, the owner of the inbox
 * @param handlerId the subscription's or the provider's handler id
 * @param eventType the event type it receives, or the command type
 * @param kind {@value #EVENT} or {@value #COMMAND}
 */
public record ConsumedSubscription(String pluginId, String handlerId, String eventType, String kind) {

    /** A subscription to an event type. */
    public static final String EVENT = "EVENT";

    /** The consumer of a provided command type. */
    public static final String COMMAND = "COMMAND";

    /**
     * Creates and validates a subscription.
     */
    public ConsumedSubscription {
        requireText(pluginId, "pluginId");
        requireText(handlerId, "handlerId");
        requireText(eventType, "eventType");
        kind = kind == null ? EVENT : kind;
        if (!EVENT.equals(kind) && !COMMAND.equals(kind)) {
            throw new IllegalArgumentException("kind must be EVENT or COMMAND");
        }
    }

    /**
     * Creates an event subscription.
     *
     * @param pluginId subscriber plugin
     * @param handlerId the subscription's handler id
     * @param eventType the event type it receives
     */
    public ConsumedSubscription(String pluginId, String handlerId, String eventType) {
        this(pluginId, handlerId, eventType, EVENT);
    }

    /**
     * Whether this consumes a command type.
     *
     * @return {@code true} for a command
     */
    public boolean command() {
        return COMMAND.equals(kind);
    }

    /**
     * Returns the name the host knows the subscription by, such as
     * {@code platform-audit.identity-audit-fact-record.v1}.
     *
     * @return plugin id and handler id
     */
    public String name() {
        return command() ? pluginId + ".command." + handlerId : pluginId + "." + handlerId;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
