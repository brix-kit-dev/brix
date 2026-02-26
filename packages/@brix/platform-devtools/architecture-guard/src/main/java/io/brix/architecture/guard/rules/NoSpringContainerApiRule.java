/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import io.brix.architecture.guard.ArchitectureLayers;

/**
 * No Spring Container API Dependency Rule.
 *
 * <p>Business code must not directly use ApplicationContext, BeanFactory, etc.
 * Use constructor injection for dependencies and RuntimeContext for capabilities.</p>
 *
 * @since 3.1.0
 */
public final class NoSpringContainerApiRule {

    private NoSpringContainerApiRule() {}

    public static ArchRule rule() {
        return noClasses()
                .should().dependOnClassesThat()
                .haveFullyQualifiedName(ArchitectureLayers.APPLICATION_CONTEXT)
                .orShould().dependOnClassesThat()
                .haveFullyQualifiedName(ArchitectureLayers.BEAN_FACTORY)
                .because("Direct Spring container API usage (ApplicationContext/BeanFactory) is forbidden. " +
                        "Use constructor injection or RuntimeContext for capabilities");
    }
}
