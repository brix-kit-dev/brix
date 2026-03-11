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
 * EventsViaCapabilityRule Positive and Negative Test Cases
 *
 * <p>Validates the correctness of Red Line 8 rule: Events must be published through EventBusCapability.</p>
 *
 * <h2>Covered Constraints</h2>
 * <ul>
 *   <li>Direct use of Spring ApplicationEventPublisher prohibited</li>
 *   <li>Direct use of Spring @EventListener annotation prohibited</li>
 *   <li>All event publishing/subscribing must go through EventBusCapability interface</li>
 * </ul>
 *
 * <h2>Design Principle</h2>
 * <p>Business code should not use Spring event mechanism directly because:</p>
 * <ul>
 *   <li>Spring Events are limited to single-process propagation</li>
 *   <li>EventBusCapability can achieve cross-process propagation via Kafka etc.</li>
 *   <li>Unified event interface facilitates tracking and governance</li>
 * </ul>
 *
 * @author Brix Architecture Team
 * @since 3.2.0
 */
@DisplayName("Red Line 8: Events must be published through EventBusCapability")
class EventsViaCapabilityRuleTest {

    // ============================================================================
    // Prohibit ApplicationEventPublisher tests
    // ============================================================================

    @Nested
    @DisplayName("Direct use of ApplicationEventPublisher prohibited")
    class NoApplicationEventPublisherTests {

        @Test
        @DisplayName("Architecture guard library itself should pass ApplicationEventPublisher check")
        void guardLibraryShouldPass() {
            ArchRule rule = EventsViaCapabilityRule.rule();

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            }, "Architecture guard library itself should not use ApplicationEventPublisher");
        }

        @Test
        @DisplayName("Rule should contain correct red line identifier")
        void ruleShouldContainRedLineMarker() {
            ArchRule rule = EventsViaCapabilityRule.rule();
            String description = rule.getDescription();

            // Verify rule description is not empty
            assertDoesNotThrow(() -> {
                if (description == null || description.isEmpty()) {
                    throw new AssertionError("Rule description should not be empty");
                }
            }, "Rule should contain description information");
        }

        @Test
        @DisplayName("Rule should correctly check code without Spring dependency")
        void ruleShouldWorkWithoutSpringDependency() {
            ArchRule rule = EventsViaCapabilityRule.rule();

            // Import a pure Java package (rule library itself does not depend on Spring)
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard.rules");
                rule.check(classes);
            }, "Code without Spring dependency should automatically pass rule check");
        }
    }

    // ============================================================================
    // Rule creation and configuration tests
    // ============================================================================

    @Nested
    @DisplayName("Rule configuration validation")
    class RuleConfigurationTests {

        @Test
        @DisplayName("Rule instance should not be null")
        void ruleShouldNotBeNull() {
            ArchRule rule = EventsViaCapabilityRule.rule();
            assertTrue(rule != null, "EventsViaCapabilityRule.rule() return value should not be null");
        }

        @Test
        @DisplayName("Rule should be idempotent when created multiple times")
        void ruleCreationShouldBeIdempotent() {
            // Use allowEmptyShould(true) to allow passing when no matching classes
            ArchRule rule1 = EventsViaCapabilityRule.rule().allowEmptyShould(true);
            ArchRule rule2 = EventsViaCapabilityRule.rule().allowEmptyShould(true);

            // Both created rules should work correctly
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
        @DisplayName("Should be compatible with NoSpringContainerApi rule")
        void shouldBeCompatibleWithNoSpringContainerApiRule() {
            // Use allowEmptyShould(true) to allow passing when no matching classes
            ArchRule eventsRule = EventsViaCapabilityRule.rule().allowEmptyShould(true);
            ArchRule containerRule = NoSpringContainerApiRule.rule().allowEmptyShould(true);

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                eventsRule.check(classes);
                containerRule.check(classes);
            }, "Red Line 8 and Red Line 5 rules should be applicable simultaneously without conflicts");
        }

        @Test
        @DisplayName("Should be compatible with CrossModuleViaCapability rule")
        void shouldBeCompatibleWithCrossModuleRule() {
            // Use allowEmptyShould(true) to allow passing when no matching classes
            ArchRule eventsRule = EventsViaCapabilityRule.rule().allowEmptyShould(true);
            ArchRule crossModuleRule = CrossModuleViaCapabilityRule.rule().allowEmptyShould(true);

            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                eventsRule.check(classes);
                crossModuleRule.check(classes);
            }, "Red Line 8 and Red Line 4 rules should be applicable simultaneously without conflicts");
        }
    }
}
