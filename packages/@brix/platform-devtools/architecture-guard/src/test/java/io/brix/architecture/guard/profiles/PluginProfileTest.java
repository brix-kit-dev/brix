/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.architecture.guard.profiles;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class PluginProfileTest {

    @Test
    void rejectsPluginThatImportsAnInfrastructureAdapter() {
        var classes = new ClassFileImporter().importPackages(
            "io.brix.architecture.guard.testfixtures.violating.adapter");

        assertThrows(AssertionError.class, () -> PluginProfile.noInfraAdapterDependency.check(classes));
    }
}
