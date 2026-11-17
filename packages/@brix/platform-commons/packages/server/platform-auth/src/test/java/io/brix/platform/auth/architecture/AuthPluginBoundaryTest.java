/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.auth.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * platform-auth reads other plugins only through L2A capabilities, typed queries, internal contracts and their api
 * artifacts: it depends on no other platform plugin's core or server classes (root BRX-RS-LIFE-010, MESSAGE-001). The
 * guard's plugin rules do not count platform plugins as plugins, so this test does what identity's, tenant's and
 * audit's do (standard-link queue item 15, 15g).
 *
 * <p>Rules are evaluated with plain JUnit so they cannot silently report zero executed tests.</p>
 */
class AuthPluginBoundaryTest {

    private static final JavaClasses OWN = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("io.brix.platform.auth");

    @Test
    void noOtherPlatformPluginsImplementationIsUsed() {
        noClasses()
            .should().dependOnClassesThat(resideInAnyPackage("io.brix.platform.identity..", "io.brix.platform.tenant..",
                    "io.brix.platform.audit..", "io.brix.platform.admin..")
                .and(not(resideInAnyPackage("..api.."))))
            .because("plugins depend only on each other's api artifacts (BRX-RS-LIFE-010)")
            .check(OWN);
    }
}
