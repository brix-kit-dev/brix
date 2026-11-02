package io.brix.architecture.guard.rules;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

@DisplayName("v3.1.3 Phase 0: plugins must not import platform tenant relationship internals")
class NoPlatformTenantRelationshipAccessRuleTest {

    @Test
    @DisplayName("rule is configured with tenant relationship boundary description")
    void ruleConfigured() {
        ArchRule rule = NoPlatformTenantRelationshipAccessRule.rule();

        assertNotNull(rule);
        assertNotNull(rule.getDescription());
        assertTrue(rule.getDescription().toLowerCase().contains("platform.tenant"));
    }

    @Test
    @DisplayName("architecture guard library itself passes the rule")
    void guardLibraryPasses() {
        ArchRule rule = NoPlatformTenantRelationshipAccessRule.rule();
        JavaClasses classes = new ClassFileImporter().importPackages("io.brix.architecture.guard");

        assertDoesNotThrow(() -> rule.check(classes));
    }

    @Test
    @DisplayName("a plugin class that reads platform-tenant's relationship entity fails")
    void pluginReadingTenantEntityFails() {
        JavaClasses classes = new ClassFileImporter().importClasses(
            io.brix.samples.fixturealpha.core.AlphaTenantReader.class);

        AssertionError error = assertThrows(AssertionError.class,
            () -> NoPlatformTenantRelationshipAccessRule.rule().check(classes));
        assertTrue(error.getMessage().contains("io.brix.platform.tenant.entity.TenantMemberRow"), error.getMessage());
    }

    @Test
    @DisplayName("a plugin class that does not touch platform-tenant internals passes")
    void pluginWithoutTenantInternalsPasses() {
        JavaClasses classes = new ClassFileImporter().importClasses(
            io.brix.samples.fixturealpha.server.AlphaEndpoint.class);

        assertDoesNotThrow(() -> NoPlatformTenantRelationshipAccessRule.rule().check(classes));
    }
}
