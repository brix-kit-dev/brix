/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.outbox;

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
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;

import io.brix.platform.tenant.PlatformTenantPlugin;
import io.brix.platform.tenant.core.IdGenerator;
import io.brix.platform.tenant.entity.InstallationQuota;
import io.brix.platform.tenant.entity.PlatformTenantFirstOwnerProjection;
import io.brix.platform.tenant.entity.PlatformTenantOutbox;
import io.brix.platform.tenant.entity.Tenant;
import io.brix.platform.tenant.entity.TenantInvitation;
import io.brix.platform.tenant.enums.InvitationInviterType;
import io.brix.platform.tenant.enums.InvitationPurpose;
import io.brix.platform.tenant.enums.InvitationStatus;
import io.brix.platform.tenant.enums.TenantMemberType;
import io.brix.platform.tenant.event.TenantFirstOwnerAcceptedEvent;
import io.brix.platform.tenant.event.TenantFirstOwnerAcceptedPayload;
import io.brix.platform.tenant.event.TenantFirstOwnerAcceptedProjectionHandler;
import io.brix.platform.tenant.internal.AcceptFirstOwnerInvitationCommand;
import io.brix.platform.tenant.repository.BizUserProfileRepository;
import io.brix.platform.tenant.repository.InstallationQuotaRepository;
import io.brix.platform.tenant.repository.PlatformTenantFirstOwnerProjectionRepository;
import io.brix.platform.tenant.repository.PlatformTenantOutboxRepository;
import io.brix.platform.tenant.repository.TenantAuditLogRepository;
import io.brix.platform.tenant.repository.TenantInvitationRepository;
import io.brix.platform.tenant.repository.TenantMemberRepository;
import io.brix.platform.tenant.repository.TenantRepository;
import io.brix.platform.tenant.security.SecretHashing;
import io.brix.platform.tenant.service.FirstOwnerInvitationService;
import io.brix.platform.tenant.service.FirstOwnerProjectionWriter;
import io.brix.platform.tenant.service.JpaFirstOwnerProjectionWriter;
import io.brix.platform.tenant.support.TransactionTemplateUnitOfWork;
import io.runtime.orchestrator.event.EventSubscribers;
import io.runtime.orchestrator.event.InProcessEventTransport;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxTransportException;
import io.runtime.orchestrator.plugin.ClasspathPluginRuntimeDescriptorResolver;
import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.orchestrator.plugin.PluginRuntimeManager.BoundEventHandler;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.FirstOwnerInviteeIdentitySetupCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.UnitOfWork;
import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.IntegrationEvent;

/**
 * The FIRST_OWNER chain inside platform-tenant: the acceptance commits an outbox row, and the
 * runtime's in-process delivery consumes it into the projection through the manifest subscription
 * {@code tenant-first-owner-projection}.
 *
 * <p>The runtime records {@code (handlerId, eventId)} in {@code platform_tenant_inbox} in the same
 * unit of work as the handler (P4 design §7.3). Its JDBC inbox store belongs to the persistence
 * adapter, so this test writes the receipt with the same statements; the PostgreSQL release gate
 * runs the adapter's own store.</p>
 */
