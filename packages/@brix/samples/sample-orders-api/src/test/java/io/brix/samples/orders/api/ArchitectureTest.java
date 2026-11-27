/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.api;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

import io.brix.architecture.guard.BrixArchitectureRules;
import io.brix.architecture.guard.ModuleClasses;

/**
 * sample-orders-api is the {@code moduleKind=plugin-api} artifact of sample-orders. It runs the guard's plugin profile
 * on its own classes, its {@code internal} contract package included, by its moduleKind (root BRX-RS-MANIFEST-009;
 * standard-link plan queue item 22, 22f, D180).
 */
@AnalyzeClasses(locations = ModuleClasses.class)
class ArchitectureTest {

    @ArchTest
    static final ArchTests pluginRules = BrixArchitectureRules.pluginProfile();
}
