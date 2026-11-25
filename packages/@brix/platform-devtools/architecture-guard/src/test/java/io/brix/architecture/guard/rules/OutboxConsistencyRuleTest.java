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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

import io.brix.architecture.guard.testsupport.CompiledModule;
import io.brix.samples.fixturealpha.core.AlphaPrice;

class OutboxConsistencyRuleTest {

    @Test
    @DisplayName("Plugin core dependencies on Outbox implementation packages fail")
    void pluginCoreOutboxImplementationDependencyFails() {
        var classes = new ClassFileImporter()
            .importPackages(
                "io.brix.architecture.guard.testfixtures.violating.outbox.core",
                "io.infra.adapter.outbox");

        assertThrows(AssertionError.class,
            () -> OutboxConsistencyRule.noDirectOutboxImplementationDependency().check(classes));
    }

    @Test
    @DisplayName("Plugin core dependencies on broker SDK packages fail")
    void pluginCoreBrokerSdkDependencyFails() {
        var classes = new ClassFileImporter()
            .importPackages(
                "io.brix.architecture.guard.testfixtures.violating.broker.core",
                "org.apache.kafka.clients.producer");

        assertThrows(AssertionError.class,
            () -> OutboxConsistencyRule.noBrokerSdkDependencies().check(classes));
    }

    /** Each plugin rule and real class names of what it refuses. */
    private static Map<ArchRule, List<String>> pluginRules() {
        return Map.of(
            OutboxConsistencyRule.noDirectKafkaTemplateUsage(), List.of("org.springframework.kafka.core.KafkaTemplate"),
            OutboxConsistencyRule.noDirectRabbitTemplateUsage(),
            List.of("org.springframework.amqp.rabbit.core.RabbitTemplate"),
            OutboxConsistencyRule.noDirectKafkaProducerUsage(),
            List.of("org.apache.kafka.clients.producer.KafkaProducer"),
            OutboxConsistencyRule.noDirectRabbitConnectionUsage(), List.of("com.rabbitmq.client.ConnectionFactory"),
            OutboxConsistencyRule.noDirectStreamBridgeUsage(),
            List.of("org.springframework.cloud.stream.function.StreamBridge"),
            OutboxConsistencyRule.noDirectJmsTemplateUsage(), List.of("org.springframework.jms.core.JmsTemplate"),
            OutboxConsistencyRule.noDirectOutboxImplementationDependency(),
            List.of("io.infra.adapter.outbox.relay.OutboxRelayGroup", "io.runtime.orchestrator.outbox.OutboxDispatcher",
                "io.runtime.orchestrator.event.EventDelivery"),
            OutboxConsistencyRule.noBrokerSdkDependencies(),
            List.of("org.apache.kafka.clients.consumer.KafkaConsumer",
                "org.springframework.kafka.listener.MessageListener", "com.rabbitmq.client.Channel",
                "org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer",
                "org.springframework.cloud.stream.messaging.Source", "jakarta.jms.Session", "javax.jms.Session"));
    }

    @Test
    @DisplayName("Each messaging rule refuses its types in every package of a plugin module")
    void everyPackageOfAModuleIsChecked(@TempDir Path directory) throws IOException {
        for (Map.Entry<ArchRule, List<String>> refused : pluginRules().entrySet()) {
            for (String library : refused.getValue()) {
                for (String violating : List.of("io.brix.platform.fixtureowner.service.Publishes",
                        "io.brix.samples.fixtureplugin.server.Publishes",
                        "io.brix.samples.fixtureplugin.core.Publishes")) {
                    var module = CompiledModule.using(Files.createTempDirectory(directory, "module"), violating, library);

                    AssertionError error = assertThrows(AssertionError.class, () -> refused.getKey().check(module),
                        library + " in " + violating);
                    assertTrue(error.getMessage().contains(violating), error.getMessage());
                }
            }
        }
    }

    @Test
    @DisplayName("A class that uses only the JDK passes each messaging rule, and a module without classes fails it")
    void jdkOnlyPassesAndNoClassFails(@TempDir Path empty) {
        for (ArchRule rule : pluginRules().keySet()) {
            assertDoesNotThrow(() -> rule.check(new ClassFileImporter().importClasses(AlphaPrice.class)),
                rule.getDescription());
            assertThrows(AssertionError.class, () -> rule.check(new ClassFileImporter().importPath(empty)),
                rule.getDescription());
        }
    }

    @Test
    @DisplayName("Retired Kafka Outbox SSoT types are absent from production sources")
    void retiredKafkaOutboxSsoTTypesAreAbsent() {
        var classes = new ClassFileImporter()
            .importPackages("io.infra.adapter.outbox");

        assertDoesNotThrow(() -> OutboxConsistencyRule.noLegacyL2cOutboxSsoTTypes().check(classes));
    }

    @Test
    @DisplayName("Production sources do not keep the global event_outbox table")
    void globalEventOutboxTableIsAbsentFromProductionSources() throws IOException {
        Set<String> allowedEvidenceFiles = Set.of(
            "docs/runtime-shell/outbox/M0-phase4-anchor-contract-freeze.md",
            "packages/@brix/platform-devtools/architecture-guard/src/test/java/io/brix/architecture/guard/rules/OutboxConsistencyRuleTest.java"
        );
        Path repoRoot = locateRepoRoot();
        try (Stream<Path> files = Files.walk(repoRoot)) {
            var offenders = files
                .filter(Files::isRegularFile)
                .filter(OutboxConsistencyRuleTest::isTextSource)
                .filter(path -> !path.toString().contains("/target/"))
                .filter(path -> !path.toString().contains("/.git/"))
                .filter(path -> contains(path, "event_outbox"))
                .map(repoRoot::relativize)
                .map(Path::toString)
                .filter(path -> !allowedEvidenceFiles.contains(path))
                .toList();

            if (!offenders.isEmpty()) {
                throw new AssertionError("global event_outbox references remain: " + offenders);
            }
        }
    }

    private static boolean isTextSource(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".java")
            || name.endsWith(".md")
            || name.endsWith(".xml")
            || name.endsWith(".yaml")
            || name.endsWith(".yml")
            || name.endsWith(".json")
            || name.endsWith(".properties")
            || name.endsWith(".sql")
            || name.endsWith(".ejs")
            || name.endsWith(".ts")
            || name.endsWith(".tsx")
            || name.endsWith(".js")
            || name.endsWith(".jsx");
    }

    private static Path locateRepoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))
                && Files.exists(current.resolve("packages/@brix/platform-devtools/architecture-guard"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root");
    }

    private static boolean contains(Path path, String needle) {
        try {
            byte[] content = Files.readAllBytes(path);
            byte[] target = needle.getBytes(StandardCharsets.UTF_8);
            return containsBytes(content, target);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read " + path, ex);
        }
    }

    private static boolean containsBytes(byte[] content, byte[] target) {
        if (target.length == 0) {
            return true;
        }
        for (int index = 0; index <= content.length - target.length; index++) {
            int offset = 0;
            while (offset < target.length && content[index + offset] == target[offset]) {
                offset++;
            }
            if (offset == target.length) {
                return true;
            }
        }
        return false;
    }
}
