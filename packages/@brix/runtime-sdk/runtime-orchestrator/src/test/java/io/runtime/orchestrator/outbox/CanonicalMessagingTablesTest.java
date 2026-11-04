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
    void eachTablesMigrationCreatesEveryColumnTheSchemaRequires() throws IOException {
        List<String> missing = new ArrayList<>();
        for (CanonicalMessagingTables.Kind kind : CanonicalMessagingTables.Kind.values()) {
            String migration = migration(kind);
            assertTrue(migration.contains("CREATE TABLE " + kind.tableOf("{{storageId}}") + " ("), kind.toString());
            for (String column : kind.columns()) {
                if (!migration.matches("(?s).*\\n    " + column + " +[A-Z].*")) {
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

    private String migration(CanonicalMessagingTables.Kind kind) throws IOException {
        String resource = kind.migrations() + "/V1__canonical_" + kind.part().replace('-', '_') + ".sql";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertTrue(input != null, resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
