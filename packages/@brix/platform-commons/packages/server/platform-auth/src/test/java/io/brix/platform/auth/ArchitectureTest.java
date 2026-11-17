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

    /**
     * platform-auth is a {@code moduleKind=plugin-server} artifact (root BRX-RS-MANIFEST-009; standard-link queue item 15,
     * D137). The one member of its Spring auto-configuration that reads configuration with {@code @Value} is listed in
     * {@code META-INF/brix/guard/spring-value-readers.txt} under exemption EX-2026-009; the list only shrinks.
     */
    @ArchTest
    static final ArchTests pluginRules = BrixArchitectureRules.pluginProfile();
}
