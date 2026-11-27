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

import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.LocationProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;
import org.opentest4j.TestAbortedException;

/**
 * Selects the classes of the module a test is in, whatever their packages: the module's own artifact, not what else is
 * on the class path under the same package names.
 *
 * <p>A plugin's artifacts may share package names, as {@code io.brix.example.controller} next to
 * {@code io.brix.example.service} of another artifact. A test that names packages then also reads the other artifact's
 * classes, or misses a package added later. This one reads {@code target/classes} next to the test's
 * {@code target/test-classes}:</p>
 * <pre>{@code
 * @AnalyzeClasses(locations = ModuleClasses.class)
 * class ArchitectureTest {
 *     @ArchTest
 *     static final ArchTests rules = BrixArchitectureRules.pluginProfile();
 * }
 * }</pre>
 *
 * <p>A module that has no classes yet, as the api and core modules of a plugin the scaffold has just generated, has
 * nothing to check. When {@code target/classes} holds no class file and {@code src/main/java} no Java source, the test
 * is aborted like a JUnit assumption that does not hold: its rules are reported as skipped, with the reason, and run
 * once the module has classes. A module with Java sources but no classes, or whose test classes are not in
 * {@code target/test-classes}, fails with the place it looked at.</p>
 *
 * @author Brix Architecture Team
 * @since 3.3.0
 */
public final class ModuleClasses implements LocationProvider {

    @Override
    public Set<Location> get(Class<?> testClass) {
        return Set.of(Location.of(classesNextTo(testClassesOf(testClass))));
    }

    private static Path testClassesOf(Class<?> testClass) {
        try {
            return Path.of(testClass.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException failure) {
            throw new IllegalStateException("cannot locate the classes of " + testClass.getName(), failure);
        }
    }

    static Path classesNextTo(Path testClasses) {
        Path target = testClasses.getParent();
        Path module = target == null ? null : target.getParent();
        if (!testClasses.endsWith("test-classes") || module == null) {
            throw new IllegalStateException("expected target/test-classes next to target/classes, found " + testClasses);
        }
        Path classes = target.resolve("classes");
        if (holdsFile(classes, ".class")) {
            return classes;
        }
        Path sources = module.resolve("src").resolve("main").resolve("java");
        if (holdsFile(sources, ".java")) {
            throw new IllegalStateException("expected the classes of " + sources + " in " + classes + ", found none");
        }
        throw new TestAbortedException(module.getFileName() + " has no classes yet, so its architecture rules have"
            + " nothing to check: they run once the module has classes");
    }

    private static boolean holdsFile(Path directory, String suffix) {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (Stream<Path> files = Files.walk(directory)) {
            return files.anyMatch(file -> Files.isRegularFile(file) && file.getFileName().toString().endsWith(suffix));
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot read " + directory, failure);
        }
    }
}
