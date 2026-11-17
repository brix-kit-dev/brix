/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import io.brix.architecture.guard.BrixArchitectureRules;

/**
 * platform-admin is a {@code moduleKind=platform-operational} module (root BRX-RS-LAYER-017): it runs the operational
 * profile over all its classes.
 */
@AnalyzeClasses(
    packages = "io.brix.platform.admin",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    @ArchTest
    static final ArchTests operationalRules = BrixArchitectureRules.operationalProfile();
}
