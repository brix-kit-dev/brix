/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.brix.platform.tenant.core.IdGenerator;
import io.brix.platform.tenant.entity.BizUserProfile;
import io.brix.platform.tenant.entity.InstallationQuota;
import io.brix.platform.tenant.entity.Tenant;
import io.brix.platform.tenant.entity.TenantAuditLog;
import io.brix.platform.tenant.entity.TenantInvitation;
import io.brix.platform.tenant.entity.TenantInvitation.InvitationTargetType;
import io.brix.platform.tenant.entity.TenantMember;
import io.brix.platform.tenant.enums.InvitationInviterType;
import io.brix.platform.tenant.enums.InvitationPurpose;
import io.brix.platform.tenant.enums.InvitationStatus;
import io.brix.platform.tenant.enums.TenantMemberType;
import io.brix.platform.tenant.enums.TenantStatus;
import io.brix.platform.tenant.event.TenantFirstOwnerAcceptedEvent;
import io.brix.platform.tenant.exception.QuotaExceededException;
import io.brix.platform.tenant.internal.AcceptFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.CreateFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.FirstOwnerAcceptanceResult;
import io.brix.platform.tenant.internal.FirstOwnerInvitationView;
import io.brix.platform.tenant.internal.ResendFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.TenantAdministrationException;
import io.brix.platform.tenant.repository.BizUserProfileRepository;
import io.brix.platform.tenant.repository.InstallationQuotaRepository;
import io.brix.platform.tenant.repository.TenantAuditLogRepository;
import io.brix.platform.tenant.repository.TenantInvitationRepository;
import io.brix.platform.tenant.repository.TenantMemberRepository;
import io.brix.platform.tenant.repository.TenantRepository;
import io.brix.platform.tenant.security.SecretHashing;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.FirstOwnerInviteeIdentitySetupCapability;
import io.runtime.sdk.capability.NotificationCapability;
import io.runtime.sdk.capability.NotificationRequest;
import io.runtime.sdk.capability.NotificationTemplateKeys;
import io.runtime.sdk.event.IntegrationEvent;

@ExtendWith(MockitoExtension.class)
class FirstOwnerInvitationServiceTest {

    @Mock
    private TenantInvitationRepository invitationRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantMemberRepository tenantMemberRepository;

    @Mock
    private FirstOwnerInviteeIdentitySetupCapability inviteeIdentitySetupCapability;

    @Mock
    private InstallationQuotaRepository installationQuotaRepository;

    @Mock
    private BizUserProfileRepository profileRepository;

    @Mock
    private EventBusCapability eventBusCapability;

    @Mock
    private TenantAuditLogRepository auditLogRepository;

    @Mock
    private NotificationCapability notificationCapability;

    @Mock
    private IdGenerator idGenerator;

    private FirstOwnerInvitationService service;

    @BeforeEach
    void setUp() {
        service = new FirstOwnerInvitationService(
            invitationRepository,
            tenantRepository,
            tenantMemberRepository,
            inviteeIdentitySetupCapability,
            installationQuotaRepository,
            profileRepository,
            eventBusCapability,
            auditLogRepository,
            Optional.of(notificationCapability),
            idGenerator,
            new ObjectMapper(),
            "https://console.example.test/invite");
    }

    @Test
    void createDelegatesSetupDeliveryWhenInviteeIdentityRequiresSetup() {
        Tenant tenant = pendingTenant(100L);
        when(tenantRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(tenant));
        when(invitationRepository.findLatestByTenantAndPurposeForUpdate(
                100L, InvitationPurpose.FIRST_OWNER, InvitationStatus.PENDING, PageRequest.of(0, 1)))
            .thenReturn(java.util.List.of());
        when(inviteeIdentitySetupCapability.sendSetupIfRequired(
                100L, "Owner@Example.com", "platform-identity:9", "en-US"))
            .thenReturn(true);
        when(idGenerator.nextId()).thenReturn(200L);
        when(invitationRepository.save(any(TenantInvitation.class))).thenAnswer(inv -> inv.getArgument(0));

        FirstOwnerInvitationView view = service.create(new CreateFirstOwnerInvitationCommand(
            100L,
            "Owner@Example.com",
            "platform-identity:9",
            "en-US"));

        assertEquals(200L, view.id());
        assertEquals("owner@example.com", view.inviteeEmail());
        assertEquals("PENDING", view.status());
        ArgumentCaptor<TenantInvitation> saved = ArgumentCaptor.forClass(TenantInvitation.class);
        verify(invitationRepository).save(saved.capture());
        assertEquals("platform-identity:9", saved.getValue().getPlatformOperatorRef());
        assertEquals(InvitationInviterType.PLATFORM_ADMIN, saved.getValue().getInviterType());
        verify(inviteeIdentitySetupCapability).sendSetupIfRequired(
            100L,
            "Owner@Example.com",
            "platform-identity:9",
            "en-US");
        verify(notificationCapability, never()).send(any(NotificationRequest.class));
    }

