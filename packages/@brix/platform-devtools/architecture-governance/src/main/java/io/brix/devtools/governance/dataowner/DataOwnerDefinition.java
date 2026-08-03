/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.dataowner;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Defines one authoritative Data Owner and the tables it is allowed to mutate.
 *
 * @param ownerId stable owner id from the ACTIVE architecture registry
 * @param modulePath source module path that owns schema and migrations
 * @param tables canonical table names owned by this owner
 * @param migrationLocation Flyway location owned by this owner
 * @param outboxTable canonical owner outbox table, when reliable messages apply
 * @param inboxTable canonical owner inbox table, when reliable messages apply
 */
public record DataOwnerDefinition(
        String ownerId,
        String modulePath,
        Set<String> tables,
        String migrationLocation,
        String outboxTable,
        String inboxTable) {

    public DataOwnerDefinition {
        ownerId = requireText(ownerId, "ownerId");
        modulePath = requireText(modulePath, "modulePath");
        migrationLocation = requireText(migrationLocation, "migrationLocation");
        tables = normalizeTables(tables);
        outboxTable = normalizeOptional(outboxTable);
        inboxTable = normalizeOptional(inboxTable);
    }

    /**
     * Returns whether this owner owns the supplied table.
     *
     * @param tableName SQL table name
     * @return true when the table is in this owner definition
     */
    public boolean owns(String tableName) {
        return tables.contains(normalizeTable(tableName));
    }

    static String normalizeTable(String tableName) {
        String value = requireText(tableName, "tableName")
            .replace("\"", "")
            .replace("`", "")
            .trim();
        int dot = value.lastIndexOf('.');
        if (dot >= 0) {
            value = value.substring(dot + 1);
        }
        int paren = value.indexOf('(');
        if (paren >= 0) {
            value = value.substring(0, paren);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static Set<String> normalizeTables(Set<String> input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("tables must not be empty");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String table : input) {
            normalized.add(normalizeTable(table));
        }
        return Set.copyOf(normalized);
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return normalizeTable(value);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
