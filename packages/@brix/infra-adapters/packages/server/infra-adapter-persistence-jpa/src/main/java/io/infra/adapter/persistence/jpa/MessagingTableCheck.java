/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.sql.DataSource;

import io.runtime.orchestrator.outbox.CanonicalMessagingTables;

/**
 * Reads an Owner's canonical messaging table from the database metadata and compares it with the platform
 * schema ({@link CanonicalMessagingTables}), so an Owner with a missing or incomplete outbox or inbox is
 * refused when its persistence is created, not on its first message.
 *
 * @author Brix Platform Authors
 * @since 3.3.0
 */
final class MessagingTableCheck {

    private MessagingTableCheck() {
    }

    /**
     * Throws when the table does not conform.
     *
     * @param dataSource the Owner's data source
     * @param ownerId the Owner, for the message
     * @param kind which canonical table it is
     * @param table its name
     */
    static void require(DataSource dataSource, String ownerId, CanonicalMessagingTables.Kind kind, String table) {
        List<String> problems = CanonicalMessagingTables.problems(kind, read(dataSource, table));
        if (!problems.isEmpty()) {
            throw new IllegalStateException("The " + kind.name().toLowerCase(Locale.ROOT) + " of '" + ownerId
                + "' does not match the platform messaging schema v" + CanonicalMessagingTables.VERSION + ": "
                + String.join("; ", problems));
        }
    }

    static CanonicalMessagingTables.TableShape read(DataSource dataSource, String table) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String schema = connection.getSchema();
            String name = table;
            Set<String> columns = columns(metadata, schema, name);
            if (columns.isEmpty()) {
                name = table.toUpperCase(Locale.ROOT);
                columns = columns(metadata, schema, name);
            }
            return new CanonicalMessagingTables.TableShape(table, columns, primaryKey(metadata, schema, name),
                indexes(metadata, schema, name));
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot read the metadata of " + table, failure);
        }
    }

    private static Set<String> columns(DatabaseMetaData metadata, String schema, String table) throws SQLException {
        Set<String> columns = new LinkedHashSet<>();
        try (ResultSet rows = metadata.getColumns(null, schema, table, null)) {
            while (rows.next()) {
                columns.add(rows.getString("COLUMN_NAME"));
            }
        }
        return columns;
    }

    private static List<String> primaryKey(DatabaseMetaData metadata, String schema, String table) throws SQLException {
        Map<Integer, String> key = new TreeMap<>();
        try (ResultSet rows = metadata.getPrimaryKeys(null, schema, table)) {
            while (rows.next()) {
                key.put(rows.getInt("KEY_SEQ"), rows.getString("COLUMN_NAME"));
            }
        }
        return new ArrayList<>(key.values());
    }

    private static List<List<String>> indexes(DatabaseMetaData metadata, String schema, String table)
            throws SQLException {
        Map<String, Map<Integer, String>> indexes = new LinkedHashMap<>();
        try (ResultSet rows = metadata.getIndexInfo(null, schema, table, false, true)) {
            while (rows.next()) {
                String index = rows.getString("INDEX_NAME");
                String column = rows.getString("COLUMN_NAME");
                if (index != null && column != null) {
                    indexes.computeIfAbsent(index, ignored -> new TreeMap<>()).put(rows.getInt("ORDINAL_POSITION"), column);
                }
            }
        }
        return indexes.values().stream().map(columns -> (List<String>) new ArrayList<>(columns.values())).toList();
    }
}
