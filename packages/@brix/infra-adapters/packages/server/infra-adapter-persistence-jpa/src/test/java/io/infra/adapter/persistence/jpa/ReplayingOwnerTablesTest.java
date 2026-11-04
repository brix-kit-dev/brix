/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.orm.jpa.JpaTransactionManager;

import io.runtime.sdk.capability.PersistenceCapabilityException;

/**
 * An owner whose endpoint has its results replayed starts only with its endpoint key table, in the platform's shape
 * (root BRX-RS-MESSAGE-026; standard-link plan point 10a); an owner without such an endpoint needs none.
 */
class ReplayingOwnerTablesTest {

    @TempDir
    Path roots;

    @Test
    void anOwnerThatReplaysAndHasNoTableIsRefused() throws IOException {
        try (URLClassLoader manifests = new URLClassLoader(new URL[] {manifest("required")}, null)) {
            PersistenceCapabilityException refused = assertThrows(PersistenceCapabilityException.class,
                () -> units(manifests).requireMessagingTables("tickets-owner"));

            assertEquals(PersistenceCapabilityException.Code.REPOSITORY_REJECTED, refused.code());
            assertTrue(refused.getCause().getMessage().contains("tickets_endpoint_idempotency does not exist"),
                refused.getCause().getMessage());
        }
    }

    @Test
    void anOwnerThatDoesNotReplayNeedsNoTable() throws IOException {
        try (URLClassLoader manifests = new URLClassLoader(new URL[] {manifest("not-applicable")}, null)) {
            assertDoesNotThrow(() -> units(manifests).requireMessagingTables("tickets-owner"));
        }
    }

    private static OwnerPersistenceUnits units(ClassLoader manifests) {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:replaying-" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        EntityManagerFactory factory = mock(EntityManagerFactory.class);
        return new OwnerPersistenceUnits(factory, new JpaTransactionManager(factory), h2, Duration.ofSeconds(5), true,
            manifests);
    }

    private URL manifest(String idempotency) throws IOException {
        Path manifest = roots.resolve(idempotency + "/META-INF/brix/plugin-manifest.yaml");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, """
            apiVersion: brix.io/v1
            kind: Plugin
            metadata:
              pluginId: tickets-owner
              name: tickets-owner
              version: 3.3.0
              vendor: Brix
              license: Apache-2.0
            runtime:
              compiledAgainst: 3.3.0
              supportedRange: ">=3.3.0 <4.0.0"
            modules:
              - groupId: io.brix
                artifactId: tickets-owner-server
                version: 3.3.0
                moduleKind: plugin-server
            endpoints:
              provides:
                - endpointId: tickets.create.v1
                  transport: http
                  method: POST
                  path: /api/tickets-owner/tickets
                  request:
                    schemaRef: brix://schemas/tickets-owner/create-ticket-request/1.0.0
                  response:
                    schemaRef: brix://schemas/tickets-owner/ticket/1.0.0
                  handlerId: tickets.create.v1
                  authentication:
                    mode: required
                  authorization:
                    permissions: [tickets-owner:ticket:create]
                  tenantContext:
                    mode: required
                  timeout:
                    policyRef: endpoint-default
                  idempotency:
                    mode: %s
            capabilities:
              required:
                - id: io.runtime.sdk.capability.PersistenceCapability
                  version: ">=3.2.0 <4.0.0"
              optional: []
            data:
              storageId: tickets
            """.formatted(idempotency));
        return roots.resolve(idempotency).toUri().toURL();
    }
}
