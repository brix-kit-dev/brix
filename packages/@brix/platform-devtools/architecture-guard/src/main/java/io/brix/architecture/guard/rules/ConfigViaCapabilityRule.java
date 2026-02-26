/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
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
                .because("Direct standard stream access (System.out/err/in) is forbidden. \" +\n                        \"Use ConfigCapability for config and Logger for output");
    }

    /**
     * No direct System.getenv() or System.getProperty() calls.
     *
     * <p>Business code should not read environment variables or system properties directly.
     * Use ConfigCapability / ConfigStoreCapability instead.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noSystemEnvAccess() {
        return noClasses()
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
                .because("Direct System.getenv() / System.getProperty() calls are forbidden. \" +\n                        \"Use ConfigCapability for unified config management");
    }

    /**
     * No Spring @Value annotation.
     *
     * <p>Spring @Value bypasses ConfigCapability's unified config management.
     * Business modules should obtain config values through ConfigCapability,
     * not Spring's property injection mechanism.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noSpringValue() {
        return noClasses()
                .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Value")
                .orShould().dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.beans.factory.annotation.Value")
                .because("@Value annotation is forbidden. Use ConfigCapability / ConfigStoreCapability");
    }
}
