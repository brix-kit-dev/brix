/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.auth.operational;

import java.util.concurrent.atomic.AtomicReference;

import io.runtime.orchestrator.operational.OperationalBootstrapContext;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.orchestrator.operational.PlatformOperationalModule;
import io.runtime.sdk.plugin.BrixHealth;

/**
 * Operational provider for AuthFlow internal contracts.
 */
public final class PlatformAuthFlowOperationalModule implements PlatformOperationalModule {

    private final AtomicReference<BrixHealth> health = new AtomicReference<>(BrixHealth.unknown("Not started"));

    @Override
    public void configure(OperationalBootstrapContext bootstrap) {
        // This module only provides internal contracts.
    }

    @Override
    public void onStart(OperationalContext context) {
        health.set(BrixHealth.up());
    }

    @Override
    public void onStop() {
        health.set(BrixHealth.down("Stopped"));
    }

    @Override
    public BrixHealth health() {
        return health.get();
    }
}
