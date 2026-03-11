/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * No Direct Plugin-to-Plugin Dependency Rule.
 *
 * <p>Enforces that business plugin modules cannot directly depend on other plugin modules.
 * Cross-plugin communication must go through Capability interfaces.</p>
 *
 * <h2>Example Violation</h2>
 * <pre>{@code
 * package io.brix.app.booking.service;
 * 
 * import io.brix.app.identity.service.UserService; // VIOLATION!
 * 
 * public class BookingService {
 *     @Autowired
 *     private UserService userService; // VIOLATION!
 * }
 * }</pre>
 *
 * <h2>Correct Approach</h2>
 * <pre>{@code
 * package io.brix.app.booking.service;
 * 
 * import io.runtime.sdk.context.RuntimeContext;
 * import io.runtime.sdk.capability.AuthContextCapability;
 * 
 * public class BookingService {
 *     private final AuthContextCapability authCapability;
 *     
 *     public BookingService(RuntimeContext context) {
 *         this.authCapability = context.getCapability(AuthContextCapability.class);
 *     }
 * }
 * }</pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @AnalyzeClasses(packages = "io.brix.app.booking")
 * class ArchitectureTest {
 *     @ArchTest
 *     static final ArchRule noDirectPluginDependency = 
 *         NoDirectPluginDependencyRule.forPlugin("io.brix.app.booking");
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

    /** All known plugin package prefixes */
    private static final Set<String> PLUGIN_PACKAGES = new HashSet<>(Arrays.asList(
            "io.brix.app.booking",
            "io.brix.app.identity",
            "io.brix.app.products",
            "io.brix.app.partners",
            "io.brix.app.contracts",
            "io.brix.app.carousel",
            "io.brix.app.messenger",
            "io.brix.app.storage",
            "io.brix.app.intake",
            "io.brix.app.compliance",
            "io.brix.app.casemanagement"
    ));

    /** Pattern to extract plugin package from class name */
    private static final Pattern PLUGIN_PACKAGE_PATTERN = 
            Pattern.compile("^(io\\.brix\\.app\\.[a-z]+|io\\.brix\\.enterprise\\.app\\.[a-z]+)\\.");

    /**
     * Creates a rule that forbids the specified plugin from depending on other plugins.
     *
     * @param currentPluginPackage the package of the current plugin (e.g., "io.brix.app.booking")
     * @return ArchUnit rule instance
     */
    public static ArchRule forPlugin(String currentPluginPackage) {
        return noClasses()
                .should(new NoOtherPluginDependencyCondition(currentPluginPackage))
                .because("Plugin modules must not directly depend on other plugins. " +
                        "Use Capability interfaces (AuthContextCapability, EventBusCapability, etc.) " +
                        "for cross-plugin communication");
    }

    /**
     * Creates a rule using automatic plugin detection.
     *
     * <p>Detects the current plugin package from the analyzed classes
     * and forbids dependencies on other known plugin packages.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule rule() {
        return noClasses()
                .should(new NoOtherPluginDependencyCondition(null))
                .because("Plugin modules must not directly depend on other plugins. " +
                        "Use Capability interfaces for cross-plugin communication")
                .allowEmptyShould(true);
    }

    /**
     * Condition that checks if a class depends on another plugin's classes.
     */
    private static class NoOtherPluginDependencyCondition extends ArchCondition<JavaClass> {

        private final String currentPluginPackage;

        NoOtherPluginDependencyCondition(String currentPluginPackage) {
            super("not depend on other plugin modules");
            this.currentPluginPackage = currentPluginPackage;
        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            String classPackage = javaClass.getPackageName();
            String effectiveCurrentPlugin = currentPluginPackage;

            // Auto-detect current plugin if not specified
            if (effectiveCurrentPlugin == null) {
                effectiveCurrentPlugin = extractPluginPackage(classPackage);
                if (effectiveCurrentPlugin == null) {
                    return; // Not a plugin class, skip
                }
            }

            // Check if this class is within the current plugin
            if (!classPackage.startsWith(effectiveCurrentPlugin)) {
                return; // Class is not in current plugin, skip
            }

            // Check all dependencies
            for (JavaClass dependency : javaClass.getDirectDependenciesFromSelf().stream()
                    .map(dep -> dep.getTargetClass())
                    .toList()) {
                
                String depPackage = dependency.getPackageName();
                String depPlugin = extractPluginPackage(depPackage);

                // If dependency is from another plugin, report violation
                if (depPlugin != null && !depPlugin.equals(effectiveCurrentPlugin)) {
                    String message = String.format(
                            "%s depends on %s (plugin: %s). " +
                            "Cross-plugin dependencies are forbidden. Use Capability interfaces instead",
                            javaClass.getName(),
                            dependency.getName(),
                            depPlugin);
                    events.add(SimpleConditionEvent.violated(javaClass, message));
                }
            }
        }

        /**
         * Extracts the plugin package from a class package name.
         *
         * @param packageName the full package name
         * @return the plugin package (e.g., "io.brix.app.booking") or null if not a plugin
         */
        private String extractPluginPackage(String packageName) {
            Matcher matcher = PLUGIN_PACKAGE_PATTERN.matcher(packageName + ".");
            if (matcher.find()) {
                String candidate = matcher.group(1);
                if (PLUGIN_PACKAGES.contains(candidate)) {
                    return candidate;
                }
            }
            return null;
        }
    }
}
