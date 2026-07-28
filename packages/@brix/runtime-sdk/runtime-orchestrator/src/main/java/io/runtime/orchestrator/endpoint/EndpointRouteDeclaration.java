/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import java.util.Locale;

/**
 * Immutable, handler-free route declaration used by the Host B2 barrier.
 *
 * @param ownerId Runtime module identity
 * @param endpointId descriptor endpoint identity
 * @param method normalized HTTP method
 * @param path normalized route template
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public record EndpointRouteDeclaration(
        String ownerId,
        String endpointId,
        String method,
        String path) {

    /**
     * Creates a route declaration.
     */
    public EndpointRouteDeclaration {
        ownerId = requireText(ownerId, "ownerId");
        endpointId = requireText(endpointId, "endpointId");
        method = requireText(method, "method").toUpperCase(Locale.ROOT);
        path = normalizePath(path);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizePath(String value) {
        String normalized = requireText(value, "path");
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
