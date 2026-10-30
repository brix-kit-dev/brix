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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.command.OwnerCommandIdempotency;
import io.runtime.orchestrator.event.EventInboxStore;

class MessageRetentionTest {

    private static final Instant NOW = Instant.parse("2026-10-30T03:00:00Z");
    private static final OffsetDateTime AT = NOW.atOffset(ZoneOffset.UTC);

    private final OutboxRelayProperties.Retention settings = new OutboxRelayProperties.Retention();
    private final Recorder metrics = new Recorder();

    @Test
    void theDefaultsKeepPublishedMessagesTwoWeeksAndInboxRecordsThirtyDays() {
        assertEquals(Duration.ofDays(14), settings.getOutbox());
        assertEquals(Duration.ofDays(30), settings.getInbox());
        MessageRetention.check(settings, Duration.ofDays(7));
    }

    @Test
    void anInboxRetentionShorterThanTheOutboxsPlusTheBrokersStopsTheHost() {
        settings.setInbox(Duration.ofDays(20));

        IllegalStateException refused = assertThrows(IllegalStateException.class,
            () -> MessageRetention.check(settings, Duration.ofDays(7)));

        assertTrue(refused.getMessage().contains("brix.outbox.relay.retention.inbox"));
        MessageRetention.check(settings, Duration.ZERO);
    }

    @Test
    void retentionsAndThePaceMustBePositive() {
        settings.setBatchSize(0);
        assertThrows(IllegalStateException.class, () -> MessageRetention.check(settings, Duration.ZERO));
        settings.setBatchSize(10);
        settings.setOutbox(Duration.ZERO);
        assertThrows(IllegalStateException.class, () -> MessageRetention.check(settings, Duration.ZERO));
    }

    @Test
    void eachTableIsCleanedABatchAtATimeWithItsOwnCutoff() {
        settings.setBatchSize(2);
        Expiring outbox = new Expiring(5);
        Expiring inbox = new Expiring(3);
        Expiring idempotency = new Expiring(1);
        MessageRetention retention = retention(outbox, inbox, idempotency);

        Map<String, Integer> deleted = retention.purgeOnce();

        assertEquals(Map.of("platform_tenant_outbox", 5, "platform_audit_inbox", 3, "shop_command_idempotency", 1),
            deleted);
        assertEquals(List.of(AT.minusDays(14)), outbox.cutoffs.stream().distinct().toList());
        assertEquals(List.of(AT.minusDays(30)), inbox.cutoffs.stream().distinct().toList());
        assertEquals(List.of(AT.minusDays(30)), idempotency.cutoffs.stream().distinct().toList());
        assertEquals(List.of(2, 2, 2), outbox.limits);
        assertEquals(Map.of("platform_tenant_outbox", 5, "platform_audit_inbox", 3, "shop_command_idempotency", 1),
            metrics.deleted);
        assertEquals(List.of(NOW), metrics.passes);
    }

    @Test
    void aTableThatCannotBeCleanedIsReportedAndTheOthersAreCleaned() {
        Expiring outbox = new Expiring(1);
        outbox.failing = true;
        Expiring inbox = new Expiring(1);
        MessageRetention retention = retention(outbox, inbox, new Expiring(0));

        Map<String, Integer> deleted = retention.purgeOnce();

        assertEquals(Map.of("platform_audit_inbox", 1, "shop_command_idempotency", 0), deleted);
        assertEquals(List.of("platform_tenant_outbox"), metrics.failed);
        assertEquals(List.of(NOW), metrics.passes);
    }

    @Test
    void aPassEndsOnALargeBacklog() {
        settings.setBatchSize(1);
        Expiring outbox = new Expiring(MessageRetention.MAX_BATCHES + 5);
        MessageRetention retention = retention(outbox, new Expiring(0), new Expiring(0));

        assertEquals(MessageRetention.MAX_BATCHES, retention.purgeOnce().get("platform_tenant_outbox"));
        assertEquals(5, retention.purgeOnce().get("platform_tenant_outbox"));
    }

