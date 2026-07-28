/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Validates the complete handler-free Host route candidate set at B2.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class EndpointRouteValidator {

    private EndpointRouteValidator() {
    }

    /**
     * Rejects normalized route collisions before any Handler factory is invoked.
     *
     * @param declarations complete Host route candidate set
     */
    public static void validate(Collection<EndpointRouteDeclaration> declarations) {
        Objects.requireNonNull(declarations, "declarations must not be null");
        Map<String, EndpointRouteDeclaration> seen = new LinkedHashMap<>();
        for (EndpointRouteDeclaration declaration : declarations) {
            Objects.requireNonNull(declaration, "route declaration must not be null");
            String key = declaration.method() + " " + canonicalTemplate(declaration.path());
            EndpointRouteDeclaration existing = seen.putIfAbsent(key, declaration);
            if (existing != null) {
                throw new EndpointDispatchException("Duplicate Runtime Shell endpoint route: " + key
                    + " from " + existing.ownerId() + "/" + existing.endpointId()
                    + " and " + declaration.ownerId() + "/" + declaration.endpointId());
            }
        }
    }

    private static String canonicalTemplate(String path) {
        String[] parts = "/".equals(path) ? new String[0] : path.substring(1).split("/");
        for (int index = 0; index < parts.length; index++) {
            if (parts[index].startsWith("{") && parts[index].endsWith("}")) {
                parts[index] = "{}";
            }
        }
        return "/" + String.join("/", parts);
    }
}
