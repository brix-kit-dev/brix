/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.shipping.server;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.brix.samples.orders.api.CancelOrder;
import io.brix.samples.shipping.api.Cancellation;
import io.brix.samples.shipping.api.CancellationRequest;
import io.runtime.sdk.command.CommandClient;
import io.runtime.sdk.command.CommandStatus;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.TypedEndpointHandler;

/**
 * {@code GET /api/sample-shipping/cancellations/{commandId}}: where a cancellation sample-shipping asked sample-orders
 * for stands (standard-link plan point 7). The command id is the one {@code POST .../refuse} answered with: the refusal
 * only says the command is queued; this says whether sample-orders cancelled the order, or took the command as a
 * duplicate of an earlier refusal of the same order.
 *
 * <p>The runtime reads the status for sample-shipping in the caller's tenant. A command of another tenant, one
 * sample-shipping did not send, and one past its retention all read as {@code UNKNOWN}.</p>
 */
final class FindCancellation implements TypedEndpointHandler<CancellationRequest, Cancellation> {

    private final CommandClient<CancelOrder> cancellations;

    FindCancellation(CommandClient<CancelOrder> cancellations) {
        this.cancellations = Objects.requireNonNull(cancellations, "cancellations must not be null");
    }

    @Override
    public CompletionStage<Cancellation> handle(EndpointInvocation<CancellationRequest> request) {
        CommandStatus status = cancellations.status(request.body().commandId());
        return CompletableFuture.completedFuture(new Cancellation(status.commandId(), status.state().name(),
            status.errorCode(), status.originalCommandId()));
    }
}