    @Test
    void withoutStoresThereIsNothingToClean() {
        MessageRetention retention = new MessageRetention(() -> null, () -> null, () -> null, settings, Duration.ZERO,
            metrics, Clock.fixed(NOW, ZoneOffset.UTC));

        assertEquals(Map.of(), retention.purgeOnce());
    }

    @Test
    void theCleaningStartsAndStopsWithTheHost() {
        MessageRetention retention = retention(new Expiring(0), new Expiring(0), new Expiring(0));

        retention.start();
        assertTrue(retention.isRunning());
        retention.stop();
        assertEquals(false, retention.isRunning());
    }

    private MessageRetention retention(Expiring outbox, Expiring inbox, Expiring idempotency) {
        OutboxStoreRegistry outboxes = () -> Map.of("platform_tenant_outbox", outbox.asOutbox());
        EventInboxStore inboxes = new EventInboxStore() {
            @Override
            public boolean insertProcessed(String ownerId, String handlerId, CanonicalOutboxMessage message) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<String> tables() {
                return List.of("platform_audit_inbox");
            }

            @Override
            public int purge(String table, OffsetDateTime processedBefore, int limit) {
                return inbox.delete(processedBefore, limit);
            }
        };
        OwnerCommandIdempotency commands = new OwnerCommandIdempotency() {
            @Override
            public boolean reserve(String ownerId, String handlerId, CanonicalOutboxMessage message, String scope,
                    String key) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<String> tables() {
                return List.of("shop_command_idempotency");
            }

            @Override
            public int purge(String table, OffsetDateTime createdBefore, int limit) {
                return idempotency.delete(createdBefore, limit);
            }
        };
        return new MessageRetention(() -> outboxes, () -> inboxes, () -> commands, settings, Duration.ofDays(7),
            metrics, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** A table holding a number of expired rows. */
    private static final class Expiring {

        private int expired;
        private boolean failing;
        private final List<OffsetDateTime> cutoffs = new ArrayList<>();
        private final List<Integer> limits = new ArrayList<>();

        Expiring(int expired) {
            this.expired = expired;
        }

        int delete(OffsetDateTime before, int limit) {
            if (failing) {
                throw new IllegalStateException("relation does not exist");
            }
            cutoffs.add(before);
            limits.add(limit);
            int rows = Math.min(expired, limit);
            expired -= rows;
            return rows;
        }

        OutboxMessageStore asOutbox() {
            return new OutboxMessageStore() {
                @Override
                public int purge(OffsetDateTime before, int limit) {
                    return delete(before, limit);
                }

                @Override
                public void append(CanonicalOutboxMessage message) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public List<CanonicalOutboxMessage> claimDue(String relayOwner, OffsetDateTime now, Duration lease,
                        int batchSize) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void markPublished(String relayOwner, String messageId, OffsetDateTime publishedAt) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void releaseForRetry(String relayOwner, String messageId, OffsetDateTime availableAt,
                        String errorCode) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void releaseUntried(String relayOwner, String messageId) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void park(String relayOwner, String messageId, String errorCode, OffsetDateTime parkedAt) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public OutboxBacklogSnapshot readBacklog(OffsetDateTime now) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public List<ParkedOutboxMessage> listParked(int limit) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean replayParked(String eventId, OffsetDateTime now) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    private static final class Recorder implements MessageRetentionMetrics {

        private final Map<String, Integer> deleted = new LinkedHashMap<>();
        private final List<String> failed = new ArrayList<>();
        private final List<Instant> passes = new ArrayList<>();

        @Override
        public void deleted(String table, int rows) {
            deleted.merge(table, rows, Integer::sum);
        }

        @Override
        public void failed(String table) {
            failed.add(table);
        }

        @Override
        public void passed(Instant at) {
            passes.add(at);
        }
    }
}
