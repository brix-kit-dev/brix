/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.operational;

import io.runtime.sdk.plugin.BrixHealth;

/**
 * ServiceLoader fixture for operational discovery contract tests.
 */
public final class ServiceLoadedTestOperationalModule implements PlatformOperationalModule {

    @Override
    public void configure(OperationalBootstrapContext bootstrap) {
        bootstrap.bindEndpointHandlerFactory("runtime.status", context -> request -> "ok");
    }

    @Override
    public void onStart(OperationalContext context) {
        context.moduleIdentity();
    }

    @Override
    public void onStop() {
    }

    @Override
    public BrixHealth health() {
        return BrixHealth.up();
    }
}
