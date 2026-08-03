/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;

import io.brix.platform.tenant.endpoint.AcceptFirstOwnerInvitationHandler;
import io.brix.platform.tenant.service.FirstOwnerInvitationService;
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

    private FirstOwnerInvitationService firstOwnerInvitationService;

    @Autowired
    public void setFirstOwnerInvitationService(FirstOwnerInvitationService firstOwnerInvitationService) {
        this.firstOwnerInvitationService = Objects.requireNonNull(
            firstOwnerInvitationService,
            "firstOwnerInvitationService must not be null");
    }

    @Override
    public void configure(PluginBootstrapContext bootstrap) {
        bootstrap.bindEndpoint(
            ENDPOINT_FIRST_OWNER_ACCEPT,
            new AcceptFirstOwnerInvitationHandler(requireFirstOwnerInvitationService()));
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

    private FirstOwnerInvitationService requireFirstOwnerInvitationService() {
        if (firstOwnerInvitationService == null) {
            throw new IllegalStateException("FirstOwnerInvitationService is not available for endpoint binding");
        }
        return firstOwnerInvitationService;
    }
}
