/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import io.runtime.manifest.loader.PluginManifestLoader;
import io.runtime.manifest.model.PluginManifest;
import io.runtime.orchestrator.event.BestEffortEventSender;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.persistence.TenantScope;
import io.runtime.sdk.capability.EventPublishException;
import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.EventScope;
import io.runtime.sdk.event.IntegrationEvent;

/**
 * The owner outbox bus writes declared CRITICAL/STANDARD events in the owner's own transaction and refuses
 * everything else (root BRX-RS-MESSAGE-021, MESSAGE-022, DATA-008; P4 design D13). H2 by default; the
 * reference DCP (PostgreSQL, JSONB payload) when {@code BRIX_P0_POSTGRES_URL}, {@code BRIX_P0_POSTGRES_USER}
 * and {@code BRIX_P0_POSTGRES_PASSWORD} point at a throwaway database.
 */
class OwnerOutboxEventBusTest {

    private static final String OWNER = "test-owner";
    private static final Instant NOW = Instant.parse("2026-10-29T01:00:00Z");

    private final String outbox = "owner_outbox_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private final ObjectMapper json = new ObjectMapper();
    private DataSource dataSource;
    private boolean postgres;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private OwnerOutboxEventBus bus;

    /** A tenant fact whose manifest id comes from the class name. */
    static final class MemberAcceptedEvent extends IntegrationEvent {
        MemberAcceptedEvent(String tenantId) {
            super(OWNER, tenantId);
        }

        @Override
        public String getRoutingKey() {
            return "member-7";
        }

        @Override
        public Object payload() {
            return Map.of("memberId", 7, "role", "OWNER");
        }
    }

    /** The same declared type with no tenant, so a PLATFORM fact. */
    static final class PlatformMemberAcceptedEvent extends IntegrationEvent {
        PlatformMemberAcceptedEvent() {
            super(OWNER);
        }

        @Override
        public String eventTypeId() {
            return "MemberAccepted";
        }

        @Override
        public String getRoutingKey() {
            return "";
        }

        @Override
        public Object payload() {
            return Map.of("memberId", 8);
        }
    }

    static final class HeartbeatEvent extends IntegrationEvent {
        HeartbeatEvent() {
            super(OWNER);
        }

        @Override
        public String getRoutingKey() {
            return "heartbeat";
        }

        @Override
        public Object payload() {
            return Map.of();
        }
    }

    static final class UndeclaredEvent extends IntegrationEvent {
        UndeclaredEvent() {
            super(OWNER);
        }

        @Override
        public String getRoutingKey() {
            return "undeclared";
        }

        @Override
        public Object payload() {
            return Map.of();
        }
    }

