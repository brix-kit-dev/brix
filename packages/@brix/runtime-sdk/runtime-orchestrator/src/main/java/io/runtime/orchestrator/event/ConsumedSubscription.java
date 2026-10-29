/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

/**
 * A subscription a broker consumes for (Kafka design §4.2): the subscriber plugin, its handler id and the
 * event type it receives. The runtime takes it from the manifests; plugins never see it.
 *
 * @param pluginId subscriber plugin, the owner of the inbox
 * @param handlerId the subscription's handler id
 * @param eventType the event type it receives
 */
public record ConsumedSubscription(String pluginId, String handlerId, String eventType) {

    /**
     * Creates and validates a subscription.
     */
    public ConsumedSubscription {
        requireText(pluginId, "pluginId");
        requireText(handlerId, "handlerId");
        requireText(eventType, "eventType");
    }

    /**
     * Returns the name the host knows the subscription by, such as
     * {@code platform-audit.identity-audit-fact-record.v1}.
     *
     * @return plugin id and handler id
     */
    public String name() {
        return pluginId + "." + handlerId;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
