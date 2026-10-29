/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * The host's delivery core, built the first time something needs it (Kafka design §4.1).
 * <p>The relay, the subscription group and the events endpoint ask for it when they start or serve, after
 * every bean exists, so the order in which the auto-configurations register the plugins, the owner
 * persistence and the inboxes does not matter. All of them share the one core.</p>
 */
public final class EventDeliveryProvider implements Supplier<EventDelivery> {

    private final Supplier<EventDelivery> factory;
    private EventDelivery delivery;

    /**
     * Creates the provider.
     *
     * @param factory builds the core; supplies {@code null} while the host lacks what the core needs
     */
    public EventDeliveryProvider(Supplier<EventDelivery> factory) {
        this.factory = Objects.requireNonNull(factory, "factory must not be null");
    }

    /**
     * Returns the core.
     *
     * @return the core, or {@code null} while the host lacks the plugins, owner persistence or inboxes it needs
     */
    @Override
    public synchronized EventDelivery get() {
        if (delivery == null) {
            delivery = factory.get();
        }
        return delivery;
    }
}
