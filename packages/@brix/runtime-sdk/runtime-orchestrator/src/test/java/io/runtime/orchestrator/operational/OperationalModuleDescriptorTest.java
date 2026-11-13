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
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.runtime.orchestrator.endpoint.EndpointAccessPolicy;
import io.runtime.orchestrator.endpoint.EndpointContract;

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
    void theBootstrapSessionIsAcceptedByTheBootstrapEntriesOnly(@TempDir Path directory) throws Exception {
        // super-admin 4.0.1 BRX-SA-BOOT-004; standard-link plan point 10b-7a (D97).
        EndpointAccessPolicy bootstrap = EndpointAccessPolicy.fromDeclaration(
            java.util.Map.of("mode", "required", "policyRef", "bootstrap-setup"),
            java.util.Map.of("permissions", java.util.List.of("bootstrap:create-first-admin")),
            java.util.Map.of("mode", "forbidden"));
        OperationalModuleDescriptor.EndpointDeclaration entry = new OperationalModuleDescriptor.EndpointDeclaration(
            "platform.bootstrap.create.v1", "POST", "/api/platform/bootstrap/create", "platform.bootstrap.create",
            bootstrap);
        assertEquals(EndpointAccessPolicy.TokenPolicy.BOOTSTRAP_SETUP, entry.accessPolicy().tokenPolicy());

        IllegalArgumentException elsewhere = assertThrows(IllegalArgumentException.class,
            () -> new OperationalModuleDescriptor.EndpointDeclaration(
                "platform.tenants.create.v1", "POST", "/api/platform/tenants", "platform.tenants.create", bootstrap));
        assertTrue(elsewhere.getMessage().contains("/api/platform/bootstrap/"), elsewhere.getMessage());
        OperationalRuntimeException loaded = assertThrows(OperationalRuntimeException.class,
            () -> load(directory, """
                  authentication:
                    mode: required
                    policyRef: bootstrap-setup
                  authorization:
                    permissions:
                      - bootstrap:create-first-admin
                """));
        assertTrue(loaded.getMessage().contains("/api/platform/bootstrap/"), loaded.getMessage());
    }

    @Test
    void loaderRejectsTheRetiredBootstrapModeAndNamesItsReplacement(@TempDir Path directory) throws Exception {
        OperationalRuntimeException failure = assertThrows(
            OperationalRuntimeException.class,
            () -> load(directory, """
                  authentication:
                    mode: bootstrap-setup
                  authorization:
                    policyRef: bootstrap:create-first-admin
                """));

        assertEquals("operational.descriptor.invalid", failure.diagnosticCode());
        assertTrue(failure.getMessage().contains("policyRef 'bootstrap-setup'"), failure.getMessage());
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
        return load(directory, accessSections, "not-applicable");
    }

    private static OperationalModuleDescriptor load(Path directory, String accessSections, String idempotency)
            throws Exception {
        return load(directory, accessSections, idempotency, "interactive-read");
    }

    private static OperationalModuleDescriptor load(Path directory, String accessSections, String idempotency,
            String timeout) throws Exception {
        return load(directory, accessSections, idempotency, timeout, "");
    }

    private static OperationalModuleDescriptor load(Path directory, String accessSections, String idempotency,
            String timeout, String responseStatus) throws Exception {
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
            """ + responseStatus + """
                  handlerId: runtime.status
            """ + accessSections.stripTrailing().indent(4) + """
                  tenantContext:
                    mode: forbidden
                  timeout:
                    policyRef: TIMEOUT
                  idempotency:
                    mode: IDEMPOTENCY
            tasks:
              provides: []
            """.replace("IDEMPOTENCY", idempotency).replace("TIMEOUT", timeout));
        return new OperationalModuleDescriptorLoader().load(descriptor.toUri().toURL());
    }

    @Test
    void loaderRejectsOptionalIdempotency(@TempDir Path directory) throws Exception {
        // Root BRX-RS-MANIFEST-018; idempotency design D72.
        OperationalRuntimeException failure = assertThrows(OperationalRuntimeException.class,
            () -> load(directory, ANONYMOUS_STATUS, "optional"));

        assertEquals("operational.descriptor.invalid", failure.diagnosticCode());
        assertTrue(failure.getMessage().contains("optional is not provided"), failure.getMessage());
    }

    @Test
    void requiredIdempotencyNamesTheWriteContract(@TempDir Path directory) throws Exception {
        // Idempotency design D79: the record goes with the endpoint's one write.
        OperationalRuntimeException failure = assertThrows(OperationalRuntimeException.class,
            () -> load(directory, PLATFORM_ACCESS, "required"));

        assertEquals("operational.descriptor.invalid", failure.diagnosticCode());
        assertTrue(failure.getMessage().contains("idempotency.writeContract"), failure.getMessage());
    }

    @Test
    void loaderReadsTheWriteContractAndTheDefaultRetention(@TempDir Path directory) throws Exception {
        EndpointContract.Replay replay = loadRequired(directory, "POST", PLATFORM_ACCESS, "").endpoints()
            .get("runtime.status.v1").contract().replay();

        assertEquals("runtime.status", replay.handlerId());
        assertEquals("test.write", replay.writeContract());
        assertEquals(Duration.ofHours(24), replay.retention());
        assertTrue(replay.throughContract());
    }

    @Test
    void aRetentionPolicyTheRuntimeDoesNotKnowIsRefused(@TempDir Path directory) throws Exception {
        OperationalRuntimeException failure = assertThrows(OperationalRuntimeException.class,
            () -> loadRequired(directory, "POST", PLATFORM_ACCESS, "        retentionPolicyRef: idempotency-forever\n"));

        assertEquals("operational.descriptor.invalid", failure.diagnosticCode());
        assertTrue(failure.getMessage().contains("unknown endpoint retention policy"), failure.getMessage());
    }

    @Test
    void onlyAWriteMethodHasResultsToReplay(@TempDir Path directory) throws Exception {
        OperationalRuntimeException failure = assertThrows(OperationalRuntimeException.class,
            () -> loadRequired(directory, "GET", PLATFORM_ACCESS, ""));

        assertTrue(failure.getMessage().contains("results to replay"), failure.getMessage());
    }

    @Test
    void anAnonymousEndpointHasNoCallerForItsKeys(@TempDir Path directory) throws Exception {
        OperationalRuntimeException failure = assertThrows(OperationalRuntimeException.class,
            () -> loadRequired(directory, "POST", ANONYMOUS_STATUS, ""));

        assertTrue(failure.getMessage().contains("authentication.mode required"), failure.getMessage());
    }

    @Test
    void theWriteContractIsOneTheModuleRequires(@TempDir Path directory) throws Exception {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
            () -> loadRequired(directory, "POST", PLATFORM_ACCESS, "", "other.contract"));

        assertTrue(failure.getMessage().contains("not an internal contract the module requires"), failure.getMessage());
    }

    @Test
    void anOperationalDescriptorThatDeclaresQueriesDoesNotLoad(@TempDir Path directory) throws Exception {
        // Root BRX-RS-HOST-014, HOST-024; RFC-011 §3 (multi-tenant T-3's gate): an operational module reads data only
        // through the internal contracts it requires, so its descriptor declares no queries. The same descriptor loads
        // without the block.
        assertEquals("runtime-test-operational",
            loadRequired(directory, "POST", PLATFORM_ACCESS, "").identity().moduleId());

        // The extra text goes at column 0, so the block is a top-level member of the descriptor.
        OperationalRuntimeException failure = assertThrows(OperationalRuntimeException.class,
            () -> loadRequired(directory, "POST", PLATFORM_ACCESS, """
                queries:
                  consumes:
                    - queryType: tenant.usage
                      versionRange: ">=1.0.0 <2.0.0"
                      required: true
                """));

        assertEquals("operational.descriptor.invalid", failure.diagnosticCode());
        assertTrue(failure.getMessage().contains("queries"), failure.getMessage());
    }

    private static final String PLATFORM_ACCESS = """
          authentication:
            mode: required
            policyRef: platform-token
          authorization:
            permissions:
              - platform:runtime:read
        """;

    private static OperationalModuleDescriptor loadRequired(Path directory, String method, String access, String extra)
            throws Exception {
        return loadRequired(directory, method, access, extra, "test.write");
    }

    /** A module that requires {@code test.write}, with one endpoint that replays its results. */
    private static OperationalModuleDescriptor loadRequired(Path directory, String method, String access, String extra,
            String writeContract) throws Exception {
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
              requires:
                - contractId: test.write
                  contractType: java.lang.Runnable
                  versionRange: ">=1.0.0 <2.0.0"
                  required: true
                  privilegeAllowlistRef: test.write
            privileges:
              allowlist:
                - test.write
            endpoints:
              provides:
                - endpointId: runtime.status.v1
                  transport: http
                  method: METHOD
                  path: /api/platform/runtime/status
                  request:
                    schemaRef: brix://schemas/empty/1.0.0
                  response:
                    schemaRef: brix://schemas/runtime/status/1.0.0
                  handlerId: runtime.status
            """.replace("METHOD", method) + access.stripTrailing().indent(4) + """
                  tenantContext:
                    mode: forbidden
                  timeout:
                    policyRef: interactive-write
                  idempotency:
                    mode: required
                    writeContract: WRITE_CONTRACT
            """.replace("WRITE_CONTRACT", writeContract) + extra + """
            tasks:
              provides: []
            """);
        return new OperationalModuleDescriptorLoader().load(descriptor.toUri().toURL());
    }

    @Test
    void loaderReadsTheEndpointsTimeoutPolicy(@TempDir Path directory) throws Exception {
        // Standard-link plan L7.5: the endpoint's deadline comes from its declared policy.
        OperationalModuleDescriptor descriptor = load(directory, ANONYMOUS_STATUS, "not-applicable", "interactive-read");

        assertEquals("interactive-read", descriptor.endpoints().get("runtime.status.v1").timeoutPolicyRef());
    }

    @Test
    void loaderReadsTheEndpointsSchemasAndStatus(@TempDir Path directory) throws Exception {
        // Idempotency design D78: the schemas and the status count as a plugin's do.
        OperationalModuleDescriptor descriptor = load(directory, ANONYMOUS_STATUS, "not-applicable", "interactive-read",
            "        status: 201\n");

        EndpointContract contract = descriptor.endpoints().get("runtime.status.v1").contract();
        assertEquals("brix://schemas/empty/1.0.0", contract.requestSchemaRef());
        assertEquals("brix://schemas/runtime/status/1.0.0", contract.responseSchemaRef());
        assertEquals(201, contract.status());
        assertEquals("interactive-read", contract.timeoutPolicyRef());
    }

    @Test
    void anEndpointThatNamesNoStatusAnswers200(@TempDir Path directory) throws Exception {
        OperationalModuleDescriptor descriptor = load(directory, ANONYMOUS_STATUS, "not-applicable", "interactive-read");

        assertEquals(200, descriptor.endpoints().get("runtime.status.v1").contract().status());
    }

    @Test
    void loaderRejectsAStatusThatIsNotASuccess(@TempDir Path directory) throws Exception {
        OperationalRuntimeException failure = assertThrows(OperationalRuntimeException.class, () -> load(directory,
            ANONYMOUS_STATUS, "not-applicable", "interactive-read", "        status: 500\n"));

        assertEquals("operational.descriptor.invalid", failure.diagnosticCode());
        assertTrue(failure.getMessage().contains("status"), failure.getMessage());
    }

    @Test
    void loaderRejectsATimeoutPolicyTheRuntimeDoesNotKnow(@TempDir Path directory) throws Exception {
        OperationalRuntimeException failure = assertThrows(OperationalRuntimeException.class,
            () -> load(directory, ANONYMOUS_STATUS, "not-applicable", "interactive-forever"));

        assertEquals("operational.descriptor.invalid", failure.diagnosticCode());
        assertTrue(failure.getMessage().contains("unknown endpoint timeout policy"), failure.getMessage());
    }

    private static final String ANONYMOUS_STATUS = """
          authentication:
            mode: anonymous
          authorization:
            policyRef: runtime-status
        """;

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
