/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class EndpointRouteValidatorTest {

    @Test
    void acceptsDeterministicCrossKindCandidateSet() {
        assertDoesNotThrow(() -> EndpointRouteValidator.validate(List.of(
            new EndpointRouteDeclaration("plugin-a", "plugin.read", "GET", "/api/plugins/{id}"),
            new EndpointRouteDeclaration(
                "platform-admin",
                "platform.status",
                "GET",
                "/api/platform/status"))));
    }

    @Test
    void rejectsEquivalentTemplatesBeforeHandlersExist() {
        List<EndpointRouteDeclaration> declarations = List.of(
            new EndpointRouteDeclaration("plugin-a", "plugin.read", "GET", "/api/items/{id}"),
            new EndpointRouteDeclaration("platform-admin", "platform.read", "get", "/api/items/{name}/"));

        assertThrows(EndpointDispatchException.class, () -> EndpointRouteValidator.validate(declarations));
    }

    @Test
    void rejectsRoutesThatDifferOnlyInSlashesCaseOrEncoding() {
        // Root BRX-RS-MANIFEST-023: normalization-equivalent forms are one route.
        for (String path : List.of("/api//items/{id}", "/API/Items/{key}", "/api/%69tems/{id}")) {
            List<EndpointRouteDeclaration> declarations = List.of(
                new EndpointRouteDeclaration("plugin-a", "plugin.read", "GET", "/api/items/{id}"),
                new EndpointRouteDeclaration("plugin-b", "plugin.other", "GET", path));
            assertThrows(EndpointDispatchException.class, () -> EndpointRouteValidator.validate(declarations), path);
        }
    }

    @Test
    void acceptsOverlappingTemplatesWhereALiteralDecides() {
        // The app-tenant pair: GET /api/v1/tenants/by-code/members goes to by-code.
        assertDoesNotThrow(() -> EndpointRouteValidator.validate(List.of(
            new EndpointRouteDeclaration("app-tenant", "tenant.by-code", "GET", "/api/v1/tenants/by-code/{code}"),
            new EndpointRouteDeclaration("app-tenant", "tenant.members", "GET", "/api/v1/tenants/{tenantId}/members"))));
    }

    @Test
    void rejectsMalformedTemplates() {
        assertThrows(EndpointDispatchException.class, () -> EndpointRouteValidator.validate(List.of(
            new EndpointRouteDeclaration("plugin-a", "plugin.read", "GET", "/api/items/{id}.json"))));
    }

    @Test
    void rejectsTheIdentityProtocolSurfacesPrefixes() {
        // Root BRX-RS-SECURITY-011: neither plugins nor operational modules take these.
        for (String path : List.of("/oauth2/token", "/.well-known/jwks.json", "/Auth/login", "/bff/session")) {
            assertThrows(EndpointDispatchException.class, () -> EndpointRouteValidator.validate(List.of(
                new EndpointRouteDeclaration("plugin-a", "plugin.read", "GET", path))), path);
        }
    }

    @Test
    void rejectsPluginRoutesUnderTheOperationalNamespace() {
        // Root BRX-RS-LAYER-017: /api/platform/** belongs to platform operational modules.
        assertThrows(EndpointDispatchException.class, () -> EndpointRouteValidator.validatePluginRoutes(List.of(
            new EndpointRouteDeclaration("plugin-a", "plugin.read", "GET", "/api//Platform/admins"))));
        assertDoesNotThrow(() -> EndpointRouteValidator.validatePluginRoutes(List.of(
            new EndpointRouteDeclaration("plugin-a", "plugin.read", "GET", "/api/platform-tools/admins"))));
    }
}
