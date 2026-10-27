/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.tenant.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import io.brix.platform.tenant.service.FirstOwnerInviteeSetupCompletedListener;

/**
 * platform-tenant reaches its data only through PersistenceCapability repositories inside
 * owner units of work (runtime-shell 3.2.0 §7).
 */
class TenantPersistenceBoundaryTest {

    private static final JavaClasses TENANT = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("io.brix.platform.tenant");

    @Test
    void tenantDoesNotUseJdbcOrTheEntityManagerDirectly() {
        noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                "java.sql..",
                "javax.sql..",
                "org.springframework.jdbc..")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("jakarta.persistence.EntityManager")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("jakarta.persistence.EntityManagerFactory")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("org.hibernate.Session")
            .because("data access goes through PersistenceCapability repositories (BRX-RS-DATA-025)")
            .check(TENANT);
    }

    @Test
    void tenantDoesNotUseFrameworkTransactions() {
        // The setup-completed listener only listens for identity's commit; it opens no transaction
        // itself. Signalling across owners through the outbox would remove it (BRX-RS-DATA-029).
        noClasses()
            .that().doNotBelongToAnyOf(FirstOwnerInviteeSetupCompletedListener.class)
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.transaction..")
            .because("transactions are owner units of work opened by the runtime (BRX-RS-DATA-028)")
            .check(TENANT);
    }

    @Test
    void tenantRepositoriesAreNotCreatedByTheContainer() {
        noClasses()
            .should().beAnnotatedWith("org.springframework.data.jpa.repository.config.EnableJpaRepositories")
            .orShould().beAnnotatedWith("org.springframework.stereotype.Repository")
            .because("repositories are created through PersistenceCapability (BRX-RS-DATA-026)")
            .check(TENANT);
    }

    @Test
    void tenantDeclaresNoNativeQueries() {
        noMethods()
            .should().beAnnotatedWith("org.springframework.data.jpa.repository.query.Procedure")
            .because("native SQL and stored procedures are outside the persistence API whitelist")
            .check(TENANT);
    }

    @Test
    void tenantDoesNotInspectFrameworkPersistenceExceptions() {
        noClasses()
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.dao..")
            .because("the persistence adapter translates failures into PersistenceCapabilityException "
                + "(BRX-RS-DATA-042)")
            .check(TENANT);
    }
}
