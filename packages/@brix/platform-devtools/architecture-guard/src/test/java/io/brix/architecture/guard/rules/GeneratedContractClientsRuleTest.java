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
import com.tngtech.archunit.lang.ArchRule;

import io.brix.samples.fixturecommands.api.Booking;
import io.brix.samples.fixturecommands.api.CancelBooking;
import io.brix.samples.fixturecommands.api.FindBooking;
import io.brix.samples.fixturecommands.api.FixtureCommands;
import io.brix.samples.fixturecommands.api.FixtureQueries;
import io.brix.samples.fixturecommands.api.HandWrittenCommands;
import io.brix.samples.fixturecommands.core.BuildsStringCommand;
import io.brix.samples.fixturecommands.core.ForwardsStringCommand;
import io.brix.samples.fixturecommands.core.MakesOptionalQueryType;
import io.brix.samples.fixturecommands.core.MakesQueryType;
import io.brix.samples.fixturecommands.core.ReadsContractTypes;
import io.brix.samples.fixturecommands.core.ReferencesCommandTypeFactory;
import io.brix.samples.fixturecommands.core.ReferencesStringSubmit;
import io.brix.samples.fixturecommands.core.SubmitsStringCommand;
import io.brix.samples.fixturecommands.core.SubmitsTypedCommand;

/**
 * Plugins use only the generated contract clients (root A-22). The string command gateway is the runtime's: a class
 * that uses {@code CommandRequest}, or calls or references {@code CommandCapability.submit(CommandRequest)}, fails; one
 * that sends the command with the client of the owner's generated type passes (standard-link plan queue item 20, 20a).
 * Only the generated constants classes, marked {@code @GeneratedContractConstants}, make query and command types: a
 * class without the mark that calls or references {@code CommandType.of}, {@code QueryType.of} or
 * {@code QueryType.optional} fails, wherever it sits; reading the constants passes (20a2).
 */
class GeneratedContractClientsRuleTest {

    @Test
    @DisplayName("A class that builds a string command and submits it fails")
    void buildingAndSubmittingAStringCommandFails() {
        assertRejects(GeneratedContractClientsRule.noStringCommandGateway(), SubmitsStringCommand.class);
    }

    @Test
    @DisplayName("A class that submits a string command another class built fails")
    void submittingAStringCommandFails() {
        assertRejects(GeneratedContractClientsRule.noStringCommandGateway(), ForwardsStringCommand.class);
    }

    @Test
    @DisplayName("A class that only builds a string command fails")
    void buildingAStringCommandFails() {
        assertRejects(GeneratedContractClientsRule.noStringCommandGateway(), BuildsStringCommand.class);
    }

    @Test
    @DisplayName("A class that references the string submit fails")
    void referencingTheStringSubmitFails() {
        assertRejects(GeneratedContractClientsRule.noStringCommandGateway(), ReferencesStringSubmit.class);
    }

    @Test
    @DisplayName("A class that sends the command with the generated type's client passes, and so do the generated types")
    void theGeneratedTypesClientPasses() {
        JavaClasses classes = new ClassFileImporter()
            .importClasses(SubmitsTypedCommand.class, FixtureCommands.class, CancelBooking.class);

        assertDoesNotThrow(() -> GeneratedContractClientsRule.noStringCommandGateway().check(classes));
    }

    @Test
    @DisplayName("A constants class written by hand fails, though it sits in the api package")
    void aHandWrittenCommandTypeFails() {
        assertRejects(GeneratedContractClientsRule.noHandMadeQueryOrCommandTypes(), HandWrittenCommands.class);
    }

    @Test
    @DisplayName("A class that makes a query type of its own fails")
    void aHandMadeQueryTypeFails() {
        assertRejects(GeneratedContractClientsRule.noHandMadeQueryOrCommandTypes(), MakesQueryType.class);
    }

    @Test
    @DisplayName("A class that makes an optional query type of its own fails")
    void aHandMadeOptionalQueryTypeFails() {
        assertRejects(GeneratedContractClientsRule.noHandMadeQueryOrCommandTypes(), MakesOptionalQueryType.class);
    }

    @Test
    @DisplayName("A class that references the command type factory fails")
    void referencingTheCommandTypeFactoryFails() {
        assertRejects(GeneratedContractClientsRule.noHandMadeQueryOrCommandTypes(), ReferencesCommandTypeFactory.class);
    }

    @Test
    @DisplayName("The generated constants classes, with the generator's mark, pass")
    void theGeneratedConstantsPass() {
        JavaClasses classes = new ClassFileImporter().importClasses(FixtureCommands.class, FixtureQueries.class,
            CancelBooking.class, FindBooking.class, Booking.class);

        assertDoesNotThrow(() -> GeneratedContractClientsRule.noHandMadeQueryOrCommandTypes().check(classes));
    }

    @Test
    @DisplayName("A class that reads the generated constants, or sends with their client, passes")
    void readingAContractTypePasses() {
        JavaClasses classes = new ClassFileImporter().importClasses(ReadsContractTypes.class, SubmitsTypedCommand.class);

        assertDoesNotThrow(() -> GeneratedContractClientsRule.noHandMadeQueryOrCommandTypes().check(classes));
    }

    private static void assertRejects(ArchRule rule, Class<?> violating) {
        JavaClasses classes = new ClassFileImporter().importClasses(violating);

        AssertionError error = assertThrows(AssertionError.class, () -> rule.check(classes));
        assertTrue(error.getMessage().contains(violating.getName()), error.getMessage());
        assertTrue(error.getMessage().contains("root A-22"), error.getMessage());
    }
}
