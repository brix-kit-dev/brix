/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.endpoint.EndpointRoute;

class OwnerUnitOfWorkEndpointBoundaryTest {

    @Test
    void safeMethodsRunInAReadOnlyUnitOfWork() {
        RecordingPersistence persistence = new RecordingPersistence();
        OwnerUnitOfWorkEndpointBoundary boundary =
            new OwnerUnitOfWorkEndpointBoundary(owner -> Optional.of(persistence));

        assertEquals("read", boundary.invoke(route("GET"), () -> "read"));
        assertEquals("head", boundary.invoke(route("HEAD"), () -> "head"));
        assertEquals(List.of("readOnly", "readOnly"), persistence.modes());
    }

    @Test
    void writeMethodsRunInAReadWriteUnitOfWork() {
        RecordingPersistence persistence = new RecordingPersistence();
        OwnerUnitOfWorkEndpointBoundary boundary =
            new OwnerUnitOfWorkEndpointBoundary(owner -> Optional.of(persistence));

        for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
            boundary.invoke(route(method), () -> method);
        }
        assertEquals(List.of("required", "required", "required", "required"), persistence.modes());
    }

    @Test
    void modulesWithoutOwnerPersistenceAreInvokedDirectly() {
        OwnerUnitOfWorkEndpointBoundary boundary =
            new OwnerUnitOfWorkEndpointBoundary(OwnerPersistenceProvider.none());

        assertEquals("direct", boundary.invoke(route("POST"), () -> "direct"));
    }

    @Test
    void ownerIsResolvedFromTheRouteModule() {
        RecordingPersistence persistence = new RecordingPersistence();
        OwnerUnitOfWorkEndpointBoundary boundary = new OwnerUnitOfWorkEndpointBoundary(
            owner -> "owner-plugin".equals(owner) ? Optional.of(persistence) : Optional.empty());

        boundary.invoke(new EndpointRoute("other-plugin", "other.read", "GET", "/api/other", "authenticated",
            invocation -> null), () -> null);
        assertTrue(persistence.modes().isEmpty());
    }

    private static EndpointRoute route(String method) {
        return new EndpointRoute("owner-plugin", "owner.entry", method, "/api/owner", "authenticated",
            invocation -> null);
    }
}
