/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.infra.adapter.persistence.jpa.fixture.ForeignNote;
import io.infra.adapter.persistence.jpa.fixture.OwnedNote;
import io.infra.adapter.persistence.jpa.fixture.OwnedNoteRepository;
import io.runtime.sdk.capability.PersistenceCapabilityException;
import io.runtime.sdk.capability.UnitOfWork;

class OwnerUnitOfWorkTest {

    private JpaTestEnvironment environment;
    private OwnedNoteRepository notes;
    private UnitOfWork unitOfWork;

    @BeforeEach
    void setUp() {
        environment = new JpaTestEnvironment();
        JpaOwnerPersistence persistence =
            environment.persistence("owner-a", Set.of(OwnedNote.class, ForeignNote.class));
        notes = persistence.repository(OwnedNoteRepository.class);
        unitOfWork = persistence.unitOfWork();
    }

    @AfterEach
    void tearDown() {
        environment.close();
    }

    @Test
    void requiredCommitsAndReadOnlyReads() {
        unitOfWork.required(() -> notes.save(new OwnedNote(1L, "n-1", "draft")));

        assertEquals("draft", unitOfWork.readOnly(() -> notes.findByCode("n-1").orElseThrow().getText()));
        assertEquals(1L, environment.count(OwnedNote.class));
    }

    @Test
    void failingWorkRollsBack() {
        IllegalStateException failure = new IllegalStateException("business rule");

        assertSame(failure, assertThrows(IllegalStateException.class, () -> unitOfWork.required(() -> {
            notes.save(new OwnedNote(1L, "n-1", "draft"));
            throw failure;
        })));
        assertEquals(0L, environment.count(OwnedNote.class));
    }

    @Test
    void nestedRequiredJoinsTheOwnerTransaction() {
        assertThrows(IllegalStateException.class, () -> unitOfWork.required(() -> {
            unitOfWork.required(() -> notes.save(new OwnedNote(1L, "n-1", "draft")));
            throw new IllegalStateException("outer failure");
        }));

        assertEquals(0L, environment.count(OwnedNote.class));
    }

    @Test
    void readOnlyUnitOfWorkRejectsWrites() {
        PersistenceCapabilityException failure = assertThrows(PersistenceCapabilityException.class,
            () -> unitOfWork.readOnly(() -> notes.save(new OwnedNote(1L, "n-1", "draft"))));

        assertEquals(PersistenceCapabilityException.Code.READ_ONLY_VIOLATION, failure.code());
        assertEquals(0L, environment.count(OwnedNote.class));
    }

    @Test
    void readOnlySectionInsideAReadWriteUnitOfWorkRejectsWrites() {
        unitOfWork.required(() -> notes.save(new OwnedNote(1L, "n-1", "draft")));

        PersistenceCapabilityException failure = assertThrows(PersistenceCapabilityException.class,
            () -> unitOfWork.required(() -> unitOfWork.readOnly(() -> {
                notes.findByCode("n-1").orElseThrow().setText("changed");
                return null;
            })));

        assertEquals(PersistenceCapabilityException.Code.READ_ONLY_VIOLATION, failure.code());
        assertEquals("draft", unitOfWork.readOnly(() -> notes.findByCode("n-1").orElseThrow().getText()));
    }

    @Test
    void readWriteWorkCannotStartInsideAReadOnlyScope() {
        PersistenceCapabilityException failure = assertThrows(PersistenceCapabilityException.class,
            () -> unitOfWork.readOnly(() -> unitOfWork.required(() -> null)));

        assertEquals(PersistenceCapabilityException.Code.READ_ONLY_VIOLATION, failure.code());
    }

    @Test
    void ownersNeverShareAPhysicalTransaction() {
        UnitOfWork otherOwner = environment.unitOfWork("owner-b");

        assertThrows(IllegalStateException.class, () -> otherOwner.required(() -> {
            unitOfWork.required(() -> notes.save(new OwnedNote(1L, "n-1", "committed by owner-a")));
            throw new IllegalStateException("owner-b fails after calling owner-a");
        }));

        assertEquals(1L, environment.count(OwnedNote.class));
    }

    @Test
    void anotherOwnersTransactionDoesNotCountAsThisOwnersUnitOfWork() {
        UnitOfWork otherOwner = environment.unitOfWork("owner-b");

        PersistenceCapabilityException failure = assertThrows(PersistenceCapabilityException.class,
            () -> otherOwner.required(() -> notes.save(new OwnedNote(1L, "n-1", "draft"))));

        assertEquals(PersistenceCapabilityException.Code.UNIT_OF_WORK_MISSING, failure.code());
    }

    @Test
    void requestEntityManagerIsNeverSharedWithAnOwnerTransaction() {
        EntityManager requestEntityManager = environment.entityManagerFactory.createEntityManager();
        TransactionSynchronizationManager.bindResource(
            environment.entityManagerFactory, new EntityManagerHolder(requestEntityManager));
        try {
            EntityManager used = unitOfWork.required(() -> EntityManagerFactoryUtils
                .getTransactionalEntityManager(environment.entityManagerFactory));

            assertNotSame(requestEntityManager, used);
            assertSame(requestEntityManager, ((EntityManagerHolder) TransactionSynchronizationManager
                .getResource(environment.entityManagerFactory)).getEntityManager());
        } finally {
            TransactionSynchronizationManager.unbindResource(environment.entityManagerFactory);
            requestEntityManager.close();
        }
    }

    @Test
    void workStartedFromACommitCallbackRunsInANewTransaction() {
        unitOfWork.required(() -> {
            notes.save(new OwnedNote(1L, "n-1", "first"));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    unitOfWork.required(() -> notes.save(new OwnedNote(2L, "n-2", "after commit")));
                }
            });
            return null;
        });

        assertEquals(2L, environment.count(OwnedNote.class));
        assertTrue(unitOfWork.readOnly(() -> notes.findByCode("n-2")).isPresent());
    }
}
