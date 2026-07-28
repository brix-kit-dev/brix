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
}
