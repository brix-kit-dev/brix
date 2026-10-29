/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import io.runtime.orchestrator.event.conformance.ConformanceWorld;
import io.runtime.orchestrator.event.conformance.EventTransportConformance;
import io.runtime.orchestrator.outbox.OutboxRelay;
import io.runtime.orchestrator.outbox.OutboxRelayManagedResource;
import io.runtime.orchestrator.outbox.OutboxRelayMetrics;

/**
 * The in-process transport passes the transport conformance suite (Kafka design §4.6): the relay hands each
 * claimed event to the delivery core, which parks it in the outbox when it cannot be delivered.
 */
class InProcessTransportConformanceTest extends EventTransportConformance {

    @Override
    protected Harness harness(ConformanceWorld world) {
        return new Harness() {

            private OutboxRelayManagedResource loop;

            @Override
            public void start() {
                OutboxRelay relay = new OutboxRelay("conformance", world.outbox(),
                    new InProcessEventTransport(world.newDelivery()), world.policy(), Clock.systemUTC(),
                    OutboxRelayMetrics.NOOP, world.tracing());
                loop = new OutboxRelayManagedResource(relay, Duration.ofMillis(20), "conformance-relay");
                loop.start();
            }

            @Override
            public void stop() {
                if (loop != null) {
                    loop.stop();
                    loop = null;
                }
            }

            @Override
            public void reachable(boolean reachable) {
                // In process, the transport can deliver once the plugins have started.
                world.pluginsStarted(reachable);
            }

            @Override
            public List<Parked> parked() {
                return world.outbox().listParked(100).stream()
                    .map(message -> new Parked(message.eventId(), message.errorCode()))
                    .toList();
            }

            @Override
            public boolean replay(String eventId) {
                return world.outbox().replayParked(eventId, OffsetDateTime.now());
            }

            @Override
            public void close() {
                stop();
            }
        };
    }
}
