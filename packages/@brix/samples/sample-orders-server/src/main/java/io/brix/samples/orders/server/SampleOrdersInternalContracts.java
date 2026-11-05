/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.server;

import io.brix.samples.orders.core.OrderCorrections;
import io.brix.samples.orders.internal.SampleOrderAdministration;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.internalcontract.InternalContractProvider;
import io.runtime.sdk.internalcontract.InternalContractProviderBootstrap;

/**
 * Binds sample-orders' internal contract: the manifest declares it, and this says who implements it (root
 * BRX-RS-HOST-021).
 */
public final class SampleOrdersInternalContracts implements InternalContractProvider {

    @Override
    public void configure(InternalContractProviderBootstrap bootstrap) {
        bootstrap.bind(SampleOrderAdministration.CONTRACT_ID, SampleOrderAdministration.class,
            context -> new OrderCorrections(context.requireOwnerCapability(PersistenceCapability.class)));
    }
}
