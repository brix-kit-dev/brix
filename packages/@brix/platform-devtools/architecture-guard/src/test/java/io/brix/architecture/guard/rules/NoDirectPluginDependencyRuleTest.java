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
import io.brix.platform.fixture.PlatformUsesPlugin;
import io.brix.samples.fixturealpha.core.AlphaCatalog;
import io.brix.samples.fixturealpha.server.AlphaEndpoint;
import io.brix.samples.fixturebeta.api.BetaStockQuery;
import io.brix.samples.fixturebeta.core.BetaStock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the plugin-to-plugin rule fires on a real plugin root (A-9): another plugin's implementation is
 * refused, its published {@code api} contract is not.
 */
class NoDirectPluginDependencyRuleTest {

    @Test
    @DisplayName("A plugin that uses another plugin's implementation class fails")
    void anotherPluginsImplementationFails() {
        JavaClasses classes = new ClassFileImporter().importClasses(AlphaCatalog.class, BetaStock.class);

        AssertionError error = assertThrows(AssertionError.class,
            () -> NoDirectPluginDependencyRule.rule().check(classes));
        assertTrue(error.getMessage().contains(BetaStock.class.getName()), error.getMessage());
    }

    @Test
    @DisplayName("The rule for a named plugin refuses another plugin's implementation class")
    void namedPluginRefusesAnotherPluginsImplementation() {
        JavaClasses classes = new ClassFileImporter().importClasses(AlphaCatalog.class, BetaStock.class);

        assertThrows(AssertionError.class,
            () -> NoDirectPluginDependencyRule.forPlugin("io.brix.samples.fixturealpha").check(classes));
    }

    @Test
    @DisplayName("A plugin that uses only another plugin's api contract passes")
    void anotherPluginsContractPasses() {
        JavaClasses classes = new ClassFileImporter().importClasses(AlphaEndpoint.class, BetaStockQuery.class);

        assertDoesNotThrow(() -> NoDirectPluginDependencyRule.rule().check(classes));
    }

    @Test
    @DisplayName("Classes outside the plugin roots are not plugins")
    void classesOutsidePluginsAreSkipped() {
        JavaClasses classes = new ClassFileImporter().importClasses(PlatformUsesPlugin.class, BetaStock.class);

        assertDoesNotThrow(() -> NoDirectPluginDependencyRule.rule().check(classes));
    }
}
