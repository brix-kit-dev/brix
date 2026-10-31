/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Set;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.infra.adapter.persistence.jpa.fixture.ForeignNote;
import io.infra.adapter.persistence.jpa.fixture.OwnedNote;
import io.infra.adapter.persistence.jpa.fixture.OwnedNoteRepository;
import io.runtime.sdk.capability.PersistenceCapabilityException;
import io.runtime.sdk.capability.UnitOfWork;

/**
 * Query design §5.2 step 7 (root BRX-RS-MESSAGE-008, DATA-010, DATA-029, GOVERNANCE-021): a query's provider reads
 * in a read-only transaction of its own that lasts no longer than the query may, with its caller's transaction
 * suspended and resumed afterwards.
 */
class OwnerQueryScopeTest {

    private JpaTestEnvironment environment;
    private OwnedNoteRepository notes;
    private OwnerUnitOfWork provider;

    @BeforeEach
    void setUp() {
        environment = new JpaTestEnvironment();
        JpaOwnerPersistence persistence =
            environment.persistence("owner-a", Set.of(OwnedNote.class, ForeignNote.class));
        notes = persistence.repository(OwnedNoteRepository.class);
        provider = (OwnerUnitOfWork) persistence.unitOfWork();
        provider.required(() -> notes.save(new OwnedNote(1L, "n-1", "committed")));
    }

    @AfterEach
    void tearDown() {
        environment.close();
    }

    @Test
    void theProviderReadsInItsOwnReadOnlyTransactionAndTheCallersResumes() {
        UnitOfWork caller = environment.unitOfWork("owner-b");

        String text = caller.required(() -> {
            EntityManager callerContext = EntityManagerFactoryUtils.getTransactionalEntityManager(
                environment.entityManagerFactory);
            String read = provider.readOnlyWithin(Duration.ofSeconds(2), () -> {
                assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                assertNotSame(callerContext, EntityManagerFactoryUtils.getTransactionalEntityManager(
                    environment.entityManagerFactory), "no shared persistence context");
                return notes.findByCode("n-1").orElseThrow().getText();
            });
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(), "the caller's is resumed");
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return read;
        });

        assertEquals("committed", text);
    }

    @Test
    void theProviderCannotWrite() {
        PersistenceCapabilityException failure = assertThrows(PersistenceCapabilityException.class,
            () -> provider.readOnlyWithin(Duration.ofSeconds(2), () -> notes.save(new OwnedNote(2L, "n-2", "write"))));

        assertEquals(PersistenceCapabilityException.Code.READ_ONLY_VIOLATION, failure.code());
        assertEquals(1L, environment.count(OwnedNote.class));
    }

    @Test
    void theProvidersTransactionLastsNoLongerThanTheQueryMay() {
        long timeToLive = provider.readOnlyWithin(Duration.ofMillis(1500), () -> holder().getTimeToLiveInMillis());

        assertTrue(timeToLive > 0 && timeToLive <= 2000, "rounded up to the next second: " + timeToLive);
    }

    @Test
    void theOwnersTransactionTimeoutCapsALongerQuery() {
        long timeToLive = provider.readOnlyWithin(Duration.ofMinutes(5), () -> holder().getTimeToLiveInMillis());

        assertTrue(timeToLive <= JpaTestEnvironment.TRANSACTION_TIMEOUT.toMillis(), "capped: " + timeToLive);
    }

    private EntityManagerHolder holder() {
        EntityManagerHolder holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(
            environment.entityManagerFactory);
        assertTrue(holder.hasTimeout(), "the transaction has a timeout");
        return holder;
    }
}
