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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.brix.samples.fixturealpha.core.AlphaPrice;
import io.brix.samples.fixturealpha.core.AlphaReadsEnvironment;
import io.brix.samples.fixturealpha.core.AlphaReadsSystemProperty;
import io.brix.samples.fixturevalue.config.ClassOnlyListedValueReader;
import io.brix.samples.fixturevalue.config.ListedConstructorReader;
import io.brix.samples.fixturevalue.config.ListedValueReader;
import io.brix.samples.fixturevalue.config.ListedValueReaderWithNewMember;
import io.brix.samples.fixturevalue.config.UnlistedValueReader;

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
        @DisplayName("A plugin class that reads neither the environment nor system properties passes")
        void pluginWithoutDirectConfigReadsPasses() {
            JavaClasses classes = new ClassFileImporter().importClasses(AlphaPrice.class);

            assertDoesNotThrow(() -> ConfigViaCapabilityRule.noSystemEnvAccess().check(classes));
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

    // ============================================================================
    // Negative tests: Violating code should be detected
    // ============================================================================

    @Nested
    @DisplayName("Negative: Violating Code")
    class ViolatingCodeTests {

        @Test
        @DisplayName("A plugin class that calls System.getenv() fails")
        void environmentReadFails() {
            JavaClasses classes = new ClassFileImporter().importClasses(AlphaReadsEnvironment.class);

            AssertionError error = assertThrows(AssertionError.class,
                () -> ConfigViaCapabilityRule.noSystemEnvAccess().check(classes));
            assertTrue(error.getMessage().contains("calls System.getenv()"), error.getMessage());
        }

        @Test
        @DisplayName("A plugin class that calls System.getProperty() fails")
        void systemPropertyReadFails() {
            JavaClasses classes = new ClassFileImporter().importClasses(AlphaReadsSystemProperty.class);

            AssertionError error = assertThrows(AssertionError.class,
                () -> ConfigViaCapabilityRule.noSystemEnvAccess().check(classes));
            assertTrue(error.getMessage().contains("calls System.getProperty()"), error.getMessage());
        }
    }

    // ============================================================================
    // @Value and the members a module lists (META-INF/brix/guard/spring-value-readers.txt of this module's tests)
    // ============================================================================

    @Nested
    @DisplayName("@Value and the listed members")
    class SpringValueTests {

        @Test
        @DisplayName("Listed members pass: a field, a method parameter, a constructor parameter")
        void listedMembersPass() {
            JavaClasses classes = new ClassFileImporter()
                .importClasses(ListedValueReader.class, ListedConstructorReader.class);

            assertDoesNotThrow(() -> ConfigViaCapabilityRule.noSpringValue().check(classes));
        }

        @Test
        @DisplayName("A new @Value field or parameter of a listed class fails, and the listed member is not reported")
        void unlistedMemberOfAListedClassFails() {
            JavaClasses classes = new ClassFileImporter().importClasses(ListedValueReaderWithNewMember.class);

            AssertionError error = assertThrows(AssertionError.class,
                () -> ConfigViaCapabilityRule.noSpringValue().check(classes));
            assertTrue(error.getMessage().contains(ListedValueReaderWithNewMember.class.getName() + ".zone"),
                error.getMessage());
            assertTrue(error.getMessage().contains(ListedValueReaderWithNewMember.class.getName() + ".timeout(int)"),
                error.getMessage());
            assertFalse(error.getMessage().contains(ListedValueReaderWithNewMember.class.getName() + ".region"),
                error.getMessage());
        }

        @Test
        @DisplayName("A class with no listed member fails on any @Value")
        void unlistedClassFails() {
            JavaClasses classes = new ClassFileImporter().importClasses(UnlistedValueReader.class);

            AssertionError error = assertThrows(AssertionError.class,
                () -> ConfigViaCapabilityRule.noSpringValue().check(classes));
            assertTrue(error.getMessage().contains(UnlistedValueReader.class.getName()), error.getMessage());
        }

        @Test
        @DisplayName("A class name alone lists no member")
        void classOnlyEntryListsNothing() {
            JavaClasses classes = new ClassFileImporter().importClasses(ClassOnlyListedValueReader.class);

            AssertionError error = assertThrows(AssertionError.class,
                () -> ConfigViaCapabilityRule.noSpringValue().check(classes));
            assertTrue(error.getMessage().contains(ClassOnlyListedValueReader.class.getName()), error.getMessage());
        }

        @Test
        @DisplayName("A plugin class without @Value passes")
        void classWithoutValuePasses() {
            JavaClasses classes = new ClassFileImporter().importClasses(AlphaPrice.class);

            assertDoesNotThrow(() -> ConfigViaCapabilityRule.noSpringValue().check(classes));
        }
    }
}
