/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.architecture.guard.executor;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import io.brix.architecture.guard.rules.AdapterIsolationRule;
import io.brix.architecture.guard.rules.ConfigViaCapabilityRule;
import io.brix.architecture.guard.rules.CoreSpringBoundaryRule;
import io.brix.architecture.guard.rules.EventsViaCapabilityRule;
import io.brix.architecture.guard.rules.HostUltraThinRule;
import io.brix.architecture.guard.rules.NoDirectHttpClientsRule;
import io.brix.architecture.guard.rules.NoDirectPluginDependencyRule;
import io.brix.architecture.guard.rules.NoInfraAdapterRule;
import io.brix.architecture.guard.rules.NoMiddlewareClientsRule;
import io.brix.architecture.guard.rules.NoPlatformTenantRelationshipAccessRule;
import io.brix.architecture.guard.rules.NoSpringContainerApiRule;
import io.brix.architecture.guard.rules.OnlyRuntimeSdkApiRule;
import io.brix.architecture.guard.rules.SecurityBoundaryRule;
import io.brix.devtools.governance.artifact.ModuleKind;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Phase 2 Java bytecode executor selected by governance {@code moduleKind}.
 */
public final class JavaStaticBoundaryExecutor {

    public static final String EXECUTOR_ID = "java-static-boundary";
    public static final String EMPTY_TARGET = "BRX-JAVA-STATIC-EMPTY-TARGET";
    public static final String RULE_VIOLATION = "BRX-JAVA-STATIC-RULE-VIOLATION";

    private static final Predicate<JavaClass> ANY_CLASS = javaClass -> true;

    /**
     * Returns the static requirements enforced for a module kind.
     *
     * @param moduleKind artifact module kind
     * @return ordered requirements
     */
    public List<JavaStaticBoundaryRequirement> requirementsFor(ModuleKind moduleKind) {
        return switch (moduleKind) {
            case PLUGIN_API -> pluginApiRequirements();
            case PLUGIN_CORE -> pluginCoreRequirements();
            case PLUGIN_SERVER -> pluginServerRequirements();
            case HOST -> hostRequirements();
            case ADAPTER -> adapterRequirements();
            case RUNTIME_CAPABILITY -> runtimeCapabilityRequirements();
            case PLATFORM_OPERATIONAL -> operationalRequirements();
            case PLATFORM_CAPABILITY -> platformCapabilityRequirements();
            case SHARED_CONTRACT, UI_WEB, UI_MOBILE -> List.of();
        };
    }

    /**
     * Executes all requirements and returns target cardinality for every rule.
     *
     * @param moduleKind artifact module kind
     * @param classes imported bytecode classes
     * @return requirement results
     */
    public List<JavaStaticBoundaryRuleResult> execute(ModuleKind moduleKind, JavaClasses classes) {
        List<JavaStaticBoundaryRuleResult> results = new ArrayList<>();
        for (JavaStaticBoundaryRequirement requirement : requirementsFor(moduleKind)) {
            long targetCardinality = classes.stream()
                .filter(requirement.targetSelector())
                .count();
            if (targetCardinality == 0) {
                results.add(new JavaStaticBoundaryRuleResult(
                    requirement.id(),
                    targetCardinality,
                    false,
                    EMPTY_TARGET,
                    requirement.id() + " has no bytecode targets"));
                continue;
            }

            try {
                requirement.rule().check(classes);
                results.add(new JavaStaticBoundaryRuleResult(
                    requirement.id(),
                    targetCardinality,
                    true,
                    "BRX-JAVA-STATIC-PASS",
                    requirement.description()));
            } catch (AssertionError error) {
                results.add(new JavaStaticBoundaryRuleResult(
                    requirement.id(),
                    targetCardinality,
                    false,
                    RULE_VIOLATION,
                    error.getMessage()));
            }
        }
        return List.copyOf(results);
    }

