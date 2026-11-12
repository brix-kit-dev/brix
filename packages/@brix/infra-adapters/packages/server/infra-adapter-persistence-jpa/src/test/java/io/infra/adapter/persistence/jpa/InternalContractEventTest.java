/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.ObjectMapper;

import io.infra.adapter.persistence.jpa.tenancy.P0Note;
import io.infra.adapter.persistence.jpa.tenancy.P0Repositories.NoteRepository;
import io.runtime.manifest.loader.PluginManifestLoader;
import io.runtime.manifest.model.PluginManifest;
import io.runtime.orchestrator.capability.DefaultCapabilityRegistry;
import io.runtime.orchestrator.internalcontract.InternalContractBinder;
import io.runtime.orchestrator.operational.OperationalModuleDescriptor;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.EventPublishException;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.TenantTarget;
import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.IntegrationEvent;

/**
 * A plugin's internal contract publishes through the plugin's own event bus (standard-link plan D125; root
 * BRX-RS-HOST-020, HOST-022, DATA-008, MESSAGE-022): the runtime's binder hands the contract the owner's outbox bus, and
 * each call runs in the owner's real unit of work, so the event commits or rolls back with the call's write. On the
 * reference DCP (PostgreSQL) when {@code BRIX_P0_POSTGRES_URL}, {@code BRIX_P0_POSTGRES_USER} and
 * {@code BRIX_P0_POSTGRES_PASSWORD} point at a throwaway database, on H2 otherwise.
 */
class InternalContractEventTest {

    private static final String OWNER = TenantTestEnvironment.OWNER;
    private static final long TENANT = 910001L;

    private final String outbox = "contract_outbox_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private final RecordingBus hostBus = new RecordingBus();
    private TenantTestEnvironment environment;
    private JdbcTemplate jdbc;
    private NoteDesk desk;

    /** The owner's contract: an operator writes a note in a tenant, and the owner says so. */
    public interface NoteDesk {
        long write(WriteNote command);
    }

    /**
     * What the operator asks for: the tenant of the note, which the call's unit of work reaches, and the tenant the
     * event names.
     */
    public record WriteNote(Long tenantId, long noteId, long eventTenantId, boolean failAfterPublishing)
            implements TenantTarget {

        @Override
        public Long targetTenantId() {
            return tenantId;
        }
    }

    @BeforeEach
    void open() {
        environment = new TenantTestEnvironment();
        jdbc = new JdbcTemplate(environment.dataSource);
        boolean postgres = "postgresql".equals(environment.database);
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
        List<PluginManifest.EventPublish> publications = new PluginManifestLoader()
            .loadActiveFromClasspath(InternalContractEventTest.class).getEvents().getPublishes();
        EventBusCapability ownerBus = new OwnerOutboxEventBus(OWNER, outbox, publications, environment.dataSource,
            new ObjectMapper(), Clock.systemUTC());
        OwnerPersistenceProvider host = new OwnerPersistenceProvider() {
            @Override
            public Optional<PersistenceCapability> forOwner(String ownerModuleId) {
                return OWNER.equals(ownerModuleId) ? Optional.of(environment.persistence) : Optional.empty();
            }

            @Override
            public Optional<EventBusCapability> eventBusFor(String ownerModuleId) {
                return OWNER.equals(ownerModuleId) ? Optional.of(ownerBus) : Optional.empty();
            }
        };
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        registry.register(EventBusCapability.class, hostBus);
        InternalContractBinder binder = new InternalContractBinder(registry, registry, getClass().getClassLoader(), host);
        binder.bindPlugin(
            PluginRuntimeDescriptor.builder(OWNER)
                .version("3.2.0")
                .providedInternalContract("test.notes", NoteDesk.class.getName(), "1.0.0", "test.notes.provider", OWNER)
                .build(),
            bootstrap -> bootstrap.bind("test.notes", NoteDesk.class, context -> new Notes(
                context.requireOwnerCapability(PersistenceCapability.class),
                context.requireOwnerCapability(EventBusCapability.class))),
            Set.of(PersistenceCapability.class, EventBusCapability.class));
        binder.activateAndFreeze();
        desk = binder.require(new OperationalModuleDescriptor.RequiredInternalContract(
            "test.notes", NoteDesk.class.getName(), ">=1.0.0 <2.0.0", true, "test.notes.allowlist"), NoteDesk.class);
    }

    @AfterEach
    void close() {
        jdbc.execute("DROP TABLE " + outbox);
        environment.close();
    }

    @Test
    void theEventCommitsWithTheContractsWriteInTheOwnersOutbox() {
        desk.write(new WriteNote(TENANT, 1L, TENANT, false));

        Map<String, Object> row = jdbc.queryForMap("SELECT message_type, reliability, producer_plugin_id, scope, tenant_id,"
            + " status FROM " + outbox);
        assertEquals("MemberAccepted", row.get("message_type"));
        assertEquals("CRITICAL", row.get("reliability"));
        assertEquals(OWNER, row.get("producer_plugin_id"), "the runtime binds the producer");
        assertEquals("TENANT", row.get("scope"));
        assertEquals(TENANT, ((Number) row.get("tenant_id")).longValue());
        assertEquals("PENDING", row.get("status"), "the relay sends it after the commit");
        assertEquals(1L, notes());
        assertEquals(0, hostBus.published, "the Host's direct bus takes no part");
    }

    @Test
    void aContractThatFailsAfterPublishingLeavesNeitherItsWriteNorItsEvent() {
        IllegalStateException failed = assertThrows(IllegalStateException.class,
            () -> desk.write(new WriteNote(TENANT, 2L, TENANT, true)));

        assertEquals("the contract fails after it published", failed.getMessage());
        assertEquals(0, rows());
        assertEquals(0L, notes());
        assertEquals(0, hostBus.published);
    }

    @Test
    void theEventNamesOnlyTheTenantTheCommandNames() {
        // The call's unit of work reaches the tenant the command names, and no other (multi-tenant §10).
        assertThrows(EventPublishException.class, () -> desk.write(new WriteNote(TENANT, 3L, TENANT + 1, false)));

        assertEquals(0, rows());
        assertEquals(0L, notes());
    }

    private int rows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + outbox, Integer.class);
    }

    private long notes() {
        NoteRepository notes = environment.repository(NoteRepository.class);
        return environment.read(TenantTestEnvironment.tenants(TENANT), notes::count);
    }

    /** The owner's implementation, which knows nothing of transactions: the runtime runs each call in one. */
    private static final class Notes implements NoteDesk {

        private final PersistenceCapability persistence;
        private final EventBusCapability events;

        private Notes(PersistenceCapability persistence, EventBusCapability events) {
            this.persistence = persistence;
            this.events = events;
        }

        @Override
        public long write(WriteNote command) {
            persistence.repository(NoteRepository.class)
                .save(new P0Note(command.noteId(), command.tenantId(), null, "from the contract"));
            events.publishIntegration(new OwnerOutboxEventBusTest.MemberAcceptedEvent(
                String.valueOf(command.eventTenantId())));
            if (command.failAfterPublishing()) {
                throw new IllegalStateException("the contract fails after it published");
            }
            return command.noteId();
        }
    }

    /** The Host's direct bus: it must never see a contract's event. */
    private static final class RecordingBus implements EventBusCapability {

        private int published;

        @Override
        public void publish(DomainEvent event) {
            published++;
        }

        @Override
        public void publishIntegration(IntegrationEvent event) {
            published++;
        }
    }
}
