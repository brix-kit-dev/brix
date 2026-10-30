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
package io.infra.adapter.persistence.jpa;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.runtime.orchestrator.event.EventInboxStore;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;

/**
 * Each owner's persistent event inbox, the {@code data.inbox} table of its manifest (root
 * BRX-RS-MESSAGE-023, MESSAGE-025).
 *
 * <p>The receipt is written on the owner's current transaction, the one the runtime opened for the
 * delivery, so it commits or rolls back with what the handler does. The store first looks for the
 * receipt and only then inserts it: on PostgreSQL a failed insert would abort the whole transaction.
 * The primary key {@code (handler_id, message_id)} still guards against a concurrent duplicate, whose
 * transaction then fails and is retried.</p>
 */
final class JdbcEventInboxStore implements EventInboxStore {

    private static final Pattern TABLE = Pattern.compile("[a-z][a-z0-9_]{0,62}");

    private final OwnerDirectory owners;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    JdbcEventInboxStore(OwnerDirectory owners, DataSource dataSource, Clock clock) {
        this.owners = Objects.requireNonNull(owners, "owners must not be null");
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public boolean insertProcessed(String ownerId, String handlerId, CanonicalOutboxMessage message) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("The inbox of " + ownerId + " is written only inside the owner's transaction");
        }
        String table = owners.inboxOf(ownerId)
            .filter(name -> TABLE.matcher(name).matches())
            .orElseThrow(() -> new IllegalStateException(ownerId + " declares no data.inbox for its deliveries"));
        Integer existing = jdbc.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE handler_id = ? AND message_id = ?",
            Integer.class, handlerId, message.eventId());
        if (existing != null && existing > 0) {
            return false;
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("INSERT INTO " + table + " (handler_id, message_id, message_kind, message_type, schema_version,"
                + " tenant_id, status, processed_at, created_at) VALUES (?, ?, ?, ?, ?, ?, 'PROCESSED', ?, ?)",
            handlerId, message.eventId(), message.messageKind(), message.messageType(), message.schemaVersion(),
            message.tenantId(), now, now);
        return true;
    }

    @Override
    public List<String> tables() {
        return owners.ownerIds().stream()
            .map(owners::inboxOf)
            .flatMap(Optional::stream)
            .filter(name -> TABLE.matcher(name).matches())
            .distinct()
            .toList();
    }

    @Override
    public int purge(String table, OffsetDateTime processedBefore, int limit) {
        if (!tables().contains(table)) {
            throw new IllegalArgumentException("Not an owner inbox: " + table);
        }
        return ExpiredRecords.delete(jdbc, table, ExpiredRecords.INBOX_KEY, "processed_at", processedBefore, limit);
    }
}
