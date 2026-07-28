/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant;

import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.BrixPlugin;
import io.runtime.sdk.plugin.PluginBootstrapContext;
import io.runtime.sdk.plugin.PluginContext;

/**
 * Runtime Shell plugin provider for the platform tenant Data Owner.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public final class PlatformTenantPlugin implements BrixPlugin {

    @Override
    public void configure(PluginBootstrapContext bootstrap) {
        // The Phase 3 Data Owner exposes no public Runtime Entry.
    }

    @Override
    public void onStart(PluginContext context) {
        // Data Owner services are provided by the Owner artifact and invoked through contracts.
    }

    @Override
    public void onStop() {
        // No unmanaged resources are held by the plugin provider.
    }

    @Override
    public BrixHealth health() {
        return BrixHealth.up();
    }
}
