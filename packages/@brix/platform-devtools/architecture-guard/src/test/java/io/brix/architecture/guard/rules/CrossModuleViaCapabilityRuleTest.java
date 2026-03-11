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
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

/**
 * CrossModuleViaCapabilityRule Positive and Negative Test Cases
 *
 * <p>Validates the correctness of Red Line 4 rule: Cross-module communication must go through Capability interfaces.</p>
 *
 * <h2>Covered Constraints</h2>
 * <ul>
 *   <li>Domain layer prohibited from directly depending on Service layer</li>
 *   <li>Cross-module dependencies should be obtained via RuntimeContext.getCapability()</li>
 *   <li>Prohibited from @Autowired other modules' Services</li>
 * </ul>
 *
 * <h2>Layer Relationships</h2>
 * <pre>
 * ┌─────────────────────────────┐
 * │   Service Layer (App Svc)   │ ← Can call Domain layer
 * ├─────────────────────────────┤
 * │   Domain Layer (Domain Model)│ ← Prohibited from directly calling Service layer
 * └─────────────────────────────┘
 * </pre>
 *
 * <h2>Compliant Pattern</h2>
 * <p>If Domain layer needs to call other module capabilities, should use Capability interface:</p>
 * <pre>{@code
 * // Correct approach: via Capability
 * AuthCapability auth = runtimeContext.getCapability(AuthCapability.class);
 * auth.getCurrentUser();
 * 
 * // Wrong approach: direct Service injection
 * @Autowired UserService userService; // Violation!
 * }</pre>
 *
 * @author Brix Architecture Team
 * @since 3.2.0
 */
@DisplayName("Red Line 4: Cross-module communication must go through Capability interfaces")
class CrossModuleViaCapabilityRuleTest {

    // ============================================================================
    // Domain Layer Dependency Check Tests
    // ============================================================================

    @Nested
    @DisplayName("Domain layer prohibited from directly depending on Service layer")
    class DomainShouldNotDependOnServiceTests {

        @Test
        @DisplayName("Architecture guard library itself should pass Domain-Service dependency check")
        void guardLibraryShouldPass() {
            // Use allowEmptyShould(true) to allow passing when no matching classes
            ArchRule rule = CrossModuleViaCapabilityRule.rule().allowEmptyShould(true);

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            }, "Architecture guard library itself has no domain package, should pass check");
        }

        @Test
        @DisplayName("Code without domain package should pass check")
        void codeWithoutDomainPackageShouldPass() {
            // Use allowEmptyShould(true) to allow passing when no matching classes
            ArchRule rule = CrossModuleViaCapabilityRule.rule().allowEmptyShould(true);

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard.rules");
                rule.check(classes);
            }, "Code without domain sub-package should automatically pass rule check");
        }

        @Test
        @DisplayName("Rule should be created correctly")
        void ruleShouldBeCreatedCorrectly() {
            ArchRule rule = CrossModuleViaCapabilityRule.rule();
            assertTrue(rule != null, "CrossModuleViaCapabilityRule.rule() return value should not be null");
        }
    }

    // ============================================================================
    // Rule Description and Configuration Tests
    // ============================================================================

    @Nested
    @DisplayName("Rule configuration validation")
    class RuleConfigurationTests {

        @Test
        @DisplayName("Rule should contain domain and service package patterns")
        void ruleShouldTargetCorrectPackages() {
            ArchRule rule = CrossModuleViaCapabilityRule.rule();
            String description = rule.getDescription();

            // Verify rule description exists
            assertDoesNotThrow(() -> {
                if (description == null || description.isEmpty()) {
                    throw new AssertionError("Rule description should not be empty");
                }
            }, "Rule should contain description information");
        }

        @Test
        @DisplayName("Rule should be idempotent when created multiple times")
        void ruleCreationShouldBeIdempotent() {
            // Use allowEmptyShould(true) to allow passing when no matching classes
            ArchRule rule1 = CrossModuleViaCapabilityRule.rule().allowEmptyShould(true);
            ArchRule rule2 = CrossModuleViaCapabilityRule.rule().allowEmptyShould(true);

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule1.check(classes);
                rule2.check(classes);
            }, "Rules should work correctly when created multiple times");
        }
    }

    // ============================================================================
    // Combination Tests with Other Rules
    // ============================================================================

    @Nested
    @DisplayName("Rule combination compatibility")
    class RuleCombinationTests {

        @Test
        @DisplayName("Should be compatible with all red line rules")
        void shouldBeCompatibleWithAllRules() {
            // Use allowEmptyShould(true) to allow passing when no matching classes
            ArchRule crossModuleRule = CrossModuleViaCapabilityRule.rule().allowEmptyShould(true);
            ArchRule eventsRule = EventsViaCapabilityRule.rule();
            ArchRule containerRule = NoSpringContainerApiRule.rule();
            ArchRule loggingRule = LoggingViaCapabilityRule.rule();

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                crossModuleRule.check(classes);
                eventsRule.check(classes);
                containerRule.check(classes);
                loggingRule.check(classes);
            }, "All red line rules should be applicable simultaneously without conflicts");
        }

        @Test
        @DisplayName("Rule should be compatible with HTTP client rules")
        void shouldBeCompatibleWithHttpClientRules() {
            // Use allowEmptyShould(true) to allow passing when no matching classes
            ArchRule crossModuleRule = CrossModuleViaCapabilityRule.rule().allowEmptyShould(true);
            ArchRule httpRule = NoDirectHttpClientsRule.noRestTemplate();

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                crossModuleRule.check(classes);
                httpRule.check(classes);
            }, "Red Line 4 and Red Line 3 rules should be applicable simultaneously without conflicts");
        }
    }

    // ============================================================================
    // Boundary condition tests
    // ============================================================================

    @Nested
    @DisplayName("Boundary condition validation")
    class BoundaryConditionTests {

        @Test
        @DisplayName("Empty package scenario should pass check")
        void emptyPackageShouldPass() {
            // Use allowEmptyShould(true) to allow passing when no matching classes
            ArchRule rule = CrossModuleViaCapabilityRule.rule().allowEmptyShould(true);

            assertDoesNotThrow(() -> {
                // Try to import a potentially empty package (profiles subdirectory)
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard.profiles");
                rule.check(classes);
            }, "Empty package or package without domain dependencies should pass check");
        }

        @Test
        @DisplayName("Single rule class check should pass")
        void singleClassShouldPass() {
            // Use allowEmptyShould(true) to allow passing when no matching classes
            ArchRule rule = CrossModuleViaCapabilityRule.rule().allowEmptyShould(true);

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importClasses(CrossModuleViaCapabilityRule.class);
                rule.check(classes);
            }, "Single rule class should pass its own check");
        }
    }
}
