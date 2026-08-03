/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.dataowner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies that released migration files still match their recorded SHA-256.
 */
public final class MigrationHashLedger {

    public static final String MISSING_LEDGER_ENTRY = "MIGRATION_LEDGER_MISSING_ENTRY";
    public static final String MISSING_FILE = "MIGRATION_LEDGER_MISSING_FILE";
    public static final String HASH_MISMATCH = "MIGRATION_LEDGER_HASH_MISMATCH";

    private final Map<String, String> hashesByRelativePath;

    private MigrationHashLedger(Map<String, String> hashesByRelativePath) {
        this.hashesByRelativePath = Map.copyOf(hashesByRelativePath);
    }

    /**
     * Creates a ledger from lines in the format {@code relative/path.sql sha256}.
     *
     * @param ledgerText ledger text
     * @return parsed ledger
     */
    public static MigrationHashLedger parse(String ledgerText) {
        if (ledgerText == null || ledgerText.isBlank()) {
            throw new IllegalArgumentException("ledgerText must not be blank");
        }
        Map<String, String> entries = new LinkedHashMap<>();
        for (String line : ledgerText.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            if (parts.length != 2 || parts[1].length() != 64) {
                throw new IllegalArgumentException("Invalid migration ledger line: " + line);
            }
            entries.put(parts[0], parts[1].toLowerCase());
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("migration ledger must contain at least one entry");
        }
        return new MigrationHashLedger(entries);
    }

    /**
     * Verifies all ledger entries against a repository root.
     *
     * @param repoRoot repository root
     * @return ledger violations
     */
    public List<SqlMigrationViolation> verify(Path repoRoot) {
        List<SqlMigrationViolation> violations = new ArrayList<>();
        for (Map.Entry<String, String> entry : hashesByRelativePath.entrySet()) {
            Path file = repoRoot.resolve(entry.getKey()).normalize();
            if (!Files.isRegularFile(file)) {
                violations.add(ledgerViolation(MISSING_FILE, entry.getKey(), "released migration file is missing"));
                continue;
            }
            String actual = sha256(file);
            if (!entry.getValue().equals(actual)) {
                violations.add(ledgerViolation(
                    HASH_MISMATCH, entry.getKey(), "released migration file hash changed"));
            }
        }
        return List.copyOf(violations);
    }

    /**
     * Computes a SHA-256 hash for one file.
     *
     * @param file file path
     * @return lowercase hexadecimal SHA-256
     */
    public static String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (IOException ex) {
            throw new UncheckedIOException("Unable to hash " + file, ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static SqlMigrationViolation ledgerViolation(String code, String path, String message) {
        return new SqlMigrationViolation(code, path, "migration-ledger", path, message);
    }
}
