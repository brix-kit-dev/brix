/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.contract.generator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Keeps an api module's checked-in contract sources equal to what its schemas generate (query design D38).
 *
 * <p>An api module calls {@link #verify(Path, String)} from a test. The test fails when a generated source is missing,
 * differs or is left over, or when the API listing in {@code src/main/brix/contracts.api} differs from what the
 * schemas give; it fails in every mode when a line of the listing would disappear, because a published minor version
 * may only be extended (root BRX-RS-CAP-019, A-20). The listing holds the content hash of every published schema
 * version, so editing a published file fails too (standard-link plan L5.2). Run it with
 * {@code -Dbrix.contracts.write=true} to rewrite the sources and the listing, then review and commit them.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class ContractSources {

    /** The system property that makes {@link #verify} rewrite instead of compare. */
    public static final String WRITE = "brix.contracts.write";

    /** Where the API listing lives in a module. */
    public static final String API_LISTING = "src/main/brix/contracts.api";

    private ContractSources() {
    }

    /**
     * Compares, or with {@value #WRITE} rewrites, a module's generated contract sources and API listing.
     *
     * @param module the api module's directory
     * @param javaPackage the package of its generated types
     * @throws AssertionError when the checked-in sources or listing are stale, or the listing would lose a line, such
     *     as the hash of a published schema version that was edited
     */
    public static void verify(Path module, String javaPackage) {
        QueryContractGenerator.Generated generated = QueryContractGenerator.generate(
            module.resolve("src/main/resources"), javaPackage);
        Path sources = module.resolve("src/main/java").resolve(javaPackage.replace('.', '/'));
        Path listing = module.resolve(API_LISTING);
        List<String> committed = lines(listing);
        List<String> lost = new ArrayList<>(committed);
        lost.removeAll(generated.api());
        if (!lost.isEmpty()) {
            Path where = module.toAbsolutePath().normalize();
            List<String> schemas = lost.stream().filter(line -> line.startsWith("schema ")).toList();
            List<String> api = lost.stream().filter(line -> !line.startsWith("schema ")).toList();
            List<String> reasons = new ArrayList<>();
            if (!schemas.isEmpty()) {
                reasons.add("Published schema versions of " + where + " changed or disappeared. A published version "
                    + "never changes: publish the change as a new version (a patch version for documentation) and keep "
                    + "the earlier file as it was:\n  " + String.join("\n  ", schemas));
            }
            if (!api.isEmpty()) {
                reasons.add("The contracts of " + where + " would lose API a published minor version must keep; a "
                    + "breaking change needs a new major version:\n  " + String.join("\n  ", api));
            }
            throw new AssertionError(String.join("\n", reasons));
        }
        Set<String> leftOver = generatedFiles(sources);
        leftOver.removeAll(generated.sources().keySet());
        if (Boolean.getBoolean(WRITE)) {
            write(sources, listing, generated, leftOver);
            return;
        }
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, String> source : generated.sources().entrySet()) {
            Path file = sources.resolve(source.getKey() + ".java");
            if (!Files.exists(file) || !read(file).equals(source.getValue())) {
                stale.add(file.toString());
            }
        }
        leftOver.forEach(name -> stale.add(sources.resolve(name + ".java") + " (no longer generated)"));
        if (!committed.equals(generated.api())) {
            stale.add(listing.toString());
        }
        if (!stale.isEmpty()) {
            throw new AssertionError("The contract sources are not what the schemas generate:\n  "
                + String.join("\n  ", stale) + "\nRegenerate them with -D" + WRITE + "=true on this test, then review "
                + "and commit the result.");
        }
    }

    private static void write(Path sources, Path listing, QueryContractGenerator.Generated generated,
            Set<String> leftOver) {
        try {
            Files.createDirectories(sources);
            for (Map.Entry<String, String> source : generated.sources().entrySet()) {
                Files.writeString(sources.resolve(source.getKey() + ".java"), source.getValue(), StandardCharsets.UTF_8);
            }
            for (String name : leftOver) {
                Files.delete(sources.resolve(name + ".java"));
            }
            Files.createDirectories(listing.getParent());
            Files.writeString(listing, String.join("\n", generated.api()) + "\n", StandardCharsets.UTF_8);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    /** The checked-in sources the generator made, by simple class name. */
    private static Set<String> generatedFiles(Path sources) {
        Set<String> names = new LinkedHashSet<>();
        if (!Files.isDirectory(sources)) {
            return names;
        }
        try (Stream<Path> files = Files.list(sources)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                if (read(file).contains(QueryContractGenerator.GENERATED_MARK)) {
                    String name = file.getFileName().toString();
                    names.add(name.substring(0, name.length() - ".java".length()));
                }
            }
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return names;
    }

    private static List<String> lines(Path listing) {
        if (!Files.exists(listing)) {
            return List.of();
        }
        return read(listing).lines().filter(line -> !line.isBlank()).toList();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
