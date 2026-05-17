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
 * NoStringRoleLiteralRule positive / smoke tests.
 *
 * <p>SSOT §11 R-3 — role identifiers must come from {@code RoleCode}
 * constants, never as bare string literals. This test class verifies the
 * rule is well-formed and that the architecture-guard library itself is
 * compliant.</p>
 *
 * @author Brix Architecture Team
 * @since 3.2.0
 */
@DisplayName("Red Line R-3: No string role literals in production code")
class NoStringRoleLiteralRuleTest {

    @Nested
    @DisplayName("Rule configuration")
    class RuleConfigurationTests {

        @Test
        @DisplayName("Rule is non-null and has a meaningful description")
        void ruleConfigured() {
            ArchRule rule = NoStringRoleLiteralRule.enforce();
            assertNotNull(rule);
            String desc = rule.getDescription();
            assertNotNull(desc);
            assertTrue(desc.toLowerCase().contains("rolecode")
                            || desc.toLowerCase().contains("role"),
                    "description should mention role/RoleCode");
        }

        @Test
        @DisplayName("KNOWN_ROLE_CODES contains the canonical four roles")
        void knownRolesAreCanonical() {
            assertTrue(NoStringRoleLiteralRule.KNOWN_ROLE_CODES.contains("SUPER_ADMIN"));
            assertTrue(NoStringRoleLiteralRule.KNOWN_ROLE_CODES.contains("PLATFORM_ADMIN"));
            assertTrue(NoStringRoleLiteralRule.KNOWN_ROLE_CODES.contains("SUPPORT_ADMIN"));
            assertTrue(NoStringRoleLiteralRule.KNOWN_ROLE_CODES.contains("AUDITOR"));
        }
    }

    @Nested
    @DisplayName("Self-compliance")
    class SelfComplianceTests {

        @Test
        @DisplayName("architecture-guard library does not contain any role-literal violations")
        void guardLibraryIsCompliant() {
            ArchRule rule = NoStringRoleLiteralRule.enforce();
            assertDoesNotThrow(() -> {
                JavaClasses classes = new ClassFileImporter()
                        .importPackages("io.brix.architecture.guard");
                rule.check(classes);
            });
        }
    }
}
