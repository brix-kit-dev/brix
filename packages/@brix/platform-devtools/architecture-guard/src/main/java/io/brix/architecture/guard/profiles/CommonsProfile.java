/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.brix.architecture.guard.profiles;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import io.brix.architecture.guard.ArchitectureLayers;
import io.brix.architecture.guard.rules.KnownViolations;
import io.brix.architecture.guard.rules.NoRoleNameAuthorizationRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

/**
 * Platform Commons Layer Architecture Constraints
 *
 * <p>Enforces that platform-commons remains a pure utility library
 * without business logic dependencies.</p>
 *
 * <h2>Architecture Principle</h2>
 * <blockquote>
 * platform-commons is a pure utility library layer, providing infrastructure-independent
 * code that can be reused across modules.
 * It must not depend on any business modules, infrastructure adapters, or Host layer.
 * </blockquote>
 *
 * <h2>Covered Constraints</h2>
 * <ul>
 *   <li>Commons must not depend on business plugins ({@link ArchitectureLayers#PLUGIN_ROOTS}), their api
 *       contracts included</li>
 *   <li>Commons must not depend on infrastructure adapters</li>
 *   <li>Commons must not depend on Host layer</li>
 *   <li>Commons must not depend on runtime-orchestrator</li>
 * </ul>
 *
 * <p>The rules apply to {@link ArchitectureLayers#PLATFORM_ROOT}. They do not allow an empty selection: a module that
 * runs this profile and has no class under the platform root fails, so a wrong root cannot pass unnoticed.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // In a platform-commons module
 * @AnalyzeClasses(packages = "io.brix.platform.common")
 * class CommonsArchitectureTest {
 *     @ArchTest
 *     static final ArchTests rules = BrixArchitectureRules.commonsProfile();
 * }
 * }</pre>
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 */
public class CommonsProfile {

    // ==================== Dependency Isolation Rules ====================

    /**
     * Commons must not depend on business modules.
     */
    @ArchTest
    static final ArchRule commonsNotDependOnPlugins = noClasses()
            .that().resideInAPackage(ArchitectureLayers.PLATFORM_ROOT)
            .should().dependOnClassesThat()
            .resideInAnyPackage(ArchitectureLayers.PLUGIN_ROOTS)
            .because("platform-commons must not depend on business plugins");

    /**
     * Commons must not depend on infra-adapters.
     */
    @ArchTest
    static final ArchRule commonsNotDependOnAdapters = noClasses()
            .that().resideInAPackage(ArchitectureLayers.PLATFORM_ROOT)
            .should().dependOnClassesThat()
            .resideInAPackage(ArchitectureLayers.INFRA_ADAPTER_ROOT)
            .because("platform-commons must not depend on infrastructure adapters");

    /**
     * Commons must not depend on host layer.
     */
    @ArchTest
    static final ArchRule commonsNotDependOnHost = noClasses()
            .that().resideInAPackage(ArchitectureLayers.PLATFORM_ROOT)
            .should().dependOnClassesThat()
            .resideInAPackage("io.brix.enterprise.host..")
            .because("platform-commons must not depend on host layer");

    /**
     * Commons must not depend on runtime-orchestrator.
     */
    @ArchTest
    static final ArchRule commonsNotDependOnOrchestrator = noClasses()
            .that().resideInAPackage(ArchitectureLayers.PLATFORM_ROOT)
            .should().dependOnClassesThat()
            .resideInAPackage("io.runtime.orchestrator..")
            .because("platform-commons must not depend on orchestrator");

    // ==================== Authorization ====================

    /**
     * Platform code authorizes on permissions: no {@code hasRole}/{@code hasAnyRole} call or method reference.
     */
    @ArchTest
    static final ArchRule noRoleNameChecks = NoRoleNameAuthorizationRule.noRoleNameChecks();

    // ==================== Code Quality Rules ====================

    /**
     * No generic exceptions in commons.
     */
    @ArchTest
    static final ArchRule noGenericExceptions = NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS
            .because("Commons should use specific exception types");

    // ==================== Infrastructure Independence ====================

    /**
     * Where a platform-commons module lists its classes that used Redis before this profile applied to the platform
     * root (see {@link KnownViolations}).
     */
    public static final String KNOWN_REDIS_CLIENTS = "META-INF/brix/guard/platform-redis-clients.txt";

    /**
     * Commons should not directly depend on Kafka.
     */
    @ArchTest
    static final ArchRule noKafkaDependency = noClasses()
            .that().resideInAPackage(ArchitectureLayers.PLATFORM_ROOT)
            .should().dependOnClassesThat()
            .resideInAPackage("org.apache.kafka..")
            .because("platform-commons must not depend on Kafka directly");

    /**
     * Commons should not directly depend on Redis. The classes that already did are listed in
     * {@value #KNOWN_REDIS_CLIENTS} of their module, under the exemption the list names; the lists only shrink.
     */
    @ArchTest
    static final ArchRule noRedisDependency = noClasses()
            .that().resideInAPackage(ArchitectureLayers.PLATFORM_ROOT)
            .and(KnownViolations.notListedIn(KNOWN_REDIS_CLIENTS))
            .should().dependOnClassesThat()
            .resideInAnyPackage("redis.clients..", "org.springframework.data.redis..")
            .because("platform-commons must not depend on Redis directly; a class that already does is listed in "
                    + KNOWN_REDIS_CLIENTS + " until it reaches Redis through a capability, and the lists only shrink");

    /**
     * Commons should not directly depend on RabbitMQ.
     */
    @ArchTest
    static final ArchRule noRabbitDependency = noClasses()
            .that().resideInAPackage(ArchitectureLayers.PLATFORM_ROOT)
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.rabbitmq..", "org.springframework.amqp..")
            .because("platform-commons must not depend on RabbitMQ directly");
}
