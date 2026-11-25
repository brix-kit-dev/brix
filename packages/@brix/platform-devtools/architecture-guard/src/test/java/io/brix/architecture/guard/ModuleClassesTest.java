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

/**
 * Proves that the module locator gives the main classes of the module the test is in, whatever their packages: none of
 * its test classes, and none of the other artifacts on the class path.
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
    void findsTheClassesNextToTheTestClasses(@TempDir Path target) throws IOException {
        Path testClasses = Files.createDirectories(target.resolve("test-classes"));
        Path classes = Files.createDirectories(target.resolve("classes"));

        assertEquals(classes, ModuleClasses.classesNextTo(testClasses));
    }

    @Test
    @DisplayName("A module without main classes, or test classes elsewhere, fails with the place it looked at")
    void anotherLayoutFails(@TempDir Path target) throws IOException {
        Path withoutMainClasses = Files.createDirectories(target.resolve("a/test-classes"));
        Path elsewhere = Files.createDirectories(target.resolve("b/test"));
        Files.createDirectories(target.resolve("b/classes"));

        for (Path place : List.of(withoutMainClasses, elsewhere)) {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ModuleClasses.classesNextTo(place));
            assertTrue(failure.getMessage().contains(place.toString()), failure.getMessage());
        }
    }
}
