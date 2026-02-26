/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import io.brix.architecture.guard.config.ArchitectureGuardConfig;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Whitelist-based Dependency Rule (Anti-Bypass Design).
 *
 * <p>This rule uses a WHITELIST approach instead of BLACKLIST to prevent bypass:</p>
 * <ul>
 *   <li><b>Blacklist problem</b>: Attacker can use unlisted packages to bypass</li>
 *   <li><b>Whitelist solution</b>: Only explicitly allowed packages are permitted</li>
 * </ul>
 *
 * <h2>How It Works</h2>
 * <ol>
 *   <li>Identify plugin classes (by package pattern or marker annotation)</li>
 *   <li>For each dependency, check if it's in the ALLOWED list</li>
 *   <li>Report ANY dependency not in the whitelist as suspicious</li>
 *   <li>Double-check against FORBIDDEN list (defense in depth)</li>
 * </ol>
 *
 * <h2>Why Whitelist is Harder to Bypass</h2>
 * <p>With blacklist, attacker needs to find ONE package not in the list.<br>
 * With whitelist, attacker needs to convince you to ADD their package to allowed list.</p>
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 */
public final class WhitelistDependencyRule {

    private WhitelistDependencyRule() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Strict whitelist rule: only allow explicitly permitted dependencies.
     *
     * <p>WARNING: This is strict and may require tuning the whitelist for your project.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule strictWhitelistRule() {
        return classes()
                .that().resideInAPackage("..core..")  // Only check core modules
                .should(new OnlyAllowedDependenciesCondition(true))
                .because("Plugin core modules should only depend on whitelisted packages. " +
                        "Add legitimate dependencies to ArchitectureGuardConfig.ALLOWED_PLUGIN_DEPENDENCIES");
    }

    /**
     * Defense-in-depth rule: forbidden packages are NOT allowed even if whitelisted.
     *
     * <p>This catches cases where someone accidentally adds a forbidden package to whitelist.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noForbiddenDependencies() {
        return classes()
                .should(new NoForbiddenDependenciesCondition())
                .because("Forbidden infrastructure packages must never be used in plugins");
    }

    /**
     * Condition that checks dependencies against whitelist.
     */
    private static class OnlyAllowedDependenciesCondition extends ArchCondition<JavaClass> {

        private final boolean reportUnknown;

        OnlyAllowedDependenciesCondition(boolean reportUnknown) {
            super("only depend on whitelisted packages");
            this.reportUnknown = reportUnknown;
        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            String classPackage = javaClass.getPackageName();

            // Only check plugin packages
            if (!ArchitectureGuardConfig.isPluginPackage(classPackage)) {
                return;
            }

            for (JavaClass dependency : javaClass.getDirectDependenciesFromSelf().stream()
                    .map(dep -> dep.getTargetClass())
                    .filter(dep -> !dep.getPackageName().equals(classPackage))
                    .toList()) {

                String depPackage = dependency.getPackageName();

                // Skip same plugin (intra-module dependencies are OK)
                if (isSamePlugin(classPackage, depPackage)) {
                    continue;
                }

                // Check forbidden first (highest priority)
                if (ArchitectureGuardConfig.isForbiddenDependency(depPackage)) {
                    String message = String.format(
                            "%s depends on FORBIDDEN package %s (%s)",
                            javaClass.getName(), depPackage, dependency.getName());
                    events.add(SimpleConditionEvent.violated(javaClass, message));
                    continue;
                }

                // Check if in whitelist
                if (!ArchitectureGuardConfig.isAllowedDependency(depPackage)) {
                    if (reportUnknown) {
                        String message = String.format(
                                "%s depends on UNKNOWN package %s (%s). " +
                                "Add to whitelist if legitimate, or remove dependency",
                                javaClass.getName(), depPackage, dependency.getName());
                        events.add(SimpleConditionEvent.violated(javaClass, message));
                    }
                }
            }
        }

        private boolean isSamePlugin(String pkg1, String pkg2) {
            // Extract plugin root (e.g., "com.shinwa.app.booking")
            String root1 = extractPluginRoot(pkg1);
            String root2 = extractPluginRoot(pkg2);
            return root1 != null && root1.equals(root2);
        }

        private String extractPluginRoot(String packageName) {
            java.util.regex.Matcher matcher = ArchitectureGuardConfig.PLUGIN_PACKAGE_PATTERN.matcher(packageName);
            if (matcher.find()) {
                return matcher.group(1);
            }
            return null;
        }
    }

    /**
     * Condition that forbids certain packages regardless of whitelist.
     */
    private static class NoForbiddenDependenciesCondition extends ArchCondition<JavaClass> {

        NoForbiddenDependenciesCondition() {
            super("not depend on forbidden infrastructure packages");
        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            for (JavaClass dependency : javaClass.getDirectDependenciesFromSelf().stream()
                    .map(dep -> dep.getTargetClass())
                    .toList()) {

                String depPackage = dependency.getPackageName();

                if (ArchitectureGuardConfig.isForbiddenDependency(depPackage)) {
                    String message = String.format(
                            "%s depends on FORBIDDEN package %s (%s). " +
                            "Use Capability interfaces instead of direct infrastructure access",
                            javaClass.getName(), depPackage, dependency.getName());
                    events.add(SimpleConditionEvent.violated(javaClass, message));
                }
            }
        }
    }
}
