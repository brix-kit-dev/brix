/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.infra.adapter.persistence.jpa.fixture.ForeignNote;
import io.infra.adapter.persistence.jpa.fixture.OwnedNote;
import io.infra.adapter.persistence.jpa.fixture.OwnedNoteRepository;
import io.runtime.sdk.capability.PersistenceCapabilityException;
import io.runtime.sdk.capability.PersistenceCapabilityException.Code;
import io.runtime.sdk.capability.UnitOfWork;

class OwnerRepositoryTest {

    private JpaTestEnvironment environment;
    private JpaOwnerPersistence persistence;
    private OwnedNoteRepository notes;
    private UnitOfWork unitOfWork;

    @BeforeEach
    void setUp() {
        environment = new JpaTestEnvironment();
        persistence = environment.persistence("owner-a", Set.of(OwnedNote.class, ForeignNote.class));
        notes = persistence.repository(OwnedNoteRepository.class);
        unitOfWork = persistence.unitOfWork();
    }

    @AfterEach
    void tearDown() {
        environment.close();
    }

    @Test
    void repositoryIsCreatedOncePerInterface() {
        assertSame(notes, persistence.repository(OwnedNoteRepository.class));
    }

    @Test
    void repositoryAccessOutsideAUnitOfWorkIsRejected() {
        PersistenceCapabilityException failure = assertThrows(PersistenceCapabilityException.class, notes::findAll);

        assertEquals(Code.UNIT_OF_WORK_MISSING, failure.code());
        assertEquals(Map.of("owner", "owner-a"), failure.safeParameters());
    }

    @Test
    void uniqueViolationIsTranslated() {
        unitOfWork.required(() -> notes.save(new OwnedNote(1L, "n-1", "first")));

        PersistenceCapabilityException failure = assertThrows(PersistenceCapabilityException.class,
            () -> unitOfWork.required(() -> notes.saveAndFlush(new OwnedNote(2L, "n-1", "duplicate"))));

        assertEquals(Code.UNIQUE_CONSTRAINT_VIOLATION, failure.code());
        assertEquals("owner-a", failure.safeParameters().get("owner"));
    }

    @Test
    void uniqueViolationDetectedAtCommitIsTranslated() {
        unitOfWork.required(() -> notes.save(new OwnedNote(1L, "n-1", "first")));

        PersistenceCapabilityException failure = assertThrows(PersistenceCapabilityException.class,
            () -> unitOfWork.required(() -> notes.save(new OwnedNote(2L, "n-1", "duplicate"))));

        assertEquals(Code.UNIQUE_CONSTRAINT_VIOLATION, failure.code());
    }

    @Test
    void concurrentModificationIsTranslated() {
        unitOfWork.required(() -> notes.save(new OwnedNote(1L, "n-1", "first")));
        OwnedNote stale = unitOfWork.readOnly(() -> notes.findByCode("n-1").orElseThrow());
        unitOfWork.required(() -> {
            notes.findByCode("n-1").orElseThrow().setText("second");
            return null;
        });
        stale.setText("lost update");

        PersistenceCapabilityException failure = assertThrows(PersistenceCapabilityException.class,
            () -> unitOfWork.required(() -> notes.save(stale)));

        assertEquals(Code.OPTIMISTIC_LOCK_CONFLICT, failure.code());
    }

    @Test
    void modifyingQueryFlushesPendingWritesBeforeClearing() {
        unitOfWork.required(() -> {
            notes.save(new OwnedNote(1L, "n-1", "pending"));
            notes.rename(1L, "renamed");
            return null;
        });

        assertEquals("renamed", unitOfWork.readOnly(() -> notes.findByCode("n-1").orElseThrow().getText()));
    }

    @Test
    void businessFailuresInsideWorkAreNotTranslated() {
        IllegalArgumentException failure = new IllegalArgumentException("invalid input");

        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> unitOfWork.required(() -> {
            throw failure;
        })));
    }
}
