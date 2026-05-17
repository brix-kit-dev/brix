package io.brix.architecture.guard.profiles;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;
import io.brix.architecture.guard.rules.CoreSpringBoundaryRule;

/**
 * Plugin-core architecture constraints for the v3.0.9 Phase 1 adjudication.
 *
 * <p>This profile composes the existing plugin red-line profile and adds the
 * R1.1 decision that plugin-core may contain application services,
 * transaction boundaries, Spring Data repository contracts, and JPA/Jakarta
 * persistence contracts. HTTP controllers and infrastructure implementation
 * clients still belong outside core.</p>
 */
public class CoreProfileV2 {

    @ArchTest
    static final ArchTests pluginBaseline = ArchTests.in(PluginProfile.class);

    @ArchTest
        static final ArchRule noHttpEndpointAnnotationsInCoreModule =
            CoreSpringBoundaryRule.noHttpEndpointAnnotationsInCoreModule();

    @ArchTest
    static final ArchRule noWebBindingDependencyInCore = CoreSpringBoundaryRule.noWebBindingDependencyInCore();

    @ArchTest
    static final ArchRule transactionalOnlyInApplicationBoundary =
            CoreSpringBoundaryRule.transactionalOnlyInApplicationBoundary();
}