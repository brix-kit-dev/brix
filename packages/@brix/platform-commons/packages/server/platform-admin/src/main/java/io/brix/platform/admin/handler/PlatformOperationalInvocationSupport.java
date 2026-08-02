/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import java.util.Map;

import io.runtime.sdk.plugin.EndpointInvocation;

final class PlatformOperationalInvocationSupport {

    private PlatformOperationalInvocationSupport() {
    }

    static Long requirePlatformActorId(EndpointInvocation<?> invocation) {
        invocation.tenantId().ifPresent(tenant -> {
            throw PlatformEndpointErrors.forbidden(
                "PLATFORM_TENANT_CONTEXT_FORBIDDEN",
                "platform endpoints forbid tenant context");
        });
        String value = invocation.actorId()
            .orElseThrow(() -> PlatformEndpointErrors.unauthorized(
                "PLATFORM_AUTH_REQUIRED",
                "platform actor is required"));
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw PlatformEndpointErrors.unauthorized(
                    "PLATFORM_AUTH_REQUIRED",
                    "platform actor is required");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw PlatformEndpointErrors.unauthorized(
                "PLATFORM_AUTH_INVALID",
                "platform actor is invalid");
        }
    }

    static Long requirePathLong(EndpointInvocation<?> invocation, String name) {
        String value = invocation.pathVariables().get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(name + " is required");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " is invalid", ex);
        }
    }

    static String platformOperatorRef(Long platformActorId) {
        if (platformActorId == null || platformActorId <= 0) {
            throw new IllegalArgumentException("platform actor is required");
        }
        return "platform-identity:" + platformActorId;
    }

    static String optionalString(Object body, String field) {
        if (body instanceof Map<?, ?> map) {
            Object value = map.get(field);
            return value == null ? null : String.valueOf(value);
        }
        return null;
    }

    static String requiredBearerToken(EndpointInvocation<?> invocation) {
        return invocation.headers().getOrDefault("authorization", java.util.List.of()).stream()
            .filter(value -> value != null && value.regionMatches(true, 0, "Bearer ", 0, 7))
            .map(value -> value.substring(7).trim())
            .filter(value -> !value.isBlank())
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("bearer token is required"));
    }

    static String requiredQueryParameter(EndpointInvocation<?> invocation, String name) {
        return invocation.queryParameters().getOrDefault(name, java.util.List.of()).stream()
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(name + " is required"));
    }
}