    @Test
    void createSendsInvitationImmediatelyForActiveInviteeIdentity() {
        Tenant tenant = pendingTenant(100L);
        when(tenantRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(tenant));
        when(invitationRepository.findLatestByTenantAndPurposeForUpdate(
                100L, InvitationPurpose.FIRST_OWNER, InvitationStatus.PENDING, PageRequest.of(0, 1)))
            .thenReturn(java.util.List.of());
        when(inviteeIdentitySetupCapability.sendSetupIfRequired(
                100L, "Owner@Example.com", "platform-identity:9", "en-US"))
            .thenReturn(false);
        when(idGenerator.nextId()).thenReturn(200L);
        when(invitationRepository.save(any(TenantInvitation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(new CreateFirstOwnerInvitationCommand(
            100L,
            "Owner@Example.com",
            "platform-identity:9",
            "en-US"));

        ArgumentCaptor<NotificationRequest> notification = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationCapability).send(notification.capture());
        assertEquals(NotificationTemplateKeys.TENANT_OWNER_INVITATION_INITIAL, notification.getValue().templateKey());
        String inviteUrl = notification.getValue().variables().get("inviteUrl");
        assertTrue(inviteUrl.startsWith("https://console.example.test/invite?token="));
    }

    @Test
    void setupCompletionDeliveryReissuesInvitationTokenAndSendsInvitation() {
        String oldRawToken = "old-tenant-owner-token";
        TenantInvitation invitation = firstOwnerInvitation(200L, 100L, "owner@example.com", oldRawToken);
        String oldHash = invitation.getTokenHash();
        Tenant tenant = pendingTenant(100L);
        when(inviteeIdentitySetupCapability.requireActiveIdentityEmail(500L)).thenReturn("owner@example.com");
        when(invitationRepository.findPendingByInviteeEmailAndPurposeForUpdate(
                "owner@example.com",
                InvitationPurpose.FIRST_OWNER,
                InvitationStatus.PENDING))
            .thenReturn(java.util.List.of(invitation));
        when(tenantRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(tenant));
        when(tenantMemberRepository.existsActiveOwnerByTenantId(100L)).thenReturn(false);
        when(invitationRepository.save(any(TenantInvitation.class))).thenAnswer(inv -> inv.getArgument(0));

        java.util.List<FirstOwnerInvitationView> delivered = service.sendPendingInvitationsAfterSetup(500L);

        assertEquals(1, delivered.size());
        assertEquals(200L, delivered.get(0).id());
        assertNotEquals(oldHash, invitation.getTokenHash());
        ArgumentCaptor<NotificationRequest> notification = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationCapability).send(notification.capture());
        assertEquals(NotificationTemplateKeys.TENANT_OWNER_INVITATION_INITIAL, notification.getValue().templateKey());
        String inviteUrl = notification.getValue().variables().get("inviteUrl");
        assertTrue(inviteUrl.startsWith("https://console.example.test/invite?token="));
        assertFalse(inviteUrl.contains(oldRawToken));
    }

    @Test
    void resendRevokesCurrentInvitationAndReplacementExpiresInFuture() {
        String oldRawToken = "old-tenant-owner-token";
        Tenant tenant = pendingTenant(100L);
        TenantInvitation existing = firstOwnerInvitation(200L, 100L, "owner@example.com", oldRawToken);
        OffsetDateTime beforeResend = OffsetDateTime.now();

        when(tenantRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(tenant));
        when(invitationRepository.findLatestByTenantAndPurposeForUpdate(
                100L, InvitationPurpose.FIRST_OWNER, InvitationStatus.PENDING, PageRequest.of(0, 1)))
            .thenReturn(java.util.List.of(existing));
        when(idGenerator.nextId()).thenReturn(201L);
        when(invitationRepository.save(any(TenantInvitation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inviteeIdentitySetupCapability.sendSetupIfRequired(
                100L, "owner@example.com", "platform-identity:9", "en-US"))
            .thenReturn(false);

        FirstOwnerInvitationView replacement = service.resend(new ResendFirstOwnerInvitationCommand(
            100L,
            "platform-identity:9",
            "en-US"));

        assertEquals(InvitationStatus.REVOKED, existing.getStatus());
        assertEquals(201L, replacement.id());
        assertEquals("PENDING", replacement.status());
        assertTrue(replacement.expiresAt().isAfter(beforeResend.plusHours(23)));
        ArgumentCaptor<NotificationRequest> notification = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationCapability).send(notification.capture());
        assertEquals(NotificationTemplateKeys.TENANT_OWNER_INVITATION_INITIAL, notification.getValue().templateKey());
        String inviteUrl = notification.getValue().variables().get("inviteUrl");
        assertTrue(inviteUrl.startsWith("https://console.example.test/invite?token="));
        assertFalse(inviteUrl.contains(oldRawToken));
    }

    @Test
    void createRejectsConfiguredInviteBaseUrlWithTokenParameter() {
        service = new FirstOwnerInvitationService(
            invitationRepository,
            tenantRepository,
            tenantMemberRepository,
            inviteeIdentitySetupCapability,
            installationQuotaRepository,
            profileRepository,
            eventBusCapability,
            auditLogRepository,
            Optional.of(notificationCapability),
            idGenerator,
            new ObjectMapper(),
            "https://console.example.test/invite?token=caller");
        Tenant tenant = pendingTenant(100L);
        when(tenantRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(tenant));
        when(invitationRepository.findLatestByTenantAndPurposeForUpdate(
                100L, InvitationPurpose.FIRST_OWNER, InvitationStatus.PENDING, PageRequest.of(0, 1)))
            .thenReturn(java.util.List.of());
        when(inviteeIdentitySetupCapability.sendSetupIfRequired(
                100L, "Owner@Example.com", "platform-identity:9", "en-US"))
            .thenReturn(false);
        when(idGenerator.nextId()).thenReturn(200L);
        when(invitationRepository.save(any(TenantInvitation.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantAdministrationException failure = assertThrows(
            TenantAdministrationException.class,
            () -> service.create(new CreateFirstOwnerInvitationCommand(
                100L,
                "Owner@Example.com",
                "platform-identity:9",
                "en-US")));

        assertEquals("FIRST_OWNER_INVITE_BASE_URL_INVALID", failure.code());
    }

    @Test
    void acceptCreatesOwnerProfileActivatesTenantReservesQuotaAndPublishesOwnerEvent() {
        String rawToken = "tenant-owner-token";
        TenantInvitation invitation = firstOwnerInvitation(200L, 100L, "owner@example.com", rawToken);
        Tenant tenant = pendingTenant(100L);
        InstallationQuota quota = new InstallationQuota(InstallationQuota.DEFAULT_INSTALLATION_ID, 3, 0);

        when(invitationRepository.findByTokenHashForUpdate(SecretHashing.sha256Base64Url(rawToken)))
            .thenReturn(Optional.of(invitation));
        when(inviteeIdentitySetupCapability.requireIdentityEmail(500L)).thenReturn("Owner@Example.com");
        when(tenantRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(tenant));
        when(installationQuotaRepository.findByInstallationIdForUpdate(InstallationQuota.DEFAULT_INSTALLATION_ID))
            .thenReturn(Optional.of(quota));
        when(tenantMemberRepository.existsActiveOwnerByTenantId(100L)).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(300L, 400L, 600L);
        when(tenantMemberRepository.save(any(TenantMember.class))).thenAnswer(inv -> inv.getArgument(0));
        when(profileRepository.save(any(BizUserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        FirstOwnerAcceptanceResult result = service.accept(new AcceptFirstOwnerInvitationCommand(
            rawToken,
            500L));

        assertEquals(100L, result.tenantId());
        assertEquals(300L, result.memberId());
        assertEquals(400L, result.profileId());
        assertEquals(TenantStatus.ACTIVE.name(), result.tenantStatus());
        assertEquals(1, quota.getUsed());
        assertEquals(InvitationStatus.ACCEPTED, invitation.getStatus());
        verify(tenantRepository).save(tenant);
        verify(invitationRepository).save(invitation);
        ArgumentCaptor<IntegrationEvent> event = ArgumentCaptor.forClass(IntegrationEvent.class);
        verify(eventBusCapability).publishIntegration(event.capture());
        TenantFirstOwnerAcceptedEvent accepted = (TenantFirstOwnerAcceptedEvent) event.getValue();
        assertEquals(100L, accepted.tenantIdValue());
        assertEquals(300L, accepted.memberId());
        assertEquals(400L, accepted.profileId());
        assertEquals(200L, accepted.invitationId());
        assertEquals("100", accepted.getRoutingKey());
        verify(auditLogRepository).save(any(TenantAuditLog.class));
    }

    @Test
    void acceptRejectsEmailMismatchWithoutSideEffects() {
        String rawToken = "tenant-owner-token";
        TenantInvitation invitation = firstOwnerInvitation(200L, 100L, "owner@example.com", rawToken);
        when(invitationRepository.findByTokenHashForUpdate(SecretHashing.sha256Base64Url(rawToken)))
            .thenReturn(Optional.of(invitation));
        when(inviteeIdentitySetupCapability.requireIdentityEmail(500L)).thenReturn("other@example.com");

        TenantAdministrationException failure = assertThrows(
            TenantAdministrationException.class,
            () -> service.accept(new AcceptFirstOwnerInvitationCommand(
                rawToken,
                500L)));

        assertEquals("FIRST_OWNER_INVITATION_EMAIL_MISMATCH", failure.code());
        verify(tenantRepository, never()).save(any(Tenant.class));
        verify(tenantMemberRepository, never()).save(any(TenantMember.class));
        verify(eventBusCapability, never()).publishIntegration(any(IntegrationEvent.class));
        verify(auditLogRepository, never()).save(any(TenantAuditLog.class));
    }

    @Test
    void acceptRejectsAlreadyAcceptedInvitationWithoutRepeatingSideEffects() {
        String rawToken = "tenant-owner-token";
        TenantInvitation invitation = firstOwnerInvitation(200L, 100L, "owner@example.com", rawToken);
        invitation.accept(OffsetDateTime.now().minusMinutes(1));
        when(invitationRepository.findByTokenHashForUpdate(SecretHashing.sha256Base64Url(rawToken)))
            .thenReturn(Optional.of(invitation));

        TenantAdministrationException failure = assertThrows(
            TenantAdministrationException.class,
            () -> service.accept(new AcceptFirstOwnerInvitationCommand(
                rawToken,
                500L)));

        assertEquals("FIRST_OWNER_INVITATION_NOT_ACCEPTABLE", failure.code());
        verify(tenantRepository, never()).save(any(Tenant.class));
        verify(tenantMemberRepository, never()).save(any(TenantMember.class));
        verify(profileRepository, never()).save(any(BizUserProfile.class));
        verify(installationQuotaRepository, never()).save(any(InstallationQuota.class));
        verify(eventBusCapability, never()).publishIntegration(any(IntegrationEvent.class));
        verify(auditLogRepository, never()).save(any(TenantAuditLog.class));
    }

    @Test
    void acceptRejectsQuotaExhaustionBeforeOwnerProfileOutboxSideEffects() {
        String rawToken = "tenant-owner-token";
        TenantInvitation invitation = firstOwnerInvitation(200L, 100L, "owner@example.com", rawToken);
        Tenant tenant = pendingTenant(100L);
        InstallationQuota quota = new InstallationQuota(InstallationQuota.DEFAULT_INSTALLATION_ID, 3, 3);

        when(invitationRepository.findByTokenHashForUpdate(SecretHashing.sha256Base64Url(rawToken)))
            .thenReturn(Optional.of(invitation));
        when(inviteeIdentitySetupCapability.requireIdentityEmail(500L)).thenReturn("owner@example.com");
        when(tenantRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(tenant));
        when(installationQuotaRepository.findByInstallationIdForUpdate(InstallationQuota.DEFAULT_INSTALLATION_ID))
            .thenReturn(Optional.of(quota));
        when(tenantMemberRepository.existsActiveOwnerByTenantId(100L)).thenReturn(false);

        assertThrows(QuotaExceededException.class, () -> service.accept(new AcceptFirstOwnerInvitationCommand(
            rawToken,
            500L)));

        assertEquals(3, quota.getUsed());
        verify(tenantMemberRepository, never()).save(any(TenantMember.class));
        verify(profileRepository, never()).save(any(BizUserProfile.class));
        verify(tenantRepository, never()).save(any(Tenant.class));
        verify(invitationRepository, never()).save(invitation);
        verify(eventBusCapability, never()).publishIntegration(any(IntegrationEvent.class));
        verify(auditLogRepository, never()).save(any(TenantAuditLog.class));
    }

    private static Tenant pendingTenant(Long tenantId) {
        Tenant tenant = new Tenant("acme", "Acme");
        tenant.setId(tenantId);
        tenant.setStatus(TenantStatus.PENDING_ACTIVATION);
        return tenant;
    }

    private static TenantInvitation firstOwnerInvitation(
            Long invitationId,
            Long tenantId,
            String email,
            String rawToken) {
        TenantInvitation invitation = new TenantInvitation();
        invitation.setId(invitationId);
        invitation.setTenantId(tenantId);
        invitation.setTargetType(InvitationTargetType.MEMBER);
        invitation.setTargetRole(TenantMemberType.OWNER);
        invitation.setInvitationPurpose(InvitationPurpose.FIRST_OWNER);
        invitation.setInviterType(InvitationInviterType.PLATFORM_ADMIN);
        invitation.setPlatformOperatorRef("platform-identity:9");
        invitation.setInviteeEmail(email);
        invitation.setTokenHash(SecretHashing.sha256Base64Url(rawToken));
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(OffsetDateTime.now().plusHours(1));
        return invitation;
    }
}
