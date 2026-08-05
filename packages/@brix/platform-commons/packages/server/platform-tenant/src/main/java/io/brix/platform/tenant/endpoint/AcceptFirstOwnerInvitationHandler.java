/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.tenant.endpoint;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.brix.platform.tenant.internal.AcceptFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.TenantAdministration;
import io.brix.platform.tenant.internal.TenantAdministrationException;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;

/**
 * Tenant Data Owner Runtime Entry for FIRST_OWNER invitation acceptance.
 */
public final class AcceptFirstOwnerInvitationHandler
        implements EndpointHandler<EndpointInvocation<AcceptFirstOwnerInvitationRequest>, FirstOwnerAcceptanceDto> {

    static final String FIRST_OWNER_ACCEPT_ACTION = "first_owner_accept";
    private static final String TOKEN_ROLE_HEADER = "x-auth-token-role";
    private static final String TOKEN_TYPE_HEADER = "x-auth-token-type";
    private static final String ALLOWED_ACTION_HEADER = "x-auth-allowed-action";

    private final Supplier<TenantAdministration> tenantAdministrationSupplier;

    public AcceptFirstOwnerInvitationHandler(Supplier<TenantAdministration> tenantAdministrationSupplier) {
        this.tenantAdministrationSupplier = Objects.requireNonNull(
            tenantAdministrationSupplier,
            "tenantAdministrationSupplier must not be null");
    }

    @Override
    public FirstOwnerAcceptanceDto handle(EndpointInvocation<AcceptFirstOwnerInvitationRequest> invocation) {
        Long identityId = requireVerifiedActorIdentity(invocation);
        AcceptFirstOwnerInvitationRequest request = request(invocation.body());
        try {
            return FirstOwnerAcceptanceDto.from(tenantAdministration().acceptFirstOwnerInvitation(
                new AcceptFirstOwnerInvitationCommand(request.invitationToken(), identityId)));
        } catch (TenantAdministrationException ex) {
            throw PlatformTenantEndpointErrors.tenantAdministration(ex);
        }
    }

    private TenantAdministration tenantAdministration() {
        TenantAdministration tenantAdministration = tenantAdministrationSupplier.get();
        if (tenantAdministration == null) {
            throw PlatformTenantEndpointErrors.serviceUnavailable(
                "TENANT_ADMINISTRATION_UNAVAILABLE",
                "tenant administration is not available");
        }
        return tenantAdministration;
    }

    private static Long requireVerifiedActorIdentity(EndpointInvocation<?> invocation) {
        invocation.tenantId().ifPresent(tenant -> {
            throw PlatformTenantEndpointErrors.forbidden(
                "FIRST_OWNER_TENANT_CONTEXT_FORBIDDEN",
                "FIRST_OWNER acceptance forbids caller tenant context");
        });
        Long identityId = invocation.actorId()
            .map(AcceptFirstOwnerInvitationHandler::parseIdentityId)
            .orElseThrow(() -> PlatformTenantEndpointErrors.unauthorized(
                "FIRST_OWNER_AUTH_REQUIRED",
                "verified actor identity is required"));
        String tokenType = firstHeader(invocation, TOKEN_TYPE_HEADER);
        String tokenRole = firstHeader(invocation, TOKEN_ROLE_HEADER);
        boolean actorAccess = "access".equals(tokenType) && "actor".equals(tokenRole);
        boolean preLinkIdentity = "identity".equals(tokenType)
            && allowedActions(invocation).contains(FIRST_OWNER_ACCEPT_ACTION);
        if (!actorAccess && !preLinkIdentity) {
            throw PlatformTenantEndpointErrors.forbidden(
                "FIRST_OWNER_ACTOR_IDENTITY_REQUIRED",
                "FIRST_OWNER acceptance requires an actor-login identity");
        }
        return identityId;
    }

    private static Long parseIdentityId(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw PlatformTenantEndpointErrors.unauthorized(
                    "FIRST_OWNER_AUTH_REQUIRED",
                    "verified actor identity is required");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw PlatformTenantEndpointErrors.unauthorized(
                "FIRST_OWNER_AUTH_INVALID",
                "verified actor identity is invalid");
        }
    }

    private static AcceptFirstOwnerInvitationRequest request(Object body) {
        if (body instanceof AcceptFirstOwnerInvitationRequest typed) {
            return typed;
        }
        if (body instanceof Map<?, ?> map) {
            Object token = map.get("invitationToken");
            return requestFromToken(token);
        }
        throw PlatformTenantEndpointErrors.badRequest(
            "FIRST_OWNER_REQUEST_REQUIRED",
            "first owner acceptance request body is required");
    }

    private static AcceptFirstOwnerInvitationRequest requestFromToken(Object token) {
        try {
            return new AcceptFirstOwnerInvitationRequest(token == null ? null : String.valueOf(token));
        } catch (IllegalArgumentException ex) {
            throw PlatformTenantEndpointErrors.badRequest(
                "FIRST_OWNER_REQUEST_INVALID",
                ex.getMessage());
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstHeader(EndpointInvocation<?> invocation, String headerName) {
        return invocation.headers().getOrDefault(headerName, java.util.List.of())
            .stream()
            .findFirst()
            .map(AcceptFirstOwnerInvitationHandler::normalize)
            .orElse("");
    }

    private static Set<String> allowedActions(EndpointInvocation<?> invocation) {
        return invocation.headers().getOrDefault(ALLOWED_ACTION_HEADER, java.util.List.of())
            .stream()
            .map(AcceptFirstOwnerInvitationHandler::normalize)
            .collect(Collectors.toUnmodifiableSet());
    }
}
