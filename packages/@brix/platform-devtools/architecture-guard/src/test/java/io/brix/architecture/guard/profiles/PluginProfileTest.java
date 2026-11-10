/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.architecture.guard.profiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.brix.samples.fixtureauth.core.AuthorizesOnPermission;
import io.brix.samples.fixtureauth.core.AuthorizesOnRoleName;
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
}
