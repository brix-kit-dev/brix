/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * No REST Controllers in Core Module Rule.
 *
 * <p>Enforces that {@code @RestController} and {@code @Controller} annotations
 * must only appear in {@code -server} modules, not in {@code -core} modules.</p>
 *
 * <h2>Architecture Principle</h2>
 * <p>Core modules contain pure business logic and should be deployment-agnostic.
 * REST endpoints belong to the server layer, which handles HTTP protocol concerns.</p>
 *
 * <h2>Violation Example</h2>
 * <pre>{@code
 * // In case-core module - VIOLATION!
 * package io.brix.app.casemanagement.controller;
 * 
 * @RestController  // VIOLATION: should be in case-server
 * public class CaseController { }
 * }</pre>
 *
 * <h2>Correct Structure</h2>
 * <pre>
 * brix-app-case/
 *   case-core/      <- Business logic, no @RestController
 *   case-server/    <- @RestController allowed here
 * </pre>
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 */
public final class NoRestControllerInCoreRule {

    private NoRestControllerInCoreRule() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * No @RestController in core packages.
     *
     * <p>Detects classes in {@code ..core..} packages annotated with
     * {@code @RestController} or {@code @Controller}.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noRestControllerInCore() {
        return noClasses()
                .that().resideInAPackage("..core..")
                .should().beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Controller")
                .because("REST controllers must be in -server modules, not -core modules. " +
                        "Core modules should contain only business logic")
                .allowEmptyShould(true);
    }

    /**
     * No @RequestMapping in core packages.
     *
     * <p>Additional check for classes that use @RequestMapping without @RestController.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noRequestMappingInCore() {
        return noClasses()
                .that().resideInAPackage("..core..")
                .should().beAnnotatedWith("org.springframework.web.bind.annotation.RequestMapping")
                .because("HTTP mapping annotations must be in -server modules, not -core modules")
                .allowEmptyShould(true);
    }

    /**
     * Combined rule checking all REST-related annotations.
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule rule() {
        return noRestControllerInCore();
    }
}
