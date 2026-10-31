/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.brix.platform.tenant.endpoint.AcceptFirstOwnerInvitationHandler;
import io.brix.platform.tenant.endpoint.TenantSessionHandler;
import io.brix.platform.tenant.event.FirstOwnerInviteeSetupCompletedHandler;
import io.brix.platform.tenant.event.TenantFirstOwnerAcceptedProjectionHandler;
import io.brix.platform.tenant.internal.TenantAdministration;
import io.brix.platform.tenant.query.TenantReferenceQueryHandler;
import io.brix.platform.tenant.repository.PlatformTenantFirstOwnerProjectionRepository;
import io.brix.platform.tenant.repository.TenantMemberRepository;
import io.brix.platform.tenant.repository.TenantPrincipalRepository;
import io.brix.platform.tenant.service.JpaFirstOwnerProjectionWriter;
import io.runtime.sdk.capability.PersistenceCapability;
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
    public static final String ENDPOINT_TENANT_SESSION = "platform-tenant.session.v1";
    public static final String HANDLER_FIRST_OWNER_INVITEE_SETUP = "first-owner-invitee-setup.v1";
    public static final String HANDLER_FIRST_OWNER_PROJECTION = "tenant-first-owner-projection.v1";
    /** The handler of the typed query {@code platform-tenant.tenant-reference} (multi-tenant T-7). */
    public static final String QUERY_TENANT_REFERENCE = "platform-tenant.tenant-reference.v1";

    private final AtomicReference<BrixHealth> health = new AtomicReference<>(BrixHealth.unknown("Not started"));

    @Override
    public void configure(PluginBootstrapContext bootstrap) {
        // Root BRX-RS-MANIFEST-019: each handler gets what it needs from the context its factory receives.
        bootstrap.bindEndpointHandlerFactory(
            ENDPOINT_FIRST_OWNER_ACCEPT,
            context -> new AcceptFirstOwnerInvitationHandler(tenantAdministrationOf(context)));
        bootstrap.bindEndpointHandlerFactory(ENDPOINT_TENANT_SESSION, context -> new TenantSessionHandler());
        bootstrap.bindEventHandlerFactory(
            HANDLER_FIRST_OWNER_INVITEE_SETUP,
            context -> new FirstOwnerInviteeSetupCompletedHandler(tenantAdministrationOf(context)));
        bootstrap.bindEventHandlerFactory(
            HANDLER_FIRST_OWNER_PROJECTION,
            context -> new TenantFirstOwnerAcceptedProjectionHandler(new JpaFirstOwnerProjectionWriter(
                context.require(PersistenceCapability.class).repository(PlatformTenantFirstOwnerProjectionRepository.class))));
        bootstrap.bindQueryHandlerFactory(QUERY_TENANT_REFERENCE, context -> {
            PersistenceCapability persistence = context.require(PersistenceCapability.class);
            return new TenantReferenceQueryHandler(persistence.repository(TenantMemberRepository.class),
                persistence.repository(TenantPrincipalRepository.class));
        });
    }

    @Override
    public void onStart(PluginContext context) {
        health.set(BrixHealth.up());
    }

    @Override
    public void onStop() {
        health.set(BrixHealth.down("Stopped"));
    }

    /** The internal contract, looked up through the handler's own context when the handler needs it. */
    private static Supplier<TenantAdministration> tenantAdministrationOf(PluginContext context) {
        return () -> context.find(TenantAdministration.class)
            .orElseThrow(() -> new IllegalStateException(
                "TenantAdministration internal contract is not available for the platform-tenant handlers"));
    }

    @Override
    public BrixHealth health() {
        return health.get();
    }
}
