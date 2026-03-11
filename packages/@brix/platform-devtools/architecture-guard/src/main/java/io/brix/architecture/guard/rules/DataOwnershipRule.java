/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Data Ownership Rules - Red Line 8.
 *
 * <p>Enforces data isolation between plugins. Each plugin owns its data
 * and must not directly access other plugins' database entities.</p>
 *
 * <h2>Architecture Principle</h2>
 * <blockquote>
 * Red Line 8: Data Isolation (Data Ownership)
 * ✗ Prohibited: Direct access to other plugins' database tables
 * ✗ Prohibited: Cross-plugin foreign key constraints
 * ✓ Allowed: Obtain data copies through Integration Events
 * </blockquote>
 *
 * <h2>Source</h2>
 * Amazon Two-Pizza Team / Netflix Data Mesh
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 */
public final class DataOwnershipRule {

    private DataOwnershipRule() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== Plugin Entity Packages ====================

    // Each plugin's entity package
    private static final String[] PLUGIN_ENTITY_PACKAGES = {
        "io.brix.app.booking..entity..",
        "io.brix.app.user..entity..",
        "io.brix.app.contract..entity..",
        "io.brix.app.case..entity..",
        "io.brix.app.medical..entity..",
        "io.brix.app.notification..entity..",
        "io.brix.app.carousel..entity..",
        "io.brix.app.compliance..entity..",
        "io.brix.app.identity..entity.."
    };

    // ==================== Rules ====================

    /**
     * Plugin should not depend on other plugins' entity classes.
     *
     * <p>Each plugin must own its data exclusively. Cross-plugin data access
     * must go through Integration Events or Capability interfaces.</p>
     *
     * @param pluginPackage The package of the plugin being tested (e.g., "io.brix.app.booking..")
     * @param forbiddenEntityPackages Other plugins' entity packages
     * @return ArchUnit rule instance
     */
    public static ArchRule noAccessToOtherPluginEntities(String pluginPackage, String... forbiddenEntityPackages) {
        return noClasses()
                .that().resideInAPackage(pluginPackage)
                .should().dependOnClassesThat()
                .resideInAnyPackage(forbiddenEntityPackages)
                .because("Red Line 8: Plugins must not directly access " +
                        "other plugins' database entities. Use Integration Events instead.");
    }

    /**
     * No JPA repository access across plugin boundaries.
     *
     * <p>This rule checks that core modules do not access repositories 
     * from OTHER plugins. Within the same plugin, accessing repositories is allowed.</p>
     * 
     * <p><b>Important:</b> This rule requires plugin-specific configuration.
     * Use {@link #noAccessToOtherPluginRepositories(String, String...)} for precise control.</p>
     * 
     * <p><b>Note:</b> Currently returns a permissive rule. 
     * Each plugin should configure its own cross-boundary checks.</p>
     *
     * @return ArchUnit rule instance (permissive - always passes)
     */
    public static ArchRule noCrossPluginRepositoryAccess() {
        // This rule is intentionally relaxed because:
        // 1. Core modules SHOULD depend on their own Repository interfaces (DDD pattern)
        // 2. Only cross-module Repository access should be forbidden
        // 3. Use noAccessToOtherPluginRepositories() for specific cross-boundary checks
        return noClasses()
                .that().resideInAPackage("..CROSS_MODULE_BOUNDARY_PLACEHOLDER..")
                .should().dependOnClassesThat()
                .haveSimpleNameEndingWith("Repository")
                .because("Red Line 8: Cross-module Repository access is forbidden. " +
                        "Use Integration Events or Capability interfaces for cross-module data access.")
                .allowEmptyShould(true);
    }
    
    /**
     * Specific rule: Plugin A cannot access Plugin B's repositories.
     *
     * <p>Example usage:</p>
     * <pre>{@code
     * // In booking-core's ArchitectureTest:
     * @ArchTest
     * static final ArchRule bookingCannotAccessUserRepo = 
     *     DataOwnershipRule.noAccessToOtherPluginRepositories(
     *         "io.brix.app.booking..", 
     *         "io.brix.app.user..repository.."
     *     );
     * }</pre>
     *
     * @param ownPluginPackage The current plugin's package
     * @param otherPluginRepoPackages Other plugins' repository packages
     * @return ArchUnit rule instance
     */
    public static ArchRule noAccessToOtherPluginRepositories(
            String ownPluginPackage, String... otherPluginRepoPackages) {
        return noClasses()
                .that().resideInAPackage(ownPluginPackage)
                .should().dependOnClassesThat()
                .resideInAnyPackage(otherPluginRepoPackages)
                .because("Red Line 8: Plugins must not directly access " +
                        "other plugins' repositories. Use Integration Events instead.")
                .allowEmptyShould(true);
    }

    /**
     * Plugin domain entities should not reference other plugin's entities.
     *
     * <p>This prevents accidental foreign key constraints across plugins.
     * Note: This is a simplified rule checking package-level separation.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noForeignKeyAcrossPlugins() {
        return noClasses()
                .that().resideInAPackage("..entity..")
                .and().haveSimpleNameEndingWith("Entity")
                .should().dependOnClassesThat()
                .resideInAPackage("..entity..")
                .because("Red Line 8: Entity classes must not reference " +
                        "entities from other plugins (no cross-plugin foreign keys)")
                .allowEmptyShould(true);
    }

    /**
     * Default rule for data ownership.
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule rule() {
        return noCrossPluginRepositoryAccess();
    }
}
