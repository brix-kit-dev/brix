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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NoEnterpriseToPlatformAdminRule positive / smoke tests.
 *
 * <p>Verifies SSOT §11 R-1 and R-2 invariants are enforced by ArchUnit:
 * the {@code platform-admin} module is a leaf in the module graph from the
 * enterprise side, and (symmetrically) does not import enterprise types.</p>
 *
 * @author Brix Architecture Team
 * @since 3.2.0
 */
@DisplayName("Red Line: Enterprise must not depend on platform-admin")
class NoEnterpriseToPlatformAdminRuleTest {

    @Nested
    @DisplayName("Rule configuration")
    class RuleConfigurationTests {

        @Test
        @DisplayName("enterpriseShouldNotDependOnPlatformAdmin produces a non-null rule with description")
        void enterpriseDirectionRuleConfigured() {
            ArchRule rule = NoEnterpriseToPlatformAdminRule
                    .enterpriseShouldNotDependOnPlatformAdmin();
            assertNotNull(rule, "rule must not be null");
            assertNotNull(rule.getDescription(), "description must not be null");
            assertTrue(rule.getDescription().toLowerCase().contains("platform.admin"),
                    "description should mention platform.admin");
        }

        @Test
        @DisplayName("platformAdminShouldNotDependOnEnterprise produces a non-null rule with description")
        void platformAdminDirectionRuleConfigured() {
            ArchRule rule = NoEnterpriseToPlatformAdminRule
                    .platformAdminShouldNotDependOnEnterprise();
            assertNotNull(rule);
            assertNotNull(rule.getDescription());
            assertTrue(rule.getDescription().toLowerCase().contains("enterprise"));
        }

        @Test
        @DisplayName("enforce() returns a non-null rule")
        void enforceConfigured() {
            assertNotNull(NoEnterpriseToPlatformAdminRule.enforce());
        }
    }

    @Nested
    @DisplayName("Vacuous truth on guard library itself")
    class GuardLibraryTests {

        @Test
        @DisplayName("Architecture guard library has neither enterprise nor platform-admin classes")
        void guardLibraryPasses() {
            ArchRule rule = NoEnterpriseToPlatformAdminRule
                    .enterpriseShouldNotDependOnPlatformAdmin();
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            });
        }

        @Test
        @DisplayName("Reverse direction also passes on guard library")
        void guardLibraryPassesReverse() {
            ArchRule rule = NoEnterpriseToPlatformAdminRule
                    .platformAdminShouldNotDependOnEnterprise();
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            });
        }
    }
}
