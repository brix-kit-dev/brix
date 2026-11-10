/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import io.infra.adapter.persistence.jpa.tenancy.P0Global;
import io.infra.adapter.persistence.jpa.tenancy.P0Repositories.GlobalRepository;
import io.runtime.orchestrator.endpoint.DefaultPluginEndpointDispatcher;
import io.runtime.orchestrator.endpoint.EndpointAccessPolicy;
import io.runtime.orchestrator.endpoint.EndpointContract;
import io.runtime.orchestrator.endpoint.EndpointDeadlineExceededException;
import io.runtime.orchestrator.endpoint.EndpointRoute;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.persistence.OwnerUnitOfWorkEndpointBoundary;
import io.runtime.orchestrator.persistence.TenantScope;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.plugin.TypedEndpointHandler;

/**
 * An endpoint whose handler answers after its deadline commits nothing, on this adapter's own unit of work (H2 by
 * default, PostgreSQL when {@code BRIX_P0_POSTGRES_URL} names a throwaway database): the runtime's dispatcher and
 * owner boundary around the time-limited transaction the host uses (root BRX-RS-DATA-011, DATA-028; standard-link plan
 * queue item 6, D122).
 *
 * <p>The deadline is 500 ms and the transaction's timeout, in whole seconds, 1 s: a handler that answers at 700 ms
 * is past its deadline while its transaction still runs, which is the case the runtime checks before the commit.</p>
 */
class EndpointDeadlineCommitTest {

    private static final Duration DEADLINE = Duration.ofMillis(500);
    private static final Duration PAST_IT = Duration.ofMillis(700);
    private static final String PATH = "/api/test/globals";

    private TenantTestEnvironment environment;
    private GlobalRepository globals;

    @BeforeEach
    void open() {
        environment = new TenantTestEnvironment();
        globals = environment.repository(GlobalRepository.class);
    }

    @AfterEach
    void close() {
        environment.close();
    }

    @Test
    void aWriteFlushedInTimeThatIsAnsweredLateRollsBack() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher(invocation -> {
            globals.saveAndFlush(new P0Global(1L, "flushed"));
            pause(PAST_IT);
            return CompletableFuture.completedFuture(Map.of("id", 1));
        });

        assertThrows(EndpointDeadlineExceededException.class, () -> post(dispatcher));
        assertEquals(0L, count());
    }

    @Test
    void aWriteStillToFlushWhenAnsweredLateRollsBackWith504() {
        // An assigned id makes Hibernate keep the insert until the flush at the commit.
        DefaultPluginEndpointDispatcher dispatcher = dispatcher(invocation -> {
            globals.save(new P0Global(2L, "pending"));
            pause(PAST_IT);
            return CompletableFuture.completedFuture(Map.of("id", 2));
        });

        assertThrows(EndpointDeadlineExceededException.class, () -> post(dispatcher));
        assertEquals(0L, count());
    }

    @Test
    void aWriteAnsweredInTimeCommits() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher(invocation -> {
            globals.save(new P0Global(3L, "prompt"));
            return CompletableFuture.completedFuture(Map.of("id", 3));
        });

        post(dispatcher);
        assertEquals(1L, count());
    }

    private DefaultPluginEndpointDispatcher dispatcher(TypedEndpointHandler<JsonNode, Map<String, Integer>> handler) {
        PersistenceCapability owner = environment.persistence;
        OwnerPersistenceProvider host = new OwnerPersistenceProvider() {
            @Override
            public Optional<PersistenceCapability> forOwner(String ownerModuleId) {
                return TenantTestEnvironment.OWNER.equals(ownerModuleId) ? Optional.of(owner) : Optional.empty();
            }

            @Override
            public <T> T readWrite(String ownerModuleId, Duration timeout, Supplier<T> work) {
                // As the host's OwnerPersistenceUnits does.
                return ((OwnerUnitOfWork) owner.unitOfWork()).requiredWithin(timeout, work);
            }
        };
        DefaultPluginEndpointDispatcher dispatcher = new DefaultPluginEndpointDispatcher(DEADLINE,
            new OwnerUnitOfWorkEndpointBoundary(host), JsonMapper.builder().build());
        EndpointAccessPolicy anonymous = EndpointAccessPolicy.fromDeclaration(
            Map.of("mode", "anonymous"), Map.of("policyRef", "test-public"), Map.of("mode", "forbidden"));
        dispatcher.replaceSnapshot(List.of(new EndpointRoute(TenantTestEnvironment.OWNER, "test.globals.create.v1",
            "POST", PATH, anonymous, handler, EndpointContract.none())));
        return dispatcher;
    }

    private static Object post(DefaultPluginEndpointDispatcher dispatcher) {
        return dispatcher.invoke("POST", PATH, Map.of(), Map.of(), Map.of());
    }

    private long count() {
        return environment.read(TenantScope.none(), () -> globals.count());
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
