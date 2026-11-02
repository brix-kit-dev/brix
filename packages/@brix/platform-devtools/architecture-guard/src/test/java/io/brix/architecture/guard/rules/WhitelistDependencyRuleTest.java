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
package io.brix.architecture.guard.rules;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.brix.samples.fixturealpha.core.AlphaPrice;
import io.brix.samples.fixturealpha.core.AlphaUsesAdapter;
import io.infra.adapter.kafka.KafkaAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the whitelist rules recognise plugin code by the guard's plugin roots and refuse a forbidden package.
 */
class WhitelistDependencyRuleTest {

    @Test
    @DisplayName("A plugin core class that uses an infrastructure adapter fails the strict whitelist")
    void forbiddenPackageInPluginCoreFails() {
        JavaClasses classes = new ClassFileImporter().importClasses(AlphaUsesAdapter.class);

        AssertionError error = assertThrows(AssertionError.class,
            () -> WhitelistDependencyRule.strictWhitelistRule().check(classes));
        assertTrue(error.getMessage().contains("FORBIDDEN package io.infra.adapter.kafka"), error.getMessage());
    }

    @Test
    @DisplayName("A plugin core class that uses only the JDK passes the strict whitelist")
    void jdkOnlyPluginCorePasses() {
        JavaClasses classes = new ClassFileImporter().importClasses(AlphaPrice.class);

        assertDoesNotThrow(() -> WhitelistDependencyRule.strictWhitelistRule().check(classes));
    }

    @Test
    @DisplayName("Any class that uses a forbidden infrastructure package fails")
    void forbiddenPackageFails() {
        JavaClasses classes = new ClassFileImporter().importClasses(AlphaUsesAdapter.class);

        AssertionError error = assertThrows(AssertionError.class,
            () -> WhitelistDependencyRule.noForbiddenDependencies().check(classes));
        assertTrue(error.getMessage().contains(KafkaAdapter.class.getName()), error.getMessage());
    }
}
