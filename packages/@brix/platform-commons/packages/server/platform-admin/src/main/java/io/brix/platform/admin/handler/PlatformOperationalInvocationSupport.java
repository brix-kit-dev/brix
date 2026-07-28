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
            throw new IllegalArgumentException("platform endpoints forbid tenant context");
        });
        String value = invocation.actorId()
            .orElseThrow(() -> new IllegalArgumentException("platform actor is required"));
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException("platform actor is required");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("platform actor is invalid", ex);
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

    static String optionalString(Object body, String field) {
        if (body instanceof Map<?, ?> map) {
            Object value = map.get(field);
            return value == null ? null : String.valueOf(value);
        }
        return null;
    }
}
