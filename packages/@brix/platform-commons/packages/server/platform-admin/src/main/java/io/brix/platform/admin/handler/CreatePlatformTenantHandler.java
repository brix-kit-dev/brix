/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import java.util.Map;

import io.brix.platform.admin.dto.CreatePlatformTenantRequest;
import io.brix.platform.admin.dto.PlatformTenantDto;
import io.brix.platform.tenant.internal.CreatePendingTenantCommand;
import io.brix.platform.tenant.internal.TenantAdministration;
import io.brix.platform.tenant.internal.TenantAdministrationTenant;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;

/**
 * Operational handler for creating a pending tenant.
 *
 * <p>The handler does not access tenant repositories or transactions. It
 * forwards the command to the tenant Data Owner internal contract and returns
 * only the typed result exposed by that contract.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public final class CreatePlatformTenantHandler
        implements EndpointHandler<EndpointInvocation<CreatePlatformTenantRequest>, PlatformTenantDto> {

    private final TenantAdministration tenantAdministration;

    public CreatePlatformTenantHandler(OperationalContext context) {
        this.tenantAdministration = context.requireInternalContract(TenantAdministration.class);
    }

    @Override
    public PlatformTenantDto handle(EndpointInvocation<CreatePlatformTenantRequest> invocation) {
        Long actorId = PlatformOperationalInvocationSupport.requirePlatformActorId(invocation);
        CreatePlatformTenantRequest request = request(invocation.body());
        TenantAdministrationTenant tenant = tenantAdministration.createPendingTenant(
            new CreatePendingTenantCommand(request.code(), request.name(), actorId));
        return toDto(tenant);
    }

    private static CreatePlatformTenantRequest request(Object body) {
        if (body instanceof CreatePlatformTenantRequest typed) {
            return typed;
        }
        if (body instanceof Map<?, ?> map) {
            return new CreatePlatformTenantRequest(
                requiredString(map, "code"),
                requiredString(map, "name"));
        }
        throw new IllegalArgumentException("create tenant request body is required");
    }

    private static String requiredString(Map<?, ?> map, String field) {
        Object value = map.get(field);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return String.valueOf(value);
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
