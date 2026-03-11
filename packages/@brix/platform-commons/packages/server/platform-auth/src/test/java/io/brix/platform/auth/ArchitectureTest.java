/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.platform.auth;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import io.brix.architecture.guard.BrixArchitectureRules;

/**
 * Architecture Constraint Test.
 *
 * <p>Uses CommonsProfile to validate platform commons layer architecture rules.</p>
 */
@AnalyzeClasses(
    packages = "io.brix.platform.auth",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    @ArchTest
    static final ArchTests commonsRules = BrixArchitectureRules.commonsProfile();
}
