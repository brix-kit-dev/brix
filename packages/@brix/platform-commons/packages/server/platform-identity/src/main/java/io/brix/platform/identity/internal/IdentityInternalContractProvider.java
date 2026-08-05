/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.identity.internal;

import io.runtime.sdk.internalcontract.InternalContractProvider;
import io.runtime.sdk.internalcontract.InternalContractProviderBootstrap;

/**
 * ServiceLoader provider for platform-identity internal contracts.
 *
 * <p>The provider only binds descriptor-declared factories. Implementations are
 * resolved later from owner-scoped runtime capabilities; this class does not use
 * Spring lookup, static holders, reflection, repositories, or services.</p>
 */
public final class IdentityInternalContractProvider implements InternalContractProvider {

    @Override
    public void configure(InternalContractProviderBootstrap bootstrap) {
        bootstrap.bind(
                PlatformBootstrapAdministration.CONTRACT_ID,
                PlatformBootstrapAdministration.class,
                context -> context.requireOwnerCapability(PlatformBootstrapAdministration.class));
        bootstrap.bind(
                PlatformIdentityAdministration.CONTRACT_ID,
                PlatformIdentityAdministration.class,
                context -> context.requireOwnerCapability(PlatformIdentityAdministration.class));
    }
}
