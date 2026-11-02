/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import java.util.Objects;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Reports the event subscriptions' consumers in the host's health (Kafka design §14 K10): DOWN when a consumer
 * the group started is no longer consuming, or while the broker refuses to carry the subscriptions until an operator
 * fixes its configuration (standard-link plan point 5). While the group still waits for the plugins or the broker it
 * is UP, as the relay waits then too (D21); how long the longest record has been in its handler is a detail, and an
 * alert rule watches the matching gauge.
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class EventSubscriptionsHealthIndicator implements HealthIndicator {

    private final EventSubscriptionGroup group;

    /**
     * Creates the indicator.
     *
     * @param group the subscription group
     */
    public EventSubscriptionsHealthIndicator(EventSubscriptionGroup group) {
        this.group = Objects.requireNonNull(group, "group must not be null");
    }

    @Override
    public Health health() {
        EventSubscriptionGroup.SubscriptionsState state = group.state();
        if (state.refusal() != null) {
            return Health.down()
                .withDetail("consuming", false)
                .withDetail("refused", state.refusal())
                .build();
        }
        Health.Builder health = state.consuming() && state.running() < state.consumers()
            ? Health.down()
            : Health.up();
        return health
            .withDetail("consuming", state.consuming())
            .withDetail("consumers", state.consumers())
            .withDetail("running", state.running())
            .withDetail("longestHandlingSeconds", state.longestHandling().toSeconds())
            .build();
    }
}
