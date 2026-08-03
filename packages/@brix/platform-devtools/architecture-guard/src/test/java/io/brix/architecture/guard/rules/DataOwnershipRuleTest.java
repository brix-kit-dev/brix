/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.architecture.guard.rules;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.importer.ClassFileImporter;

class DataOwnershipRuleTest {

    @Test
    @DisplayName("Same-owner repository access passes the Phase 4 Data Owner rule")
    void sameOwnerRepositoryAccessPasses() {
        var classes = new ClassFileImporter()
            .importPackages("io.brix.app.booking.service");

        assertDoesNotThrow(() -> DataOwnershipRule.noCrossPluginRepositoryAccess().check(classes));
    }

    @Test
    @DisplayName("Cross-owner repository access fails the Phase 4 Data Owner rule")
    void crossOwnerRepositoryAccessFails() {
        var classes = new ClassFileImporter()
            .importPackages(
                "io.brix.app.booking.violating",
                "io.brix.app.caseapp.repository");

        assertThrows(AssertionError.class,
            () -> DataOwnershipRule.noCrossPluginRepositoryAccess().check(classes));
    }

    @Test
    @DisplayName("Raw persistence APIs in core/domain fail the Phase 4 Data Owner rule")
    void rawPersistenceApiInDomainFails() {
        var classes = new ClassFileImporter()
            .importPackages("io.brix.app.booking.domain");

        assertThrows(AssertionError.class,
            () -> DataOwnershipRule.noRawPersistenceApiInCoreOrDomain().check(classes));
    }
}
