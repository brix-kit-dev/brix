/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import io.infra.adapter.persistence.jpa.fixture.CrossOwnerQueryRepository;
import io.infra.adapter.persistence.jpa.fixture.ForeignNote;
import io.infra.adapter.persistence.jpa.fixture.ForeignNoteRepository;
import io.infra.adapter.persistence.jpa.fixture.NativeNoteRepository;
import io.infra.adapter.persistence.jpa.fixture.OwnedNote;
import io.infra.adapter.persistence.jpa.fixture.OwnedNoteRepository;
import io.infra.adapter.persistence.jpa.fixture.UnsafeClearRepository;
import io.infra.adapter.persistence.jpa.rejected.FormulaNote;
import io.infra.adapter.persistence.jpa.rejected.GeneratedNote;
import io.infra.adapter.persistence.jpa.rejected.LinkedNote;
import io.infra.adapter.persistence.jpa.rejected.SchemaPinnedNote;
import io.runtime.sdk.capability.PersistenceCapabilityException;

class OwnerPersistenceValidatorTest {

    private JpaTestEnvironment environment;

    @BeforeEach
    void setUp() {
        environment = new JpaTestEnvironment();
    }

    @AfterEach
    void tearDown() {
        environment.close();
    }

    @Test
    void ownersOwnRepositoryIsAccepted() {
        try (OwnerPersistenceValidator validator = validator(Set.of(OwnedNote.class), true)) {
            assertDoesNotThrow(() -> validator.validateRepository(OwnedNoteRepository.class));
        }
    }

    @Test
    void jpqlNamingAnotherOwnersEntityIsRejected() {
        try (OwnerPersistenceValidator validator = validator(Set.of(OwnedNote.class), true)) {
            assertRejected(() -> validator.validateRepository(CrossOwnerQueryRepository.class),
                "does not resolve against this owner's entities");
        }
    }

    @Test
    void repositoryOfAnotherOwnersEntityIsRejected() {
        try (OwnerPersistenceValidator validator = validator(Set.of(OwnedNote.class), true)) {
            assertRejected(() -> validator.validateRepository(ForeignNoteRepository.class),
                "which is not an entity of this owner");
        }
    }

    @Test
    void nativeQueryIsRejected() {
        try (OwnerPersistenceValidator validator = validator(Set.of(OwnedNote.class), true)) {
            assertRejected(() -> validator.validateRepository(NativeNoteRepository.class),
                "declares a native query");
        }
    }

    @Test
    void clearingWithoutFlushingIsRejected() {
        try (OwnerPersistenceValidator validator = validator(Set.of(OwnedNote.class), true)) {
            assertRejected(() -> validator.validateRepository(UnsafeClearRepository.class),
                "without flushing it first");
        }
    }

    @Test
    void associationToAnotherOwnersEntityIsRejected() {
        assertRejected(() -> validator(Set.of(LinkedNote.class), false), "failed validation");
    }

    @Test
    void databaseGeneratedIdentifiersAreRejected() {
        assertRejected(() -> validator(Set.of(GeneratedNote.class), false), "IdGeneratorCapability");
    }

    @Test
    void hardCodedSchemaIsRejected() {
        assertRejected(() -> validator(Set.of(SchemaPinnedNote.class), false), "hard-codes a schema");
    }

    @Test
    void vendorAnnotationOutsideTheWhitelistIsRejected() {
        assertRejected(() -> validator(Set.of(FormulaNote.class), false), "@Formula");
    }

    @Test
    void mappingWithoutAMigratedTableIsRejected() {
        assertDoesNotThrow(() -> validator(Set.of(OwnedNote.class, ForeignNote.class), true).close());
        assertRejected(() -> validator(Set.of(LinkedNote.class, ForeignNote.class), true), "failed validation");
    }

    @Test
    void repositoryNotPackagedWithTheOwnerIsRejected() {
        OwnerPersistenceValidator validator = validator(Set.of(OwnedNote.class), true);
        JpaOwnerPersistence persistence = new JpaOwnerPersistence(
            "owner-a",
            type -> "another-owner",
            environment.sharedEntityManager,
            validator,
            environment.unitOfWork("owner-a"),
            environment.failureTranslator);
        try {
            assertRejected(() -> persistence.repository(OwnedNoteRepository.class), "not packaged with this owner");
        } finally {
            validator.close();
        }
    }

    private OwnerPersistenceValidator validator(Set<Class<?>> ownerTypes, boolean validateSchema) {
        return OwnerPersistenceValidator.create(
            "owner-a", ownerTypes, environment.entityManagerFactory, environment.dataSource, validateSchema);
    }

    private static void assertRejected(Executable executable, String reason) {
        PersistenceCapabilityException failure = assertThrows(PersistenceCapabilityException.class, executable);
        assertEquals(PersistenceCapabilityException.Code.REPOSITORY_REJECTED, failure.code());
        assertTrue(failure.getCause().getMessage().contains(reason),
            () -> "Expected rejection reason '" + reason + "' but was: " + failure.getCause().getMessage());
    }
}
