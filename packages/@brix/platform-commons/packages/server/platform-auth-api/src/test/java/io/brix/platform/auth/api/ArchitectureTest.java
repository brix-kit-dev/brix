/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.auth.api;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import io.brix.architecture.guard.BrixArchitectureRules;

/**
 * platform-auth-api is the {@code moduleKind=plugin-api} artifact of platform-auth. It runs the plugin profile, by its
 * moduleKind (root BRX-RS-MANIFEST-009), and the commons profile (standard-link queue item 15, D137).
 */
@AnalyzeClasses(
    packages = "io.brix.platform.auth.api",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    @ArchTest
    static final ArchTests pluginRules = BrixArchitectureRules.pluginProfile();

    @ArchTest
    static final ArchTests platformRules = BrixArchitectureRules.commonsProfile();
}
