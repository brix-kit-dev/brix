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
     * <p>A plugin whose manifest declares {@code EventBusCapability} gets the same event bus its
     * {@code PluginContext} gives its handlers: CRITICAL and STANDARD events go to the plugin's own
     * {@code data.outbox} in the contract call's unit of work, so they commit or roll back with the call's
     * writes, and BEST_EFFORT events leave after the commit.</p>
     *
     * @param capabilityType capability contract type
     * @param <C> capability type
     * @return owner-scoped capability
     * @throws RuntimeException when the capability is undeclared or unavailable
     */
    <C> C requireOwnerCapability(Class<C> capabilityType);
}
