/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.identity;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import io.brix.architecture.guard.BrixArchitectureRules;

/**
 * platform-identity is the {@code moduleKind=plugin-server} artifact of the identity Data Owner. It runs the plugin
 * profile, by its moduleKind (root BRX-RS-MANIFEST-009), and the commons profile, which checks the whole module for
 * business plugins, Host source and the Runtime implementation (standard-link queue item 15, D134). The members of its
 * Spring auto-configuration that read configuration with {@code @Value} are listed in
 * {@code META-INF/brix/guard/spring-value-readers.txt} under exemption EX-2026-009 (D135); the list only shrinks.
 */
@AnalyzeClasses(
    packages = "io.brix.platform.identity",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    @ArchTest
    static final ArchTests pluginRules = BrixArchitectureRules.pluginProfile();

    @ArchTest
    static final ArchTests platformRules = BrixArchitectureRules.commonsProfile();
}
