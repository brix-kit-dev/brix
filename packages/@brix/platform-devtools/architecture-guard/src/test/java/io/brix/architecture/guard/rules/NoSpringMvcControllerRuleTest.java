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

import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.brix.architecture.guard.testsupport.CompiledModule;
import io.brix.samples.fixturealpha.core.AlphaPrice;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves that a Spring MVC controller is refused in every package of a plugin module, not only in core packages.
 */
class NoSpringMvcControllerRuleTest {

    @TempDir
    private Path directory;

    @Test
    @DisplayName("A controller in any package of a plugin module fails")
    void controllerFails() throws IOException {
        for (String annotation : List.of("org.springframework.web.bind.annotation.RestController",
                "org.springframework.stereotype.Controller")) {
            for (String controller : List.of("io.brix.platform.fixtureowner.endpoint.Controls",
                    "io.brix.samples.fixtureplugin.server.Controls", "io.brix.samples.fixtureplugin.core.Controls")) {
                var module = CompiledModule.annotatedWith(Files.createTempDirectory(directory, "module"), controller,
                    annotation);

                AssertionError error = assertThrows(AssertionError.class,
                    () -> NoSpringMvcControllerRule.rule().check(module), annotation + " in " + controller);
                assertTrue(error.getMessage().contains(controller), error.getMessage());
            }
        }
    }

    @Test
    @DisplayName("A listed controller passes, alone in its module too, and an unlisted one of the module fails")
    void listedControllerPasses() throws IOException {
        for (String annotation : List.of("org.springframework.web.bind.annotation.RestController",
                "org.springframework.stereotype.Controller")) {
            var module = CompiledModule.annotatedWith(Files.createTempDirectory(directory, "module"),
                "io.brix.samples.fixtureplugin.server.ListedController", annotation);

            assertDoesNotThrow(() -> NoSpringMvcControllerRule.rule().check(module), annotation);
        }
        var other = CompiledModule.annotatedWith(Files.createTempDirectory(directory, "module"),
            "io.brix.samples.fixtureplugin.server.ListedControllerTwo", "org.springframework.stereotype.Controller");
        AssertionError error = assertThrows(AssertionError.class, () -> NoSpringMvcControllerRule.rule().check(other));
        assertTrue(error.getMessage().contains("ListedControllerTwo"), error.getMessage());
    }

    @Test
    @DisplayName("A class with another annotation of the same packages passes")
    void otherAnnotationsPass() throws IOException {
        for (String annotation : List.of("org.springframework.web.bind.annotation.ResponseStatus",
                "org.springframework.stereotype.Component")) {
            var module = CompiledModule.annotatedWith(Files.createTempDirectory(directory, "module"),
                "io.brix.samples.fixtureplugin.server.Plain", annotation);

            assertDoesNotThrow(() -> NoSpringMvcControllerRule.rule().check(module), annotation);
        }
    }

    @Test
    @DisplayName("A class without annotations passes, and a module without classes fails")
    void plainClassPassesAndNoClassFails() {
        assertDoesNotThrow(
            () -> NoSpringMvcControllerRule.rule().check(new ClassFileImporter().importClasses(AlphaPrice.class)));
        assertThrows(AssertionError.class,
            () -> NoSpringMvcControllerRule.rule().check(new ClassFileImporter().importPath(directory)));
    }
}
