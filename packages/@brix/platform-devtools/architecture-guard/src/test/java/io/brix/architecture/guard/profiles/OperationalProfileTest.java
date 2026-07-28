/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.architecture.guard.profiles;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import io.brix.test.operational.CompliantOperationalComponent;
import io.brix.test.operational.EnterpriseEscapingOperationalComponent;

class OperationalProfileTest {

    @Test
    void exposesA25A26ContainerAndRegistryBoundaries() {
        assertTrue(OperationalProfile.operationalImplementationBoundary.getDescription()
            .contains("platform-operational"));
        assertTrue(OperationalProfile.operationalCannotAccessCapabilityRegistry.getDescription()
            .contains("OperationalContext"));
    }

    @Test
    void acceptsRestrictedOperationalCode() {
        var classes = new ClassFileImporter().importClasses(CompliantOperationalComponent.class);

        assertDoesNotThrow(() -> OperationalProfile.operationalImplementationBoundary.check(classes));
    }

    @Test
    void rejectsEnterpriseImplementationEscapeHatch() {
        var classes = new ClassFileImporter().importClasses(
            EnterpriseEscapingOperationalComponent.class,
            io.brix.enterprise.ForbiddenEnterpriseService.class);

        assertThrows(
            AssertionError.class,
            () -> OperationalProfile.operationalImplementationBoundary.check(classes));
    }
}
