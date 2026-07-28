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
 * ServiceLoader provider for internal contract factory bindings.
 *
 * <p>This SPI is provider-only. It does not expose internal contract
 * consumption, enumeration, or registry access.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface InternalContractProvider {

    /**
     * Registers factories declared by the provider artifact descriptor.
     *
     * @param bootstrap provider binding surface
     */
    void configure(InternalContractProviderBootstrap bootstrap);
}
