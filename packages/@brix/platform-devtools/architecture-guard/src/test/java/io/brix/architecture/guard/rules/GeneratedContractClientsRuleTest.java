/*
 * Copyright 2026 Brix Authors. Licensed under Apache-2.0.
 */
package io.brix.architecture.guard.rules;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import io.brix.samples.fixturecommands.api.CancelBooking;
import io.brix.samples.fixturecommands.api.FixtureCommands;
import io.brix.samples.fixturecommands.core.BuildsStringCommand;
import io.brix.samples.fixturecommands.core.ForwardsStringCommand;
import io.brix.samples.fixturecommands.core.ReferencesStringSubmit;
import io.brix.samples.fixturecommands.core.SubmitsStringCommand;
import io.brix.samples.fixturecommands.core.SubmitsTypedCommand;

/**
 * The string command gateway is the runtime's (root A-22): a class that uses {@code CommandRequest}, or calls or
 * references {@code CommandCapability.submit(CommandRequest)}, fails; one that sends the command with the client of
 * the owner's generated type passes (standard-link plan queue item 20, 20a).
 */
class GeneratedContractClientsRuleTest {

    @Test
    @DisplayName("A class that builds a string command and submits it fails")
    void buildingAndSubmittingAStringCommandFails() {
        assertRejects(SubmitsStringCommand.class);
    }

    @Test
    @DisplayName("A class that submits a string command another class built fails")
    void submittingAStringCommandFails() {
        assertRejects(ForwardsStringCommand.class);
    }

    @Test
    @DisplayName("A class that only builds a string command fails")
    void buildingAStringCommandFails() {
        assertRejects(BuildsStringCommand.class);
    }

    @Test
    @DisplayName("A class that references the string submit fails")
    void referencingTheStringSubmitFails() {
        assertRejects(ReferencesStringSubmit.class);
    }

    @Test
    @DisplayName("A class that sends the command with the generated type's client passes, and so do the generated types")
    void theGeneratedTypesClientPasses() {
        JavaClasses classes = new ClassFileImporter()
            .importClasses(SubmitsTypedCommand.class, FixtureCommands.class, CancelBooking.class);

        assertDoesNotThrow(() -> GeneratedContractClientsRule.noStringCommandGateway().check(classes));
    }

    private static void assertRejects(Class<?> violating) {
        JavaClasses classes = new ClassFileImporter().importClasses(violating);

        AssertionError error = assertThrows(AssertionError.class,
            () -> GeneratedContractClientsRule.noStringCommandGateway().check(classes));
        assertTrue(error.getMessage().contains(violating.getName()), error.getMessage());
        assertTrue(error.getMessage().contains("root A-22"), error.getMessage());
    }
}
