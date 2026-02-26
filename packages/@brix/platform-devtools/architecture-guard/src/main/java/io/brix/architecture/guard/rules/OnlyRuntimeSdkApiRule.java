/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Core Module Only Depends on Runtime SDK API Rule.
 *
 * <p>Enforces that plugin core modules can ONLY depend on {@code runtime-sdk-api},
 * not on {@code runtime-sdk} (implementation package).</p>
 *
 * <h2>Architecture Principle (Blueprint Constraint 2)</h2>
 * <blockquote>
 * 插件代码中禁止出现 Kafka / Redis / HTTP Client 等基础设施依赖。
 * 插件不得绕过 Runtime Shell 直接访问基础设施。
 * Runtime Shell 是唯一允许接触基础设施的层。
 * </blockquote>
 *
 * <h2>Violation Example</h2>
 * <pre>{@code
 * // In case-core/pom.xml - VIOLATION!
 * <dependency>
 *     <groupId>io.runtime.sdk</groupId>
 *     <artifactId>runtime-sdk</artifactId>  // Should be runtime-sdk-api!
 * </dependency>
 * 
 * // In code - VIOLATION!
 * import io.runtime.sdk.impl.DefaultRuntimeContext; // Implementation class!
 * }</pre>
 *
 * <h2>Correct Approach</h2>
 * <pre>{@code
 * // Only depend on API package
 * import io.runtime.sdk.context.RuntimeContext;        // API interface - OK
 * import io.runtime.sdk.capability.EventBusCapability; // API interface - OK
 * }</pre>
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 * @see <a href="docs/v3.0.4-运行壳架构设计蓝图.md">Blueprint Constraint 2</a>
 */
public final class OnlyRuntimeSdkApiRule {

    private OnlyRuntimeSdkApiRule() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== Forbidden Implementation Packages ====================

    /** Runtime SDK implementation package (plugins should NOT depend on this) */
    private static final String RUNTIME_SDK_IMPL = "io.runtime.sdk.impl..";

    /** Runtime SDK internal package */
    private static final String RUNTIME_SDK_INTERNAL = "io.runtime.sdk.internal..";

    /** Runtime Orchestrator package (implementation layer) */
    private static final String RUNTIME_ORCHESTRATOR = "io.runtime.orchestrator..";

    // ==================== Rules ====================

    /**
     * No dependency on runtime-sdk implementation classes.
     *
     * <p>Plugin core modules must only depend on runtime-sdk-api interfaces,
     * not on implementation classes from runtime-sdk.</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noRuntimeSdkImpl() {
        return noClasses()
                .that().resideInAPackage("..core..")
                .should().dependOnClassesThat()
                .resideInAPackage(RUNTIME_SDK_IMPL)
                .because("Plugin core modules must only depend on runtime-sdk-api, " +
                        "not runtime-sdk implementation classes (Blueprint Constraint 2)")
                .allowEmptyShould(true);
    }

    /**
     * No dependency on runtime-sdk internal classes.
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noRuntimeSdkInternal() {
        return noClasses()
                .that().resideInAPackage("..core..")
                .should().dependOnClassesThat()
                .resideInAPackage(RUNTIME_SDK_INTERNAL)
                .because("Plugin core modules must not depend on runtime-sdk internal classes")
                .allowEmptyShould(true);
    }

    /**
     * No dependency on runtime-orchestrator (Layer 2.5).
     *
     * <p>Plugins are Layer 1, they can only depend on Layer 2 (Capability Contract),
     * not on Layer 2.5 (Orchestrator implementation).</p>
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule noRuntimeOrchestrator() {
        return noClasses()
                .that().resideInAPackage("..core..")
                .should().dependOnClassesThat()
                .resideInAPackage(RUNTIME_ORCHESTRATOR)
                .because("Plugin core modules (Layer 1) must not depend on " +
                        "runtime-orchestrator (Layer 2.5). Only depend on Layer 2 (runtime-sdk-api)")
                .allowEmptyShould(true);
    }

    /**
     * Combined rule: core modules only depend on runtime-sdk-api.
     *
     * @return ArchUnit rule instance
     */
    public static ArchRule rule() {
        return noRuntimeSdkImpl();
    }
}
