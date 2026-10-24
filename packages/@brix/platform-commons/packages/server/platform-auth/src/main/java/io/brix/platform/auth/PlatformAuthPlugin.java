/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.auth;

import java.util.concurrent.atomic.AtomicReference;

import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.BrixPlugin;
import io.runtime.sdk.plugin.PluginBootstrapContext;
import io.runtime.sdk.plugin.PluginContext;

/**
 * Runtime Shell plugin provider of platform-auth, the Data Owner of the {@code platform_auth}
 * schema.
 *
 * <p>It publishes no endpoint. Its only one, {@code POST /api/auth/login/actor}, took a password
 * and returned tokens to the browser; staff now sign in through the hosted login of the identity
 * protocol surface (identity-auth {@code BRX-IA-FLOW-006}, IA-1; M1 slice 3).</p>
 */
public final class PlatformAuthPlugin implements BrixPlugin {

    private final AtomicReference<BrixHealth> health = new AtomicReference<>(BrixHealth.unknown("Not started"));

    @Override
    public void configure(PluginBootstrapContext bootstrap) {
        // No Runtime Entry to bind.
    }

    @Override
    public void onStart(PluginContext context) {
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
