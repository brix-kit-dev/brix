/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.architecture.guard.profiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.auth0.jwt.JWT;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.brix.architecture.guard.testsupport.CompiledModule;
import io.brix.platform.fixture.CompliantPlatformService;
import io.brix.platform.fixturesecurity.service.AuthenticatesItself;
import io.brix.samples.fixtureauth.core.AuthorizesOnPermission;
import io.brix.samples.fixtureauth.core.AuthorizesOnRoleName;
import io.brix.samples.fixturebeta.api.BetaStockQuery;
import io.brix.samples.fixturecommands.api.CancelBooking;
import io.brix.samples.fixturecommands.api.FixtureCommands;
import io.brix.samples.fixturecommands.api.FixtureQueries;
import io.brix.samples.fixturecommands.api.HandWrittenCommands;
import io.brix.samples.fixturecommands.core.ReadsContractTypes;
import io.brix.samples.fixturecommands.core.SubmitsStringCommand;
import io.brix.samples.fixturecommands.core.SubmitsTypedCommand;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;

class PluginProfileTest {

    /** The rules about a plugin's own layers, which look at packages by name. Every other rule checks every class. */
    private static final Set<String> LAYER_RULES = new TreeSet<>(Set.of("coreNotDependOnAdapter",
        "coreNotDependOnServer", "crossModuleViaCapability", "domainNotDependOnInfrastructure", "noLoggerInDomain"));

    @Test
    void rejectsPluginThatImportsAnInfrastructureAdapter() {
        var classes = new ClassFileImporter().importPackages(
            "io.brix.architecture.guard.testfixtures.violating.adapter");

        assertThrows(AssertionError.class, () -> PluginProfile.noInfraAdapterDependency.check(classes));
    }

    @Test
    @DisplayName("A plugin that authorizes on a role name through the SDK fails")
    void rejectsPluginThatAuthorizesOnARoleName() {
        var classes = new ClassFileImporter().importClasses(AuthorizesOnRoleName.class);

        AssertionError error = assertThrows(AssertionError.class, () -> PluginProfile.noRoleNameChecks.check(classes));
        assertTrue(error.getMessage().contains(AuthorizesOnRoleName.class.getName() + ".mayApprove()"),
            error.getMessage());
    }

    @Test
    @DisplayName("A plugin that authorizes on a permission code passes")
    void acceptsPluginThatAuthorizesOnAPermission() {
        var classes = new ClassFileImporter().importClasses(AuthorizesOnPermission.class);

        assertDoesNotThrow(() -> PluginProfile.noRoleNameChecks.check(classes));
    }

    @Test
    @DisplayName("A plugin that sends a command through the string gateway fails (root A-22)")
    void rejectsPluginThatSubmitsAStringCommand() {
        var classes = new ClassFileImporter().importClasses(SubmitsStringCommand.class);

        AssertionError error = assertThrows(AssertionError.class,
            () -> PluginProfile.noStringCommandGateway.check(classes));
        assertTrue(error.getMessage().contains(SubmitsStringCommand.class.getName()), error.getMessage());
    }

    @Test
    @DisplayName("A plugin that sends a command with the client of the owner's generated type passes")
    void acceptsPluginThatSubmitsATypedCommand() {
        var classes = new ClassFileImporter()
            .importClasses(SubmitsTypedCommand.class, FixtureCommands.class, CancelBooking.class);

        assertDoesNotThrow(() -> PluginProfile.noStringCommandGateway.check(classes));
    }

    @Test
    @DisplayName("A plugin that makes a command type itself fails (root A-22)")
    void rejectsPluginThatMakesACommandType() {
        var classes = new ClassFileImporter().importClasses(HandWrittenCommands.class);

        AssertionError error = assertThrows(AssertionError.class,
            () -> PluginProfile.noHandMadeQueryOrCommandTypes.check(classes));
        assertTrue(error.getMessage().contains(HandWrittenCommands.class.getName()), error.getMessage());
    }

    @Test
    @DisplayName("A plugin that takes its types from the generated constants passes, and so do the constants")
    void acceptsPluginThatUsesTheGeneratedConstants() {
        var classes = new ClassFileImporter().importClasses(ReadsContractTypes.class, SubmitsTypedCommand.class,
            FixtureCommands.class, FixtureQueries.class, CancelBooking.class);

        assertDoesNotThrow(() -> PluginProfile.noHandMadeQueryOrCommandTypes.check(classes));
    }

    @Test
    @DisplayName("A plugin of one artifact that authenticates itself fails the four security rules, whatever its package")
    void rejectsAuthenticationOutsideCorePackages() {
        var classes = new ClassFileImporter().importClasses(AuthenticatesItself.class);

        Map<ArchRule, Class<?>> rules = Map.of(
            PluginProfile.noDirectJwtUsage, Jwts.class,
            PluginProfile.noDirectAuth0Usage, JWT.class,
            PluginProfile.noDirectCryptoUsage, BCryptPasswordEncoder.class,
            PluginProfile.noDirectOAuth2ClientUsage, OAuth2AuthorizedClient.class);
        rules.forEach((rule, library) -> {
            AssertionError error = assertThrows(AssertionError.class, () -> rule.check(classes), rule.getDescription());
            assertTrue(error.getMessage().contains(AuthenticatesItself.class.getName()), error.getMessage());
            assertTrue(error.getMessage().contains(library.getName()), error.getMessage());
        });
    }

