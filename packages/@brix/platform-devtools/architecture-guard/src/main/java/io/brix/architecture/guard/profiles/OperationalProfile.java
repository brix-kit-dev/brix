/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.architecture.guard.profiles;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * A-25/A-26 rules for {@code moduleKind=platform-operational} artifacts.
 *
 * @author Brix Architecture Team
 * @since 3.2.0
 */
public final class OperationalProfile {

    private static final String[] FORBIDDEN_IMPLEMENTATION_PACKAGES = {
        "io.brix.app..",
        "io.brix.enterprise..",
        "io.brix.infra..",
        "org.springframework.context..",
        "org.springframework.beans.factory..",
        "org.springframework.web.bind.annotation.."
    };

    private OperationalProfile() {
    }

    /**
     * Operational code cannot reach Plugin, Adapter, Host, container, or MVC implementation APIs.
     */
    @ArchTest
    public static final ArchRule operationalImplementationBoundary = noClasses()
        .that().resideInAPackage("..operational..")
        .should().dependOnClassesThat()
        .resideInAnyPackage(FORBIDDEN_IMPLEMENTATION_PACKAGES)
        .because("A-26 requires platform-operational code to use only the restricted L2B operational SPI")
        .allowEmptyShould(true);

    /**
     * Operational code cannot obtain the general capability Registry.
     */
    @ArchTest
    public static final ArchRule operationalCannotAccessCapabilityRegistry = noClasses()
        .that().resideInAPackage("..operational..")
        .should().dependOnClassesThat()
        .haveFullyQualifiedName("io.runtime.sdk.capability.registry.CapabilityRegistry")
        .because("A-26 requires OperationalContext and an isolated internal namespace")
        .allowEmptyShould(true);
}
