/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import io.brix.architecture.guard.ArchitectureLayers;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * No Circular Dependency Rules - Red Line 11.
 *
 * <p>Enforces acyclic dependency graph between plugins and layers.</p>
 *
 * <h2>Architecture Principle</h2>
 * <blockquote>
 * Red Line 11: No Circular Dependency
 * ✗ Prohibited: Plugin A depends on Plugin B while Plugin B depends on Plugin A
 * ✗ Prohibited: Core layer depends on Server/Adapter layer
 * </blockquote>
 *
 * <p>runtime-sdk-api's independence from implementation layers is checked by {@code SdkProfile}.</p>
 *
 * <h2>Source</h2>
 * Clean Architecture / SOLID Dependency Inversion Principle
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 */
public final class NoCyclicDependencyRule {

    private NoCyclicDependencyRule() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== Cycle Detection ====================

    /**
     * No cycles between plugin modules.
     *
     * <p>Plugin A cannot depend on Plugin B if Plugin B depends on Plugin A. One slice per plugin, as
     * {@link ArchitectureLayers#pluginOf(String)} finds it; classes outside the plugin roots belong to no slice.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noCyclesBetweenPlugins() {
        return SlicesRuleDefinition.slices()
                .assignedFrom(new SliceAssignment() {
                    @Override
                    public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
                        String plugin = ArchitectureLayers.pluginOf(javaClass.getPackageName());
                        return plugin == null ? SliceIdentifier.ignore() : SliceIdentifier.of(plugin);
                    }

                    @Override
                    public String getDescription() {
                        return "plugins under " + String.join(", ", ArchitectureLayers.PLUGIN_ROOTS);
                    }
                })
                .should().beFreeOfCycles()
                .because("Red Line 11: Plugins must not have circular dependencies. " +
                        "Use Integration Events for bi-directional communication.");
    }

    /**
     * No cycles in infra-adapters.
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noCyclesBetweenAdapters() {
        return SlicesRuleDefinition.slices()
                .matching("io.infra.adapter.(*)..")
                .should().beFreeOfCycles()
                .because("Red Line 11: Adapters must not have circular dependencies.");
    }

    // ==================== Layer Rules ====================

    /**
     * Core layer must not depend on Server layer.
     *
     * <p>Dependencies should only flow downward: Server -&gt; Core -&gt; Domain.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule coreNotDependOnServer() {
        return noClasses()
                .that().resideInAPackage("..core..")
                .should().dependOnClassesThat()
                .resideInAPackage("..server..")
                .because("Red Line 11: Core layer must not depend on Server layer. " +
                        "Only downward dependencies allowed.")
                .allowEmptyShould(true);
    }

    /**
     * Core layer must not depend on Adapter layer.
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule coreNotDependOnAdapter() {
        return noClasses()
                .that().resideInAPackage("..core..")
                .should().dependOnClassesThat()
                .resideInAPackage("..adapter..")
                .because("Red Line 11: Core layer must not depend on Adapter layer.")
                .allowEmptyShould(true);
    }

    /**
     * Domain layer must not depend on infrastructure.
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule domainNotDependOnInfrastructure() {
        return noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "..server..",
                        "..adapter..",
                        "..repository..",
                        "org.springframework.web..",
                        "org.springframework.data.."
                )
                .because("Red Line 11: Domain layer must be pure and not depend on infrastructure.")
                .allowEmptyShould(true);
    }

    /**
     * Default rule: check for plugin cycles.
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule rule() {
        return noCyclesBetweenPlugins();
    }
}
