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
package io.brix.architecture.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.Location;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

/**
 * Proves that the module locator gives the main classes of the module the test is in, whatever their packages: none of
 * its test classes, and none of the other artifacts on the class path. A module without classes is skipped; one with
 * sources but no classes, or another layout, fails.
 */
class ModuleClassesTest {

    @Test
    @DisplayName("The locations are the module's own main classes and nothing else")
    void selectsTheModulesMainClasses() {
        Set<Location> locations = new ModuleClasses().get(ModuleClassesTest.class);

        assertEquals(1, locations.size());
        JavaClasses classes = new ClassFileImporter().importLocations(locations);
        assertTrue(classes.contain(ModuleClasses.class), "the guard's own main classes");
        List<String> others = classes.stream().map(JavaClass::getName)
            .filter(name -> !name.startsWith("io.brix.architecture.guard.") || name.endsWith("Test")
                || name.contains(".testfixtures.") || name.contains(".testsupport.")).toList();
        assertTrue(others.isEmpty(), "test classes or classes of other artifacts: " + others);
    }

    @Test
    @DisplayName("target/classes is found next to target/test-classes")
    void findsTheClassesNextToTheTestClasses(@TempDir Path module) throws IOException {
        Path testClasses = Files.createDirectories(module.resolve("target/test-classes"));
        Path classes = Files.createDirectories(module.resolve("target/classes/io/brix/app/example"));
        Files.createFile(classes.resolve("Example.class"));

        assertEquals(module.resolve("target/classes"), ModuleClasses.classesNextTo(testClasses));
    }

    @Test
    @DisplayName("A module without classes and without Java sources is skipped, with its name and the reason")
    void aModuleWithoutClassesIsSkipped(@TempDir Path root) throws IOException {
        Path noOutput = root.resolve("app-example-api");
        Files.createDirectories(noOutput.resolve("target/test-classes"));
        Path resourcesOnly = root.resolve("app-example-core");
        Files.createDirectories(resourcesOnly.resolve("target/test-classes"));
        Files.createDirectories(resourcesOnly.resolve("target/classes/META-INF/brix"));
        Files.createFile(resourcesOnly.resolve("target/classes/META-INF/brix/schema.json"));
        Files.createDirectories(resourcesOnly.resolve("src/main/java/io/brix/app/example/core"));

        for (Path module : List.of(noOutput, resourcesOnly)) {
            TestAbortedException skipped = assertThrows(TestAbortedException.class,
                () -> ModuleClasses.classesNextTo(module.resolve("target/test-classes")));
            assertTrue(skipped.getMessage().startsWith(module.getFileName() + " has no classes yet"),
                skipped.getMessage());
        }
    }

    @Test
    @DisplayName("A module with Java sources but no classes fails with the places it looked at")
    void sourcesWithoutClassesFail(@TempDir Path module) throws IOException {
        Path sources = Files.createDirectories(module.resolve("src/main/java/io/brix/app/example"));
        Files.createFile(sources.resolve("Example.java"));
        Path testClasses = Files.createDirectories(module.resolve("target/test-classes"));

        IllegalStateException withoutOutput = assertThrows(IllegalStateException.class,
            () -> ModuleClasses.classesNextTo(testClasses));
        Files.createDirectories(module.resolve("target/classes/META-INF"));
        Files.createFile(module.resolve("target/classes/META-INF/notes.txt"));
        IllegalStateException resourcesOnly = assertThrows(IllegalStateException.class,
            () -> ModuleClasses.classesNextTo(testClasses));

        for (IllegalStateException failure : List.of(withoutOutput, resourcesOnly)) {
            assertTrue(failure.getMessage().contains(module.resolve("src/main/java").toString())
                && failure.getMessage().contains(module.resolve("target/classes").toString()), failure.getMessage());
        }
    }

    @Test
    @DisplayName("Test classes elsewhere than target/test-classes fail with the place it looked at")
    void anotherLayoutFails(@TempDir Path module) throws IOException {
        Path elsewhere = Files.createDirectories(module.resolve("target/test"));
        Files.createDirectories(module.resolve("target/classes"));
        Files.createFile(module.resolve("target/classes/Example.class"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> ModuleClasses.classesNextTo(elsewhere));
        assertTrue(failure.getMessage().contains(elsewhere.toString()), failure.getMessage());
    }
}
