/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.brix.architecture.guard.ArchitectureLayers;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
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

    // ==================== Rules ====================

    /**
     * Plugin should not depend on other plugins' entity classes.
     *
     * <p>Each plugin must own its data exclusively. Cross-plugin data access
     * must go through Integration Events or Capability interfaces.</p>
     *
     * @param pluginPackage The package of the plugin being tested (e.g., "io.brix.samples.orders..")
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
     * No repository access across owner boundaries.
     *
     * <p>The previous placeholder rule deliberately matched an impossible
     * package and therefore could not block violations. Phase 4 makes the
     * default rule executable: plugin or platform core classes may not import
     * another owner's Repository type. Same-owner repositories remain allowed
     * for owner application services.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noCrossPluginRepositoryAccess() {
        return classes()
                .that().resideInAnyPackage("io.brix..core..", "io.brix..service..", "io.brix..app..")
                .should(notDependOnForeignRepository())
                .because("A-8/Phase 4: cross-Owner Repository access is forbidden; use typed contracts or events")
                .allowEmptyShould(true);
    }

    /**
     * Plugin core and domain code must not depend on raw persistence APIs.
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noRawPersistenceApiInCoreOrDomain() {
        return noClasses()
                .that().resideInAnyPackage("..core..", "..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                    "jakarta.persistence..",
                    "javax.persistence..",
                    "org.springframework.data.jpa..",
                    "org.jooq..",
                    "java.sql..")
                .because("A-8/Phase 4: plugin core/domain must stay persistence-agnostic")
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
     *         "io.brix.samples.orders..",
     *         "io.brix.samples.shipping..repository.."
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

    private static ArchCondition<JavaClass> notDependOnForeignRepository() {
        return new ArchCondition<>("not depend on another Data Owner repository") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String originOwner = ownerPrefix(item.getPackageName());
                if (originOwner.isBlank()) {
                    return;
                }
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (!target.getSimpleName().endsWith("Repository")) {
                        continue;
                    }
                    String targetOwner = ownerPrefix(target.getPackageName());
                    if (!targetOwner.isBlank() && !originOwner.equals(targetOwner)) {
                        events.add(SimpleConditionEvent.violated(item,
                            item.getName() + " depends on foreign repository " + target.getName()));
                    }
                }
            }
        };
    }

    /**
     * Returns the Data Owner of a package: its plugin ({@link ArchitectureLayers#pluginOf(String)}) or its
     * platform-commons module ({@code io.brix.platform.<module>}), or an empty string for anything else.
     */
    private static String ownerPrefix(String packageName) {
        String plugin = ArchitectureLayers.pluginOf(packageName);
        if (plugin != null) {
            return plugin;
        }
        String[] segments = packageName.split("\\.");
        if (segments.length >= 4 && packageName.startsWith("io.brix.platform.")) {
            return join(segments, 0, 4);
        }
        return "";
    }

    private static String join(String[] segments, int startInclusive, int endExclusive) {
        StringBuilder builder = new StringBuilder();
        for (int index = startInclusive; index < endExclusive; index++) {
            if (builder.length() > 0) {
                builder.append('.');
            }
            builder.append(segments[index]);
        }
        return builder.toString();
    }
}
