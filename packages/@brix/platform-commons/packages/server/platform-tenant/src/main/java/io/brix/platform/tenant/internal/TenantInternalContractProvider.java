/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.internal;

import io.runtime.sdk.internalcontract.InternalContractProvider;
import io.runtime.sdk.internalcontract.InternalContractProviderBootstrap;

/**
 * ServiceLoader provider binding the platform-tenant internal contract.
 *
 * <p>The factory obtains the Owner-scoped implementation from the Runtime
 * provider context. It does not use Spring lookup, static holders, reflection,
 * or repository access.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public final class TenantInternalContractProvider implements InternalContractProvider {

    @Override
    public void configure(InternalContractProviderBootstrap bootstrap) {
        bootstrap.bind(
            TenantAdministration.CONTRACT_ID,
            TenantAdministration.class,
            context -> context.requireOwnerCapability(TenantAdministration.class));
    }
}
