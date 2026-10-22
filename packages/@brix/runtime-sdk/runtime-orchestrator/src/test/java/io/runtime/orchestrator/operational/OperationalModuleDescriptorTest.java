/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.operational;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.runtime.orchestrator.endpoint.EndpointAccessPolicy;

class OperationalModuleDescriptorTest {

    @Test
    void bundledSchemaMatchesDevtoolsAuthoringSchema() throws Exception {
        assertArrayEquals(
            Files.readAllBytes(Path.of("../../platform-devtools/schemas/platform-operational.schema.json")),
            Files.readAllBytes(Path.of(
                "src/main/resources/META-INF/schemas/platform-operational.schema.json")));
    }

    @Test
    void strictLoaderAcceptsTheServiceLoaderFixture() {
        OperationalModuleDescriptor descriptor = new OperationalModuleDescriptorLoader().load(
            getClass().getClassLoader().getResource(
                OperationalModuleDescriptorLoader.DESCRIPTOR_RESOURCE));

        assertEquals("runtime-test-operational", descriptor.identity().moduleId());
        assertEquals(1, descriptor.endpoints().size());
        EndpointAccessPolicy policy = descriptor.endpoints().get("runtime.status.v1").accessPolicy();
        assertEquals(EndpointAccessPolicy.TokenPolicy.PLATFORM_TOKEN, policy.tokenPolicy());
        assertEquals(java.util.Set.of("platform:runtime:read"), policy.permissions());
        assertEquals(EndpointAccessPolicy.TenantContextMode.FORBIDDEN, policy.tenantContext());
    }

    @Test
    void loaderRejectsAuthenticatedEndpointWithoutPlatformTokenPolicy(@TempDir Path directory) throws Exception {
        OperationalRuntimeException failure = assertThrows(
            OperationalRuntimeException.class,
            () -> load(directory, """
                  authentication:
                    mode: required
                  authorization:
                    permissions:
                      - platform:runtime:read
                """));

        assertEquals("operational.descriptor.invalid", failure.diagnosticCode());
        assertTrue(failure.getMessage().contains("platform-token"));
    }

    @Test
    void loaderRejectsAuthenticatedEndpointWhosePermissionHidesInPolicyRef(@TempDir Path directory)
            throws Exception {
        OperationalRuntimeException failure = assertThrows(
            OperationalRuntimeException.class,
            () -> load(directory, """
                  authentication:
                    mode: required
                    policyRef: platform-token
                  authorization:
                    policyRef: platform:runtime:read
                """));

        assertEquals("operational.descriptor.invalid", failure.diagnosticCode());
        assertTrue(failure.getMessage().contains("authorization.permissions"));
    }

    @Test
    void loaderRejectsUnknownAuthenticationMode(@TempDir Path directory) throws Exception {
        OperationalRuntimeException failure = assertThrows(
            OperationalRuntimeException.class,
            () -> load(directory, """
                  authentication:
                    mode: optional
                  authorization:
                    policyRef: runtime-status
                """));

        assertEquals("operational.descriptor.invalid", failure.diagnosticCode());
    }

    @Test
    void loaderKeepsNamedPolicyOfAnonymousEndpointOutOfPermissions(@TempDir Path directory) throws Exception {
        OperationalModuleDescriptor descriptor = load(directory, """
              authentication:
                mode: anonymous
              authorization:
                policyRef: runtime-status
            """);

        EndpointAccessPolicy policy = descriptor.endpoints().get("runtime.status.v1").accessPolicy();
        assertTrue(policy.permissions().isEmpty());
        assertEquals("runtime-status", policy.authorizationPolicyRef());
    }

    private static OperationalModuleDescriptor load(Path directory, String accessSections) throws Exception {
        Path descriptor = directory.resolve("platform-operational.yaml");
        Files.writeString(descriptor, """
            apiVersion: brix.io/platform-operational/v1
            kind: PlatformOperationalModule
            moduleKind: platform-operational
            metadata:
              id: runtime-test-operational
              version: 3.2.0
              owner: runtime-tests
            runtime:
              supportedRange: ">=3.2.0 <4.0.0"
            internalContracts:
              provides: []
              requires: []
            privileges:
              allowlist: []
            endpoints:
              provides:
                - endpointId: runtime.status.v1
                  transport: http
                  method: GET
                  path: /api/platform/runtime/status
                  request:
                    schemaRef: brix://schemas/empty/1.0.0
                  response:
                    schemaRef: brix://schemas/runtime/status/1.0.0
                  handlerId: runtime.status
            """ + accessSections.stripTrailing().indent(4) + """
                  tenantContext:
                    mode: forbidden
                  timeout:
                    policyRef: interactive-read
                  idempotency:
                    mode: not-applicable
            tasks:
              provides: []
            """);
        return new OperationalModuleDescriptorLoader().load(descriptor.toUri().toURL());
    }

    @Test
    void descriptorRejectsUnapprovedInternalContractPrivilege() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalModuleDescriptor(
                new OperationalModuleIdentity("runtime-test", "3.2.0", "runtime"),
                ">=3.2.0 <4.0.0",
                java.util.List.of(),
                java.util.List.of(new OperationalModuleDescriptor.RequiredInternalContract(
                    "test.contract",
                    Runnable.class.getName(),
                    ">=1.0.0 <2.0.0",
                    true,
                    "contract.test")),
                java.util.Set.of(),
                java.util.Map.of(),
                java.util.Map.of()));
    }
}
