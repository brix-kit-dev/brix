package io.infra.adapter.identity.provider;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * identity-auth {@code BRX-IA-LAYER-001}: the identity provider Adapter depends only on Layer 2A
 * contracts and infrastructure libraries, never on an Owner or on the Runtime internals.
 */
class IdentityProviderLayeringTest {

    private final JavaClasses adapter = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.infra.adapter.identity.provider");

    @Test
    void readsIdentityAndTenantDataOnlyThroughLayer2aContracts() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage(
                        "io.brix..",
                        "io.runtime.orchestrator..",
                        "io.runtime.manifest..",
                        "io.infra.adapter.redis..",
                        "jakarta.persistence..",
                        "org.springframework.data..",
                        "org.springframework.jdbc..")
                .because("BRX-IA-LAYER-001: identity and tenant data are reached through Layer 2A contracts only")
                .check(adapter);
    }

    @Test
    void dependsOnlyOnLayer2aContractsAndInfrastructureLibraries() {
        classes().should().onlyDependOnClassesThat().resideInAnyPackage(
                        "io.infra.adapter.identity.provider..",
                        "io.runtime.sdk.capability..",
                        "java..", "javax.crypto..",
                        "jakarta.servlet..",
                        "org.springframework..",
                        "org.slf4j..",
                        "com.nimbusds..",
                        "tools.jackson..",
                        "com.fasterxml.jackson.annotation..")
                .check(adapter);
    }
}
