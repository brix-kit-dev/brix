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

import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.brix.samples.fixturealpha.core.AlphaPrice;
import io.brix.samples.fixturealpha.core.AlphaUsesRuntime;
import io.runtime.orchestrator.bootstrap.RuntimeShellFatalAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the core-only-SDK rule refuses the Runtime implementation, io.runtime.orchestrator.
 */
class OnlyRuntimeSdkApiRuleTest {

    @Test
    @DisplayName("A plugin core class that uses the Runtime implementation fails")
    void runtimeImplementationFails() {
        AssertionError error = assertThrows(AssertionError.class,
            () -> OnlyRuntimeSdkApiRule.rule().check(new ClassFileImporter().importClasses(AlphaUsesRuntime.class)));
        assertTrue(error.getMessage().contains(RuntimeShellFatalAction.class.getName()), error.getMessage());
    }

    @Test
    @DisplayName("A plugin core class that uses only the JDK passes")
    void jdkOnlyCorePasses() {
        assertDoesNotThrow(
            () -> OnlyRuntimeSdkApiRule.rule().check(new ClassFileImporter().importClasses(AlphaPrice.class)));
    }
}
