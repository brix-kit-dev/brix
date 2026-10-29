package io.brix.platform.audit.server.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;

import io.brix.platform.audit.api.PlatformAuditActions;
import io.brix.platform.audit.api.PlatformAuditWriters;
import io.brix.platform.audit.core.entity.PlatformAuditLog;
import io.brix.platform.audit.core.repository.PlatformAuditLogRepository;
import io.brix.platform.audit.core.service.PlatformAuditService;
import io.brix.platform.audit.internal.PlatformAuditEntryView;
import io.brix.platform.audit.server.PlatformAuditPlugin;
import io.runtime.orchestrator.event.EventSubscribers;
import io.runtime.orchestrator.event.InProcessEventTransport;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxTransportException;
import io.runtime.orchestrator.plugin.ClasspathPluginRuntimeDescriptorResolver;
import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.orchestrator.plugin.PluginRuntimeManager.BoundEventHandler;
import io.runtime.sdk.capability.IdGeneratorCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.UnitOfWork;

/**
 * The identity audit fact chain into platform-audit (P4 design §4.2, §5 P4c): an outbox row as
 * platform-identity's owner outbox bus writes it is delivered by the runtime's in-process transport
 * through the manifest subscription {@code identity-audit-fact}.
 *
 * <p>The runtime records {@code (handlerId, eventId)} in {@code platform_audit_inbox} in the same unit of
 * work as the record (root BRX-RS-MESSAGE-025). Its JDBC inbox store belongs to the persistence adapter,
 * so this test writes the receipt with the same statements; the end-to-end environment runs the
 * adapter's own store.</p>
 */
