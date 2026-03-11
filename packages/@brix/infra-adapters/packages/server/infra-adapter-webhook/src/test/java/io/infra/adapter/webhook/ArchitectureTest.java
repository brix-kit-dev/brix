/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.infra.adapter.webhook;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import io.brix.architecture.guard.BrixArchitectureRules;

/**
 * Architecture constraint tests for the adapter layer.
 *
 * <p>Uses AdapterProfile to validate infrastructure adapter layer architecture rules.</p>
 */
@AnalyzeClasses(
    packages = "io.infra.adapter.webhook",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    @ArchTest
    static final ArchTests adapterRules = BrixArchitectureRules.adapterProfile();
}
