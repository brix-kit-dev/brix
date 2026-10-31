/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.server;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

import io.brix.samples.orders.core.OrderPlacement;
import io.brix.samples.shipping.api.SampleShippingQueries;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.IdGeneratorCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.QueryCapability;
import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.BrixPlugin;
import io.runtime.sdk.plugin.PluginBootstrapContext;
import io.runtime.sdk.plugin.PluginContext;

/**
 * The sample publisher (Kafka design §14 K23). Its manifest declares the endpoints, the two events with their schemas
 * and reliability, the query of sample-shipping it calls, the command it handles, its task and the data it owns; this
 * class only binds the handlers. The runtime creates them after the plugin started, with the capabilities the manifest
 * requires.
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 */
public final class SampleOrdersPlugin implements BrixPlugin {

    /** The handler of {@code POST /api/sample-orders/orders}, as the manifest names it. */
    public static final String PLACE_ORDER = "sample-orders.place.v1";

    /** The handler of {@code GET /api/sample-orders/orders/{orderId}}, as the manifest names it. */
    public static final String SHOW_ORDER = "sample-orders.show.v1";

    /** The handler of the typed command {@code sample-orders.cancel-order}, as the manifest names it. */
    public static final String CANCEL_ORDER = "sample-orders.cancel-order.v1";

    /** The handler of the task {@code sample-orders.heartbeat}, as the manifest names it. */
    public static final String HEARTBEAT = "sample-orders.heartbeat.v1";

    private final AtomicReference<BrixHealth> health = new AtomicReference<>(BrixHealth.down("not started"));

    @Override
    public void configure(PluginBootstrapContext bootstrap) {
        bootstrap.bindEndpointHandlerFactory(PLACE_ORDER, context -> new PlaceOrder(orders(context)));
        // The order detail reads sample-shipping's shipment through its typed query (query design §4.2).
        bootstrap.bindEndpointHandlerFactory(SHOW_ORDER, context -> new ShowOrder(orders(context),
            context.require(QueryCapability.class).client(SampleShippingQueries.FIND_SHIPMENT)));
        // Other plugins ask for a cancellation with the typed command; the runtime delivers each command once
        // (standard-link plan L4.7).
        bootstrap.bindCommandHandlerFactory(CANCEL_ORDER, context -> new CancelOrderHandler(orders(context)));
        // The runtime's scheduler runs the declared task at each trigger, on one host (standard-link plan L6.7).
        bootstrap.bindTaskFactory(HEARTBEAT, context -> new Heartbeat());
    }

    private static OrderPlacement orders(PluginContext context) {
        return new OrderPlacement(
            context.require(PersistenceCapability.class),
            context.require(EventBusCapability.class),
            context.require(IdGeneratorCapability.class),
            Clock.systemUTC());
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
