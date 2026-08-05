/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant;

import java.util.concurrent.atomic.AtomicReference;

import io.brix.platform.tenant.endpoint.AcceptFirstOwnerInvitationHandler;
import io.brix.platform.tenant.internal.TenantAdministration;
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

    public static final String ENDPOINT_FIRST_OWNER_ACCEPT = "platform-tenant.first-owner.accept.v1";

    private final AtomicReference<TenantAdministration> tenantAdministration = new AtomicReference<>();
    private final AtomicReference<BrixHealth> health = new AtomicReference<>(BrixHealth.unknown("Not started"));

    @Override
    public void configure(PluginBootstrapContext bootstrap) {
        bootstrap.bindEndpoint(
            ENDPOINT_FIRST_OWNER_ACCEPT,
            new AcceptFirstOwnerInvitationHandler(tenantAdministration::get));
    }

    @Override
    public void onStart(PluginContext context) {
        tenantAdministration.set(context.find(TenantAdministration.class)
            .orElseThrow(() -> new IllegalStateException(
                "TenantAdministration internal contract is not available for first-owner endpoint binding")));
        health.set(BrixHealth.up());
    }

    @Override
    public void onStop() {
        tenantAdministration.set(null);
        health.set(BrixHealth.down("Stopped"));
    }

    @Override
    public BrixHealth health() {
        return health.get();
    }
}
