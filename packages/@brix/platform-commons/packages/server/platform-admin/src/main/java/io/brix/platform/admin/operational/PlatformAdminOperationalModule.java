/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.operational;

import java.util.concurrent.atomic.AtomicReference;

import io.brix.platform.admin.handler.CompletePlatformSetupHandler;
import io.brix.platform.admin.handler.CreateFirstOwnerInvitationHandler;
import io.brix.platform.admin.handler.CreateFirstAdminHandler;
import io.brix.platform.admin.handler.CreatePlatformTenantHandler;
import io.brix.platform.admin.handler.GetBootstrapStatusHandler;
import io.brix.platform.admin.handler.GetFirstOwnerInvitationStatusHandler;
import io.brix.platform.admin.handler.InitPlatformSetupTotpHandler;
import io.brix.platform.admin.handler.OpenBootstrapSessionHandler;
import io.brix.platform.admin.handler.PlatformLoginHandler;
import io.brix.platform.admin.handler.PlatformTotpLoginHandler;
import io.brix.platform.admin.handler.ResendFirstOwnerInvitationHandler;
import io.brix.platform.admin.handler.RevokeFirstOwnerInvitationHandler;
import io.brix.platform.admin.handler.ValidatePlatformSetupHandler;
import io.runtime.orchestrator.operational.OperationalBootstrapContext;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.orchestrator.operational.PlatformOperationalModule;
import io.runtime.sdk.plugin.BrixHealth;

/**
 * ServiceLoader entry point for the platform administration operational module.
 *
 * <p>The module registers descriptor-declared factories only. Handlers are
 * created by the Runtime after dependency resolution and Host route
 * reservation, then call Data Owner internal contracts through the restricted
 * {@link OperationalContext}.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public final class PlatformAdminOperationalModule implements PlatformOperationalModule {

    private final AtomicReference<BrixHealth> health = new AtomicReference<>(BrixHealth.unknown("Not started"));

    @Override
    public void configure(OperationalBootstrapContext bootstrap) {
        bootstrap.bindEndpointHandlerFactory(
            "platform.bootstrap.status",
            GetBootstrapStatusHandler::new);
        bootstrap.bindEndpointHandlerFactory(
            "platform.bootstrap.session",
            OpenBootstrapSessionHandler::new);
        bootstrap.bindEndpointHandlerFactory(
            "platform.bootstrap.create-first-admin",
            CreateFirstAdminHandler::new);
        bootstrap.bindEndpointHandlerFactory(
            "platform.auth.setup.validate",
            ValidatePlatformSetupHandler::new);
        bootstrap.bindEndpointHandlerFactory(
            "platform.auth.setup.totp.init",
            InitPlatformSetupTotpHandler::new);
        bootstrap.bindEndpointHandlerFactory(
            "platform.auth.setup.complete",
            CompletePlatformSetupHandler::new);
        bootstrap.bindEndpointHandlerFactory(
            "platform.auth.login",
            PlatformLoginHandler::new);
        bootstrap.bindEndpointHandlerFactory(
            "platform.auth.login.totp",
            PlatformTotpLoginHandler::new);
        bootstrap.bindEndpointHandlerFactory(
            "platform.tenants.create",
            CreatePlatformTenantHandler::new);
        bootstrap.bindEndpointHandlerFactory(
            "platform.tenants.first-owner-invitations.create",
            CreateFirstOwnerInvitationHandler::new);
        bootstrap.bindEndpointHandlerFactory(
            "platform.tenants.first-owner-invitations.current",
            GetFirstOwnerInvitationStatusHandler::new);
        bootstrap.bindEndpointHandlerFactory(
            "platform.tenants.first-owner-invitations.resend",
            ResendFirstOwnerInvitationHandler::new);
        bootstrap.bindEndpointHandlerFactory(
            "platform.tenants.first-owner-invitations.revoke",
            RevokeFirstOwnerInvitationHandler::new);
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
