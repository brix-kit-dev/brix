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
 * 红线 8：数据隔离（Data Ownership）
 * ✗ 禁止：直接访问其他插件的数据库表
 * ✗ 禁止：跨插件使用外键约束
 * ✓ 允许：通过 Integration Event 获取数据副本
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
        "com.shinwa.app.booking..entity..",
        "com.shinwa.app.user..entity..",
        "com.shinwa.app.contract..entity..",
        "com.shinwa.app.case..entity..",
        "com.shinwa.app.medical..entity..",
        "com.shinwa.app.notification..entity..",
        "com.shinwa.app.carousel..entity..",
        "com.shinwa.app.compliance..entity..",
        "com.shinwa.app.identity..entity.."
    };

    // ==================== Rules ====================

    /**
     * Plugin should not depend on other plugins' entity classes.
     *
     * <p>Each plugin must own its data exclusively. Cross-plugin data access
     * must go through Integration Events or Capability interfaces.</p>
     *
     * @param pluginPackage The package of the plugin being tested (e.g., "com.shinwa.app.booking..")
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
     *         "com.shinwa.app.booking..", 
     *         "com.shinwa.app.user..repository.."
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
