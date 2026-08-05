/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.identity;

import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.BrixPlugin;
import io.runtime.sdk.plugin.PluginBootstrapContext;
import io.runtime.sdk.plugin.PluginContext;

/**
 * Runtime Shell plugin entry for the platform identity Data Owner.
 *
 * <p>This first-step module is contract-only. It declares no runtime entry
 * handlers until the copied identity/bootstrap implementation is wired in a
 * later slice.</p>
 */
public final class PlatformIdentityPlugin implements BrixPlugin {

    @Override
    public void configure(PluginBootstrapContext bootstrap) {
        // No endpoint/task bindings in the source-module bootstrap slice.
    }

    @Override
    public void onStart(PluginContext context) {
        // No managed resources in the source-module bootstrap slice.
    }

    @Override
    public void onStop() {
        // No managed resources in the source-module bootstrap slice.
    }

    @Override
    public BrixHealth health() {
        return BrixHealth.up();
    }
}
