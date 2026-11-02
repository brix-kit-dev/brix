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

/**
 * No Direct Plugin-to-Plugin Dependency Rule.
 *
 * <p>A plugin may use another plugin's published contract, its {@code api} package, and nothing else of it (root
 * BRX-RS-LAYER-004, A-9). Plugins are found under {@link ArchitectureLayers#PLUGIN_ROOTS}.</p>
 *
 * <h2>Example Violation</h2>
 * <pre>{@code
 * package io.brix.samples.orders.core;
 *
 * import io.brix.samples.shipping.core.Shipment; // VIOLATION: another plugin's implementation
 * }</pre>
 *
 * <h2>Correct Approach</h2>
 * <pre>{@code
 * package io.brix.samples.orders.server;
 *
 * import io.brix.samples.shipping.api.FindShipmentRequest; // the other plugin's contract
 * }</pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @AnalyzeClasses(packages = "io.brix.samples.orders")
 * class ArchitectureTest {
 *     @ArchTest
 *     static final ArchRule noDirectPluginDependency =
 *         NoDirectPluginDependencyRule.forPlugin("io.brix.samples.orders");
 * }
 * }</pre>
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 */
public final class NoDirectPluginDependencyRule {

    private NoDirectPluginDependencyRule() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Creates a rule that forbids the specified plugin from depending on other plugins.
     *
     * @param currentPluginPackage the package of the current plugin (e.g., "io.brix.samples.orders")
     * @return ArchUnit rule instance
     */
    public static ArchRule forPlugin(String currentPluginPackage) {
        return classes()
                .should(new NoOtherPluginDependencyCondition(currentPluginPackage))
                .because("Plugin modules must not directly depend on other plugins. Use another plugin's api "
                        + "contract (typed Query, Command, Event) for cross-plugin communication");
    }

    /**
     * Creates a rule using automatic plugin detection.
     *
     * <p>Each analyzed class under a plugin root may depend on its own plugin and on other plugins' {@code api}
     * packages only.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule rule() {
        return classes()
                .should(new NoOtherPluginDependencyCondition(null))
                .because("Plugin modules must not directly depend on other plugins. " +
                        "Use another plugin's api contract for cross-plugin communication")
                .allowEmptyShould(true);
    }

    /**
     * Reports each dependency of a plugin class on another plugin's implementation. It is used with
     * {@code classes().should(...)}: under {@code noClasses()} ArchUnit inverts a condition's events, and the
     * violations it reports would pass.
     */
    private static class NoOtherPluginDependencyCondition extends ArchCondition<JavaClass> {

        private final String currentPluginPackage;

        NoOtherPluginDependencyCondition(String currentPluginPackage) {
            super("not depend on other plugins except their api contracts");
            this.currentPluginPackage = currentPluginPackage;
        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            String classPackage = javaClass.getPackageName();
            String plugin = currentPluginPackage != null
                    ? currentPluginPackage
                    : ArchitectureLayers.pluginOf(classPackage);
            if (plugin == null || !(classPackage.equals(plugin) || classPackage.startsWith(plugin + "."))) {
                return; // not a class of the plugin under test
            }
            for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                String targetPackage = target.getPackageName();
                String targetPlugin = ArchitectureLayers.pluginOf(targetPackage);
                if (targetPlugin != null && !targetPlugin.equals(plugin)
                        && !ArchitectureLayers.isPluginContract(targetPackage)) {
                    events.add(SimpleConditionEvent.violated(javaClass, String.format(
                            "%s depends on %s, an implementation class of plugin %s; only its api package may be used",
                            javaClass.getName(), target.getName(), targetPlugin)));
                }
            }
        }
    }
}