    /**
     * Executes all requirements and throws on the first blocking result set.
     *
     * @param moduleKind artifact module kind
     * @param classes imported bytecode classes
     */
    public void executeOrThrow(ModuleKind moduleKind, JavaClasses classes) {
        List<JavaStaticBoundaryRuleResult> failures = execute(moduleKind, classes).stream()
            .filter(result -> !result.passed())
            .toList();
        if (!failures.isEmpty()) {
            throw new JavaStaticBoundaryViolationException(failures);
        }
    }

    private static List<JavaStaticBoundaryRequirement> pluginApiRequirements() {
        return List.of(
            requirement("A-1:plugin-api-no-l0", "Plugin API must not use L0 infrastructure SDKs", NoInfraAdapterRule.rule()),
            requirement("A-21:plugin-api-no-l2b", "Plugin API must not import Runtime implementation", noRuntimeOrchestrator()),
            requirement("A-24:plugin-api-no-container", "Plugin API must not use Spring container APIs", NoSpringContainerApiRule.rule())
        );
    }

    private static List<JavaStaticBoundaryRequirement> pluginCoreRequirements() {
        return List.of(
            requirement("A-1:plugin-core-no-adapter", "Plugin core must not depend on infrastructure adapters", NoInfraAdapterRule.rule()),
            requirement("A-1:plugin-core-no-kafka", "Plugin core must not depend on Kafka clients", NoMiddlewareClientsRule.noApacheKafka()),
            requirement("A-1:plugin-core-no-http-client", "Plugin core must not depend on direct HTTP clients", NoDirectHttpClientsRule.noJdkHttpClient()),
            requirement("A-2:plugin-core-no-container", "Plugin core must not use Spring container APIs", NoSpringContainerApiRule.rule()),
            requirement("A-13:plugin-core-no-runtime-impl", "Plugin core must not depend on Runtime implementation", OnlyRuntimeSdkApiRule.noRuntimeOrchestrator()),
            requirement("A-17:plugin-core-no-http-binding", "Plugin core must not declare HTTP bindings", CoreSpringBoundaryRule.noWebBindingDependencyInCore()),
            requirement("A-21:plugin-core-no-plugin-internals", "Plugin core must not depend on another plugin internals", NoDirectPluginDependencyRule.rule()),
            requirement("S-1:plugin-core-no-jwt", "Plugin core must not parse JWT directly", SecurityBoundaryRule.noDirectJwtUsage())
        );
    }

    private static List<JavaStaticBoundaryRequirement> pluginServerRequirements() {
        return List.of(
            requirement("A-1:plugin-server-no-adapter", "Plugin server must not depend on infrastructure adapters", NoInfraAdapterRule.rule()),
            requirement("A-1:plugin-server-no-kafka", "Plugin server must not depend on Kafka clients", NoMiddlewareClientsRule.noApacheKafka()),
            requirement("A-2:plugin-server-no-container", "Plugin server must not use Spring container APIs", NoSpringContainerApiRule.rule()),
            requirement("A-21:plugin-server-no-runtime-impl", "Plugin server must not depend on Runtime implementation", noRuntimeOrchestrator()),
            requirement("S-1:plugin-server-no-jwt", "Plugin server must not parse JWT directly", noDirectJwt())
        );
    }

    private static List<JavaStaticBoundaryRequirement> hostRequirements() {
        return List.of(
            requirement("A-6:host-no-adapter-source", "Host source must not import adapter implementation types", NoInfraAdapterRule.rule()),
            requirement("A-6:host-no-l0-client", "Host source must not use middleware clients", NoMiddlewareClientsRule.noApacheKafka()),
            requirement("A-6:host-no-http-client", "Host source must not use direct HTTP clients", NoDirectHttpClientsRule.noJdkHttpClient()),
            requirement("A-6:host-no-bean-definitions", "Host source must not define capability beans", HostUltraThinRule.noBeanDefinitionsInAutoConfiguration()),
            requirement("A-6:host-no-business-annotations", "Host source must not declare business components", HostUltraThinRule.noBusinessAnnotations())
        );
    }

    private static List<JavaStaticBoundaryRequirement> adapterRequirements() {
        return List.of(
            requirement("A-9:adapter-no-plugin-dependency", "Adapters must not depend on plugin internals", noPluginImplementationDependency()),
            requirement("A-9:adapter-isolated-public-api", "Adapter public API must not leak third-party types", AdapterIsolationRule.publicInterfaceNoThirdPartyTypes())
        );
    }

