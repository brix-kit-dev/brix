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
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

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
        Path classes = testClasses.resolveSibling("classes");
        if (!testClasses.endsWith("test-classes") || !Files.isDirectory(classes)) {
            throw new IllegalStateException("expected target/test-classes next to target/classes, found " + testClasses);
        }
        return classes;
    }
}
