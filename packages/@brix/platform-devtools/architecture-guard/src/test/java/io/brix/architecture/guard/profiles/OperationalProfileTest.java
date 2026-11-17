/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.architecture.guard.profiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.brix.platform.fixtureops.handler.FixtureHandler;
import io.brix.platform.fixtureops.handler.HandlerUsesAdapter;
import io.brix.platform.fixtureops.handler.HandlerUsesContainer;
import io.brix.platform.fixtureops.handler.HandlerUsesEnterprise;
import io.brix.platform.fixtureops.handler.HandlerUsesOwnerRepository;
import io.brix.platform.fixtureops.handler.HandlerUsesPlatformLibrary;
import io.brix.platform.fixtureops.handler.HandlerUsesPlugin;
import io.brix.platform.fixtureops.handler.HandlerUsesRegistry;
import io.brix.platform.fixtureops.handler.HandlerUsesRuntime;
import io.brix.platform.fixtureops.operational.FixtureOperationalModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Deliberate violations of every operational rule (root A-26, BRX-RS-LAYER-015, LAYER-016). The violating classes sit
 * in {@code io.brix.platform.fixtureops.handler}, a package not named operational: the rules check every class of the
 * module.
 */
class OperationalProfileTest {

    @Test
    @DisplayName("Code that uses only the operational SPI, an Owner's internal contract and api, and its own classes passes")
    void compliantModulePasses() {
        JavaClasses module = new ClassFileImporter().importClasses(FixtureOperationalModule.class, FixtureHandler.class);

        for (ArchRule rule : rules()) {
            assertDoesNotThrow(() -> rule.check(module), rule.getDescription());
        }
    }

    @Test
    @DisplayName("A module with no class fails every rule rather than passing on nothing")
    void emptyModuleFails() {
        JavaClasses nothing = new ClassFileImporter().importClasses();

        for (ArchRule rule : rules()) {
            assertThrows(AssertionError.class, () -> rule.check(nothing), rule.getDescription());
        }
    }

    @Test
    @DisplayName("Operational code that uses an enterprise implementation fails")
    void enterpriseDependencyFails() {
        assertRejects(OperationalProfile.operationalImplementationBoundary, HandlerUsesEnterprise.class,
            "io.brix.enterprise.ForbiddenEnterpriseService");
    }

    @Test
    @DisplayName("Operational code that uses an infrastructure adapter fails")
    void adapterDependencyFails() {
        assertRejects(OperationalProfile.operationalImplementationBoundary, HandlerUsesAdapter.class,
            "io.infra.adapter.persistence.jpa.JdbcAdapter");
    }

    @Test
    @DisplayName("Operational code that uses a plugin's implementation fails")
    void pluginDependencyFails() {
        assertRejects(OperationalProfile.operationalImplementationBoundary, HandlerUsesPlugin.class,
            "io.brix.samples.fixturebeta.core.BetaStock");
    }

    @Test
    @DisplayName("Operational code that uses the Spring container fails")
    void containerDependencyFails() {
        assertRejects(OperationalProfile.operationalImplementationBoundary, HandlerUsesContainer.class,
            "org.springframework.context.ApplicationContext");
    }

    @Test
    @DisplayName("Operational code that uses the general capability registry fails")
    void registryDependencyFails() {
        assertRejects(OperationalProfile.operationalCannotAccessCapabilityRegistry, HandlerUsesRegistry.class,
            "io.runtime.sdk.capability.registry.CapabilityRegistry");
    }

    @Test
    @DisplayName("Operational code that uses the Runtime outside the operational SPI fails")
    void runtimeOutsideTheOperationalSpiFails() {
        assertRejects(OperationalProfile.operationalRuntimeOnlyThroughOperationalSpi, HandlerUsesRuntime.class,
            "io.runtime.orchestrator.bootstrap.RuntimeShellFatalAction");
    }

    @Test
    @DisplayName("Operational code that uses a Data Owner's repository fails")
    void ownerRepositoryFails() {
        assertRejects(OperationalProfile.operationalOwnersOnlyThroughContracts, HandlerUsesOwnerRepository.class,
            "io.brix.platform.fixtureowner.repository.FixtureOwnerRepository");
    }

    @Test
    @DisplayName("Operational code that uses a platform class outside an Owner's contracts fails")
    void platformLibraryFails() {
        assertRejects(OperationalProfile.operationalOwnersOnlyThroughContracts, HandlerUsesPlatformLibrary.class,
            "io.brix.platform.fixture.CompliantPlatformService");
    }

    @Test
    @DisplayName("Only the checked classes are the module: a platform class left out of it is another module's implementation")
    void anotherModulesClassFails() {
        AssertionError error = assertThrows(AssertionError.class,
            () -> OperationalProfile.operationalOwnersOnlyThroughContracts.check(
                new ClassFileImporter().importClasses(FixtureHandler.class)));
        assertTrue(error.getMessage().contains(FixtureOperationalModule.class.getName()), error.getMessage());
    }

    @Test
    @DisplayName("The profile runs every one of its rules")
    void everyRuleIsAnArchTest() {
        List<Field> rules = Arrays.stream(OperationalProfile.class.getDeclaredFields())
            .filter(field -> ArchRule.class.isAssignableFrom(field.getType()))
            .toList();

        assertEquals(4, rules.size(), rules.toString());
        for (Field field : rules) {
            assertTrue(field.isAnnotationPresent(ArchTest.class) && Modifier.isStatic(field.getModifiers()),
                field.getName());
        }
    }

    private static List<ArchRule> rules() {
        return List.of(OperationalProfile.operationalImplementationBoundary,
            OperationalProfile.operationalCannotAccessCapabilityRegistry,
            OperationalProfile.operationalRuntimeOnlyThroughOperationalSpi,
            OperationalProfile.operationalOwnersOnlyThroughContracts);
    }

    private static void assertRejects(ArchRule rule, Class<?> violating, String forbidden) {
        AssertionError error = assertThrows(AssertionError.class,
            () -> rule.check(new ClassFileImporter().importClasses(violating)));
        assertTrue(error.getMessage().contains(forbidden), error.getMessage());
    }
}
