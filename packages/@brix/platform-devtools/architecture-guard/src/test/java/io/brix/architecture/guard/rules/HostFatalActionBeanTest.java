/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.importer.ClassFileImporter;

import io.brix.hostfixtures.CapabilityBeanAutoConfiguration;
import io.brix.hostfixtures.FatalActionOnlyAutoConfiguration;

/**
 * A Host's auto-configuration may define the terminal action the runtime requires of standalone and local Hosts,
 * and nothing else (standard-link plan L2.1).
 */
class HostFatalActionBeanTest {

    @Test
    void theTerminalActionTheRuntimeRequiresIsAllowed() {
        assertDoesNotThrow(() -> HostUltraThinRule.noBeanDefinitionsInAutoConfiguration()
                .check(new ClassFileImporter().importClasses(FatalActionOnlyAutoConfiguration.class)));
    }

    @Test
    void anyOtherBeanStillFails() {
        AssertionError failure = assertThrows(AssertionError.class, () -> HostUltraThinRule
                .noBeanDefinitionsInAutoConfiguration()
                .check(new ClassFileImporter().importClasses(CapabilityBeanAutoConfiguration.class)));

        assertTrue(failure.getMessage().contains("contains 1 @Bean methods"), failure.getMessage());
    }
}
