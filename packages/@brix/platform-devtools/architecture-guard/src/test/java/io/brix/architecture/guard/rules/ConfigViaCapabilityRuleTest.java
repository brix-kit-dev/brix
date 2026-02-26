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

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ConfigViaCapabilityRule Test Cases
 *
 * <p>Tests for Rule 6 enforcement:</p>
 * <ul>
 *   <li>No direct standard stream access (System.out/err/in)</li>
 *   <li>No direct System.getenv() / System.getProperty() calls</li>
 *   <li>No @Value annotation usage</li>
 * </ul>
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 */
@DisplayName("Rule 6: Config Must Be Obtained Via ConfigCapability")
class ConfigViaCapabilityRuleTest {

    // ============================================================================
    // Rule Instantiation Tests
    // ============================================================================

    @Nested
    @DisplayName("Rule Instantiation")
    class RuleInstantiationTests {

        @Test
        @DisplayName("rule() should create standard stream check rule correctly")
        void shouldCreateStandardStreamRule() {
            ArchRule rule = ConfigViaCapabilityRule.rule();
            assertNotNull(rule, "Rule instance should not be null");
        }

        @Test
        @DisplayName("noSystemEnvAccess() should create environment variable check rule correctly")
        void shouldCreateNoSystemEnvAccessRule() {
            ArchRule rule = ConfigViaCapabilityRule.noSystemEnvAccess();
            assertNotNull(rule, "Rule instance should not be null");
        }

        @Test
        @DisplayName("noSpringValue() should create @Value check rule correctly")
        void shouldCreateNoSpringValueRule() {
            ArchRule rule = ConfigViaCapabilityRule.noSpringValue();
            assertNotNull(rule, "Rule instance should not be null");
        }
    }

    // ============================================================================
    // Positive tests: Compliant code should pass
    // ============================================================================

    @Nested
    @DisplayName("Positive: Compliant Code")
    class CompliantCodeTests {

        @Test
        @DisplayName("Guard library should pass standard stream checks")
        void guardLibraryShouldPassStandardStreamCheck() {
            ArchRule rule = ConfigViaCapabilityRule.rule();
            
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            }, "Guard library should not use standard streams");
        }

        @Test
        @DisplayName("Guard library should pass System.getenv checks")
        void guardLibraryShouldPassSystemEnvCheck() {
            ArchRule rule = ConfigViaCapabilityRule.noSystemEnvAccess();
            
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            }, "Guard library should not call System.getenv()");
        }

        @Test
        @DisplayName("Guard library should pass @Value checks")
        void guardLibraryShouldPassSpringValueCheck() {
            ArchRule rule = ConfigViaCapabilityRule.noSpringValue();
            
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            }, "Guard library should not use @Value annotation");
        }
    }
}
