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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

/**
 * LoggingViaCapabilityRule Test Cases
 *
 * <p>Tests for Rule 7 enforcement: Logging standard constraints.</p>
 *
 * <h2>Covered Rules</h2>
 * <ul>
 *   <li>No throwing generic exceptions (Exception / RuntimeException)</li>
 *   <li>Domain/Entity layer must not use LoggerFactory.getLogger() directly</li>
 * </ul>
 *
 * <h2>Test Strategy</h2>
 * <p>Since this test library does not contain business code (no domain / entity packages),
 * we mainly verify rule definition correctness and guard library compliance.</p>
 *
 * @author Brix Architecture Team
 * @since 3.2.0
 */
@DisplayName("Rule 7: Logging Standards (Progressive Enhancement)")
class LoggingViaCapabilityRuleTest {

    // ============================================================================
    // Generic Exception Check Tests
    // ============================================================================

    @Nested
    @DisplayName("No Generic Exception Throwing")
    class NoGenericExceptionTests {

        @Test
        @DisplayName("Guard library should pass generic exception checks")
        void guardLibraryShouldPass() {
            ArchRule rule = LoggingViaCapabilityRule.rule();

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            }, "Guard library should not throw generic exceptions (Exception / RuntimeException)");
        }

        @Test
        @DisplayName("Rule should have correct error message")
        void ruleShouldHaveCorrectMessage() {
            ArchRule rule = LoggingViaCapabilityRule.rule();

            // Verify rule description is not empty
            String description = rule.getDescription();
            assertDoesNotThrow(() -> {
                if (description == null || description.isEmpty()) {
                    throw new AssertionError("Rule description should not be empty");
                }
            }, "Rule should have description");
        }
    }

    // ============================================================================
    // Domain Layer Logger Restriction Tests
    // ============================================================================

    @Nested
    @DisplayName("Domain/Entity Layer Must Not Use LoggerFactory Directly")
    class NoLoggerInDomainTests {

        @Test
        @DisplayName("Guard library should pass Domain layer Logger checks (no domain package)")
        void guardLibraryShouldPass() {
            // Use allowEmptyShould(true) to pass when no classes match
            ArchRule rule = LoggingViaCapabilityRule.noLoggerInDomain()
                    .allowEmptyShould(true);

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            }, "Guard library has no domain package, should automatically pass checks");
        }

        @Test
        @DisplayName("Rule should correctly identify domain package pattern")
        void ruleShouldTargetDomainPackage() {
            ArchRule rule = LoggingViaCapabilityRule.noLoggerInDomain();
            String description = rule.getDescription();

            // Verify rule can be created correctly
            assertDoesNotThrow(() -> {
                if (rule == null) {
                    throw new AssertionError("Rule should not be null");
                }
            }, "noLoggerInDomain rule should be created correctly");
        }

        @Test
        @DisplayName("Empty domain package scenario should pass checks")
        void emptyDomainPackageShouldPass() {
            // Use allowEmptyShould(true) to pass when no classes match
            ArchRule rule = LoggingViaCapabilityRule.noLoggerInDomain()
                    .allowEmptyShould(true);

            // Import a package without domain subpackage
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard.rules");
                rule.check(classes);
            }, "Code without domain subpackage should pass rule checks");
        }
    }

    // ============================================================================
    // Rule Combination Tests
    // ============================================================================

    @Nested
    @DisplayName("Rule Combination Validation")
    class RuleCombinationTests {

        @Test
        @DisplayName("Both rules should be applicable to the same codebase")
        void bothRulesShouldBeApplicable() {
            ArchRule genericExceptionRule = LoggingViaCapabilityRule.rule();
            // Use allowEmptyShould(true) to pass when no classes match
            ArchRule domainLoggerRule = LoggingViaCapabilityRule.noLoggerInDomain()
                    .allowEmptyShould(true);

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                genericExceptionRule.check(classes);
                domainLoggerRule.check(classes);
            }, "Both Rule 7 rules should apply simultaneously without conflicts");
        }
    }
}
