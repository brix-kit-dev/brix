/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.shipping;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.runtime.sdk.capability.IdGeneratorCapability;
import io.runtime.sdk.plugin.EventHandler;
import io.runtime.sdk.plugin.EventInvocation;

/**
 * Prepares a shipment for each placed order.
 *
 * <p>The runtime calls it inside the plugin's unit of work, in the event's tenant, and records the event in
 * {@code sample_shipping_inbox} in the same transaction: the shipment and the receipt commit together, and a
 * redelivered event never reaches the handler again. When the handler fails, both roll back and the runtime retries;
 * after the last attempt it parks the event for an operator. The handler therefore does its work synchronously and
 * returns a completed stage. The payload type is the class's type argument, from which the runtime decodes it.</p>
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 */
final class PrepareShipment implements EventHandler<OrderPlaced> {

    private final SampleShipmentRepository shipments;
    private final IdGeneratorCapability ids;
    private final Clock clock;

    PrepareShipment(SampleShipmentRepository shipments, IdGeneratorCapability ids, Clock clock) {
        this.shipments = Objects.requireNonNull(shipments, "shipments must not be null");
        this.ids = Objects.requireNonNull(ids, "ids must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public CompletionStage<Void> handle(EventInvocation<OrderPlaced> invocation) {
        OrderPlaced order = invocation.event().payload();
        shipments.save(SampleShipment.prepare(Long.parseLong(ids.generate()), order, OffsetDateTime.now(clock)));
        return CompletableFuture.completedFuture(null);
    }
}
