/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.shipping.server;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

import io.brix.samples.orders.api.SampleOrdersCommands;
import io.brix.samples.shipping.core.ShipmentDesk;
import io.runtime.sdk.capability.CommandCapability;
import io.runtime.sdk.capability.IdGeneratorCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.BrixPlugin;
import io.runtime.sdk.plugin.PluginBootstrapContext;
import io.runtime.sdk.plugin.PluginContext;

/**
 * The sample subscriber (Kafka design §14 K23). Its manifest subscribes to {@code SampleOrderPlaced} and
 * {@code SampleOrderQuantityAdjusted} with a persistent inbox, offers a read endpoint and provides the typed query
 * {@code sample-shipping.find-shipment} (query design §4.1); its refuse endpoint sends sample-orders' typed command
 * {@code sample-orders.cancel-order} (standard-link plan L4.7). This class binds the handlers under the manifest's
 * handler ids. The plugin reads each event as its own type ({@code OrderPlaced}, {@code OrderQuantityAdjusted}), a view
 * of the published schema; for the command it addresses sample-orders, it takes the generated type from sample-orders'
 * api artifact and nothing else of it.
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 */
public final class SampleShippingPlugin implements BrixPlugin {

    /** The handler of the {@code SampleOrderPlaced} subscription, as the manifest names it. */
    public static final String PREPARE_SHIPMENT = "sample-shipping.prepare-shipment.v1";

    /** The handler of the {@code SampleOrderQuantityAdjusted} subscription, as the manifest names it. */
    public static final String FOLLOW_QUANTITY = "sample-shipping.follow-quantity.v1";

    /** The handler of {@code GET /api/sample-shipping/shipments/{orderId}}, as the manifest names it. */
    public static final String FIND_SHIPMENT = "sample-shipping.find-shipment.v1";

    /** The handler of the typed query {@code sample-shipping.find-shipment}, as the manifest names it. */
    public static final String FIND_SHIPMENT_QUERY = "sample-shipping.find-shipment.query.v1";

    /** The handler of {@code POST /api/sample-shipping/shipments/{orderId}/refuse}, as the manifest names it. */
    public static final String REFUSE_SHIPMENT = "sample-shipping.refuse-shipment.v1";

    /**
     * The handler of {@code GET /api/sample-shipping/cancellations/{commandId}}, as the manifest names it
     * (standard-link plan point 7).
     */
    public static final String FIND_CANCELLATION = "sample-shipping.find-cancellation.v1";

    private final AtomicReference<BrixHealth> health = new AtomicReference<>(BrixHealth.down("not started"));

    @Override
    public void configure(PluginBootstrapContext bootstrap) {
        bootstrap.bindEventHandlerFactory(PREPARE_SHIPMENT, context -> new PrepareShipment(desk(context)));
        bootstrap.bindEventHandlerFactory(FOLLOW_QUANTITY, context -> new FollowQuantity(desk(context)));
        bootstrap.bindEndpointHandlerFactory(FIND_SHIPMENT, context -> new FindShipment(desk(context)));
        bootstrap.bindQueryHandlerFactory(FIND_SHIPMENT_QUERY, context -> new FindShipmentQuery(desk(context)));
        // Refusing a shipment asks sample-orders to cancel the order with its typed command (plan L4.7).
        bootstrap.bindEndpointHandlerFactory(REFUSE_SHIPMENT, context -> new RefuseShipment(desk(context),
            context.require(CommandCapability.class).client(SampleOrdersCommands.CANCEL_ORDER)));
        // The refusal answers with the command id; this reads where that command stands (plan point 7).
        bootstrap.bindEndpointHandlerFactory(FIND_CANCELLATION, context -> new FindCancellation(
            context.require(CommandCapability.class).client(SampleOrdersCommands.CANCEL_ORDER)));
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
