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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Runtime SDK Layer Architecture Constraints
 *
 * <p>Enforces that runtime-sdk-api remains the stable contract layer
 * at the top of the dependency hierarchy.</p>
 *
 * <h2>Architecture Principle</h2>
 * <blockquote>
 * runtime-sdk-api 是整个系统的稳定契约层，位于依赖链顶端。
 * 它不能依赖任何实现类（runtime-sdk 实现、Orchestrator、Adapter）。
 * </blockquote>
 *
 * <h2>Covered Red Lines</h2>
 * <ul>
 *   <li><b>红线 11</b>: runtime-sdk-api 不依赖具体实现模块</li>
 *   <li><b>红线 2</b>: SDK API 只定义 Capability 契约</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // In runtime-sdk-api module
 * @AnalyzeClasses(packages = "io.runtime.sdk")
 * class SdkArchitectureTest {
 *     @ArchTest
 *     static final ArchTests rules = BrixArchitectureRules.sdkProfile();
 * }
 * }</pre>
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 */
public class SdkProfile {

    // ==================== SDK API Stability Rules ====================

    /**
     * runtime-sdk-api must not depend on runtime-sdk implementation.
     */
    @ArchTest
    static final ArchRule apiNotDependOnImpl = noClasses()
            .that().resideInAPackage("io.runtime.sdk..")
            .and().resideOutsideOfPackage("io.runtime.sdk.impl..")
            .should().dependOnClassesThat()
            .resideInAPackage("io.runtime.sdk.impl..")
            .because("红线 11: runtime-sdk-api must not depend on implementation classes")
            .allowEmptyShould(true);

    /**
     * runtime-sdk must not depend on runtime-orchestrator.
     */
    @ArchTest
    static final ArchRule sdkNotDependOnOrchestrator = noClasses()
            .that().resideInAPackage("io.runtime.sdk..")
            .should().dependOnClassesThat()
            .resideInAPackage("io.runtime.orchestrator..")
            .because("红线 11: runtime-sdk must not depend on orchestrator layer")
            .allowEmptyShould(true);

    /**
     * runtime-sdk must not depend on infra-adapters.
     */
    @ArchTest
    static final ArchRule sdkNotDependOnAdapters = noClasses()
            .that().resideInAPackage("io.runtime.sdk..")
            .should().dependOnClassesThat()
            .resideInAPackage("io.brix.infra.adapter..")
            .because("红线 11: runtime-sdk must not depend on infrastructure adapters")
            .allowEmptyShould(true);

    /**
     * runtime-sdk must not depend on plugin code.
     */
    @ArchTest
    static final ArchRule sdkNotDependOnPlugins = noClasses()
            .that().resideInAPackage("io.runtime.sdk..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.shinwa.app..", "shinwa.plugin..")
            .because("红线 11: runtime-sdk must not depend on plugin/business code")
            .allowEmptyShould(true);

    /**
     * runtime-sdk must not depend on host layer.
     */
    @ArchTest
    static final ArchRule sdkNotDependOnHost = noClasses()
            .that().resideInAPackage("io.runtime.sdk..")
            .should().dependOnClassesThat()
            .resideInAPackage("io.host.shell..")
            .because("红线 11: runtime-sdk must not depend on host layer")
            .allowEmptyShould(true);

    // ==================== Capability Contract Rules ====================

    /**
     * Capability interfaces should be in capability package.
     */
    @ArchTest
    static final ArchRule capabilityInCorrectPackage = noClasses()
            .that().haveSimpleNameEndingWith("Capability")
            .and().resideInAPackage("io.runtime.sdk..")
            .should().resideOutsideOfPackage("io.runtime.sdk.capability..")
            .because("Capability interfaces must reside in io.runtime.sdk.capability package")
            .allowEmptyShould(true);

    // ==================== No Infrastructure Dependencies ====================

    /**
     * SDK should not directly depend on Kafka.
     */
    @ArchTest
    static final ArchRule noKafkaDependency = noClasses()
            .that().resideInAPackage("io.runtime.sdk..")
            .should().dependOnClassesThat()
            .resideInAPackage("org.apache.kafka..")
            .because("红线 1: runtime-sdk must not depend on Kafka directly")
            .allowEmptyShould(true);

    /**
     * SDK should not directly depend on Redis.
     */
    @ArchTest
    static final ArchRule noRedisDependency = noClasses()
            .that().resideInAPackage("io.runtime.sdk..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("redis.clients..", "org.springframework.data.redis..")
            .because("红线 1: runtime-sdk must not depend on Redis directly")
            .allowEmptyShould(true);
}
