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
import com.tngtech.archunit.core.domain.PackageMatcher;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import io.brix.architecture.guard.ArchitectureLayers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NoInfraAdapterRule Test Cases
 *
 * <p>Tests for Rule 1 enforcement: Ensures rule detects violations and passes compliant code.</p>
 *
 * <h2>Test Strategy</h2>
 * <ul>
 *   <li><b>Adapter root</b>: Verify the rule's package root covers every package the real
 *       infra-adapter modules declare, so the rule cannot drift away from them unnoticed</li>
 *   <li><b>Positive tests</b>: Verify compliant code passes rule checks</li>
 *   <li><b>Negative tests</b>: Verify violating code is caught by rules; the fixtures import
 *       stand-in classes declared in real adapter packages</li>
 * </ul>
 *
 * @author Brix Architecture Team
 * @since 3.1.0
 */
@DisplayName("Rule 1: No Direct Infrastructure Adapter Dependencies")
class NoInfraAdapterRuleTest {

    private static final Path ADAPTER_MODULES = Path.of("packages", "@brix", "infra-adapters");

    private static final Pattern PACKAGE_DECLARATION =
            Pattern.compile("^package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    private static ArchRule rule;

    @BeforeAll
    static void setUp() {
        rule = NoInfraAdapterRule.rule();
    }

    // ============================================================================
    // Adapter root: the rule must match the packages real adapters live in
    // ============================================================================

    @Nested
    @DisplayName("Adapter Root: Real Adapter Packages")
    class AdapterRootTests {

        @Test
        @DisplayName("Every package of an infra-adapter module is under the adapter root")
        void shouldCoverEveryAdapterModulePackage() throws IOException {
            PackageMatcher root = PackageMatcher.of(ArchitectureLayers.INFRA_ADAPTER_ROOT);

            List<String> outsideRoot = adapterModulePackages().stream()
                    .filter(adapterPackage -> !root.matches(adapterPackage))
                    .toList();

            assertTrue(outsideRoot.isEmpty(),
                    ArchitectureLayers.INFRA_ADAPTER_ROOT + " does not match adapter packages " + outsideRoot);
        }

        @Test
        @DisplayName("Every named adapter package matches a real adapter package")
        void shouldMatchARealPackageForEveryNamedAdapter() throws IOException {
            Set<String> adapterPackages = adapterModulePackages();

            for (String namedAdapter : List.of(
                    ArchitectureLayers.INFRA_ADAPTER_KAFKA,
                    ArchitectureLayers.INFRA_ADAPTER_REDIS,
                    ArchitectureLayers.INFRA_ADAPTER_WEBHOOK,
                    ArchitectureLayers.INFRA_ADAPTER_OTEL)) {
                PackageMatcher matcher = PackageMatcher.of(namedAdapter);
                assertTrue(adapterPackages.stream().anyMatch(matcher::matches),
                        namedAdapter + " matches no package of an infra-adapter module");
            }
        }
    }

    // ============================================================================
    // Positive tests: Compliant code should pass
    // ============================================================================

    @Nested
    @DisplayName("Positive: Compliant Code")
    class CompliantCodeTests {

        @Test
        @DisplayName("A plugin with an adapter package of its own should pass")
        void shouldPassWhenDependingOnPluginOwnAdapter() {
            JavaClasses classes = importFixtures("io.brix.architecture.guard.testfixtures.compliant.plugin");

            assertDoesNotThrow(() -> rule.check(classes),
                    "Only io.infra.adapter is the L2C adapter root; a plugin's own adapter package is not");
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
            JavaClasses classes = importFixtures("io.brix.architecture.guard.testfixtures.violating.adapter.kafka");

            AssertionError error = assertThrows(AssertionError.class, () -> rule.check(classes),
                    "Code directly depending on the Kafka adapter should violate Rule 1");
            assertTrue(error.getMessage().contains("io.infra.adapter.kafka.KafkaAdapter"), error.getMessage());
        }

        @Test
        @DisplayName("Direct dependency on infra-adapter-persistence-jpa should fail")
        void shouldFailWhenDependingOnJdbcAdapter() {
            JavaClasses classes = importFixtures("io.brix.architecture.guard.testfixtures.violating.adapter.jdbc");

            AssertionError error = assertThrows(AssertionError.class, () -> rule.check(classes),
                    "Code directly depending on the JDBC persistence adapter should violate Rule 1");
            assertTrue(error.getMessage().contains("io.infra.adapter.persistence.jpa.JdbcAdapter"),
                    error.getMessage());
        }
    }

    private static JavaClasses importFixtures(String fixturePackage) {
        JavaClasses classes = new ClassFileImporter().importPackages(fixturePackage);
        assertFalse(classes.isEmpty(), "Fixture package " + fixturePackage + " has no classes");
        return classes;
    }

    private static Set<String> adapterModulePackages() throws IOException {
        Path modules = locateRepoRoot().resolve(ADAPTER_MODULES);
        Set<String> packages = new TreeSet<>();
        Files.walkFileTree(modules, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                String name = dir.getFileName().toString();
                return name.equals("target") || name.equals("node_modules")
                        ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (file.toString().endsWith(".java") && file.toString().contains("/src/main/java/")) {
                    Matcher declaration = PACKAGE_DECLARATION.matcher(Files.readString(file));
                    if (declaration.find()) {
                        packages.add(declaration.group(1));
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        assertFalse(packages.isEmpty(), "No adapter sources found under " + modules);
        return packages;
    }

    private static Path locateRepoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.isDirectory(current.resolve(ADAPTER_MODULES))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root");
    }
}
