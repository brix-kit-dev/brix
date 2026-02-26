/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import io.brix.architecture.guard.ArchitectureLayers;

/**
 * No Direct Infrastructure Adapter Dependency Rule.
 *
 * <p>Business modules can only depend on runtime-sdk-api contracts.
 * Direct imports from infra-adapter-* packages are forbidden.</p>
 *
 * @since 3.1.0
 */
public final class NoInfraAdapterRule {

    private NoInfraAdapterRule() {}

    /** Forbid dependency on all infrastructure adapter packages. */
    public static ArchRule rule() {
        return noClasses()
                .should().dependOnClassesThat()
                .resideInAPackage(ArchitectureLayers.INFRA_ADAPTER_ROOT)
                .because("Business modules must not depend on infrastructure adapters. " +
                        "Use Capability interfaces from runtime-sdk-api");
    }
}
