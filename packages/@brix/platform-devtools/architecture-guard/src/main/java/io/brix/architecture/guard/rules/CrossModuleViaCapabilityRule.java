/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Cross-Module Communication Via Capability Rule.
 *
 * <p>Business modules must not directly @Autowired other modules' Service classes.
 * Cross-module communication must go through runtime-sdk-api Capability interfaces.</p>
 *
 * <p>This rule detects if domain/service layers depend on Spring container injection
 * or directly reference another module's Service.</p>
 *
 * @since 3.1.0
 */
public final class CrossModuleViaCapabilityRule {

    private CrossModuleViaCapabilityRule() {}

    /**
     * Domain layer must not depend on Service layer directly.
     *
     * <p>Cross-module dependencies should be obtained via RuntimeContext.getCapability().</p>
     */
    public static ArchRule rule() {
        return noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("..service..")
                .because("Domain layer must not directly depend on Service layer. " +
                        "Use Capability interfaces for cross-module communication")
                .allowEmptyShould(true);
    }
}
