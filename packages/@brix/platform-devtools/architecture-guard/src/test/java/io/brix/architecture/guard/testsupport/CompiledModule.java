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
package io.brix.architecture.guard.testsupport;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Compiles the classes of a module that uses a library type, against a stand-in of that type, and imports only the
 * module's own classes: as a module's architecture test does.
 *
 * <p>The stand-in is generated under the library's real class name. A rule whose package pattern names a package the
 * library does not have therefore lets the module through, and its test fails.</p>
 */
public final class CompiledModule {

    private CompiledModule() {
    }

    /**
     * A module of one class that uses a library class.
     *
     * @param directory an empty directory for the sources and classes
     * @param moduleClass the module class's full name
     * @param libraryClass the library class's full, real name
     * @return the module's classes
     */
    public static JavaClasses using(Path directory, String moduleClass, String libraryClass) {
        return compile(directory, moduleClass,
            new Source(libraryClass, "package %s;%npublic class %s {%n}%n"
                .formatted(packageOf(libraryClass), simpleNameOf(libraryClass))),
            new Source(moduleClass, "package %s;%npublic class %s {%n    public Object used = new %s();%n}%n"
                .formatted(packageOf(moduleClass), simpleNameOf(moduleClass), libraryClass)));
    }

    /**
     * A module of one class that carries an annotation of a library.
     *
     * @param directory an empty directory for the sources and classes
     * @param moduleClass the module class's full name
     * @param annotation the annotation's full, real name
     * @return the module's classes
     */
    public static JavaClasses annotatedWith(Path directory, String moduleClass, String annotation) {
        return compile(directory, moduleClass,
            new Source(annotation, ("package %s;%nimport java.lang.annotation.Retention;%n"
                + "import java.lang.annotation.RetentionPolicy;%n@Retention(RetentionPolicy.RUNTIME)%n"
                + "public @interface %s {%n}%n").formatted(packageOf(annotation), simpleNameOf(annotation))),
            new Source(moduleClass, "package %s;%n@%s%npublic class %s {%n}%n"
                .formatted(packageOf(moduleClass), annotation, simpleNameOf(moduleClass))));
    }

    private static JavaClasses compile(Path directory, String moduleClass, Source... sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "the guard's tests need a JDK, not only a JRE");
        Path classes = directory.resolve("classes");
        List<String> arguments = new ArrayList<>(List.of("-d", classes.toString(), "-proc:none"));
        try {
            Files.createDirectories(classes);
            for (Source source : sources) {
                Path file = directory.resolve("sources").resolve(source.className().replace('.', '/') + ".java");
                Files.createDirectories(file.getParent());
                arguments.add(Files.writeString(file, source.code()).toString());
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        ByteArrayOutputStream messages = new ByteArrayOutputStream();
        int result = compiler.run(null, messages, messages, arguments.toArray(String[]::new));
        assertTrue(result == 0, messages::toString);
        return new ClassFileImporter().importPath(classes.resolve(moduleClass.replace('.', '/') + ".class"));
    }

    private static String packageOf(String className) {
        return className.substring(0, className.lastIndexOf('.'));
    }

    private static String simpleNameOf(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    private record Source(String className, String code) {
    }
}
