/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.tenant.endpoint;

import io.brix.platform.tenant.internal.TenantAdministrationException;
import io.runtime.sdk.plugin.EndpointHandlingException;

/** Stable Runtime Entry error mapping for platform-tenant handlers. */
final class PlatformTenantEndpointErrors {

    private PlatformTenantEndpointErrors() {
    }

    static EndpointHandlingException unauthorized(String errorCode, String message) {
        return new EndpointHandlingException(401, errorCode, safeMessage(message));
    }

    static EndpointHandlingException forbidden(String errorCode, String message) {
        return new EndpointHandlingException(403, errorCode, safeMessage(message));
    }

    static EndpointHandlingException badRequest(String errorCode, String message) {
        return new EndpointHandlingException(400, errorCode, safeMessage(message));
    }

    static EndpointHandlingException serviceUnavailable(String errorCode, String message) {
        return new EndpointHandlingException(503, errorCode, safeMessage(message));
    }

    static EndpointHandlingException tenantAdministration(TenantAdministrationException cause) {
        return new EndpointHandlingException(
            tenantAdministrationStatus(cause.code()),
            cause.code(),
            safeMessage(cause));
    }

    private static int tenantAdministrationStatus(String code) {
        if (code == null) {
            return 400;
        }
        return switch (code) {
            case "TENANT_NOT_FOUND",
                 "FIRST_OWNER_IDENTITY_NOT_FOUND",
                 "FIRST_OWNER_INVITATION_INVALID",
                 "FIRST_OWNER_INVITATION_MISSING" -> 404;
            case "FIRST_OWNER_INVITATION_EXISTS",
                 "FIRST_OWNER_INVITEE_IDENTITY_NOT_ELIGIBLE",
                 "TENANT_NOT_PENDING_ACTIVATION",
                 "FIRST_OWNER_INVITATION_NOT_REVOKABLE",
                 "FIRST_OWNER_INVITATION_NOT_ACCEPTABLE",
                 "FIRST_OWNER_INVITATION_EMAIL_MISMATCH",
                 "FIRST_OWNER_ALREADY_EXISTS" -> 409;
            case "NOTIFICATION_PROVIDER_MISSING",
                 "FIRST_OWNER_INVITE_BASE_URL_NOT_CONFIGURED",
                 "FIRST_OWNER_INVITE_BASE_URL_INVALID",
                 "FIRST_OWNER_SETUP_BASE_URL_NOT_CONFIGURED",
                 "FIRST_OWNER_SETUP_BASE_URL_INVALID" -> 503;
            default -> 400;
        };
    }

    private static String safeMessage(RuntimeException cause) {
        return safeMessage(cause.getMessage());
    }

    private static String safeMessage(String message) {
        return message == null || message.isBlank() ? "Request cannot be processed" : message;
    }
}
