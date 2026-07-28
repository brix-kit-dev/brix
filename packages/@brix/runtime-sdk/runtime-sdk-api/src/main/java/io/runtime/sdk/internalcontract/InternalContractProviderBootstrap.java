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
 * Provider-only binding surface for descriptor-declared internal contracts.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface InternalContractProviderBootstrap {

    /**
     * Binds one descriptor-declared contract factory.
     *
     * @param contractId stable contract identifier
     * @param contractType contract Java type
     * @param factory owner-scoped factory
     * @param <C> contract type
     */
    <C> void bind(
        String contractId,
        Class<C> contractType,
        InternalContractProviderFactory<C> factory);
}
