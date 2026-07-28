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
 * Factory for a descriptor-declared internal contract implementation.
 *
 * @param <C> internal contract type
 * @author Runtime SDK Team
 * @since 3.0.10
 */
@FunctionalInterface
public interface InternalContractProviderFactory<C> {

    /**
     * Creates the contract using an owner-scoped context.
     *
     * @param context provider context
     * @return non-null contract implementation
     */
    C create(InternalContractProviderContext context);
}
