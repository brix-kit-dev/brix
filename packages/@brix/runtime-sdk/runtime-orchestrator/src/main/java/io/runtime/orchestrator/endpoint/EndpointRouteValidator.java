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
 * <p>Root BRX-RS-MANIFEST-023: every route is a well-formed {@link EndpointPathTemplate}, no two routes of one method
 * share an {@linkplain EndpointPathTemplate#identity() identity}, and no route takes a reserved prefix. Routes that
 * could tie for a request always share an identity, so this also rules out same-priority matches.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class EndpointRouteValidator {

    /** The identity protocol surface's prefixes, which no endpoint may take (root BRX-RS-SECURITY-011). */
    private static final String[] IDENTITY_PREFIXES = {"oauth2", ".well-known", "auth", "bff"};

    private EndpointRouteValidator() {
    }

    /**
     * Rejects malformed templates, reserved prefixes and normalized route collisions before any Handler factory is
     * invoked.
     *
     * @param declarations complete Host route candidate set
     */
    public static void validate(Collection<EndpointRouteDeclaration> declarations) {
        Objects.requireNonNull(declarations, "declarations must not be null");
        Map<String, EndpointRouteDeclaration> seen = new LinkedHashMap<>();
        for (EndpointRouteDeclaration declaration : declarations) {
            Objects.requireNonNull(declaration, "route declaration must not be null");
            EndpointPathTemplate template = template(declaration);
            for (String prefix : IDENTITY_PREFIXES) {
                if (template.startsWith(prefix)) {
                    throw new EndpointDispatchException("Runtime Shell endpoint route " + describe(declaration)
                        + " takes /" + prefix + "/**, which belongs to the identity protocol surface");
                }
            }
            String key = declaration.method() + " " + template.identity();
            EndpointRouteDeclaration existing = seen.putIfAbsent(key, declaration);
            if (existing != null) {
                throw new EndpointDispatchException("Duplicate Runtime Shell endpoint route: " + key
                    + " from " + existing.ownerId() + "/" + existing.endpointId()
                    + " and " + declaration.ownerId() + "/" + declaration.endpointId());
            }
        }
    }

    /**
     * Rejects plugin routes under {@code /api/platform/**}, which only platform operational modules publish (root
     * BRX-RS-LAYER-017).
     *
     * @param declarations the plugins' route candidates
     */
    public static void validatePluginRoutes(Collection<EndpointRouteDeclaration> declarations) {
        Objects.requireNonNull(declarations, "declarations must not be null");
        for (EndpointRouteDeclaration declaration : declarations) {
            if (template(declaration).startsWith("api", "platform")) {
                throw new EndpointDispatchException("Plugin endpoint route " + describe(declaration)
                    + " takes /api/platform/**, which only platform operational modules publish");
            }
        }
    }

    private static EndpointPathTemplate template(EndpointRouteDeclaration declaration) {
        try {
            return EndpointPathTemplate.parse(declaration.path());
        } catch (IllegalArgumentException e) {
            throw new EndpointDispatchException("Invalid Runtime Shell endpoint route " + describe(declaration)
                + ": " + e.getMessage(), e);
        }
    }

    private static String describe(EndpointRouteDeclaration declaration) {
        return declaration.method() + " " + declaration.path() + " of " + declaration.ownerId() + "/"
            + declaration.endpointId();
    }
}
