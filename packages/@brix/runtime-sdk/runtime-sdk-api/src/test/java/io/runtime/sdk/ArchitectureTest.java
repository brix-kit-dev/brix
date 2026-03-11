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
package io.runtime.sdk;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

import io.brix.architecture.guard.BrixArchitectureRules;

/**
 * Runtime SDK Architecture Guard Test.
 *
 * <p>This test class validates that the runtime-sdk-api module adheres to the following
 * architecture red lines:
 * <ul>
 *   <li>R5: API layer must not depend on implementation layer (Orchestrator, Adapter)</li>
 *   <li>R6: Direct dependencies on infrastructure middleware (Kafka, Redis, MySQL) are prohibited</li>
 *   <li>R9: Cyclic dependencies are prohibited</li>
 * </ul>
 *
 * <p>The runtime-sdk-api is the capability contract layer, defining interfaces between
 * modules and the Runtime Shell. This module must remain pure without any infrastructure
 * implementation dependencies.
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see BrixArchitectureRules#sdkProfile()
 */
@AnalyzeClasses(
    packages = "io.runtime.sdk",
    importOptions = ImportOption.DoNotIncludeTests.class
)
public class ArchitectureTest {

    /**
     * Applies the SDK layer architecture rule set.
     *
     * <p>Contains the following rules:
     * <ul>
     *   <li>apiNotDependOnImpl - API must not depend on implementation packages</li>
     *   <li>sdkNotDependOnOrchestrator - SDK must not depend on orchestrator layer</li>
     *   <li>sdkNotDependOnAdapters - SDK must not depend on adapters</li>
     *   <li>sdkNotDependOnPlugins - SDK must not depend on plugins</li>
     *   <li>noKafkaDependency - Kafka dependencies are prohibited</li>
     *   <li>noRedisDependency - Redis dependencies are prohibited</li>
     *   <li>noMySqlDependency - MySQL dependencies are prohibited</li>
     *   <li>noMongoDbDependency - MongoDB dependencies are prohibited</li>
     *   <li>noCyclicDependencies - Cyclic dependencies are prohibited</li>
     * </ul>
     */
    @ArchTest
    static final ArchTests sdkRules = BrixArchitectureRules.sdkProfile();
}
