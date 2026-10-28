/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.outbox;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Runs one relay per owner outbox, all sending through the same transport (root BRX-RS-MESSAGE-023,
 * MESSAGE-024; P4 design D12).
 *
 * <p>The stores and the transport are resolved when the group starts, after every bean exists, so the
 * order in which the auto-configurations register them does not matter. A host that enables the
 * relay without a store registry or a transport fails to start rather than relaying nothing.</p>
 */
public final class OutboxRelayGroup implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayGroup.class);

    private final OutboxRelayProperties properties;
    private final OutboxRelayPolicy policy;
    private final Supplier<OutboxStoreRegistry> registry;
    private final Supplier<OutboxTransport> transport;
    private final List<OutboxRelayManagedResource> loops = new ArrayList<>();
    private volatile boolean running;

    /**
     * Creates the group.
     *
     * @param properties relay settings
     * @param policy batch, attempt and lease policy shared by every relay
     * @param registry supplies the owner outboxes; may supply {@code null}
     * @param transport supplies the transport; may supply {@code null}
     */
    public OutboxRelayGroup(
            OutboxRelayProperties properties,
            OutboxRelayPolicy policy,
            Supplier<OutboxStoreRegistry> registry,
            Supplier<OutboxTransport> transport) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        String owner = properties.getOwner();
        if (owner == null || owner.isBlank()) {
            throw new IllegalStateException("brix.outbox.relay.owner must be configured when the relay is enabled");
        }
        OutboxStoreRegistry stores = registry.get();
        if (stores == null) {
            throw new IllegalStateException("The outbox relay is enabled but no outbox store registry exists;"
                + " enable brix.outbox.relay.persistence");
        }
        OutboxTransport sender = transport.get();
        if (sender == null) {
            throw new IllegalStateException("The outbox relay is enabled but transport '" + properties.getTransport()
                + "' is not available");
        }
        for (Map.Entry<String, OutboxMessageStore> store : stores.stores().entrySet()) {
            OutboxRelay relay = new OutboxRelay(owner, store.getValue(), sender, policy, Clock.systemUTC());
            OutboxRelayManagedResource loop = new OutboxRelayManagedResource(relay, properties.getPollDelay(),
                "brix-outbox-relay-" + store.getKey());
            loop.start();
            loops.add(loop);
        }
        running = true;
        log.info("event=outbox.relay_started outboxes={} transport={}", stores.stores().keySet(), properties.getTransport());
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        loops.forEach(OutboxRelayManagedResource::stop);
        loops.clear();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Whether every relay is running and its last polling pass succeeded.
     *
     * @return readiness
     */
    public boolean isReady() {
        return running && loops.stream().allMatch(OutboxRelayManagedResource::isReady);
    }
}
