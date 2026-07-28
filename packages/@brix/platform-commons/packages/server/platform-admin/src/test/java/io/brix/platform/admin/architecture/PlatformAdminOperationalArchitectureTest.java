/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class PlatformAdminOperationalArchitectureTest {

    private static final Path MAIN_SOURCE = Path.of("src/main/java");

    @Test
    void mainSourceDoesNotPublishSpringMvcPlatformRoutes() throws IOException {
        List<String> violations = javaFiles().stream()
            .filter(path -> containsAny(path, "@RestController", "@Controller", "@ComponentScan"))
            .map(Path::toString)
            .toList();

        assertTrue(violations.isEmpty(), "Spring MVC publication must not return to platform-admin: " + violations);
    }

    @Test
    void mainSourceDoesNotImportTenantOwnerImplementationTypes() throws IOException {
        List<String> violations = javaFiles().stream()
            .filter(path -> {
                String text = read(path);
                return text.contains("io.brix.platform.tenant.entity.")
                    || text.contains("io.brix.platform.tenant.repository.")
                    || text.contains("io.brix.platform.tenant.service.");
            })
            .map(Path::toString)
            .toList();

        assertTrue(violations.isEmpty(), "platform-admin must consume only tenant internal contract API: " + violations);
    }

    private static List<Path> javaFiles() throws IOException {
        try (var stream = Files.walk(MAIN_SOURCE)) {
            return stream
                .filter(path -> path.toString().endsWith(".java"))
                .toList();
        }
    }

    private static boolean containsAny(Path path, String... values) {
        String text = read(path);
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read " + path, e);
        }
    }
}
