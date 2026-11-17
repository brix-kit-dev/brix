/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.architecture.guard.profiles;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.Arrays;
import java.util.stream.Stream;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import io.brix.architecture.guard.ArchitectureLayers;
import io.brix.architecture.guard.rules.OperationalBoundaryRule;

/**
 * Rules for {@code moduleKind=platform-operational} modules (root A-26, BRX-RS-LAYER-015, LAYER-016).
 *
 * <p>Every rule checks every class the module's test imports, whatever its package, and fails when the test imports
 * none. The test therefore imports the module's own classes and nothing else:</p>
 * <pre>{@code
 * @AnalyzeClasses(packages = "io.brix.platform.admin", importOptions = ImportOption.DoNotIncludeTests.class)
 * class ArchitectureTest {
 *     @ArchTest
 *     static final ArchTests operationalRules = BrixArchitectureRules.operationalProfile();
 * }
 * }</pre>
 *
 * @author Brix Architecture Team
 * @since 3.2.0
 */
public final class OperationalProfile {

    private static final String[] FORBIDDEN_IMPLEMENTATION_PACKAGES = Stream.concat(
            Arrays.stream(ArchitectureLayers.PLUGIN_ROOTS),
            Stream.of(
                "io.brix.enterprise..",
                ArchitectureLayers.INFRA_ADAPTER_ROOT,
                "org.springframework.context..",
                "org.springframework.beans.factory..",
                "org.springframework.web.bind.annotation.."))
        .toArray(String[]::new);

    private OperationalProfile() {
    }

    /**
     * Operational code cannot reach Plugin, Adapter, Host, container, or MVC implementation APIs.
     */
    @ArchTest
    public static final ArchRule operationalImplementationBoundary = noClasses()
        .should().dependOnClassesThat()
        .resideInAnyPackage(FORBIDDEN_IMPLEMENTATION_PACKAGES)
        .because("A-26 requires platform-operational code to use only the restricted L2B operational SPI");

    /**
     * Operational code cannot obtain the general capability Registry.
     */
    @ArchTest
    public static final ArchRule operationalCannotAccessCapabilityRegistry = noClasses()
        .should().dependOnClassesThat()
        .haveFullyQualifiedName("io.runtime.sdk.capability.registry.CapabilityRegistry")
        .because("A-26 requires OperationalContext and an isolated internal namespace");

    /**
     * Operational code reaches the Runtime only through the operational SPI.
     */
    @ArchTest
    public static final ArchRule operationalRuntimeOnlyThroughOperationalSpi =
        OperationalBoundaryRule.runtimeOnlyThroughOperationalSpi();

    /**
     * Operational code reaches other platform modules only through a Data Owner's internal contracts and api.
     */
    @ArchTest
    public static final ArchRule operationalOwnersOnlyThroughContracts =
        OperationalBoundaryRule.ownersOnlyThroughContracts();
}
