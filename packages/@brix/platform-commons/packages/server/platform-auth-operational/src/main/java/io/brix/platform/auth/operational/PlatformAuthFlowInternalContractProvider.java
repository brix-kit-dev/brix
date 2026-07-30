/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.auth.operational;

import io.runtime.sdk.capability.AuthFlowCapability;
import io.runtime.sdk.internalcontract.InternalContractProvider;
import io.runtime.sdk.internalcontract.InternalContractProviderBootstrap;

/**
 * Binds the descriptor-declared platform AuthFlow internal contract.
 */
public final class PlatformAuthFlowInternalContractProvider implements InternalContractProvider {

    @Override
    public void configure(InternalContractProviderBootstrap bootstrap) {
        bootstrap.bind(
            "brix.internal.platform.auth-flow",
            AuthFlowCapability.class,
            context -> context.requireOwnerCapability(AuthFlowCapability.class));
    }
}
