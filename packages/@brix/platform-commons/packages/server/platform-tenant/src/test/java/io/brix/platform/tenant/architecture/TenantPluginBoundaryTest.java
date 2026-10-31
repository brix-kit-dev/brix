/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.tenant.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * platform-tenant reads other plugins only through L2A capabilities, typed queries, internal contracts and their api
 * artifacts: it depends on no other plugin's core or server classes (root BRX-RS-LIFE-010, MESSAGE-001;
 * standard-link plan L3.3).
 *
 * <p>Rules are evaluated with plain JUnit so they cannot silently report zero executed tests.</p>
 */
class TenantPluginBoundaryTest {

    private static final JavaClasses OWN = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("io.brix.platform.tenant");

    @Test
    void noOtherPlatformPluginsImplementationIsUsed() {
        noClasses()
            .should().dependOnClassesThat(resideInAnyPackage("io.brix.platform.auth..", "io.brix.platform.identity..", "io.brix.platform.audit..", "io.brix.platform.admin..")
                .and(not(resideInAnyPackage("..api.."))))
            .because("plugins depend only on each other's api artifacts (BRX-RS-LIFE-010)")
            .check(OWN);
    }
}
