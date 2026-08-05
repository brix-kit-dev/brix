/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import io.brix.platform.tenant.internal.TenantAdministrationException;
import io.runtime.sdk.capability.AuthFlowCapability.AuthFlowException;
import io.runtime.sdk.plugin.EndpointHandlingException;

/**
 * Stable Runtime Entry error mapping for platform-admin handlers.
 */
final class PlatformEndpointErrors {

    private PlatformEndpointErrors() {
    }

    static EndpointHandlingException badRequest(String errorCode, RuntimeException cause) {
        return new EndpointHandlingException(400, errorCode, safeMessage(cause));
    }

    static EndpointHandlingException conflict(String errorCode, RuntimeException cause) {
        return new EndpointHandlingException(409, errorCode, safeMessage(cause));
    }

    static EndpointHandlingException unauthorized(String errorCode, String message) {
        return new EndpointHandlingException(401, errorCode, safeMessage(message));
    }

    static EndpointHandlingException forbidden(String errorCode, String message) {
        return new EndpointHandlingException(403, errorCode, safeMessage(message));
    }

    static EndpointHandlingException authFlow(AuthFlowException cause) {
        return new EndpointHandlingException(
                authStatus(cause.getErrorCode()),
                cause.getErrorCode(),
                safeMessage(cause));
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
                 "TENANT_NOT_PENDING_ACTIVATION",
                 "FIRST_OWNER_INVITATION_NOT_REVOKABLE",
                 "FIRST_OWNER_INVITATION_NOT_ACCEPTABLE",
                 "FIRST_OWNER_INVITATION_EMAIL_MISMATCH",
                 "FIRST_OWNER_INVITEE_IDENTITY_NOT_ELIGIBLE",
                 "FIRST_OWNER_INVITEE_IDENTITY_NOT_ACTIVE",
                 "FIRST_OWNER_ALREADY_EXISTS" -> 409;
            case "NOTIFICATION_PROVIDER_MISSING",
                 "FIRST_OWNER_INVITE_BASE_URL_NOT_CONFIGURED",
                 "FIRST_OWNER_INVITE_BASE_URL_INVALID",
                 "FIRST_OWNER_SETUP_BASE_URL_NOT_CONFIGURED",
                 "FIRST_OWNER_SETUP_BASE_URL_INVALID" -> 503;
            default -> 400;
        };
    }

    private static int authStatus(String code) {
        if (code == null) {
            return 401;
        }
        return switch (code) {
            case AuthFlowException.CODE_INVALID_CREDENTIALS,
                 AuthFlowException.CODE_INVALID_REFRESH_TOKEN,
                 AuthFlowException.CODE_IDENTITY_NOT_FOUND,
                 AuthFlowException.CODE_MFA_REQUIRED,
                 AuthFlowException.CODE_CONTEXT_SELECTION_TICKET_INVALID -> 401;
            case AuthFlowException.CODE_ACCOUNT_DISABLED,
                 AuthFlowException.CODE_TENANT_ACCESS_DENIED,
                 AuthFlowException.CODE_NO_TENANT_ASSOCIATION -> 403;
            case AuthFlowException.CODE_PENDING_SETUP,
                 AuthFlowException.CODE_MFA_SETUP_REQUIRED -> 422;
            case AuthFlowException.CODE_ACCOUNT_LOCKED -> 423;
            case AuthFlowException.CODE_OLD_PASSWORD_MISMATCH,
                 AuthFlowException.CODE_PASSWORD_POLICY_VIOLATION -> 400;
            case AuthFlowException.CODE_CAPABILITY_UNAVAILABLE -> 503;
            default -> 401;
        };
    }

    private static String safeMessage(RuntimeException cause) {
        String message = cause.getMessage();
        return safeMessage(message);
    }

    private static String safeMessage(String message) {
        return message == null || message.isBlank() ? "Request cannot be processed" : message;
    }
}
