/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.runtime.sdk.internalcontract;

/**
 * Owner-scoped creation context for an internal contract provider.
 *
 * <p>The context deliberately exposes neither a registry nor any consumer
 * lookup operation.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface InternalContractProviderContext {

    /**
     * Returns the immutable descriptor owner identity.
     *
     * @return owner identity
     */
    RuntimeModuleIdentity ownerIdentity();

    /**
     * Returns an owner-declared capability.
     *
     * @param capabilityType capability contract type
     * @param <C> capability type
     * @return owner-scoped capability
     * @throws RuntimeException when the capability is undeclared or unavailable
     */
    <C> C requireOwnerCapability(Class<C> capabilityType);
}
