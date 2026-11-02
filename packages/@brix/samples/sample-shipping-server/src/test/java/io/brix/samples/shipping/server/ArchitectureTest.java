/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.shipping.server;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

import io.brix.architecture.guard.BrixArchitectureRules;

/**
 * The guard's plugin rules on sample-shipping-server, which uses sample-orders only through its api contract
 * (standard-link plan §11 point 1).
 */
@AnalyzeClasses(packages = "io.brix.samples.shipping.server", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchTests rules = BrixArchitectureRules.pluginProfile();
}
