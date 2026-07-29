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

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.brix.platform.tenant.core.IdGenerator;
import io.brix.platform.tenant.entity.InstallationQuota;
import io.brix.platform.tenant.entity.PlatformTenantFirstOwnerProjection;
import io.brix.platform.tenant.entity.PlatformTenantInbox;
import io.brix.platform.tenant.entity.PlatformTenantInboxId;
import io.brix.platform.tenant.entity.PlatformTenantOutbox;
import io.brix.platform.tenant.entity.Tenant;
import io.brix.platform.tenant.entity.TenantInvitation;
import io.brix.platform.tenant.enums.InvitationInviterType;
import io.brix.platform.tenant.enums.InvitationPurpose;
import io.brix.platform.tenant.enums.InvitationStatus;
import io.brix.platform.tenant.enums.TenantMemberType;
import io.brix.platform.tenant.event.TenantFirstOwnerAcceptedEvent;
import io.brix.platform.tenant.internal.AcceptFirstOwnerInvitationCommand;
import io.brix.platform.tenant.repository.BizUserProfileRepository;
import io.brix.platform.tenant.repository.InstallationQuotaRepository;
import io.brix.platform.tenant.repository.PlatformTenantFirstOwnerProjectionRepository;
import io.brix.platform.tenant.repository.PlatformTenantInboxRepository;
import io.brix.platform.tenant.repository.PlatformTenantOutboxRepository;
import io.brix.platform.tenant.repository.TenantAuditLogRepository;
import io.brix.platform.tenant.repository.TenantInvitationRepository;
import io.brix.platform.tenant.repository.TenantMemberRepository;
import io.brix.platform.tenant.repository.TenantRepository;
import io.brix.platform.tenant.security.SecretHashing;
import io.brix.platform.tenant.service.FirstOwnerInvitationService;
import io.brix.platform.tenant.service.FirstOwnerProjectionWriter;
import io.brix.platform.tenant.service.JpaFirstOwnerProjectionWriter;
import io.brix.platform.tenant.service.TenantFirstOwnerAcceptedProjectionService;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:platform_tenant_inbox;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PlatformTenantPersistentInboxE2ETest.JpaConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PlatformTenantPersistentInboxE2ETest {

    @Autowired
    private PlatformTenantInboxRepository inboxRepository;

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanDatabase() {
        projectionRepository.deleteAll();
        inboxRepository.deleteAll();
        outboxRepository.deleteAll();
        auditLogRepository.deleteAll();
        profileRepository.deleteAll();
        tenantMemberRepository.deleteAll();
        invitationRepository.deleteAll();
        installationQuotaRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void duplicateDeliveryAfterAckLossDoesNotRepeatSideEffect() {
        TenantFirstOwnerAcceptedProjectionService service = projectionService();
        TenantFirstOwnerAcceptedEvent event = new TenantFirstOwnerAcceptedEvent(100L, 200L, 300L, 400L);

        boolean first = committed(() -> service.handle(event));
        boolean replayAfterAckLoss = committed(() -> service.handle(event));

        assertTrue(first);
        assertFalse(replayAfterAckLoss);
        assertEquals(1, inboxRepository.count());
        assertEquals(1, projectionRepository.count());
    }

    @Test
    void handlerFailureRollsBackInboxAndProjectionSoRetryCanConsume() {
        TenantFirstOwnerAcceptedEvent event = new TenantFirstOwnerAcceptedEvent(101L, 201L, 301L, 401L);
        FirstOwnerProjectionWriter failingWriter = (messageId, payload) -> {
            projectionRepository.save(PlatformTenantFirstOwnerProjection.create(
                payload.tenantIdValue(),
                messageId,
                payload.memberId(),
                payload.profileId(),
                payload.invitationId()));
            throw new IllegalStateException("projection-writer-failed");
        };
        TenantFirstOwnerAcceptedProjectionService failingService =
            new TenantFirstOwnerAcceptedProjectionService(inboxRepository, failingWriter);

        assertThrows(IllegalStateException.class, () -> committed(() -> failingService.handle(event)));

        assertEquals(0, inboxRepository.count());
        assertEquals(0, projectionRepository.count());
        assertTrue(committed(() -> projectionService().handle(event)));
        assertEquals(1, inboxRepository.count());
        assertEquals(1, projectionRepository.count());
    }

    @Test
    void concurrentDuplicateDeliveriesProduceOneSideEffect() throws Exception {
        TenantFirstOwnerAcceptedProjectionService service = projectionService();
        TenantFirstOwnerAcceptedEvent event = new TenantFirstOwnerAcceptedEvent(102L, 202L, 302L, 402L);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> delivery = () -> {
            ready.countDown();
            start.await();
            return committed(() -> service.handle(event));
        };
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(delivery);
            Future<Boolean> second = executor.submit(delivery);
            ready.await();
            start.countDown();

            int handled = (first.get() ? 1 : 0) + (second.get() ? 1 : 0);

            assertEquals(1, handled);
            assertEquals(1, inboxRepository.count());
            assertEquals(1, projectionRepository.count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void firstOwnerAcceptanceOutboxCommitCanBeConsumedIntoPersistentSideEffect() {
        String rawToken = "raw-first-owner-token";
        seedPendingTenantAndInvitation(110L, 410L, rawToken);
        FirstOwnerInvitationService producer = firstOwnerInvitationService();

        committed(() -> producer.accept(new AcceptFirstOwnerInvitationCommand(
            rawToken,
            900L,
            "owner@example.com")));

        PlatformTenantOutbox outbox = outboxRepository.findAll().get(0);
        TenantFirstOwnerAcceptedEvent event = new TenantFirstOwnerAcceptedEvent(
            outbox.getTenantId(),
            1_001L,
            1_002L,
            410L);

        assertTrue(committed(() -> projectionService().handle(outbox.getMessageId(), event)));

        PlatformTenantInbox inbox = inboxRepository.findById(new PlatformTenantInboxId(
            TenantFirstOwnerAcceptedProjectionService.HANDLER_ID,
            outbox.getMessageId())).orElseThrow();
        assertEquals(TenantFirstOwnerAcceptedEvent.EVENT_TYPE, inbox.getMessageType());
        assertEquals(110L, inbox.getTenantId());
        assertEquals(1, projectionRepository.count());
    }

    private TenantFirstOwnerAcceptedProjectionService projectionService() {
        return new TenantFirstOwnerAcceptedProjectionService(
            inboxRepository,
            new JpaFirstOwnerProjectionWriter(projectionRepository));
    }

    private FirstOwnerInvitationService firstOwnerInvitationService() {
        return new FirstOwnerInvitationService(
            invitationRepository,
            tenantRepository,
            tenantMemberRepository,
            installationQuotaRepository,
            profileRepository,
            new PlatformTenantReliableEventBusCapability(outboxRepository, objectMapper),
            auditLogRepository,
            Optional.empty(),
            new SequentialIdGenerator(),
            objectMapper);
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
}
