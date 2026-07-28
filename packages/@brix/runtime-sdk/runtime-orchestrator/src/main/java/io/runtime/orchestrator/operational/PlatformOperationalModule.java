/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.operational;

import io.runtime.sdk.plugin.BrixHealth;

/**
 * L2B-internal ServiceLoader entry point for a platform operational module.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface PlatformOperationalModule {

    /**
     * Registers descriptor-declared endpoint and task factories.
     *
     * @param bootstrap factory-only binding context
     */
    void configure(OperationalBootstrapContext bootstrap);

    /**
     * Starts already-created and wired module resources.
     *
     * @param context module-scoped operational context
     */
    void onStart(OperationalContext context);

    /**
     * Stops the module during bounded drain.
     */
    void onStop();

    /**
     * Returns the current non-null module health.
     *
     * @return module health
     */
    BrixHealth health();
}
