/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.e2e;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.brix.samples.orders.internal.AdjustOrderQuantity;
import io.brix.samples.orders.internal.AdjustedOrder;
import io.brix.samples.orders.internal.SampleOrderAdministration;
import io.brix.samples.orders.internal.SampleOrderAdministrationException;
import io.runtime.orchestrator.operational.OperationalBootstrapContext;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.orchestrator.operational.PlatformOperationalModule;
import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.TypedEndpointHandler;

/**
 * The sample's operational module, only in these tests. It owns no data: an operator's change reaches sample-orders
 * through its internal contract, in sample-orders' own unit of work (root BRX-RS-HOST-019, HOST-020).
 */
public final class SampleOperationsModule implements PlatformOperationalModule {

    /** How often the handler ran: a replay does not run it. */
    static final AtomicInteger HANDLED = new AtomicInteger();

    /** Makes the handler fail after the owner committed, as platform-admin's audit can. */
    static final AtomicBoolean FAIL_AFTER_THE_WRITE = new AtomicBoolean();

    /** Makes the handler work this long after the owner committed, so that it can answer after its deadline. */
    static final AtomicReference<Duration> PAUSE_AFTER_THE_WRITE = new AtomicReference<>(Duration.ZERO);

    private static final String ADJUST_QUANTITY = "sample-operations.adjust-quantity.v1";

    @Override
    public void configure(OperationalBootstrapContext bootstrap) {
        bootstrap.bindEndpointHandlerFactory(ADJUST_QUANTITY,
            context -> new AdjustQuantity(context.requireInternalContract(SampleOrderAdministration.class)));
    }

    @Override
    public void onStart(OperationalContext context) {
    }

    @Override
    public void onStop() {
    }

    @Override
    public BrixHealth health() {
        return BrixHealth.up();
    }

    /**
     * What an operator asks for: the runtime builds it from the path and the body, after checking it against the
     * request schema.
     *
     * @param tenantId the tenant of the order
     * @param orderId the order
     * @param delta how many items to add
     */
    record AdjustQuantityRequest(long tenantId, long orderId, int delta) {
    }

    /** A typed handler, as a plugin's: the runtime writes the status the descriptor declares. */
    private static final class AdjustQuantity implements TypedEndpointHandler<AdjustQuantityRequest, AdjustedOrder> {

        private final SampleOrderAdministration orders;

        private AdjustQuantity(SampleOrderAdministration orders) {
            this.orders = orders;
        }

        @Override
        public CompletionStage<AdjustedOrder> handle(EndpointInvocation<AdjustQuantityRequest> invocation) {
            HANDLED.incrementAndGet();
            AdjustQuantityRequest request = invocation.body();
            AdjustedOrder adjusted;
            try {
                adjusted = orders.adjustQuantity(
                    new AdjustOrderQuantity(request.tenantId(), request.orderId(), request.delta()));
            } catch (SampleOrderAdministrationException refused) {
                int status = SampleOrderAdministrationException.ORDER_NOT_FOUND.equals(refused.code()) ? 404 : 409;
                throw new EndpointHandlingException(status, refused.code(), refused.getMessage());
            }
            if (FAIL_AFTER_THE_WRITE.get()) {
                throw new EndpointHandlingException(503, "SAMPLE_AUDIT_WRITE_FAILED", "the change could not be recorded");
            }
            pause(PAUSE_AFTER_THE_WRITE.get());
            return CompletableFuture.completedFuture(adjusted);
        }

        private static void pause(Duration duration) {
            try {
                Thread.sleep(duration.toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        }
    }
}
