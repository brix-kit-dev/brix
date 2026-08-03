/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.dataowner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lints owner-scoped SQL migrations for cross-Owner writes and references.
 */
public final class SqlMigrationLinter {

    public static final String CROSS_OWNER_WRITE = "DATA_OWNER_CROSS_WRITE";
    public static final String CROSS_OWNER_REFERENCE = "DATA_OWNER_CROSS_REFERENCE";
    public static final String UNREGISTERED_WRITE = "DATA_OWNER_UNREGISTERED_WRITE";

    private static final Pattern CREATE_TABLE = Pattern.compile(
        "\\bCREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([\\w.\"`]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALTER_TABLE = Pattern.compile(
        "\\bALTER\\s+TABLE\\s+(?:IF\\s+EXISTS\\s+)?([\\w.\"`]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_TABLE = Pattern.compile(
        "\\bDROP\\s+TABLE\\s+(?:IF\\s+EXISTS\\s+)?([\\w.\"`]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSERT_INTO = Pattern.compile(
        "\\bINSERT\\s+INTO\\s+([\\w.\"`]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPDATE_TABLE = Pattern.compile(
        "^\\s*UPDATE\\s+([\\w.\"`]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_FROM = Pattern.compile(
        "^\\s*DELETE\\s+FROM\\s+([\\w.\"`]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REFERENCES = Pattern.compile(
        "\\bREFERENCES\\s+([\\w.\"`]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern JOIN = Pattern.compile(
        "\\bJOIN\\s+([\\w.\"`]+)", Pattern.CASE_INSENSITIVE);

    private final DataOwnerRegistry registry;

    /**
     * Creates a migration linter.
     *
     * @param registry authoritative table owner registry
     */
    public SqlMigrationLinter(DataOwnerRegistry registry) {
        this.registry = registry;
    }

    /**
     * Lints one SQL migration document.
     *
     * @param document migration document
     * @return violations in source order
     */
    public List<SqlMigrationViolation> lint(SqlMigrationDocument document) {
        DataOwnerDefinition owner = registry.requireOwner(document.ownerId());
        List<SqlMigrationViolation> violations = new ArrayList<>();
        for (String statement : statements(document.sql())) {
            checkWrites(document, owner, statement, violations);
            checkReferences(document, owner, statement, violations);
        }
        return List.copyOf(violations);
    }

    private void checkWrites(
            SqlMigrationDocument document,
            DataOwnerDefinition owner,
            String statement,
            List<SqlMigrationViolation> violations) {
        for (Pattern pattern : List.of(CREATE_TABLE, ALTER_TABLE, DROP_TABLE, INSERT_INTO, UPDATE_TABLE, DELETE_FROM)) {
            Matcher matcher = pattern.matcher(statement);
            while (matcher.find()) {
                String table = DataOwnerDefinition.normalizeTable(matcher.group(1));
                Optional<DataOwnerDefinition> actualOwner = registry.ownerOf(table);
                if (actualOwner.isEmpty()) {
                    violations.add(violation(
                        UNREGISTERED_WRITE, document, table, "writes table not registered to any Data Owner"));
                } else if (!actualOwner.get().ownerId().equals(owner.ownerId())) {
                    violations.add(violation(
                        CROSS_OWNER_WRITE, document, table,
                        "writes table owned by " + actualOwner.get().ownerId()));
                }
            }
        }
    }

    private void checkReferences(
            SqlMigrationDocument document,
            DataOwnerDefinition owner,
            String statement,
            List<SqlMigrationViolation> violations) {
        for (Pattern pattern : List.of(REFERENCES, JOIN)) {
            Matcher matcher = pattern.matcher(statement);
            while (matcher.find()) {
                String table = DataOwnerDefinition.normalizeTable(matcher.group(1));
                registry.ownerOf(table)
                    .filter(actualOwner -> !actualOwner.ownerId().equals(owner.ownerId()))
                    .ifPresent(actualOwner -> violations.add(violation(
                        CROSS_OWNER_REFERENCE, document, table,
                        "references table owned by " + actualOwner.ownerId())));
            }
        }
    }

    private static List<String> statements(String sql) {
        String withoutLineComments = sql.replaceAll("(?m)--.*$", "");
        String withoutBlockComments = withoutLineComments.replaceAll("(?s)/\\*.*?\\*/", "");
        List<String> statements = new ArrayList<>();
        for (String statement : withoutBlockComments.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isBlank()) {
                statements.add(trimmed.toUpperCase(Locale.ROOT).startsWith("COMMENT ON ")
                    ? ""
                    : trimmed);
            }
        }
        statements.removeIf(String::isBlank);
        return statements;
    }

    private static SqlMigrationViolation violation(
            String code,
            SqlMigrationDocument document,
            String table,
            String message) {
        return new SqlMigrationViolation(code, document.name(), document.ownerId(), table, message);
    }
}