    @Test
    @DisplayName("A plugin of one artifact fails the messaging, runtime, repository and controller rules, whatever its "
        + "package")
    void rejectsInfrastructureOutsideCorePackages(@TempDir Path directory) throws IOException {
        Map<ArchRule, String> refused = Map.of(
            PluginProfile.noDirectKafkaTemplateUsage, "org.springframework.kafka.core.KafkaTemplate",
            PluginProfile.noDirectRabbitTemplateUsage, "org.springframework.amqp.rabbit.core.RabbitTemplate",
            PluginProfile.noDirectKafkaProducerUsage, "org.apache.kafka.clients.producer.KafkaProducer",
            PluginProfile.noDirectStreamBridgeUsage, "org.springframework.cloud.stream.function.StreamBridge",
            PluginProfile.noDirectJmsTemplateUsage, "org.springframework.jms.core.JmsTemplate",
            PluginProfile.noDirectOutboxImplementationDependency, "io.infra.adapter.outbox.relay.OutboxRelayGroup",
            PluginProfile.noBrokerSdkDependencies, "jakarta.jms.Session",
            PluginProfile.noRuntimeOrchestrator, "io.runtime.orchestrator.plugin.PluginRuntimeManager",
            PluginProfile.noCrossPluginRepositoryAccess, "io.brix.platform.fixtureother.repository.OtherRepository");
        for (Map.Entry<ArchRule, String> rule : refused.entrySet()) {
            JavaClasses module = CompiledModule.using(Files.createTempDirectory(directory, "module"),
                "io.brix.platform.fixtureowner.config.Violates", rule.getValue());
            assertRejects(rule.getKey(), module, rule.getValue());
        }
        String controller = "org.springframework.web.bind.annotation.RestController";
        assertRejects(PluginProfile.noSpringMvcController, CompiledModule.annotatedWith(
            Files.createTempDirectory(directory, "module"), "io.brix.platform.fixtureowner.config.Violates", controller),
            "RestController");
    }

    @Test
    @DisplayName("Only the rules about a plugin's own layers check nothing in a plugin without packages of those names")
    void onlyTheLayerRulesCheckNothingInAPluginWithoutLayers() {
        assertEquals(LAYER_RULES, rulesThatCheckNothingIn(BetaStockQuery.class));
    }

    @Test
    @DisplayName("In a platform plugin, the rule about the platform tenant's tables also checks nothing: it selects "
        + "business plugins")
    void onlyTheLayerAndTenantRulesCheckNothingInAPlatformPlugin() {
        Set<String> expected = new TreeSet<>(LAYER_RULES);
        expected.add("noPlatformTenantRelationshipAccess");

        assertEquals(expected, rulesThatCheckNothingIn(CompliantPlatformService.class));
    }

    @Test
    @DisplayName("The profile runs every one of its rules: each is a static @ArchTest field")
    void everyRuleIsAnArchTest() {
        List<Field> rules = Arrays.stream(PluginProfile.class.getDeclaredFields())
            .filter(field -> ArchRule.class.isAssignableFrom(field.getType()))
            .toList();

        assertFalse(rules.isEmpty());
        for (String name : List.of("noStringCommandGateway", "noHandMadeQueryOrCommandTypes")) {
            assertTrue(rules.stream().anyMatch(field -> field.getName().equals(name)), name + " in " + rules);
        }
        for (Field field : rules) {
            assertTrue(field.isAnnotationPresent(ArchTest.class) && Modifier.isStatic(field.getModifiers()),
                field.getName());
        }
    }

    private static void assertRejects(ArchRule rule, JavaClasses module, String refused) {
        AssertionError error = assertThrows(AssertionError.class, () -> rule.check(module), refused);
        assertTrue(error.getMessage().contains("io.brix.platform.fixtureowner.config.Violates"), error.getMessage());
        assertTrue(error.getMessage().contains(refused), error.getMessage());
    }

    /** The rules of the profile that pass a compliant class only because they select nothing of it. */
    private static Set<String> rulesThatCheckNothingIn(Class<?> compliant) {
        JavaClasses classes = new ClassFileImporter().importClasses(compliant);
        Set<String> checkNothing = new TreeSet<>();
        for (Field field : PluginProfile.class.getDeclaredFields()) {
            if (!ArchRule.class.isAssignableFrom(field.getType())) {
                continue;
            }
            ArchRule rule = ruleIn(field);
            assertDoesNotThrow(() -> rule.allowEmptyShould(true).check(classes), field.getName());
            try {
                rule.allowEmptyShould(false).check(classes);
            } catch (AssertionError nothingSelected) {
                checkNothing.add(field.getName());
            }
        }
        return checkNothing;
    }

    private static ArchRule ruleIn(Field field) {
        try {
            return (ArchRule) field.get(null);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
