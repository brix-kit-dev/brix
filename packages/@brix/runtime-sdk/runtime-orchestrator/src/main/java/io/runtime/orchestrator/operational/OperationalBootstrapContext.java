/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.operational;

import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.ManagedTask;

/**
 * Factory-only binding surface for a platform operational module.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface OperationalBootstrapContext {

    /**
     * Binds a descriptor-declared endpoint handler factory.
     *
     * @param handlerId descriptor handler identifier
     * @param factory handler factory
     */
    void bindEndpointHandlerFactory(
        String handlerId,
        OperationalHandlerFactory<? extends EndpointHandler<?, ?>> factory);

    /**
     * Binds a descriptor-declared task factory.
     *
     * @param handlerId descriptor handler identifier
     * @param factory task factory
     */
    void bindTaskFactory(
        String handlerId,
        OperationalHandlerFactory<? extends ManagedTask> factory);
}
