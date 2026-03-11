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
package io.infra.adapter.kafka;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import io.brix.architecture.guard.BrixArchitectureRules;

/**
 * Architecture guard tests for the Kafka adapter.
 *
 * <p>This test class validates that the infra-adapter-kafka module adheres to the following architecture rules:
 * <ul>
 *   <li>R6/D4: Adapter isolation, third-party types must not be exposed to upper layers</li>
 *   <li>R4: Depends on runtime-sdk-api capability interfaces</li>
 *   <li>R9: Inter-adapter dependencies are prohibited</li>
 * </ul>
 *
 * <p>infra-adapter-kafka is an infrastructure adapter layer that encapsulates Kafka implementation details
 * and only exposes the EventBusCapability interface externally.
 *
 * @see BrixArchitectureRules#adapterProfile()
 */
@AnalyzeClasses(
    packages = "io.infra.adapter.kafka",
    importOptions = ImportOption.DoNotIncludeTests.class
)
public class ArchitectureTest {

    /**
     * Applies the adapter layer architecture rule set.
     *
     * <p>Contains the following rules:
     * <ul>
     *   <li>adaptersMustImplementCapability - Adapters must implement capability interfaces</li>
     *   <li>noPluginDependency - Adapters must not depend on plugins</li>
     *   <li>noHostDependency - Adapters must not depend on the host shell</li>
     *   <li>noAdapterCircularDependency - No circular dependencies between adapters</li>
     *   <li>publicApiMustNotLeakThirdParty - Public APIs must not leak third-party types</li>
     *   <li>configClassesMustBeInternal - Configuration classes must be internal</li>
     *   <li>noCyclicPackageDependencies - No cyclic dependencies at package level</li>
     * </ul>
     */
    @ArchTest
    static final ArchTests adapterRules = BrixArchitectureRules.adapterProfile();
}
