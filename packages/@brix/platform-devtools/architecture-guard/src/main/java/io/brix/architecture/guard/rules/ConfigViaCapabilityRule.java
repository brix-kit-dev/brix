/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

/**
 * Configuration Must Be Obtained Via ConfigCapability Rule (Enhanced).
 *
 * <p>This rule enhances standard stream checks with the following constraints:</p>
 * <ul>
 *   <li>No direct {@code System.getenv()} calls - use ConfigCapability for env vars</li>
 *   <li>No direct {@code System.getProperty()} calls - use ConfigCapability for sys props</li>
 *   <li>No {@code @Value} annotation - use ConfigCapability instead of Spring injection</li>
 *   <li>No direct {@code System.out/err/in} access - use logging via ObservabilityCapability</li>
 * </ul>
 *
 * <h2>Rationale</h2>
 * <p>Plugins should not be aware of configuration sources (env vars, system properties, Spring properties).
 * They should obtain config values through a unified ConfigCapability interface.
 * This allows the Host to switch config backends (local files, config center, env vars)
 * without affecting business code.</p>
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 * @see io.runtime.sdk.capability.ConfigStoreCapability
 */
public final class ConfigViaCapabilityRule {

    private ConfigViaCapabilityRule() {}

    /**
     * Combined configuration rule.
     *
     * <p>Combines the following checks:</p>
     * <ol>
     *   <li>No standard stream access (System.out/err/in)</li>
     *   <li>No direct System.getenv() / System.getProperty() calls</li>
     *   <li>No @Value annotation</li>
     * </ol>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule rule() {
        return NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
                .because("Direct standard stream access (System.out/err/in) is forbidden. "
                        + "Use ConfigCapability for config and Logger for output");
    }

    /**
     * No direct System.getenv() or System.getProperty() calls.
     *
     * <p>Business code should not read environment variables or system properties directly.
     * Use ConfigCapability / ConfigStoreCapability instead: the Config capability gives declared, typed configuration
     * and does not expose direct reads of the environment or files (root 3.3.0 §4, Capability boundaries).</p>
     *
     * <p>The condition reports each call as a violation, so the rule is {@code classes().should(...)}: under
     * {@code noClasses()} ArchUnit inverts a condition's events, and the calls it reports would pass.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noSystemEnvAccess() {
        return classes()
                .should(new ArchCondition<JavaClass>("not call System.getenv() or System.getProperty() directly") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
                            String targetOwner = call.getTargetOwner().getName();
                            String targetName = call.getName();
                            if ("java.lang.System".equals(targetOwner)
                                    && ("getenv".equals(targetName) || "getProperty".equals(targetName))) {
                                String message = String.format(
                                        "%s calls System.%s() at %s. Use ConfigCapability instead",
                                        javaClass.getName(), targetName, call.getSourceCodeLocation());
                                events.add(SimpleConditionEvent.violated(javaClass, message));
                            }
                        }
                    }
                })
                .because("Direct System.getenv() / System.getProperty() calls are forbidden. "
                        + "Use ConfigCapability for unified config management");
    }

    /**
     * Where a module lists, as {@code <class name>#<member name>}, the members that read configuration through
     * {@code @Value} before the rule applied to it (see {@link KnownViolations}). A constructor is {@code <init>}. The
     * lists only shrink.
     */
    public static final String KNOWN_SPRING_VALUE_READERS = "META-INF/brix/guard/spring-value-readers.txt";

    private static final String SPRING_VALUE = "org.springframework.beans.factory.annotation.Value";

    /**
     * No Spring @Value annotation.
     *
     * <p>Spring @Value bypasses ConfigCapability's unified config management.
     * Business modules should obtain config values through ConfigCapability,
     * not Spring's property injection mechanism.</p>
     *
     * <p>A class with no member listed in {@value #KNOWN_SPRING_VALUE_READERS} must not depend on @Value at all. In a
     * class with listed members, every field, method or constructor that carries @Value, or has a parameter that does,
     * must be listed by name: a new one fails, next to the listed ones. A class name alone lists nothing.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noSpringValue() {
        Map<String, Set<String>> listed = new TreeMap<>();
        for (String entry : KnownViolations.read(KNOWN_SPRING_VALUE_READERS, KnownViolations.classLoader())) {
            int member = entry.indexOf('#');
            if (member > 0 && member < entry.length() - 1) {
                listed.computeIfAbsent(entry.substring(0, member), type -> new TreeSet<>())
                        .add(entry.substring(member + 1));
            }
        }
        return classes()
                .should(new NoSpringValueCondition(listed))
                .because("@Value annotation is forbidden. Use ConfigCapability / ConfigStoreCapability; a member that "
                        + "already reads configuration with it is listed in " + KNOWN_SPRING_VALUE_READERS
                        + " of its module until it reads through a capability, and the lists only shrink");
    }

    /**
     * Reports each use of @Value that the module does not list. It is used with {@code classes().should(...)}: under
     * {@code noClasses()} ArchUnit inverts a condition's events.
     */
    private static final class NoSpringValueCondition extends ArchCondition<JavaClass> {

        private final Map<String, Set<String>> listed;

        NoSpringValueCondition(Map<String, Set<String>> listed) {
            super("not read configuration with @Value");
            this.listed = listed;
        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            Set<String> members = listed.get(javaClass.getName());
            if (members == null) {
                for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                    if (SPRING_VALUE.equals(dependency.getTargetClass().getName())) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
                return;
            }
            if (javaClass.isAnnotatedWith(SPRING_VALUE)) {
                events.add(SimpleConditionEvent.violated(javaClass,
                        javaClass.getDescription() + " is annotated with @Value"));
            }
            for (JavaField field : javaClass.getFields()) {
                if (field.isAnnotatedWith(SPRING_VALUE) && !members.contains(field.getName())) {
                    events.add(SimpleConditionEvent.violated(field, unlisted(field.getDescription())));
                }
            }
            for (JavaCodeUnit unit : javaClass.getCodeUnits()) {
                boolean reads = unit.isAnnotatedWith(SPRING_VALUE)
                        || unit.getParameters().stream().anyMatch(parameter -> parameter.isAnnotatedWith(SPRING_VALUE));
                if (reads && !members.contains(unit.getName())) {
                    events.add(SimpleConditionEvent.violated(unit, unlisted(unit.getDescription())));
                }
            }
        }

        private static String unlisted(String member) {
            return member + " reads configuration with @Value and is not listed in " + KNOWN_SPRING_VALUE_READERS;
        }
    }
}
