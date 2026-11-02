/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.shipping.core;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

import io.brix.architecture.guard.BrixArchitectureRules;

/**
 * The guard's plugin rules on sample-shipping-core: no other plugin's implementation, no Runtime implementation,
 * no infrastructure clients (standard-link plan §11 point 1).
 */
@AnalyzeClasses(packages = "io.brix.samples.shipping.core", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchTests rules = BrixArchitectureRules.pluginProfile();
}
