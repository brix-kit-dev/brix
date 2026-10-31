/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import io.brix.architecture.guard.testfixtures.legacybus.ContextUser;
import io.brix.architecture.guard.testfixtures.legacybus.LegacyBusCaller;
import io.brix.architecture.guard.testfixtures.legacybus.ListedLegacyBusCaller;

/**
 * The legacy event bus may only lose callers (standard-link plan L2.3).
 */
class NoLegacyEventBusRuleTest {

    @Test
    void aClassThatTakesTheLegacyBusAndIsNotListedFails() {
        JavaClasses classes = new ClassFileImporter().importClasses(LegacyBusCaller.class);

        AssertionError failure = assertThrows(AssertionError.class, () -> NoLegacyEventBusRule.rule().check(classes));

        assertTrue(failure.getMessage().contains("LegacyBusCaller"), failure.getMessage());
    }

    @Test
    void aListedClassAndItsNestedClassesPass() {
        JavaClasses classes = new ClassFileImporter().importPackagesOf(ListedLegacyBusCaller.class)
                .that(com.tngtech.archunit.base.DescribedPredicate.describe("listed fixtures",
                        type -> type.getName().startsWith(ListedLegacyBusCaller.class.getName())));

        assertDoesNotThrow(() -> NoLegacyEventBusRule.rule().check(classes));
    }

    @Test
    void holdingTheContextWithoutTakingTheBusPasses() {
        JavaClasses classes = new ClassFileImporter().importClasses(ContextUser.class);

        assertDoesNotThrow(() -> NoLegacyEventBusRule.rule().check(classes));
    }

    @Test
    void theListIsReadFromTheTestClassPath() {
        Set<String> known = NoLegacyEventBusRule.knownCallers(getClass().getClassLoader());

        assertEquals(Set.of(ListedLegacyBusCaller.class.getName()), known);
    }
}
