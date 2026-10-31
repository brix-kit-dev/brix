/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.server;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.brix.samples.orders.api.CancelOrder;
import io.brix.samples.orders.core.OrderPlacement;
import io.runtime.sdk.plugin.CommandHandler;
import io.runtime.sdk.plugin.CommandInvocation;

/**
 * Applies the typed command {@code sample-orders.cancel-order} (root §6.3; standard-link plan L4.7).
 *
 * <p>The runtime delivers the command once per command id (the inbox) and once per business key of its sender and
 * tenant, in sample-orders' unit of work bound to the command's tenant. An order id that is not a number, or not an
 * order of that tenant, leaves nothing to cancel; a cancelled order stays cancelled.</p>
 */
final class CancelOrderHandler implements CommandHandler<CancelOrder> {

    private final OrderPlacement orders;

    CancelOrderHandler(OrderPlacement orders) {
        this.orders = Objects.requireNonNull(orders, "orders must not be null");
    }

    @Override
    public CompletionStage<Void> handle(CommandInvocation<CancelOrder> invocation) {
        CancelOrder command = invocation.command().payload();
        try {
            orders.cancel(Long.parseLong(command.orderId()), command.reason().name());
        } catch (NumberFormatException notAnOrder) {
            // Nothing to cancel: sample-orders' ids are numbers.
        }
        return CompletableFuture.completedFuture(null);
    }
}
