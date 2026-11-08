/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.brix.platform.admin.dto.CreateFirstAdminRequest;
import io.brix.platform.admin.dto.CreatePlatformAdminResponse;
import io.brix.platform.identity.internal.CreateFirstPlatformAdminCommand;
import io.brix.platform.identity.internal.PlatformBootstrapAdministration;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.TypedEndpointHandler;

/**
 * Handler for creating the first formal platform super administrator.
 *
 * <p>Its caller is the BOOTSTRAP_SETUP session the runtime verified before the handler (standard-link plan point
 * 10b-7a): the handler names that session to the identity owner, which spends it, and never sees its token.</p>
 *
 * <p>The caller names a key, and the runtime keeps the record with the owner's write, under the session: a repeated
 * call gets the first answer before this handler runs. A call that stopped after the owner committed runs it again,
 * and the contract gives its result again without writing (root BRX-RS-MANIFEST-018; idempotency design §5.6;
 * standard-link plan point 10b-7, D95).</p>
 */
public final class CreateFirstAdminHandler
        implements TypedEndpointHandler<CreateFirstAdminRequest, CreatePlatformAdminResponse> {

    private final PlatformBootstrapAdministration bootstrapAdministration;

    public CreateFirstAdminHandler(OperationalContext context) {
        this.bootstrapAdministration = context.requireInternalContract(PlatformBootstrapAdministration.class);
    }

    @Override
    public CompletionStage<CreatePlatformAdminResponse> handle(EndpointInvocation<CreateFirstAdminRequest> invocation) {
        invocation.tenantId().ifPresent(tenant -> {
            throw PlatformEndpointErrors.badRequest(
                    "PLATFORM_BOOTSTRAP_TENANT_CONTEXT_FORBIDDEN",
                    new IllegalArgumentException("bootstrap endpoints forbid tenant context"));
        });
        String session = invocation.actorId()
                .orElseThrow(() -> PlatformEndpointErrors.unauthorized(
                        "PLATFORM_BOOTSTRAP_SESSION_REQUIRED",
                        "a verified bootstrap session is required"));
        CreateFirstAdminRequest request = invocation.body();
        try {
            var created = bootstrapAdministration.createFirstAdmin(new CreateFirstPlatformAdminCommand(
                    session,
                    request.username(),
                    request.email(),
                    request.notes()));
            return CompletableFuture.completedFuture(
                    new CreatePlatformAdminResponse(created.id(), created.identityId(), created.setupLinkSent()));
        } catch (IllegalArgumentException e) {
            throw PlatformEndpointErrors.badRequest("PLATFORM_BOOTSTRAP_INVALID_REQUEST", e);
        } catch (IllegalStateException e) {
            throw PlatformEndpointErrors.conflict("PLATFORM_BOOTSTRAP_NOT_READY", e);
        }
    }
}
