/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.IdGeneratorCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.BrixPlugin;
import io.runtime.sdk.plugin.PluginBootstrapContext;
import io.runtime.sdk.plugin.PluginContext;

/**
 * The sample publisher (Kafka design §14 K23). Its manifest declares the endpoint, the two events with their schemas
 * and reliability, and the data it owns; this class only binds the endpoint handler. The runtime creates the handler
 * after the plugin started, with the capabilities the manifest requires.
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 */
public final class SampleOrdersPlugin implements BrixPlugin {

    /** The plugin id, as the manifest names it. */
    public static final String PLUGIN_ID = "sample-orders";

    /** The handler of {@code POST /api/sample-orders/orders}, as the manifest names it. */
    public static final String PLACE_ORDER = "sample-orders.place.v1";

    private final AtomicReference<BrixHealth> health = new AtomicReference<>(BrixHealth.down("not started"));

    @Override
    public void configure(PluginBootstrapContext bootstrap) {
        bootstrap.bindEndpointHandlerFactory(PLACE_ORDER, context -> new PlaceOrder(
            context.require(PersistenceCapability.class),
            context.require(EventBusCapability.class),
            context.require(IdGeneratorCapability.class),
            Clock.systemUTC()));
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