    private static List<JavaStaticBoundaryRequirement> runtimeCapabilityRequirements() {
        return List.of(
            requirement("A-21:runtime-capability-no-plugin", "Runtime capabilities must not depend on plugin code", noPluginImplementationDependency()),
            requirement("A-21:runtime-capability-no-host", "Runtime capabilities must not depend on Host source", noHostDependency()),
            requirement("A-1:runtime-capability-no-l0", "Runtime capability contracts must not expose L0 clients", NoMiddlewareClientsRule.noApacheKafka())
        );
    }

    private static List<JavaStaticBoundaryRequirement> platformCapabilityRequirements() {
        return List.of(
            requirement("A-21:platform-capability-no-plugin", "Platform capabilities must not depend on plugin code", noPluginImplementationDependency()),
            requirement("A-21:platform-capability-no-host", "Platform capabilities must not depend on Host source", noHostDependency()),
            requirement("A-24:platform-capability-no-container", "Platform capability contracts must not use Spring container APIs", NoSpringContainerApiRule.rule())
        );
    }

    private static List<JavaStaticBoundaryRequirement> operationalRequirements() {
        return List.of(
            requirement("A-26:operational-no-plugin", "Operational modules must not depend on plugin internals", noPluginImplementationDependency()),
            requirement("A-26:operational-no-adapter", "Operational modules must not depend on L2C adapters", NoInfraAdapterRule.rule()),
            requirement("A-26:operational-no-capability-registry", "Operational modules must not access the general CapabilityRegistry", noCapabilityRegistry()),
            requirement("A-26:operational-no-platform-route-bypass", "Operational modules must not publish platform routes through Spring MVC", noSpringMvcOrScheduled())
        );
    }

    private static JavaStaticBoundaryRequirement requirement(String id, String description, com.tngtech.archunit.lang.ArchRule rule) {
        return new JavaStaticBoundaryRequirement(id, description, ANY_CLASS, rule);
    }

    private static com.tngtech.archunit.lang.ArchRule noRuntimeOrchestrator() {
        return noClasses()
            .should().dependOnClassesThat()
            .resideInAPackage("io.runtime.orchestrator..")
            .because("A-21 forbids Java boundary targets from depending on L2B Runtime implementation")
            .allowEmptyShould(true);
    }

    private static com.tngtech.archunit.lang.ArchRule noPluginImplementationDependency() {
        return noClasses()
            .should().dependOnClassesThat()
            .resideInAnyPackage("io.brix.app..", "io.brix.enterprise.solutions..")
            .because("A-9/A-21 forbid direct dependencies on plugin implementation internals")
            .allowEmptyShould(true);
    }

    private static com.tngtech.archunit.lang.ArchRule noHostDependency() {
        return noClasses()
            .should().dependOnClassesThat()
            .resideInAnyPackage("io.brix.enterprise.host..", "io.brix.host..")
            .because("A-6/A-21 forbid lower layers from depending on Host source")
            .allowEmptyShould(true);
    }

    private static com.tngtech.archunit.lang.ArchRule noCapabilityRegistry() {
        return noClasses()
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("io.runtime.sdk.capability.registry.CapabilityRegistry")
            .because("A-26 requires OperationalContext and the L2B internal namespace, not the general registry")
            .allowEmptyShould(true);
    }

    private static com.tngtech.archunit.lang.ArchRule noSpringMvcOrScheduled() {
        return noClasses()
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework.web.bind.annotation..", "org.springframework.scheduling.annotation..")
            .because("A-25/A-26 require Runtime entry admission, not Spring MVC or Scheduled bypass")
            .allowEmptyShould(true);
    }

    private static com.tngtech.archunit.lang.ArchRule noDirectJwt() {
        return noClasses()
            .should().dependOnClassesThat()
            .resideInAnyPackage("io.jsonwebtoken..", "com.auth0..")
            .because("S-1 requires AuthCapability and boundary authorization, not raw token parsing")
            .allowEmptyShould(true);
    }
}
