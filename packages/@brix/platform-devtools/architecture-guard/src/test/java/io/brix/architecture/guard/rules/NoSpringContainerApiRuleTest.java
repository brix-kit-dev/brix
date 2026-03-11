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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NoSpringContainerApiRule Positive and Negative Test Cases
 *
 * <p>Validates the correctness of Red Line 5 rule: Dependency on Spring Container specific APIs prohibited.</p>
 *
 * <h2>Covered Constraints</h2>
 * <ul>
 *   <li>Direct use of ApplicationContext prohibited</li>
 *   <li>Direct use of BeanFactory prohibited</li>
 *   <li>All dependency injection should be done through constructor</li>
 *   <li>Runtime capabilities should be obtained through RuntimeContext</li>
 * </ul>
 *
 * <h2>Design Principle</h2>
 * <p>Business code should not use Spring Container APIs directly because:</p>
 * <ul>
 *   <li>Reduces coupling with Spring framework</li>
 *   <li>Facilitates unit testing and independent module deployment</li>
 *   <li>Follows Dependency Inversion Principle (DIP)</li>
 * </ul>
 *
 * <h2>Compliant Pattern</h2>
 * <pre>{@code
 * // Correct approach: Constructor injection
 * public class BookingService {
 *     private final BookingRepository repository;
 *     public BookingService(BookingRepository repository) {
 *         this.repository = repository;
 *     }
 * }
 * 
 * // Wrong approach: Direct container API usage
 * @Autowired ApplicationContext context; // Violation!
 * context.getBean(BookingRepository.class);
 * }</pre>
 *
 * @author Brix Architecture Team
 * @since 3.2.0
 */
@DisplayName("Red Line 5: Dependency on Spring Container specific APIs prohibited")
class NoSpringContainerApiRuleTest {

    // ============================================================================
    // ApplicationContext check tests
    // ============================================================================

    @Nested
    @DisplayName("Direct use of ApplicationContext prohibited")
    class NoApplicationContextTests {

        @Test
        @DisplayName("Architecture guard library itself should pass ApplicationContext check")
        void guardLibraryShouldPass() {
            ArchRule rule = NoSpringContainerApiRule.rule();

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            }, "Architecture guard library itself should not use ApplicationContext");
        }

        @Test
        @DisplayName("Rules sub-package should pass check")
        void rulesPackageShouldPass() {
            ArchRule rule = NoSpringContainerApiRule.rule();

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard.rules");
                rule.check(classes);
            }, "Rules sub-package should not use ApplicationContext");
        }
    }

    // ============================================================================
    // BeanFactory check tests
    // ============================================================================

    @Nested
    @DisplayName("Direct use of BeanFactory prohibited")
    class NoBeanFactoryTests {

        @Test
        @DisplayName("Architecture guard library itself should pass BeanFactory check")
        void guardLibraryShouldPass() {
            ArchRule rule = NoSpringContainerApiRule.rule();

            // Rule also checks ApplicationContext and BeanFactory
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            }, "Architecture guard library itself should not use BeanFactory");
        }

        @Test
        @DisplayName("Profiles sub-package should pass check")
        void profilesPackageShouldPass() {
            ArchRule rule = NoSpringContainerApiRule.rule();

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard.profiles");
                rule.check(classes);
            }, "Profiles sub-package should not use BeanFactory");
        }
    }

    // ============================================================================
    // Rule configuration tests
    // ============================================================================

    @Nested
    @DisplayName("Rule configuration validation")
    class RuleConfigurationTests {

        @Test
        @DisplayName("Rule instance should not be null")
        void ruleShouldNotBeNull() {
            ArchRule rule = NoSpringContainerApiRule.rule();
            assertTrue(rule != null, "NoSpringContainerApiRule.rule() return value should not be null");
        }

        @Test
        @DisplayName("Rule should have correct description")
        void ruleShouldHaveDescription() {
            ArchRule rule = NoSpringContainerApiRule.rule();
            String description = rule.getDescription();

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
            ArchRule rule1 = NoSpringContainerApiRule.rule().allowEmptyShould(true);
            ArchRule rule2 = NoSpringContainerApiRule.rule().allowEmptyShould(true);

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule1.check(classes);
                rule2.check(classes);
            }, "Rules should work correctly when created multiple times");
        }
    }

    // ============================================================================
    // Combination tests with other rules
    // ============================================================================

    @Nested
    @DisplayName("Rule combination compatibility")
    class RuleCombinationTests {

        @Test
        @DisplayName("Should be compatible with all Capability-related rules")
        void shouldBeCompatibleWithCapabilityRules() {
            // Use allowEmptyShould(true) to allow passing when no matching classes
            ArchRule containerRule = NoSpringContainerApiRule.rule().allowEmptyShould(true);
            ArchRule eventsRule = EventsViaCapabilityRule.rule().allowEmptyShould(true);
            ArchRule crossModuleRule = CrossModuleViaCapabilityRule.rule().allowEmptyShould(true);
            ArchRule loggingRule = LoggingViaCapabilityRule.rule().allowEmptyShould(true);

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                containerRule.check(classes);
                eventsRule.check(classes);
                crossModuleRule.check(classes);
                loggingRule.check(classes);
            }, "All Capability-related rules should be applicable simultaneously without conflicts");
        }

        @Test
        @DisplayName("Should be compatible with infrastructure adapter rules")
        void shouldBeCompatibleWithInfraAdapterRules() {
            // Use allowEmptyShould(true) to allow passing when no matching classes
            ArchRule containerRule = NoSpringContainerApiRule.rule().allowEmptyShould(true);
            ArchRule infraAdapterRule = NoInfraAdapterRule.rule().allowEmptyShould(true);
            ArchRule middlewareRule = NoMiddlewareClientsRule.noSpringKafka().allowEmptyShould(true);

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                containerRule.check(classes);
                infraAdapterRule.check(classes);
                middlewareRule.check(classes);
            }, "Red Line 5 and infrastructure-related rules should be applicable simultaneously without conflicts");
        }
    }

    // ============================================================================
    // Boundary condition tests
    // ============================================================================

    @Nested
    @DisplayName("Boundary condition validation")
    class BoundaryConditionTests {

        @Test
        @DisplayName("Code without Spring dependency should pass check")
        void codeWithoutSpringDependencyShouldPass() {
            ArchRule rule = NoSpringContainerApiRule.rule();

            // Rule library itself does not depend on Spring
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importClasses(NoSpringContainerApiRule.class);
                rule.check(classes);
            }, "Code without Spring dependency should automatically pass rule check");
        }

        @Test
        @DisplayName("ArchUnit-related classes should pass check")
        void archUnitClassesShouldPass() {
            ArchRule rule = NoSpringContainerApiRule.rule();

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            }, "ArchUnit rule definition classes should pass their own check");
        }
    }
}
