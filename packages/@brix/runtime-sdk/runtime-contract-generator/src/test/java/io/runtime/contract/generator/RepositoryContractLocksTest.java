/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.contract.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every module of this repository that ships contracts is locked (standard-link plan queue item 3, D115).
 *
 * <p>A module's lock is its own test calling {@link ContractSources#verify}, which pins each schema version the module
 * ships in {@code src/main/brix/contracts.api}. A module without one could change a published version unnoticed, so
 * this test walks the repository and fails for every Maven module whose {@code src/main/resources/META-INF/brix} ships
 * a schema, query, command or event contract but has no listing or no such test. Test resources, build output, hidden
 * directories and templates ship nothing.</p>
 */
class RepositoryContractLocksTest {

    /** What the generator reads, so what the lock covers. */
    private static final List<String> CONTRACTS = List.of("schemas", "queries", "commands", "events");

    private static final Set<String> NOT_SOURCES = Set.of("target", "node_modules");

    private static final String LOCK_CALL = "ContractSources.verify(";

    @Test
    void everyModuleThatShipsContractsIsLocked() {
        Path repository = repositoryRoot();
        Map<String, List<String>> modules = scan(repository);
        assertFalse(modules.isEmpty(),
            () -> "No module under " + repository + " ships contracts: the scan looks in the wrong place");
        List<String> unlocked = unlocked(modules);
        assertTrue(unlocked.isEmpty(), () -> "A module that ships contracts locks them: add a test under src/test/java "
            + "that calls ContractSources.verify(Path.of(\"\"), \"<package of the generated types>\"), with "
            + "io.brix:runtime-contract-generator in test scope, and run it once with -D" + ContractSources.WRITE
            + "=true to write " + ContractSources.API_LISTING + " (see the Queries guide):\n  "
            + String.join("\n  ", unlocked));
    }

    @Test
    void aModuleThatShipsContractsWithoutAListingAndATestIsUnlocked(@TempDir Path repository) throws IOException {
        Path booking = module(repository, "plugins/booking-api");
        write(booking.resolve("src/main/resources/META-INF/brix/schemas/booking/create-request/1.0.0.json"), "{}");
        Path rooms = module(repository, "plugins/rooms-api");
        write(rooms.resolve("src/main/resources/META-INF/brix/queries/rooms.find-room/1.0.0.json"), "{}");

        assertEquals(List.of(
                "plugins/booking-api: no " + ContractSources.API_LISTING + ", no test calls " + LOCK_CALL + ")",
                "plugins/rooms-api: no " + ContractSources.API_LISTING + ", no test calls " + LOCK_CALL + ")"),
            unlocked(scan(repository)));

        write(booking.resolve(ContractSources.API_LISTING), "\n");
        write(rooms.resolve("src/test/java/rooms/ContractSourcesTest.java"),
            "class ContractSourcesTest { void locked() { ContractSources.verify(Path.of(\"\"), \"rooms\"); } }");
        assertEquals(List.of(
                "plugins/booking-api: no test calls " + LOCK_CALL + ")",
                "plugins/rooms-api: no " + ContractSources.API_LISTING),
            unlocked(scan(repository)));

        write(booking.resolve("src/test/java/booking/ContractSourcesTest.java"),
            "class ContractSourcesTest { void locked() { ContractSources.verify(Path.of(\"\"), \"booking\"); } }");
        write(rooms.resolve(ContractSources.API_LISTING), "\n");
        assertEquals(Map.of("plugins/booking-api", List.of(), "plugins/rooms-api", List.of()), scan(repository));
    }

    @Test
    void testResourcesBuildOutputHiddenDirectoriesAndTemplatesShipNoContract(@TempDir Path repository)
            throws IOException {
        Path module = module(repository, "plugins/booking-server");
        write(module.resolve("src/test/resources/META-INF/brix/schemas/booking/fixture/1.0.0.json"), "{}");
        write(module.resolve("src/main/resources/META-INF/brix/plugin-manifest.yaml"), "kind: Plugin\n");
        for (String elsewhere : List.of("plugins/booking-server/target/generated", "ui/node_modules/some-package",
                ".worktrees/other/plugins/booking-api")) {
            write(module(repository, elsewhere)
                .resolve("src/main/resources/META-INF/brix/schemas/booking/create-request/1.0.0.json"), "{}");
        }
        write(repository.resolve("templates/plugin-api/src/main/resources/META-INF/brix/schemas/x/y/1.0.0.json"), "{}");

        assertEquals(Map.of(), scan(repository));
    }

    /** The Maven modules under {@code repository} that ship contracts, by path, with what each lacks of a lock. */
    private static Map<String, List<String>> scan(Path repository) {
        Map<String, List<String>> modules = new TreeMap<>();
        try {
            Files.walkFileTree(repository, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    String name = directory.getFileName().toString();
                    if (!directory.equals(repository) && (name.startsWith(".") || NOT_SOURCES.contains(name))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (Files.isRegularFile(directory.resolve("pom.xml")) && shipsContracts(directory)) {
                        List<String> lacks = new ArrayList<>();
                        if (!Files.isRegularFile(directory.resolve(ContractSources.API_LISTING))) {
                            lacks.add("no " + ContractSources.API_LISTING);
                        }
                        if (!callsTheLock(directory.resolve("src/test/java"))) {
                            lacks.add("no test calls " + LOCK_CALL + ")");
                        }
                        modules.put(repository.relativize(directory).toString().replace('\\', '/'), lacks);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return modules;
    }

    private static List<String> unlocked(Map<String, List<String>> modules) {
        return modules.entrySet().stream().filter(module -> !module.getValue().isEmpty())
            .map(module -> module.getKey() + ": " + String.join(", ", module.getValue())).toList();
    }

    private static boolean shipsContracts(Path module) {
        Path brix = module.resolve("src/main/resources/META-INF/brix");
        return CONTRACTS.stream().anyMatch(kind -> files(brix.resolve(kind), ".json").findAny().isPresent());
    }

    private static boolean callsTheLock(Path tests) {
        return files(tests, ".java").anyMatch(source -> read(source).contains(LOCK_CALL));
    }

    private static Stream<Path> files(Path root, String suffix) {
        if (!Files.isDirectory(root)) {
            return Stream.empty();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(file -> Files.isRegularFile(file) && file.toString().endsWith(suffix)).toList().stream();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))
                    && Files.exists(current.resolve("packages/@brix/runtime-sdk/runtime-contract-generator"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate the repository root above " + Path.of("").toAbsolutePath());
    }

    private static Path module(Path repository, String path) throws IOException {
        Path module = repository.resolve(path);
        write(module.resolve("pom.xml"), "<project/>");
        return module;
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
