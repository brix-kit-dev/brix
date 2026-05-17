/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.platform.auth.reactive;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import io.brix.architecture.guard.BrixArchitectureRules;

/**
 * Architecture Constraint Test for the Reactive OAuth2 module.
 *
 * <p>Validates that this module follows platform commons layer architecture rules,
 * including no forbidden infrastructure dependencies and proper layering.</p>
 */
@AnalyzeClasses(
    packages = "io.brix.platform.auth.reactive",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    @ArchTest
    static final ArchTests commonsRules = BrixArchitectureRules.commonsProfile();
}
