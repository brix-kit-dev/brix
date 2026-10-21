/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.identity.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Keeps platform-identity on the persistence API whitelist of root blueprint BRX-RS-DATA-024 and
 * BRX-RS-DATA-025.
 *
 * <p>Rules are evaluated with plain JUnit so they cannot silently report zero executed tests.</p>
 */
class IdentityPersistenceBoundaryTest {

    private static final JavaClasses IDENTITY = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("io.brix.platform.identity");

    @Test
    void identityDoesNotUseJdbcOrTheEntityManagerDirectly() {
        noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                "java.sql..",
                "javax.sql..",
                "org.springframework.jdbc..")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("jakarta.persistence.EntityManager")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("jakarta.persistence.EntityManagerFactory")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("org.hibernate.Session")
            .because("data access goes through PersistenceCapability repositories (BRX-RS-DATA-025)")
            .check(IDENTITY);
    }

    @Test
    void identityDoesNotUseFrameworkTransactions() {
        noClasses()
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.transaction..")
            .because("transactions are owner units of work opened by the runtime (BRX-RS-DATA-028)")
            .check(IDENTITY);
    }

    @Test
    void identityRepositoriesAreNotCreatedByTheContainer() {
        noClasses()
            .should().beAnnotatedWith("org.springframework.data.jpa.repository.config.EnableJpaRepositories")
            .orShould().beAnnotatedWith("org.springframework.stereotype.Repository")
            .because("repositories are created through PersistenceCapability (BRX-RS-DATA-026)")
            .check(IDENTITY);
    }

    @Test
    void identityDeclaresNoNativeQueries() {
        noMethods()
            .should().beAnnotatedWith("org.springframework.data.jpa.repository.query.Procedure")
            .because("native SQL and stored procedures are outside the persistence API whitelist")
            .check(IDENTITY);
    }
}