    @BeforeEach
    void setUp() {
        String url = System.getenv("BRIX_P0_POSTGRES_URL");
        postgres = url != null && !url.isBlank();
        if (postgres) {
            PGSimpleDataSource pg = new PGSimpleDataSource();
            pg.setUrl(url);
            pg.setUser(System.getenv("BRIX_P0_POSTGRES_USER"));
            pg.setPassword(System.getenv("BRIX_P0_POSTGRES_PASSWORD"));
            dataSource = pg;
        } else {
            JdbcDataSource h2 = new JdbcDataSource();
            h2.setURL("jdbc:h2:mem:outbox-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
            dataSource = h2;
        }
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE " + outbox + " ("
            + "message_id VARCHAR(64) PRIMARY KEY, event_id VARCHAR(64) NOT NULL, message_kind VARCHAR(20) NOT NULL,"
            + " message_type VARCHAR(128) NOT NULL, event_type VARCHAR(128) NOT NULL, schema_version VARCHAR(16) NOT NULL,"
            + " tenant_id BIGINT, partition_key VARCHAR(128) NOT NULL, payload " + (postgres ? "JSONB" : "VARCHAR(4000)")
            + " NOT NULL, reliability VARCHAR(20) NOT NULL, producer_plugin_id VARCHAR(128) NOT NULL,"
            + " scope VARCHAR(20) NOT NULL, correlation_id VARCHAR(128) NOT NULL, causation_id VARCHAR(128),"
            + " traceparent VARCHAR(128), tracestate VARCHAR(512), status VARCHAR(20) NOT NULL,"
            + " occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, available_at TIMESTAMP WITH TIME ZONE NOT NULL,"
            + " attempt_count INTEGER NOT NULL, claim_owner VARCHAR(128), claim_until TIMESTAMP WITH TIME ZONE,"
            + " published_at TIMESTAMP WITH TIME ZONE, last_error_code VARCHAR(128),"
            + " created_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        List<PluginManifest.EventPublish> publications = new PluginManifestLoader()
            .loadActiveFromClasspath(OwnerOutboxEventBusTest.class).getEvents().getPublishes();
        bus = new OwnerOutboxEventBus(OWNER, outbox, publications, dataSource, json, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void dropOutbox() {
        jdbc.execute("DROP TABLE " + outbox);
    }

    @Test
    void aDeclaredEventIsWrittenWithTheOwnersTransaction() {
        MemberAcceptedEvent event = new MemberAcceptedEvent("910001");

        inUnitOfWork(OWNER, false, TenantScope.tenant(910001L), status -> bus.publishIntegration(event));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM " + outbox);
        assertEquals(event.getEventId(), row.get("message_id"));
        assertEquals(event.getEventId(), row.get("event_id"));
        assertEquals("EVENT", row.get("message_kind"));
        assertEquals("MemberAccepted", row.get("message_type"));
        assertEquals("MemberAccepted", row.get("event_type"));
        assertEquals("1.2.0", row.get("schema_version"), "the version the manifest declares");
        assertEquals("CRITICAL", row.get("reliability"));
        assertEquals(OWNER, row.get("producer_plugin_id"));
        assertEquals("TENANT", row.get("scope"));
        assertEquals(910001L, ((Number) row.get("tenant_id")).longValue());
        assertEquals("member-7", row.get("partition_key"));
        assertEquals(event.getEventId(), row.get("correlation_id"), "the event id when nothing else correlates it");
        assertTrue(((String) row.get("traceparent")).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01"));
        assertEquals("PENDING", row.get("status"));
        assertEquals(0, ((Number) row.get("attempt_count")).intValue());
        assertEquals(Map.of("memberId", 7, "role", "OWNER"), json.readValue(String.valueOf(row.get("payload")), Map.class));
    }

    @Test
    void aPlatformEventCarriesNoTenant() {
        PlatformMemberAcceptedEvent event = new PlatformMemberAcceptedEvent();

        inUnitOfWork(OWNER, false, TenantScope.none(), status -> bus.publishIntegration(event));

        Map<String, Object> row = jdbc.queryForMap("SELECT scope, tenant_id, partition_key FROM " + outbox);
        assertEquals(EventScope.PLATFORM.name(), row.get("scope"));
        assertNull(row.get("tenant_id"));
        assertEquals(event.getEventId(), row.get("partition_key"), "the event id when the event names no partition");
    }

    @Test
    void aRolledBackTransactionLeavesNoEvent() {
        inUnitOfWork(OWNER, false, TenantScope.tenant(910001L), status -> {
            bus.publishIntegration(new MemberAcceptedEvent("910001"));
            status.setRollbackOnly();
        });

        assertEquals(0, rows());
    }

    @Test
    void outsideTheOwnersUnitOfWorkNothingIsWritten() {
        MemberAcceptedEvent event = new MemberAcceptedEvent("910001");

        assertThrows(EventPublishException.class, () -> bus.publishIntegration(event), "no transaction");
        assertThrows(EventPublishException.class, () -> transactions.executeWithoutResult(
            status -> bus.publishIntegration(event)), "a transaction no owner opened");
        assertThrows(EventPublishException.class, () -> inUnitOfWork("other-owner", false, TenantScope.tenant(910001L),
            status -> bus.publishIntegration(event)), "another owner's unit of work");
        assertThrows(EventPublishException.class, () -> inUnitOfWork(OWNER, true, TenantScope.tenant(910001L),
            status -> bus.publishIntegration(event)), "a read-only unit of work");
        assertEquals(0, rows());
    }

    @Test
    void onlyDeclaredEventsArePublished() {
        assertThrows(EventPublishException.class, () -> inUnitOfWork(OWNER, false, TenantScope.none(),
            status -> bus.publishIntegration(new UndeclaredEvent())), "not under events.publishes");
        assertThrows(EventPublishException.class, () -> inUnitOfWork(OWNER, false, TenantScope.none(),
            status -> bus.publish(new DomainEvent() {
                @Override
                public String getAggregateId() {
                    return "member-7";
                }

                @Override
                public String getAggregateType() {
                    return "member";
                }
            })), "a domain event");
        assertEquals(0, rows());
    }

    @Test
    void aBestEffortEventLeavesAfterTheTransactionCommitsWithTheProducerTheRuntimeBinds() {
        // Kafka design §14 K21: no outbox row; the runtime's producer, not what the event says about itself.
        List<CanonicalOutboxMessage> sent = new ArrayList<>();
        OwnerOutboxEventBus bestEffortBus = busSendingTo(sent::add);

        inUnitOfWork(OWNER, true, TenantScope.none(), status -> {
            bestEffortBus.publishIntegration(new HeartbeatEvent());
            assertEquals(List.of(), sent, "not before the commit");
        });

        assertEquals(1, sent.size());
        CanonicalOutboxMessage message = sent.get(0);
        assertEquals("Heartbeat", message.messageType());
        assertEquals("BEST_EFFORT", message.reliability());
        assertEquals(OWNER, message.producerPluginId());
        assertEquals("PLATFORM", message.scope());
        assertEquals(0, rows());
    }

    @Test
    void aBestEffortEventOfARolledBackTransactionNeverLeaves() {
        List<CanonicalOutboxMessage> sent = new ArrayList<>();
        OwnerOutboxEventBus bestEffortBus = busSendingTo(sent::add);

        inUnitOfWork(OWNER, false, TenantScope.none(), status -> {
            bestEffortBus.publishIntegration(new HeartbeatEvent());
            status.setRollbackOnly();
        });

        assertEquals(List.of(), sent);
    }

    @Test
    void aPlatformBestEffortEventOutsideAUnitOfWorkLeavesAtOnce() {
        List<CanonicalOutboxMessage> sent = new ArrayList<>();

        busSendingTo(sent::add).publishIntegration(new HeartbeatEvent());

        assertEquals(1, sent.size());
    }

    @Test
    void anOwnerWithoutAnOutboxPublishesOnlyBestEffortEvents() {
        List<PluginManifest.EventPublish> publications = new PluginManifestLoader()
            .loadActiveFromClasspath(OwnerOutboxEventBusTest.class).getEvents().getPublishes();
        OwnerOutboxEventBus withoutOutbox = new OwnerOutboxEventBus(OWNER, null, publications, dataSource, json,
            Clock.fixed(NOW, ZoneOffset.UTC), () -> message -> { });

        assertThrows(EventPublishException.class, () -> inUnitOfWork(OWNER, false, TenantScope.tenant(910001L),
            status -> withoutOutbox.publishIntegration(new MemberAcceptedEvent("910001"))));
        assertEquals(0, rows());
    }

    private OwnerOutboxEventBus busSendingTo(BestEffortEventSender sender) {
        List<PluginManifest.EventPublish> publications = new PluginManifestLoader()
            .loadActiveFromClasspath(OwnerOutboxEventBusTest.class).getEvents().getPublishes();
        return new OwnerOutboxEventBus(OWNER, outbox, publications, dataSource, json, Clock.fixed(NOW, ZoneOffset.UTC),
            () -> sender);
    }

    @Test
    void aTenantEventNamesOnlyATenantTheTransactionReaches() {
        assertThrows(EventPublishException.class, () -> inUnitOfWork(OWNER, false, TenantScope.tenant(910001L),
            status -> bus.publishIntegration(new MemberAcceptedEvent("910002"))), "another tenant");
        assertThrows(EventPublishException.class, () -> inUnitOfWork(OWNER, false, TenantScope.none(),
            status -> bus.publishIntegration(new MemberAcceptedEvent("910001"))), "a transaction that reaches no tenant");
        assertEquals(0, rows());
    }

    @Test
    void anEventWithoutAPayloadIsRefused() {
        IntegrationEvent bare = new IntegrationEvent(OWNER) {
            @Override
            public String eventTypeId() {
                return "MemberAccepted";
            }

            @Override
            public String getRoutingKey() {
                return "bare";
            }
        };

        assertThrows(EventPublishException.class, () -> inUnitOfWork(OWNER, false, TenantScope.none(),
            status -> bus.publishIntegration(bare)));
        assertEquals(0, rows());
    }

    /** Runs the work in a transaction bound to {@code owner}, as an owner unit of work binds it. */
    private void inUnitOfWork(
            String owner, boolean readOnly, TenantScope scope, java.util.function.Consumer<TransactionStatus> work) {
        transactions.executeWithoutResult(status -> {
            OwnerTransactionScope.open(owner, readOnly, status, new TenantScopeState(scope, OwnerTenantTables.none(owner)));
            work.accept(status);
        });
    }

    private int rows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + outbox, Integer.class);
    }
}
