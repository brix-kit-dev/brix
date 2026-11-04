/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CanonicalMessagingTablesTest {

    @Test
    void namesFollowTheStorageId() {
        assertEquals("app_orders_outbox", CanonicalMessagingTables.outboxOf("app_orders"));
        assertEquals("app_orders_inbox", CanonicalMessagingTables.inboxOf("app_orders"));
        assertEquals("app_orders_command_idempotency", CanonicalMessagingTables.commandIdempotencyOf("app_orders"));
        assertEquals("app_orders_command_outcome", CanonicalMessagingTables.commandOutcomeOf("app_orders"));
        assertEquals("app_orders_endpoint_idempotency", CanonicalMessagingTables.endpointIdempotencyOf("app_orders"));
        assertThrows(IllegalArgumentException.class, () -> CanonicalMessagingTables.outboxOf("app-orders"));
    }

    @Test
    void aConformingInboxHasNoProblems() {
        CanonicalMessagingTables.TableShape inbox = new CanonicalMessagingTables.TableShape("app_inbox",
            Set.copyOf(CanonicalMessagingTables.Kind.INBOX.columns()), List.of("handler_id", "message_id"),
            List.of(List.of("handler_id", "message_id"), List.of("processed_at")));

        assertEquals(List.of(), CanonicalMessagingTables.problems(CanonicalMessagingTables.Kind.INBOX, inbox));
    }

    @Test
    void aMissingKeyColumnOrIndexIsReported() {
        Set<String> columns = new HashSet<>(CanonicalMessagingTables.Kind.OUTBOX.columns());
        columns.remove("seq");
        CanonicalMessagingTables.TableShape outbox = new CanonicalMessagingTables.TableShape("app_outbox", columns,
            List.of("message_id"), List.of(List.of("status", "available_at")));

        List<String> problems = CanonicalMessagingTables.problems(CanonicalMessagingTables.Kind.OUTBOX, outbox);

        assertTrue(problems.contains("app_outbox has no column seq"), problems.toString());
        assertTrue(problems.contains("app_outbox has no index on [partition_key, seq]"), problems.toString());
    }

    @Test
    void anInboxWithoutItsDeduplicationKeyIsRefused() {
        CanonicalMessagingTables.TableShape inbox = new CanonicalMessagingTables.TableShape("app_inbox",
            Set.copyOf(CanonicalMessagingTables.Kind.INBOX.columns()), List.of(), List.of(List.of("processed_at")));

        assertEquals(List.of("app_inbox must have the primary key [handler_id, message_id], not []"),
            CanonicalMessagingTables.problems(CanonicalMessagingTables.Kind.INBOX, inbox));
    }

    @Test
    void aMissingTableIsOneProblem() {
        assertEquals(List.of("app_outbox does not exist"), CanonicalMessagingTables.problems(
            CanonicalMessagingTables.Kind.OUTBOX,
            new CanonicalMessagingTables.TableShape("app_outbox", Set.of(), List.of(), List.of())));
    }

    @Test
    void eachTablesMigrationsCreateEveryColumnTheSchemaRequires() throws IOException {
        // A column comes with the table (V1) or is added by a later migration of the same table, such as the command
        // key's request fingerprint (V2, standard-link plan point 8).
        List<String> missing = new ArrayList<>();
        for (CanonicalMessagingTables.Kind kind : CanonicalMessagingTables.Kind.values()) {
            String migration = migration(kind);
            assertTrue(migration.contains("CREATE TABLE " + kind.tableOf("{{storageId}}") + " ("), kind.toString());
            String migrations = migrations(kind);
            for (String column : kind.columns()) {
                if (!migrations.matches("(?s).*\\n    " + column + " +[A-Z].*")
                        && !migrations.contains(" ADD COLUMN " + column + " ")) {
                    missing.add(kind + "." + column);
                }
            }
        }
        assertEquals(List.of(), missing);
        assertTrue(migration(CanonicalMessagingTables.Kind.OUTBOX)
            .contains("CREATE INDEX idx_{{storageId}}_outbox_key ON {{storageId}}_outbox (partition_key, seq);"));
    }

    @Test
    void theTablesAreNamedAfterTheStorage() {
        assertEquals("shop_outbox", CanonicalMessagingTables.Kind.OUTBOX.tableOf("shop"));
        assertEquals("shop_inbox", CanonicalMessagingTables.Kind.INBOX.tableOf("shop"));
        assertEquals("shop_command_idempotency", CanonicalMessagingTables.Kind.COMMAND_IDEMPOTENCY.tableOf("shop"));
        assertEquals("shop_command_outcome", CanonicalMessagingTables.Kind.COMMAND_OUTCOME.tableOf("shop"));
        assertEquals(CanonicalMessagingTables.outboxOf("shop"), CanonicalMessagingTables.Kind.OUTBOX.tableOf("shop"));
    }

    @Test
    void theCommandKeyGetsItsFingerprintInItsSecondMigration() throws IOException {
        assertTrue(migrations(CanonicalMessagingTables.Kind.COMMAND_IDEMPOTENCY).contains(
            "ALTER TABLE {{storageId}}_command_idempotency ADD COLUMN request_fingerprint VARCHAR(64);"));
    }

    @Test
    void theEndpointKeyTableCameWithTheThirdVersion() throws IOException {
        // Standard-link plan point 10a: its primary key is what a call is deduplicated by, and its expiry what the
        // cleaning reads.
        String migrations = migrations(CanonicalMessagingTables.Kind.ENDPOINT_IDEMPOTENCY);
        assertTrue(migrations.contains("PRIMARY KEY (entry_id, idempotency_scope, idempotency_key)"), migrations);
        assertTrue(migrations.contains("ON {{storageId}}_endpoint_idempotency (expires_at)"), migrations);
        assertEquals(3, CanonicalMessagingTables.VERSION);
    }

    /** Every migration of a table, in version order. */
    private String migrations(CanonicalMessagingTables.Kind kind) throws IOException {
        java.net.URL directory = getClass().getClassLoader().getResource(kind.migrations());
        assertTrue(directory != null, kind.migrations());
        StringBuilder all = new StringBuilder();
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(
                java.nio.file.Path.of(java.net.URI.create(directory.toString())))) {
            List<java.nio.file.Path> ordered = files
                .filter(path -> path.getFileName().toString().matches("V\\d+__.*\\.sql"))
                .sorted(java.util.Comparator.comparingInt(CanonicalMessagingTablesTest::version))
                .toList();
            for (java.nio.file.Path file : ordered) {
                all.append(java.nio.file.Files.readString(file)).append('\n');
            }
        }
        return all.toString();
    }

    private static int version(java.nio.file.Path migration) {
        String name = migration.getFileName().toString();
        return Integer.parseInt(name.substring(1, name.indexOf("__")));
    }

    private String migration(CanonicalMessagingTables.Kind kind) throws IOException {
        String resource = kind.migrations() + "/V1__canonical_" + kind.part().replace('-', '_') + ".sql";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertTrue(input != null, resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
