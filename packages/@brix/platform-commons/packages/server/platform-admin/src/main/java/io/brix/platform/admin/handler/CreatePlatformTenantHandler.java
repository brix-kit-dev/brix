/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.brix.platform.admin.dto.CreatePlatformTenantRequest;
import io.brix.platform.admin.dto.PlatformTenantDto;
import io.brix.platform.audit.api.PlatformAuditActions;
import io.brix.platform.tenant.internal.CreatePendingTenantCommand;
import io.brix.platform.tenant.internal.TenantAdministration;
import io.brix.platform.tenant.internal.TenantAdministrationTenant;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.TypedEndpointHandler;

/**
 * Operational handler for creating a pending tenant.
 *
 * <p>The handler does not access tenant repositories or transactions. It
 * forwards the command to the tenant Data Owner internal contract and returns
 * only the typed result exposed by that contract. The creation is then written
 * to the platform audit (multi-tenant T-3).</p>
 *
 * <p>The caller names a key, and the runtime keeps the record with the owner's
 * write: a repeated call gets the first answer before this handler runs. A call
 * that stopped after the owner committed runs it again, the contract gives its
 * result again, and the audit record is written then (root BRX-RS-MANIFEST-018;
 * idempotency design §5.6).</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public final class CreatePlatformTenantHandler
        implements TypedEndpointHandler<CreatePlatformTenantRequest, PlatformTenantDto> {

    private final TenantAdministration tenantAdministration;
    private final PlatformOperationAudit audit;

    public CreatePlatformTenantHandler(OperationalContext context) {
        this.tenantAdministration = context.requireInternalContract(TenantAdministration.class);
        this.audit = new PlatformOperationAudit(context);
    }

    @Override
    public CompletionStage<PlatformTenantDto> handle(EndpointInvocation<CreatePlatformTenantRequest> invocation) {
        Long actorId = PlatformOperationalInvocationSupport.requirePlatformActorId(invocation);
        CreatePlatformTenantRequest request = invocation.body();
        TenantAdministrationTenant tenant = audit.run(invocation, actorId, PlatformAuditActions.TENANT_CREATED,
            new PlatformOperationAudit.Target("TENANT", null, null),
            created -> new PlatformOperationAudit.Target("TENANT", created.id(), created.id()),
            () -> tenantAdministration.createPendingTenant(
                new CreatePendingTenantCommand(request.code(), request.name(), actorId)));
        return CompletableFuture.completedFuture(toDto(tenant));
    }

    private static PlatformTenantDto toDto(TenantAdministrationTenant tenant) {
        return new PlatformTenantDto(
            tenant.id(),
            tenant.code(),
            tenant.name(),
            tenant.status(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    }
}
