/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;

import io.brix.platform.identity.internal.CreateFirstPlatformAdminCommand;
import io.brix.platform.identity.internal.PlatformAdminCreationView;
import io.brix.platform.identity.internal.PlatformBootstrapAdministration;
import io.runtime.orchestrator.endpoint.DefaultPluginEndpointDispatcher;
import io.runtime.orchestrator.endpoint.EndpointContract;
import io.runtime.orchestrator.endpoint.EndpointRequestInvalidException;
import io.runtime.orchestrator.endpoint.EndpointRoute;
import io.runtime.orchestrator.endpoint.TypedEndpointRules;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.orchestrator.operational.OperationalModuleDescriptor;
import io.runtime.orchestrator.operational.OperationalModuleDescriptorLoader;
import io.runtime.orchestrator.operational.OperationalModuleIdentity;
import io.runtime.orchestrator.operational.RuntimeOperationalView;
import io.runtime.sdk.plugin.EndpointResponse;

/**
 * {@code POST /api/platform/bootstrap/create-first-admin} with the shipped descriptor and schemas, through the
 * dispatcher (super-admin 4.0.1 §6; standard-link plan point 10b-7).
 */
class CreateFirstAdminEndpointTest {

    private static final String CREATE_FIRST_ADMIN = "platform.bootstrap.create-first-admin.v1";

    private final PlatformBootstrapAdministration bootstrapAdministration =
            org.mockito.Mockito.mock(PlatformBootstrapAdministration.class);
    private final OperationalContext context = new TestOperationalContext(bootstrapAdministration);

    @Test
    void theFirstAdminIsAnsweredAsItsSchemaSaysForTheVerifiedSession() {
        when(bootstrapAdministration.createFirstAdmin(any()))
                .thenReturn(new PlatformAdminCreationView(976369206184382464L, 974180454301175808L, true));

        EndpointResponse answer = (EndpointResponse) create(Map.of(
                "username", "root-admin", "email", "root@example.invalid", "notes", "the installer"));

        assertEquals(200, answer.status());
        JsonNode body = (JsonNode) answer.body();
        assertEquals("976369206184382464", body.get("id").asString());
        assertEquals("974180454301175808", body.get("identityId").asString());
        assertEquals(true, body.get("setupLinkSent").asBoolean());
        assertEquals(3, body.size(), "only the ids and the delivery marker");
        ArgumentCaptor<CreateFirstPlatformAdminCommand> command =
                ArgumentCaptor.forClass(CreateFirstPlatformAdminCommand.class);
        verify(bootstrapAdministration).createFirstAdmin(command.capture());
        assertEquals("session-jti", command.getValue().bootstrapSessionId());
        assertEquals("root-admin", command.getValue().username());
        assertEquals("root@example.invalid", command.getValue().email());
        assertEquals("the installer", command.getValue().notes());
    }

    @ParameterizedTest
    @MethodSource("requestsTheSchemaRefuses")
    void aRequestTheSchemaRefusesNeverReachesTheOwner(Map<String, Object> request) {
        assertThrows(EndpointRequestInvalidException.class, () -> create(request));

        verify(bootstrapAdministration, never()).createFirstAdmin(any());
    }

    static Stream<Map<String, Object>> requestsTheSchemaRefuses() {
        Map<String, Object> password = new HashMap<>(Map.of("username", "root-admin", "email", "root@example.invalid"));
        password.put("password", "secret");
        return Stream.of(
                Map.of("email", "root@example.invalid"),
                Map.of("username", "root-admin"),
                Map.of("username", "   ", "email", "root@example.invalid"),
                Map.of("username", "x".repeat(129), "email", "root@example.invalid"),
                Map.of("username", "root-admin", "email", "not-an-email"),
                Map.of("username", "root-admin", "email", "root@example.invalid", "notes", "n".repeat(1001)),
                password);
    }

    @Test
    void theFirstAdminIsReplayedThroughTheIdentityOwner() {
        // Super-admin §6: required. The record goes with the one write through the identity owner's contract.
        EndpointContract contract = endpoint().contract();

        assertEquals("brix.internal.platform.bootstrap-administration", contract.replay().writeContract());
        assertEquals(200, contract.status());
        assertEquals(Optional.empty(), TypedEndpointRules.problem(new CreateFirstAdminHandler(context),
                contract, getClass().getClassLoader()));
    }

    private Object create(Map<String, Object> request) {
        OperationalModuleDescriptor.EndpointDeclaration endpoint = endpoint();
        EndpointContract contract = endpoint.contract();
        DefaultPluginEndpointDispatcher dispatcher = new DefaultPluginEndpointDispatcher(Duration.ofSeconds(5));
        dispatcher.replaceSnapshot(List.of(new EndpointRoute("platform-admin", endpoint.endpointId(), endpoint.method(),
                endpoint.path(), endpoint.accessPolicy(), new CreateFirstAdminHandler(context),
                new EndpointContract(contract.requestSchemaRef(), contract.responseSchemaRef(), contract.status(),
                        contract.timeoutPolicyRef()))));
        return dispatcher.invoke(endpoint.method(), endpoint.path(), request, Map.of(), Map.of(
                "x-actor-id", List.of("session-jti"),
                "x-auth-token-role", List.of("bootstrap"),
                "x-auth-token-type", List.of("BOOTSTRAP_SETUP"),
                "x-auth-token-scope", List.of("BOOTSTRAP"),
                "x-auth-permission", List.of("bootstrap:status", "bootstrap:session", "bootstrap:create-first-admin")));
    }

    private OperationalModuleDescriptor.EndpointDeclaration endpoint() {
        return new OperationalModuleDescriptorLoader()
                .load(getClass().getClassLoader().getResource(OperationalModuleDescriptorLoader.DESCRIPTOR_RESOURCE))
                .endpoints().get(CREATE_FIRST_ADMIN);
    }

    private record TestOperationalContext(PlatformBootstrapAdministration bootstrapAdministration)
            implements OperationalContext {

        @Override
        public OperationalModuleIdentity moduleIdentity() {
            return new OperationalModuleIdentity("platform-admin", "3.2.0", "platform-commons");
        }

        @Override
        public RuntimeOperationalView runtimeView() {
            return new RuntimeOperationalView() {
                @Override
                public long entryGeneration() {
                    return 1L;
                }

                @Override
                public boolean ready() {
                    return true;
                }

                @Override
                public Set<String> requiredModuleIds() {
                    return Set.of("platform-admin");
                }
            };
        }

        @Override
        public <C> C requireInternalContract(Class<C> contractType) {
            if (!contractType.equals(PlatformBootstrapAdministration.class)) {
                throw new IllegalArgumentException("Unexpected contract type " + contractType.getName());
            }
            return contractType.cast(bootstrapAdministration);
        }
    }
}
