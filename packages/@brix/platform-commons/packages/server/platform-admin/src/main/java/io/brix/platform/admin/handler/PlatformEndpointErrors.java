/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

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

    static EndpointHandlingException authFlow(AuthFlowException cause) {
        return new EndpointHandlingException(
                authStatus(cause.getErrorCode()),
                cause.getErrorCode(),
                safeMessage(cause));
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
        return message == null || message.isBlank() ? "Request cannot be processed" : message;
    }
}
