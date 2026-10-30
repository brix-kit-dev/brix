/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.shipping.server;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

import io.brix.samples.shipping.core.ShipmentDesk;
import io.runtime.sdk.capability.IdGeneratorCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.BrixPlugin;
import io.runtime.sdk.plugin.PluginBootstrapContext;
import io.runtime.sdk.plugin.PluginContext;

/**
 * The sample subscriber (Kafka design §14 K23). Its manifest subscribes to {@code SampleOrderPlaced} with a persistent
 * inbox and offers a read endpoint; this class binds both handlers under the manifest's handler ids. The plugin
 * depends on no artifact of the publisher: it reads the event as its own {@code OrderPlaced}, a view of the
 * published schema.
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 */
public final class SampleShippingPlugin implements BrixPlugin {

    /** The handler of the {@code SampleOrderPlaced} subscription, as the manifest names it. */
    public static final String PREPARE_SHIPMENT = "sample-shipping.prepare-shipment.v1";

    /** The handler of {@code GET /api/sample-shipping/shipments/{orderId}}, as the manifest names it. */
    public static final String FIND_SHIPMENT = "sample-shipping.find-shipment.v1";

    private final AtomicReference<BrixHealth> health = new AtomicReference<>(BrixHealth.down("not started"));

    @Override
    public void configure(PluginBootstrapContext bootstrap) {
        bootstrap.bindEventHandlerFactory(PREPARE_SHIPMENT, context -> new PrepareShipment(desk(context)));
        bootstrap.bindEndpointHandlerFactory(FIND_SHIPMENT, context -> new FindShipment(desk(context)));
    }

    private static ShipmentDesk desk(PluginContext context) {
        return new ShipmentDesk(context.require(PersistenceCapability.class),
            context.require(IdGeneratorCapability.class), Clock.systemUTC());
    }

    @Override
    public void onStart(PluginContext context) {
        health.set(BrixHealth.up());
    }

    @Override
    public void onStop() {
        health.set(BrixHealth.down("stopped"));
    }

    @Override
    public BrixHealth health() {
        return health.get();
    }
}
