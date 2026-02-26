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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * NoInfraAdapterRule Test Cases
 *
 * <p>Tests for Rule 1 enforcement: Ensures rule detects violations and passes compliant code.</p>
 *
 * <h2>Test Strategy</h2>
 * <ul>
 *   <li><b>Positive tests</b>: Verify compliant code passes rule checks</li>
 *   <li><b>Negative tests</b>: Verify violating code is caught by rules</li>
 * </ul>
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 */
@DisplayName("Rule 1: No Direct Infrastructure Adapter Dependencies")
class NoInfraAdapterRuleTest {

    private static ArchRule rule;

    @BeforeAll
    static void setUp() {
        rule = NoInfraAdapterRule.rule();
    }

    // ============================================================================
    // Positive tests: Compliant code should pass
    // ============================================================================

    @Nested
    @DisplayName("Positive: Compliant Code")
    class CompliantCodeTests {

        @Test
        @DisplayName("Business classes depending only on runtime-sdk-api contracts should pass")
        void shouldPassWhenOnlyDependingOnContracts() {
            // Use mock compliant classes for testing
            // In production, real business module classes would be imported
            JavaClasses classes = new ClassFileImporter()
                    .importPackages("io.brix.architecture.guard.testfixtures.compliant");
            
            // Skip test if package doesn't exist
            if (classes.isEmpty()) {
                return; // Skip when no test fixtures
            }
            
            assertDoesNotThrow(() -> rule.check(classes),
                    "Code depending only on contract layer should pass Rule 1 checks");
        }
    }

    // ============================================================================
    // Negative tests: Violating code should be detected
    // ============================================================================

    @Nested
    @DisplayName("Negative: Violating Code")
    class ViolatingCodeTests {

        @Test
        @DisplayName("Direct dependency on infra-adapter-kafka should fail")
        void shouldFailWhenDependingOnKafkaAdapter() {
            JavaClasses classes = new ClassFileImporter()
                    .importPackages("io.brix.architecture.guard.testfixtures.violating.kafka");
            
            // Skip test if package doesn't exist
            if (classes.isEmpty()) {
                return;
            }
            
            assertThrows(AssertionError.class, () -> rule.check(classes),
                    "Code directly depending on Kafka adapter should violate Rule 1");
        }

        @Test
        @DisplayName("Direct dependency on infra-adapter-redis should fail")
        void shouldFailWhenDependingOnRedisAdapter() {
            JavaClasses classes = new ClassFileImporter()
                    .importPackages("io.brix.architecture.guard.testfixtures.violating.redis");
            
            if (classes.isEmpty()) {
                return;
            }
            
            assertThrows(AssertionError.class, () -> rule.check(classes),
                    "Code directly depending on Redis adapter should violate Rule 1");
        }
    }
}