@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:platform_audit_inbox;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PlatformAuditIdentityFactInboxE2ETest.JpaConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PlatformAuditIdentityFactInboxE2ETest {

    private static final String OWNER = "platform-audit";
    private static final String PUBLISHER = "platform-identity";
    private static final String EVENT_TYPE = "IdentityAuditFactRecorded";
    private static final OffsetDateTime OCCURRED_AT = OffsetDateTime.of(2026, 10, 29, 8, 0, 0, 0, ZoneOffset.UTC);
    private static final PluginRuntimeDescriptor DESCRIPTOR = new ClasspathPluginRuntimeDescriptorResolver()
        .resolve(new PlatformAuditPlugin())
        .orElseThrow();

    @Autowired
    private PlatformAuditLogRepository records;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    private final ObjectMapper json = new ObjectMapper();
    private final AtomicLong ids = new AtomicLong(374829105938497600L);
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS platform_audit_inbox (
                handler_id     VARCHAR(128) NOT NULL,
                message_id     VARCHAR(64)  NOT NULL,
                message_kind   VARCHAR(20)  NOT NULL,
                message_type   VARCHAR(128) NOT NULL,
                schema_version VARCHAR(16)  NOT NULL,
                tenant_id      BIGINT,
                status         VARCHAR(20)  NOT NULL,
                processed_at   TIMESTAMP WITH TIME ZONE NOT NULL,
                created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
                CONSTRAINT pk_platform_audit_inbox PRIMARY KEY (handler_id, message_id))""");
        jdbc.update("DELETE FROM platform_audit_inbox");
        records.deleteAll();
    }

    @Test
    void anIdentityFactFromTheIdentityOutboxBecomesOneOwnerRecord() {
        CanonicalOutboxMessage message = identityFact(PlatformAuditActions.IDENTITY_ACTIVATED);

        transport().publish(message);

        assertEquals(List.of(Map.of(
                "handler_id", PlatformAuditPlugin.HANDLER_IDENTITY_AUDIT_FACT,
                "message_id", message.eventId(),
                "message_type", EVENT_TYPE)),
            jdbc.queryForList("SELECT handler_id, message_id, message_type FROM platform_audit_inbox"));
        List<PlatformAuditLog> rows = records.findAll();
        assertEquals(1, rows.size());
        PlatformAuditEntryView row = rows.get(0).toView();
        assertEquals(PlatformAuditActions.IDENTITY_ACTIVATED, row.action());
        assertEquals(7L, row.actorIdentityId());
        assertEquals("platform-identity", row.executionIdentity());
        assertEquals("IDENTITY", row.targetType());
        assertEquals("7", row.targetId());
        assertEquals("tenant-first-owner-setup", row.reasonCode());
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", row.traceId());
        assertEquals(PlatformAuditWriters.OWNER, row.writer());
        assertEquals(OCCURRED_AT.toInstant(), row.createdAt(), "the record keeps the time of the fact");
    }

    @Test
    void aRedeliveredFactIsRecordedOnce() {
        CanonicalOutboxMessage message = identityFact(PlatformAuditActions.TOTP_BOUND);
        InProcessEventTransport transport = transport();

        transport.publish(message);
        // The relay lost the outbox row's PUBLISHED mark after platform-audit committed, so it delivers
        // the same event again.
        transport.publish(redelivered(message));

        assertEquals(1, inboxRows());
        assertEquals(1, records.count());
    }

    @Test
    void aFactTheOwnerRefusesRollsBackItsReceiptSoTheRelayRetriesAndParksIt() {
        CanonicalOutboxMessage refused = identityFact(PlatformAuditActions.TENANT_CREATED);

        OutboxTransportException retry = assertThrows(OutboxTransportException.class, () -> transport().publish(refused));

        assertFalse(retry.permanent(), "the relay retries, then parks the fact where it is counted and alerted");
        assertEquals(0, inboxRows());
        assertEquals(0, records.count());
    }

    @Test
    void aPayloadWithoutItsRequiredFieldsIsParkedWithoutAReceipt() {
        CanonicalOutboxMessage incomplete = message(UUID.randomUUID().toString(),
            "{\"action\":\"IDENTITY_ACTIVATED\",\"targetType\":\"IDENTITY\"}");

        OutboxTransportException parked = assertThrows(OutboxTransportException.class,
            () -> transport().publish(incomplete));

        assertTrue(parked.permanent());
        assertEquals("EVENT_PAYLOAD_INVALID", parked.errorCode());
        assertEquals(0, inboxRows());
        assertEquals(0, records.count());
    }

    /** An outbox row as platform-identity's owner outbox bus writes it after the identity transaction. */
    private CanonicalOutboxMessage identityFact(String action) {
        String payload = json.writeValueAsString(Map.of(
            "action", action,
            "actorIdentityId", 7L,
            "executionIdentity", "platform-identity",
            "targetType", "IDENTITY",
            "targetId", "7",
            "affectedTenants", List.of(),
            "success", true,
            "reasonCode", "tenant-first-owner-setup",
            "traceId", "4bf92f3577b34da6a3ce929d0e0e4736"));
        return message(UUID.randomUUID().toString(), payload);
    }

    private static CanonicalOutboxMessage message(String eventId, String payload) {
        return new CanonicalOutboxMessage(eventId, eventId, "EVENT", EVENT_TYPE, "1.0.0", "CRITICAL", PUBLISHER,
            "PLATFORM", null, "IDENTITY:7", OCCURRED_AT, eventId, null,
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", null, payload, 1);
    }

    private static CanonicalOutboxMessage redelivered(CanonicalOutboxMessage message) {
        return new CanonicalOutboxMessage(message.messageId(), message.eventId(), message.messageKind(),
            message.messageType(), message.schemaVersion(), message.reliability(), message.producerPluginId(),
            message.scope(), message.tenantId(), message.partitionKey(), message.occurredAt(),
            message.correlationId(), message.causationId(), message.traceparent(), message.tracestate(),
            message.payload(), message.attemptCount() + 1);
    }

    /** The runtime's in-process delivery to the manifest's identity audit fact subscription. */
    private InProcessEventTransport transport() {
        PluginRuntimeDescriptor.EventSubscription subscription =
            DESCRIPTOR.eventSubscriptions().get(PlatformAuditPlugin.HANDLER_IDENTITY_AUDIT_FACT);
        BoundEventHandler bound = new BoundEventHandler(OWNER, subscription,
            new IdentityAuditFactHandler(new PlatformAuditService(records, new SequentialIds())),
            IdentityAuditFactPayload.class);
        EventSubscribers subscribers = new EventSubscribers() {
            @Override
            public boolean ready() {
                return true;
            }

            @Override
            public Optional<String> publisherOf(String eventType) {
                // platform-identity declares the event under events.publishes.
                return EVENT_TYPE.equals(eventType) ? Optional.of(PUBLISHER) : Optional.empty();
            }

            @Override
            public List<DeclaredSubscriber> subscribersOf(String eventType) {
                return subscription.eventType().equals(eventType)
                    ? List.of(new DeclaredSubscriber(OWNER, subscription, bound))
                    : List.of();
            }
        };
        UnitOfWork unitOfWork = new UnitOfWork() {
            private final TransactionTemplate readWrite = new TransactionTemplate(transactionManager);

            @Override
            public <T> T required(java.util.function.Supplier<T> work) {
                return readWrite.execute(status -> work.get());
            }

            @Override
            public <T> T readOnly(java.util.function.Supplier<T> work) {
                throw new UnsupportedOperationException("the delivery writes");
            }
        };
        PersistenceCapability persistence = new PersistenceCapability() {
            @Override
            public <R> R repository(Class<R> repositoryType) {
                throw new UnsupportedOperationException("the delivery opens units of work only");
            }

            @Override
            public UnitOfWork unitOfWork() {
                return unitOfWork;
            }
        };
        return new InProcessEventTransport(subscribers,
            owner -> OWNER.equals(owner) ? Optional.of(persistence) : Optional.empty(),
            this::insertProcessed, json, Clock.systemUTC(), Duration.ofSeconds(30));
    }

    /** The inbox receipt as the persistence adapter writes {@code data.inbox}, inside the delivery's transaction. */
    private boolean insertProcessed(String owner, String handlerId, CanonicalOutboxMessage message) {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive(), "the receipt joins the owner's transaction");
        Integer existing = jdbc.queryForObject(
            "SELECT COUNT(*) FROM platform_audit_inbox WHERE handler_id = ? AND message_id = ?",
            Integer.class, handlerId, message.eventId());
        if (existing != null && existing > 0) {
            return false;
        }
        jdbc.update("INSERT INTO platform_audit_inbox (handler_id, message_id, message_kind, message_type, schema_version,"
                + " tenant_id, status, processed_at, created_at)"
                + " VALUES (?, ?, 'EVENT', ?, ?, ?, 'PROCESSED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            handlerId, message.eventId(), message.messageType(), message.schemaVersion(), message.tenantId());
        return true;
    }

    private int inboxRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM platform_audit_inbox", Integer.class);
    }

    /** Numeric ids, as the Host's generator produces them. */
    private final class SequentialIds implements IdGeneratorCapability {

        @Override
        public String generate() {
            return String.valueOf(ids.incrementAndGet());
        }

        @Override
        public String generate(String prefix) {
            throw new UnsupportedOperationException("the platform audit uses plain ids");
        }

        @Override
        public List<String> generateBatch(int count) {
            throw new UnsupportedOperationException("the platform audit uses one id per record");
        }

        @Override
        public List<String> generateBatch(String prefix, int count) {
            throw new UnsupportedOperationException("the platform audit uses one id per record");
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaRepositories(basePackages = "io.brix.platform.audit.core.repository")
    @EntityScan(basePackages = "io.brix.platform.audit.core.entity")
    static class JpaConfig {
    }
}
