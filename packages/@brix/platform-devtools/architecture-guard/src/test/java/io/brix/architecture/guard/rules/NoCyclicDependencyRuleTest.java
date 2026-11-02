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

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.brix.samples.fixturealpha.core.AlphaCatalog;
import io.brix.samples.fixturealpha.server.AlphaEndpoint;
import io.brix.samples.fixturebeta.api.BetaStockQuery;
import io.brix.samples.fixturebeta.core.BetaRestock;
import io.brix.samples.fixturebeta.core.BetaStock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the plugin cycle rule slices by the real plugin roots.
 */
class NoCyclicDependencyRuleTest {

    @Test
    @DisplayName("Two plugins that use each other form a cycle and fail")
    void cycleBetweenPluginsFails() {
        JavaClasses classes = new ClassFileImporter()
            .importClasses(AlphaCatalog.class, BetaStock.class, BetaRestock.class);

        assertThrows(AssertionError.class, () -> NoCyclicDependencyRule.noCyclesBetweenPlugins().check(classes));
    }

    @Test
    @DisplayName("A one-way dependency on another plugin's contract is no cycle")
    void oneWayDependencyPasses() {
        JavaClasses classes = new ClassFileImporter().importClasses(AlphaEndpoint.class, BetaStockQuery.class);

        assertDoesNotThrow(() -> NoCyclicDependencyRule.noCyclesBetweenPlugins().check(classes));
    }
}