@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:platform_tenant_inbox;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PlatformTenantPersistentInboxE2ETest.JpaConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PlatformTenantPersistentInboxE2ETest {

    private static final String OWNER = "platform-tenant";
    private static final PluginRuntimeDescriptor DESCRIPTOR = new ClasspathPluginRuntimeDescriptorResolver()
        .resolve(new PlatformTenantPlugin())
        .orElseThrow();

    @Autowired
    private PlatformTenantFirstOwnerProjectionRepository projectionRepository;

    @Autowired
    private PlatformTenantOutboxRepository outboxRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantInvitationRepository invitationRepository;

    @Autowired
    private TenantMemberRepository tenantMemberRepository;

    @Autowired
    private InstallationQuotaRepository installationQuotaRepository;

    @Autowired
    private BizUserProfileRepository profileRepository;

    @Autowired
    private TenantAuditLogRepository auditLogRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS platform_tenant_inbox (
                handler_id     VARCHAR(128) NOT NULL,
                message_id     VARCHAR(64)  NOT NULL,
                message_kind   VARCHAR(20)  NOT NULL,
                message_type   VARCHAR(128) NOT NULL,
                schema_version VARCHAR(16)  NOT NULL,
                tenant_id      BIGINT,
                status         VARCHAR(20)  NOT NULL,
                processed_at   TIMESTAMP WITH TIME ZONE NOT NULL,
                created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
                CONSTRAINT pk_platform_tenant_inbox PRIMARY KEY (handler_id, message_id))""");
        jdbc.update("DELETE FROM platform_tenant_inbox");
        projectionRepository.deleteAll();
        outboxRepository.deleteAll();
        auditLogRepository.deleteAll();
        profileRepository.deleteAll();
        tenantMemberRepository.deleteAll();
        invitationRepository.deleteAll();
        installationQuotaRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void firstOwnerAcceptanceOutboxCommitCanBeConsumedIntoPersistentSideEffect() {
        CanonicalOutboxMessage message = acceptedFirstOwner();

        transport(projectionWriter()).publish(message);

        TenantFirstOwnerAcceptedPayload payload =
            objectMapper.readValue(message.payload(), TenantFirstOwnerAcceptedPayload.class);
        assertEquals(List.of(Map.of(
                "handler_id", PlatformTenantPlugin.HANDLER_FIRST_OWNER_PROJECTION,
                "message_id", message.eventId(),
                "message_type", TenantFirstOwnerAcceptedEvent.EVENT_TYPE,
                "tenant_id", 110L)),
            jdbc.queryForList("SELECT handler_id, message_id, message_type, tenant_id FROM platform_tenant_inbox"));
        PlatformTenantFirstOwnerProjection projection = projectionRepository.findById(110L).orElseThrow();
        assertEquals(message.eventId(), projection.getMessageId());
        assertEquals(payload.memberId(), projection.getOwnerMemberId());
        assertEquals(payload.profileId(), projection.getProfileId());
        assertEquals(410L, projection.getInvitationId());
    }

    @Test
    void duplicateDeliveryAfterAckLossDoesNotRepeatSideEffect() {
        CanonicalOutboxMessage message = acceptedFirstOwner();
        InProcessEventTransport transport = transport(projectionWriter());

        transport.publish(message);
        // The relay lost the outbox row's PUBLISHED mark after the subscriber committed, so it
        // delivers the same event again.
        transport.publish(redelivered(message));

        assertEquals(1, inboxRows());
        assertEquals(1, projectionRepository.count());
    }

    @Test
    void handlerFailureRollsBackInboxAndProjectionSoRetryCanConsume() {
        CanonicalOutboxMessage message = acceptedFirstOwner();
        FirstOwnerProjectionWriter failingWriter = (eventId, payload) -> {
            projectionWriter().write(eventId, payload);
            throw new IllegalStateException("projection-writer-failed");
        };

        OutboxTransportException retry = assertThrows(OutboxTransportException.class,
            () -> transport(failingWriter).publish(message));

        assertFalse(retry.permanent());
        assertEquals(0, inboxRows());
        assertEquals(0, projectionRepository.count());
        transport(projectionWriter()).publish(redelivered(message));
        assertEquals(1, inboxRows());
        assertEquals(1, projectionRepository.count());
    }

    @Test
    void concurrentDuplicateDeliveriesProduceOneSideEffect() throws Exception {
        CanonicalOutboxMessage message = acceptedFirstOwner();
        InProcessEventTransport transport = transport(projectionWriter());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        // Each delivery opens its own platform-tenant unit of work. The inbox key lets one commit;
        // the other either finds the receipt or fails on the key and waits for the relay's retry.
        Callable<Void> delivery = () -> {
            ready.countDown();
            start.await();
            transport.publish(message);
            return null;
        };
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> first = executor.submit(delivery);
            Future<Void> second = executor.submit(delivery);
            ready.await();
            start.countDown();

            int delivered = delivered(first) + delivered(second);

            assertTrue(delivered >= 1, "at least one delivery commits");
            assertEquals(1, inboxRows());
            assertEquals(1, projectionRepository.count());
        } finally {
            executor.shutdownNow();
        }
    }

    /** Accepts a FIRST_OWNER invitation and returns its outbox row as the relay claims it. */
    private CanonicalOutboxMessage acceptedFirstOwner() {
        String rawToken = "raw-first-owner-token";
        seedPendingTenantAndInvitation(110L, 410L, rawToken);
        committed(() -> firstOwnerInvitationService().accept(new AcceptFirstOwnerInvitationCommand(rawToken, 900L)));
        PlatformTenantOutbox row = outboxRepository.findAll().get(0);
        return new CanonicalOutboxMessage(row.getMessageId(), row.getEventId(), row.getMessageKind(),
            row.getMessageType(), row.getSchemaVersion(), row.getReliability(), row.getProducerPluginId(),
            row.getScope(), row.getTenantId(), row.getPartitionKey(), row.getOccurredAt(), row.getCorrelationId(),
            row.getCausationId(), row.getTraceparent(), row.getTracestate(), row.getPayload(),
            row.getAttemptCount() + 1);
    }

    private static CanonicalOutboxMessage redelivered(CanonicalOutboxMessage message) {
        return new CanonicalOutboxMessage(message.messageId(), message.eventId(), message.messageKind(),
            message.messageType(), message.schemaVersion(), message.reliability(), message.producerPluginId(),
            message.scope(), message.tenantId(), message.partitionKey(), message.occurredAt(),
            message.correlationId(), message.causationId(), message.traceparent(), message.tracestate(),
            message.payload(), message.attemptCount() + 1);
    }

    /** The runtime's in-process delivery to the manifest's projection subscription. */
    private InProcessEventTransport transport(FirstOwnerProjectionWriter writer) {
        PluginRuntimeDescriptor.EventSubscription subscription =
            DESCRIPTOR.eventSubscriptions().get(PlatformTenantPlugin.HANDLER_FIRST_OWNER_PROJECTION);
        BoundEventHandler bound = new BoundEventHandler(OWNER, subscription,
            new TenantFirstOwnerAcceptedProjectionHandler(writer), TenantFirstOwnerAcceptedPayload.class);
        EventSubscribers subscribers = new EventSubscribers() {
            @Override
            public boolean ready() {
                return true;
            }

            @Override
            public Optional<String> publisherOf(String eventType) {
                return DESCRIPTOR.eventPublications().containsKey(eventType) ? Optional.of(OWNER) : Optional.empty();
            }

            @Override
            public List<DeclaredSubscriber> subscribersOf(String eventType) {
                return subscription.eventType().equals(eventType)
                    ? List.of(new DeclaredSubscriber(OWNER, subscription, bound))
                    : List.of();
            }

            @Override
            public List<DeclaredSubscriber> subscriptions() {
                return List.of(new DeclaredSubscriber(OWNER, subscription, bound));
            }

            @Override
            public Set<String> publishedEventTypes() {
                return Set.of(subscription.eventType());
            }
        };
        UnitOfWork unitOfWork = new TransactionTemplateUnitOfWork(transactionManager);
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
            this::insertProcessed, objectMapper, Clock.systemUTC(), Duration.ofSeconds(30));
    }

    /** The inbox receipt as the persistence adapter writes {@code data.inbox}, inside the delivery's transaction. */
    private boolean insertProcessed(String owner, String handlerId, CanonicalOutboxMessage message) {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive(), "the receipt joins the owner's transaction");
        Integer existing = jdbc.queryForObject(
            "SELECT COUNT(*) FROM platform_tenant_inbox WHERE handler_id = ? AND message_id = ?",
            Integer.class, handlerId, message.eventId());
        if (existing != null && existing > 0) {
            return false;
        }
        jdbc.update("INSERT INTO platform_tenant_inbox (handler_id, message_id, message_kind, message_type, schema_version,"
                + " tenant_id, status, processed_at, created_at)"
                + " VALUES (?, ?, 'EVENT', ?, ?, ?, 'PROCESSED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            handlerId, message.eventId(), message.messageType(), message.schemaVersion(), message.tenantId());
        return true;
    }

    private int inboxRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM platform_tenant_inbox", Integer.class);
    }

    private static int delivered(Future<Void> delivery) throws InterruptedException {
        try {
            delivery.get();
            return 1;
        } catch (ExecutionException failed) {
            if (!(failed.getCause() instanceof OutboxTransportException retry)) {
                throw new AssertionError("the delivery failed outside the transport", failed.getCause());
            }
            assertFalse(retry.permanent(), "a delivery that loses the race waits for a retry");
            return 0;
        }
    }

    private FirstOwnerProjectionWriter projectionWriter() {
        return new JpaFirstOwnerProjectionWriter(projectionRepository);
    }

    private FirstOwnerInvitationService firstOwnerInvitationService() {
        return new FirstOwnerInvitationService(
            invitationRepository,
            tenantRepository,
            tenantMemberRepository,
            new StaticFirstOwnerInviteeIdentitySetupCapability("owner@example.com"),
            installationQuotaRepository,
            profileRepository,
            new OwnerOutbox(),
            auditLogRepository,
            Optional.empty(),
            new SequentialIdGenerator(),
            objectMapper,
            "https://console.example.test/invite");
    }

    /**
     * Writes the acceptance's outbox row with the envelope the persistence adapter's owner outbox bus writes in the
     * host (Kafka design §13 K9); that adapter is not on this module's classpath.
     */
    private final class OwnerOutbox implements EventBusCapability {

        @Override
        public void publish(DomainEvent event) {
            throw new UnsupportedOperationException("the owner outbox records integration events only");
        }

        @Override
        public void publishIntegration(IntegrationEvent event) {
            PluginRuntimeDescriptor.EventPublication publication = DESCRIPTOR.eventPublications().get(event.eventTypeId());
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            PlatformTenantOutbox row = new PlatformTenantOutbox();
            row.setMessageId(event.getEventId());
            row.setEventId(event.getEventId());
            row.setMessageKind("EVENT");
            row.setMessageType(event.eventTypeId());
            row.setEventType(event.eventTypeId());
            row.setSchemaVersion(publication.version());
            row.setReliability(publication.reliability().name());
            row.setProducerPluginId(OWNER);
            row.setScope(event.scope().name());
            row.setTenantId(Long.valueOf(event.getTenantId()));
            row.setPartitionKey(event.getRoutingKey());
            row.setOccurredAt(OffsetDateTime.ofInstant(event.getTimestamp(), ZoneOffset.UTC));
            row.setCorrelationId(event.getEventId());
            row.setPayload(objectMapper.writeValueAsString(event.payload()));
            row.setStatus("PENDING");
            row.setAvailableAt(now);
            row.setAttemptCount(0);
            row.setCreatedAt(now);
            outboxRepository.save(row);
        }
    }

    private void seedPendingTenantAndInvitation(Long tenantId, Long invitationId, String rawToken) {
        committed(() -> {
            Tenant tenant = new Tenant();
            tenant.setId(tenantId);
            tenant.setCode("tenant-" + tenantId);
            tenant.setName("Tenant " + tenantId);
            tenantRepository.save(tenant);
            installationQuotaRepository.save(new InstallationQuota(
                InstallationQuota.DEFAULT_INSTALLATION_ID,
                InstallationQuota.DEFAULT_TENANT_QUOTA,
                0));

            TenantInvitation invitation = new TenantInvitation();
            invitation.setId(invitationId);
            invitation.setTenantId(tenantId);
            invitation.setTargetType(TenantInvitation.InvitationTargetType.MEMBER);
            invitation.setTargetRole(TenantMemberType.OWNER);
            invitation.setInvitationPurpose(InvitationPurpose.FIRST_OWNER);
            invitation.setInviterType(InvitationInviterType.PLATFORM_ADMIN);
            invitation.setPlatformOperatorRef("platform-identity:77");
            invitation.setInviteeEmail("owner@example.com");
            invitation.setTokenHash(SecretHashing.sha256Base64Url(rawToken));
            invitation.setStatus(InvitationStatus.PENDING);
            invitation.setExpiresAt(OffsetDateTime.now().plusHours(1));
            invitationRepository.save(invitation);
        });
    }

    private <T> T committed(Callable<T> callback) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            try {
                return callback.call();
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    private void committed(Runnable callback) {
        committed(() -> {
            callback.run();
            return null;
        });
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaRepositories(basePackages = "io.brix.platform.tenant.repository")
    @EntityScan(basePackages = "io.brix.platform.tenant.entity")
    static class JpaConfig {
    }

    private static final class SequentialIdGenerator implements IdGenerator {
        private final AtomicLong next = new AtomicLong(1_000L);

        @Override
        public long nextId() {
            return next.incrementAndGet();
        }

        @Override
        public long parseTimestamp(long id) {
            return id;
        }

        @Override
        public long parseWorkerId(long id) {
            return 0;
        }

    }

    private record StaticFirstOwnerInviteeIdentitySetupCapability(String email)
            implements FirstOwnerInviteeIdentitySetupCapability {

        @Override
        public boolean sendSetupIfRequired(
                Long tenantId,
                String inviteeEmail,
                String platformOperatorRef,
                String locale) {
            return false;
        }

        @Override
        public String requireIdentityEmail(Long identityId) {
            return email;
        }

        @Override
        public String requireActiveIdentityEmail(Long identityId) {
            return email;
        }
    }
}
