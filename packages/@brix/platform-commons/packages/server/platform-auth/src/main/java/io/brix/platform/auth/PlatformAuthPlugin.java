/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.auth;

import java.util.concurrent.atomic.AtomicReference;

import io.brix.platform.auth.endpoint.ActorLoginHandler;
import io.runtime.sdk.capability.AuthFlowCapability;
import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.BrixPlugin;
import io.runtime.sdk.plugin.PluginBootstrapContext;
import io.runtime.sdk.plugin.PluginContext;

/**
 * Runtime Shell plugin provider for public authentication endpoints.
 */
public final class PlatformAuthPlugin implements BrixPlugin {

    public static final String ENDPOINT_ACTOR_LOGIN = "platform-auth.login.actor.v1";

    private final AtomicReference<AuthFlowCapability> authFlow = new AtomicReference<>();
    private final AtomicReference<BrixHealth> health = new AtomicReference<>(BrixHealth.unknown("Not started"));

    @Override
    public void configure(PluginBootstrapContext bootstrap) {
        bootstrap.bindEndpoint(ENDPOINT_ACTOR_LOGIN, new ActorLoginHandler(authFlow::get));
    }

    @Override
    public void onStart(PluginContext context) {
        authFlow.set(context.require(AuthFlowCapability.class));
        health.set(BrixHealth.up());
    }

    @Override
    public void onStop() {
        authFlow.set(null);
        health.set(BrixHealth.down("Stopped"));
    }

    @Override
    public BrixHealth health() {
        return health.get();
    }
}
