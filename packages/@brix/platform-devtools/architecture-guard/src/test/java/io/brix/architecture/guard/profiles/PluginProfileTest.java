/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.architecture.guard.profiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.brix.samples.fixtureauth.core.AuthorizesOnPermission;
import io.brix.samples.fixtureauth.core.AuthorizesOnRoleName;
import io.brix.samples.fixturecommands.api.CancelBooking;
import io.brix.samples.fixturecommands.api.FixtureCommands;
import io.brix.samples.fixturecommands.core.SubmitsStringCommand;
import io.brix.samples.fixturecommands.core.SubmitsTypedCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PluginProfileTest {

    @Test
    void rejectsPluginThatImportsAnInfrastructureAdapter() {
        var classes = new ClassFileImporter().importPackages(
            "io.brix.architecture.guard.testfixtures.violating.adapter");

        assertThrows(AssertionError.class, () -> PluginProfile.noInfraAdapterDependency.check(classes));
    }

    @Test
    @DisplayName("A plugin that authorizes on a role name through the SDK fails")
    void rejectsPluginThatAuthorizesOnARoleName() {
        var classes = new ClassFileImporter().importClasses(AuthorizesOnRoleName.class);

        AssertionError error = assertThrows(AssertionError.class, () -> PluginProfile.noRoleNameChecks.check(classes));
        assertTrue(error.getMessage().contains(AuthorizesOnRoleName.class.getName() + ".mayApprove()"),
            error.getMessage());
    }

    @Test
    @DisplayName("A plugin that authorizes on a permission code passes")
    void acceptsPluginThatAuthorizesOnAPermission() {
        var classes = new ClassFileImporter().importClasses(AuthorizesOnPermission.class);

        assertDoesNotThrow(() -> PluginProfile.noRoleNameChecks.check(classes));
    }

    @Test
    @DisplayName("A plugin that sends a command through the string gateway fails (root A-22)")
    void rejectsPluginThatSubmitsAStringCommand() {
        var classes = new ClassFileImporter().importClasses(SubmitsStringCommand.class);

        AssertionError error = assertThrows(AssertionError.class,
            () -> PluginProfile.noStringCommandGateway.check(classes));
        assertTrue(error.getMessage().contains(SubmitsStringCommand.class.getName()), error.getMessage());
    }

    @Test
    @DisplayName("A plugin that sends a command with the client of the owner's generated type passes")
    void acceptsPluginThatSubmitsATypedCommand() {
        var classes = new ClassFileImporter()
            .importClasses(SubmitsTypedCommand.class, FixtureCommands.class, CancelBooking.class);

        assertDoesNotThrow(() -> PluginProfile.noStringCommandGateway.check(classes));
    }

    @Test
    @DisplayName("The profile runs every one of its rules: each is a static @ArchTest field")
    void everyRuleIsAnArchTest() {
        List<Field> rules = Arrays.stream(PluginProfile.class.getDeclaredFields())
            .filter(field -> ArchRule.class.isAssignableFrom(field.getType()))
            .toList();

        assertFalse(rules.isEmpty());
        assertTrue(rules.stream().anyMatch(field -> field.getName().equals("noStringCommandGateway")),
            rules.toString());
        for (Field field : rules) {
            assertTrue(field.isAnnotationPresent(ArchTest.class) && Modifier.isStatic(field.getModifiers()),
                field.getName());
        }
    }
}
