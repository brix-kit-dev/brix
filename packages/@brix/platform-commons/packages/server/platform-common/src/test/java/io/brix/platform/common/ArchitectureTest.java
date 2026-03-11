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
package io.brix.platform.common;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import io.brix.architecture.guard.BrixArchitectureRules;

/**
 * Platform Common Library Architecture Guard Test.
 *
 * <p>This test class verifies that the platform-common module adheres to the following architectural constraints:
 * <ul>
 *   <li>R5: Commons library must not depend on business layer (plugins, adapters)</li>
 *   <li>R6: Direct dependency on infrastructure middleware is prohibited</li>
 *   <li>R7: Throwing generic exceptions is prohibited</li>
 *   <li>R9: Cyclic dependencies are prohibited</li>
 * </ul>
 *
 * <p>platform-common is a pure utility library layer providing shared foundational capabilities
 * across modules (such as pagination, exceptions, tenant context, etc.), without any business logic.
 *
 * @see BrixArchitectureRules#commonsProfile()
 */
@AnalyzeClasses(
    packages = "io.brix.platform.common",
    importOptions = ImportOption.DoNotIncludeTests.class
)
public class ArchitectureTest {

    /**
     * Apply the commons library architecture rule set.
     *
     * <p>Includes the following rules:
     * <ul>
     *   <li>commonsNotDependOnPlugins - No dependency on plugins</li>
     *   <li>commonsNotDependOnAdapters - No dependency on adapters</li>
     *   <li>commonsNotDependOnHost - No dependency on host shell</li>
     *   <li>commonsNotDependOnOrchestrator - No dependency on orchestration layer</li>
     *   <li>noGenericExceptions - Generic exceptions prohibited</li>
     *   <li>noKafkaDependency - Kafka dependency prohibited</li>
     *   <li>noRedisDependency - Redis dependency prohibited</li>
     *   <li>noMySqlDependency - MySQL dependency prohibited</li>
     *   <li>noCyclicDependencies - Cyclic dependencies prohibited</li>
     * </ul>
     */
    @ArchTest
    static final ArchTests commonsRules = BrixArchitectureRules.commonsProfile();
}
