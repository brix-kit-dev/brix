/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.architecture.guard.rules;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tngtech.archunit.core.importer.ClassFileImporter;

import io.brix.architecture.guard.testsupport.CompiledModule;

class DataOwnershipRuleTest {

    @Test
    @DisplayName("Same-owner repository access passes the Phase 4 Data Owner rule")
    void sameOwnerRepositoryAccessPasses() {
        var classes = new ClassFileImporter()
            .importPackages("io.brix.samples.fixturebooking.service");

        assertDoesNotThrow(() -> DataOwnershipRule.noCrossPluginRepositoryAccess().check(classes));
    }

    @Test
    @DisplayName("Cross-owner repository access fails the Phase 4 Data Owner rule")
    void crossOwnerRepositoryAccessFails() {
        var classes = new ClassFileImporter()
            .importPackages(
                "io.brix.samples.fixturebooking.core",
                "io.brix.samples.fixturecase.repository");

        assertThrows(AssertionError.class,
            () -> DataOwnershipRule.noCrossPluginRepositoryAccess().check(classes));
    }

    @Test
    @DisplayName("Cross-owner repository access fails in every package of a module, in both directions")
    void crossOwnerRepositoryAccessFailsInEveryPackage(@TempDir Path directory) throws IOException {
        Map<String, String> violating = Map.of(
            "io.brix.platform.fixtureowner.endpoint.ReadsPlugin", "io.brix.samples.fixturecase.repository.CaseRepository",
            "io.brix.samples.fixtureplugin.server.ReadsOwner", "io.brix.platform.fixtureother.repository.OtherRepository",
            "io.brix.samples.fixtureplugin.handler.ReadsPlugin", "io.brix.samples.fixturecase.repository.CaseRepository");
        for (Map.Entry<String, String> access : violating.entrySet()) {
            var module = CompiledModule.using(Files.createTempDirectory(directory, "module"), access.getKey(),
                access.getValue());

            AssertionError error = assertThrows(AssertionError.class,
                () -> DataOwnershipRule.noCrossPluginRepositoryAccess().check(module), access.getKey());
            assertTrue(error.getMessage().contains(access.getKey() + " depends on foreign repository "
                + access.getValue()), error.getMessage());
        }
    }

    @Test
    @DisplayName("A module's own repository passes in every package, and a module without classes fails")
    void ownRepositoryPassesAndNoClassFails(@TempDir Path directory) throws IOException {
        var module = CompiledModule.using(Files.createTempDirectory(directory, "module"),
            "io.brix.platform.fixtureowner.endpoint.ReadsItself", "io.brix.platform.fixtureowner.repository.OwnRepository");

        assertDoesNotThrow(() -> DataOwnershipRule.noCrossPluginRepositoryAccess().check(module));
        assertThrows(AssertionError.class, () -> DataOwnershipRule.noCrossPluginRepositoryAccess()
            .check(new ClassFileImporter().importPath(Files.createTempDirectory(directory, "empty"))));
    }

    @Test
    @DisplayName("Raw persistence APIs in core/domain fail the Phase 4 Data Owner rule")
    void rawPersistenceApiInDomainFails() {
        var classes = new ClassFileImporter()
            .importPackages("io.brix.samples.fixturebooking.domain");

        assertThrows(AssertionError.class,
            () -> DataOwnershipRule.noRawPersistenceApiInCoreOrDomain().check(classes));
    }
}
