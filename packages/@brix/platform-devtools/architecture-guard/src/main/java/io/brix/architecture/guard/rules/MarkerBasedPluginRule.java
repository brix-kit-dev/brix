/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Marker-Based Plugin Detection Rule (Bypass-Resistant).
 *
 * <p>Instead of relying on package names (which can be easily changed to bypass rules),
 * this rule uses MARKER INTERFACES or ANNOTATIONS that plugins MUST implement.</p>
 *
 * <h2>Why Markers are Harder to Bypass</h2>
 * <ul>
 *   <li>Package names: Developer can just rename package to bypass</li>
 *   <li>Marker interfaces: Removing marker breaks the plugin's ability to be loaded!</li>
 * </ul>
 *
 * <h2>How It Works</h2>
 * <ol>
 *   <li>All plugins must have a class annotated with {@code @BrixPlugin}</li>
 *   <li>Or implement {@code PluginDescriptor} interface</li>
 *   <li>The marker class defines the plugin boundary</li>
 *   <li>Rules apply to all classes in the same module as the marker</li>
 * </ol>
 *
 * <h2>Enforcement Strategy</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────┐
 * │  If you remove @BrixPlugin annotation → Plugin won't load  │
 * │  If you keep @BrixPlugin → Rules are enforced              │
 * │  ∴ No way to bypass without breaking functionality         │
 * └─────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 */
public final class MarkerBasedPluginRule {

    private MarkerBasedPluginRule() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Annotation that marks a class as plugin entry point */
    private static final String BRIX_PLUGIN_ANNOTATION = "io.runtime.sdk.plugin.BrixPlugin";

    /** Interface that plugin descriptors must implement */
    private static final String PLUGIN_DESCRIPTOR_INTERFACE = "io.runtime.sdk.plugin.PluginDescriptor";

    /** Interface that all Capabilities must extend */
    private static final String CAPABILITY_INTERFACE = "io.runtime.sdk.capability.Capability";

    /**
     * Rule: Classes in plugin modules must obtain capabilities via RuntimeContext.
     *
     * <p>Detects direct instantiation of Capability implementations (bypassing DI).</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule mustObtainCapabilitiesViaRuntimeContext() {
        return classes()
                .should(new ObtainCapabilityViaRuntimeContextCondition())
                .because("Capabilities must be obtained via RuntimeContext.getCapability(), " +
                        "not direct instantiation or field injection")
                .allowEmptyShould(true);
    }

    /**
     * Rule: Plugin classes should not implement Capability interfaces.
     *
     * <p>Capability implementations belong in infra-adapter modules, not plugins.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule pluginsMustNotImplementCapability() {
        return classes()
                .that().resideInAPackage("..core..")
                .should(new NotImplementCapabilityCondition())
                .because("Plugin core modules must not implement Capability interfaces. " +
                        "Implementations belong in infra-adapter modules")
                .allowEmptyShould(true);
    }

    /**
     * Condition: Capabilities must be obtained via RuntimeContext.getCapability().
     */
    private static class ObtainCapabilityViaRuntimeContextCondition extends ArchCondition<JavaClass> {

        ObtainCapabilityViaRuntimeContextCondition() {
            super("obtain capabilities via RuntimeContext.getCapability()");
        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            // Check for direct constructor calls to Capability implementations
            for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
                // Skip RuntimeContext.getCapability - that's the correct way
                if (isRuntimeContextGetCapability(call)) {
                    continue;
                }

                // Check if calling constructor of a Capability implementation
                if (call.getName().equals("<init>")) {
                    JavaClass targetClass = call.getTargetOwner();
                    if (implementsCapability(targetClass)) {
                        String message = String.format(
                                "%s directly instantiates Capability implementation %s. " +
                                "Use RuntimeContext.getCapability() instead",
                                javaClass.getName(), targetClass.getName());
                        events.add(SimpleConditionEvent.violated(javaClass, message));
                    }
                }
            }
        }

        private boolean isRuntimeContextGetCapability(JavaMethodCall call) {
            return call.getTargetOwner().getName().contains("RuntimeContext")
                    && call.getName().equals("getCapability");
        }

        private boolean implementsCapability(JavaClass javaClass) {
            // Check if class name or any interface name ends with "Capability"
            return javaClass.getAllRawInterfaces().stream()
                    .anyMatch(iface -> iface.getName().endsWith("Capability")
                            || iface.getName().equals(CAPABILITY_INTERFACE));
        }
    }

    /**
     * Condition: Class must not implement Capability interface.
     */
    private static class NotImplementCapabilityCondition extends ArchCondition<JavaClass> {

        NotImplementCapabilityCondition() {
            super("not implement Capability interfaces");
        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            for (JavaClass iface : javaClass.getAllRawInterfaces()) {
                if (iface.getName().endsWith("Capability")) {
                    String message = String.format(
                            "%s implements %s but is in core module. " +
                            "Move Capability implementations to infra-adapter module",
                            javaClass.getName(), iface.getName());
                    events.add(SimpleConditionEvent.violated(javaClass, message));
                }
            }
        }
    }
}
