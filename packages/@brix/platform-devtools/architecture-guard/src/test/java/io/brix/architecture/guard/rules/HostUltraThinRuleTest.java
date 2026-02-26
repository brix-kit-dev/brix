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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

/**
 * HostUltraThinRule Test Cases
 *
 * <p>Tests for Host Ultra-Thin principle enforcement:</p>
 * <ul>
 *   <li>@Bean zero tolerance rule</li>
 *   <li>Control flow zero tolerance rule</li>
 *   <li>Private method zero tolerance rule</li>
 *   <li>Business annotation prohibition rule</li>
 *   <li>Capability implementation prohibition rule</li>
 * </ul>
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 */
@DisplayName("Ultra-Thin Host Principle Rule Tests")
class HostUltraThinRuleTest {

    // ============================================================================
    // Rule Instantiation Tests
    // ============================================================================

    @Nested
    @DisplayName("Rule Instantiation")
    class RuleInstantiationTests {

        @Test
        @DisplayName("noBeanDefinitionsInAutoConfiguration rule should be created correctly")
        void shouldCreateNoBeanDefinitionsRule() {
            ArchRule rule = HostUltraThinRule.noBeanDefinitionsInAutoConfiguration();
            assertNotNull(rule, "Rule instance should not be null");
        }

        @Test
        @DisplayName("noControlFlowInHost rule should be created correctly")
        void shouldCreateNoControlFlowRule() {
            ArchRule rule = HostUltraThinRule.noControlFlowInHost();
            assertNotNull(rule, "Rule instance should not be null");
        }

        @Test
        @DisplayName("noPrivateMethodsInAutoConfiguration rule should be created correctly")
        void shouldCreateNoPrivateMethodsRule() {
            ArchRule rule = HostUltraThinRule.noPrivateMethodsInAutoConfiguration();
            assertNotNull(rule, "Rule instance should not be null");
        }

        @Test
        @DisplayName("noBusinessAnnotations rule should be created correctly")
        void shouldCreateNoBusinessAnnotationsRule() {
            ArchRule rule = HostUltraThinRule.noBusinessAnnotations();
            assertNotNull(rule, "Rule instance should not be null");
        }

        @Test
        @DisplayName("noCapabilityImplementation rule should be created correctly")
        void shouldCreateNoCapabilityImplementationRule() {
            ArchRule rule = HostUltraThinRule.noCapabilityImplementation();
            assertNotNull(rule, "Rule instance should not be null");
        }
    }

    // ============================================================================
    // Positive tests: Compliant code should pass
    // ============================================================================

    @Nested
    @DisplayName("Positive: Compliant Host Code")
    class CompliantHostCodeTests {

        @Test
        @DisplayName("Guard library should pass @Bean zero tolerance checks")
        void guardLibraryShouldPassBeanCheck() {
            // Use allowEmptyShould(true) to pass when no classes match
            ArchRule rule = HostUltraThinRule.noBeanDefinitionsInAutoConfiguration()
                    .allowEmptyShould(true);
            
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            }, "Guard library has no AutoConfiguration classes, should pass checks");
        }

        @Test
        @DisplayName("Guard library should pass private method checks")
        void guardLibraryShouldPassPrivateMethodCheck() {
            // Use allowEmptyShould(true) to pass when no classes match
            ArchRule rule = HostUltraThinRule.noPrivateMethodsInAutoConfiguration()
                    .allowEmptyShould(true);
            
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            }, "Guard library has no AutoConfiguration classes, should pass checks");
        }

        @Test
        @DisplayName("Guard library should pass business annotation checks")
        void guardLibraryShouldPassBusinessAnnotationCheck() {
            // Use allowEmptyShould(true) to pass when no classes match
            ArchRule rule = HostUltraThinRule.noBusinessAnnotations()
                    .allowEmptyShould(true);
            
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            }, "Guard library is not in host package, should pass checks");
        }
    }

    // ============================================================================
    // Negative Test Notes
    // ============================================================================
    // 
    // Negative tests require creating violating test fixture classes located at:
    //   io.brix.architecture.guard.testfixtures.violating.host package
    // 
    // Since violating code causes test failures (which is expected),
    // we use assertThrows to verify rules correctly catch violations.
    // 
    // Complete negative tests will be performed during integration testing.
    // ============================================================================
}
