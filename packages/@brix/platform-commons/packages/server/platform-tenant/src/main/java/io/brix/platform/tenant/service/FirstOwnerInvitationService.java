/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import io.brix.platform.tenant.internal.RevokeFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.TenantAdministrationException;
import io.brix.platform.tenant.repository.BizUserProfileRepository;
import io.brix.platform.tenant.repository.InstallationQuotaRepository;
import io.brix.platform.tenant.repository.TenantAuditLogRepository;
import io.brix.platform.tenant.repository.TenantInvitationRepository;
import io.brix.platform.tenant.repository.TenantMemberRepository;
import io.brix.platform.tenant.repository.TenantRepository;
import io.brix.platform.tenant.security.SecretHashing;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.NotificationCapability;
import io.runtime.sdk.capability.NotificationRequest;
import io.runtime.sdk.capability.NotificationTemplateKeys;

/**
 * FIRST_OWNER invitation workflow owned by {@code platform-tenant}.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public class FirstOwnerInvitationService {

    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final TenantInvitationRepository invitationRepository;
    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final InstallationQuotaRepository installationQuotaRepository;
    private final BizUserProfileRepository profileRepository;
    private final EventBusCapability eventBusCapability;
    private final TenantAuditLogRepository auditLogRepository;
    private final Optional<NotificationCapability> notificationCapability;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    /**
     * Creates the FIRST_OWNER invitation service.
     */
    public FirstOwnerInvitationService(
            TenantInvitationRepository invitationRepository,
            TenantRepository tenantRepository,
            TenantMemberRepository tenantMemberRepository,
            InstallationQuotaRepository installationQuotaRepository,
            BizUserProfileRepository profileRepository,
            EventBusCapability eventBusCapability,
            TenantAuditLogRepository auditLogRepository,
            Optional<NotificationCapability> notificationCapability,
            IdGenerator idGenerator,
            ObjectMapper objectMapper) {
        this.invitationRepository = invitationRepository;
        this.tenantRepository = tenantRepository;
        this.tenantMemberRepository = tenantMemberRepository;
        this.installationQuotaRepository = installationQuotaRepository;
        this.profileRepository = profileRepository;
        this.eventBusCapability = eventBusCapability;
        this.auditLogRepository = auditLogRepository;
        this.notificationCapability = notificationCapability;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates one pending FIRST_OWNER invitation and sends it through
     * NotificationCapability before the transaction commits.
     *
     * @param command create command
     * @return invitation view without token material
     */
    @Transactional
    public FirstOwnerInvitationView create(CreateFirstOwnerInvitationCommand command) {
        Tenant tenant = lockTenant(command.tenantId());
        requirePendingTenant(tenant);
        latestPendingFirstOwnerInvitation(
                command.tenantId(),
                InvitationPurpose.FIRST_OWNER)
            .ifPresent(existing -> {
                throw new TenantAdministrationException(
                    "FIRST_OWNER_INVITATION_EXISTS",
                    "A pending FIRST_OWNER invitation already exists");
            });
        TokenPair tokenPair = newTokenPair();
        TenantInvitation invitation = newFirstOwnerInvitation(
            command.tenantId(),
            command.inviteeEmail(),
            command.platformOperatorRef(),
            tokenPair.hash());
        invitation = invitationRepository.save(invitation);
        sendInvitation(command.tenantId(), command.inviteeEmail(), command.inviteBaseUrl(), tokenPair.raw(), command.locale());
        return view(invitation);
    }

    /**
     * Returns the latest FIRST_OWNER invitation without exposing token material.
     *
     * @param tenantId tenant identifier
     * @return latest invitation view when one exists
     */
    @Transactional(readOnly = true)
    public Optional<FirstOwnerInvitationView> latest(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantAdministrationException(
                "TENANT_NOT_FOUND",
                "Tenant not found"));
        return invitationRepository.findLatestByTenantAndPurpose(
                tenantId,
                InvitationPurpose.FIRST_OWNER,
                PageRequest.of(0, 1))
            .stream()
            .findFirst()
            .map(FirstOwnerInvitationService::view);
    }

    /**
     * Revokes the current pending FIRST_OWNER invitation and sends a replacement.
     *
     * @param command resend command
     * @return replacement invitation view without token material
     */
    @Transactional
    public FirstOwnerInvitationView resend(ResendFirstOwnerInvitationCommand command) {
        Tenant tenant = lockTenant(command.tenantId());
        requirePendingTenant(tenant);
        TenantInvitation existing = latestPendingFirstOwnerInvitation(
                command.tenantId(),
                InvitationPurpose.FIRST_OWNER)
            .orElseThrow(() -> new TenantAdministrationException(
                "FIRST_OWNER_INVITATION_MISSING",
                "No pending FIRST_OWNER invitation exists"));
        existing.revoke(OffsetDateTime.now());
        invitationRepository.save(existing);
        TokenPair tokenPair = newTokenPair();
        TenantInvitation replacement = newFirstOwnerInvitation(
            command.tenantId(),
            existing.getInviteeEmail(),
            command.platformOperatorRef(),
            tokenPair.hash());
        replacement = invitationRepository.save(replacement);
        sendInvitation(command.tenantId(), replacement.getInviteeEmail(), command.inviteBaseUrl(), tokenPair.raw(), command.locale());
        return view(replacement);
    }

    /**
     * Revokes a pending FIRST_OWNER invitation.
     *
     * @param command revoke command
     */
    @Transactional
    public void revoke(RevokeFirstOwnerInvitationCommand command) {
        TenantInvitation invitation = invitationRepository.findById(command.invitationId())
            .orElseThrow(() -> new TenantAdministrationException(
                "FIRST_OWNER_INVITATION_MISSING",
                "FIRST_OWNER invitation not found"));
        if (!invitation.getTenantId().equals(command.tenantId())
                || invitation.getInvitationPurpose() != InvitationPurpose.FIRST_OWNER
                || invitation.getStatus() != InvitationStatus.PENDING) {
            throw new TenantAdministrationException(
                "FIRST_OWNER_INVITATION_NOT_REVOKABLE",
                "FIRST_OWNER invitation is not revokable");
        }
        invitation.revoke(OffsetDateTime.now());
        invitationRepository.save(invitation);
    }

    /**
     * Accepts a FIRST_OWNER invitation in one local Data Owner transaction.
     *
     * @param command acceptance command
     * @return acceptance result
     */
    @Transactional
    public FirstOwnerAcceptanceResult accept(AcceptFirstOwnerInvitationCommand command) {
        OffsetDateTime now = OffsetDateTime.now();
        TenantInvitation invitation = invitationRepository.findByTokenHashForUpdate(
                SecretHashing.sha256Base64Url(command.invitationToken()))
            .orElseThrow(() -> new TenantAdministrationException(
                "FIRST_OWNER_INVITATION_INVALID",
                "FIRST_OWNER invitation is invalid"));
        validateAcceptanceInvitation(invitation, command, now);

        Tenant tenant = lockTenant(invitation.getTenantId());
        requirePendingTenant(tenant);
        InstallationQuota quota = lockInstallationQuota();
        if (tenantMemberRepository.existsActiveOwnerByTenantId(tenant.getId())) {
            throw new TenantAdministrationException(
                "FIRST_OWNER_ALREADY_EXISTS",
                "Tenant already has an active OWNER");
        }
        if (!quota.hasAvailableSlot()) {
            throw new QuotaExceededException("installationTenants", quota.getUsed(), quota.getQuota());
        }

        quota.reserveSlot();
        installationQuotaRepository.save(quota);

        TenantMember ownerMember = new TenantMember(
            tenant.getId(),
            command.identityId(),
            TenantMemberType.OWNER);
        ownerMember.setId(idGenerator.nextId());
        ownerMember = tenantMemberRepository.save(ownerMember);
        tenantMemberRepository.flush();

        BizUserProfile profile = new BizUserProfile();
        profile.setId(idGenerator.nextId());
        profile.setTenantId(tenant.getId());
        profile.setMemberId(ownerMember.getId());
        profile.setPreferences("{}");
        profile.setExtended("{}");
        profile = profileRepository.save(profile);

        tenant.activate();
        tenantRepository.save(tenant);
        invitation.accept(now);
        invitationRepository.save(invitation);
        publishFirstOwnerAccepted(invitation, ownerMember, profile);
        writeAudit(invitation, ownerMember, profile, now);

        return new FirstOwnerAcceptanceResult(
            tenant.getId(),
            ownerMember.getId(),
            profile.getId(),
            tenant.getStatus().name());
    }

    private TenantInvitation newFirstOwnerInvitation(
            Long tenantId,
            String inviteeEmail,
            String platformOperatorRef,
            String tokenHash) {
        TenantInvitation invitation = new TenantInvitation();
        invitation.setId(idGenerator.nextId());
        invitation.setTenantId(tenantId);
        invitation.setTargetType(InvitationTargetType.MEMBER);
        invitation.setTargetRole(TenantMemberType.OWNER);
        invitation.setInvitationPurpose(InvitationPurpose.FIRST_OWNER);
        invitation.setInviterType(InvitationInviterType.PLATFORM_ADMIN);
        invitation.setPlatformOperatorRef(platformOperatorRef);
        invitation.setInviteeEmail(normalizeEmail(inviteeEmail));
        invitation.setTokenHash(tokenHash);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(OffsetDateTime.now().plus(DEFAULT_TTL));
        return invitation;
    }

    private Tenant lockTenant(Long tenantId) {
        return tenantRepository.findByIdForUpdate(tenantId)
            .orElseThrow(() -> new TenantAdministrationException(
                "TENANT_NOT_FOUND",
                "Tenant not found"));
    }

    private InstallationQuota lockInstallationQuota() {
        return installationQuotaRepository
            .findByInstallationIdForUpdate(InstallationQuota.DEFAULT_INSTALLATION_ID)
            .orElseGet(() -> installationQuotaRepository.saveAndFlush(new InstallationQuota(
                InstallationQuota.DEFAULT_INSTALLATION_ID,
                InstallationQuota.DEFAULT_TENANT_QUOTA,
                0)));
    }

    private Optional<TenantInvitation> latestPendingFirstOwnerInvitation(Long tenantId, InvitationPurpose purpose) {
        return invitationRepository.findLatestByTenantAndPurposeForUpdate(
                tenantId,
                purpose,
                InvitationStatus.PENDING,
                PageRequest.of(0, 1))
            .stream()
            .findFirst();
    }

    private static void requirePendingTenant(Tenant tenant) {
        if (tenant.getStatus() != TenantStatus.PENDING_ACTIVATION) {
            throw new TenantAdministrationException(
                "TENANT_NOT_PENDING_ACTIVATION",
                "Tenant is not pending activation");
        }
    }

    private static void validateAcceptanceInvitation(
            TenantInvitation invitation,
            AcceptFirstOwnerInvitationCommand command,
            OffsetDateTime now) {
        if (invitation.getInvitationPurpose() != InvitationPurpose.FIRST_OWNER
                || invitation.getTargetRole() != TenantMemberType.OWNER
                || !invitation.isPendingNow(now)) {
            throw new TenantAdministrationException(
                "FIRST_OWNER_INVITATION_NOT_ACCEPTABLE",
                "FIRST_OWNER invitation is not acceptable");
        }
        if (!normalizeEmail(invitation.getInviteeEmail()).equals(normalizeEmail(command.identityEmail()))) {
            throw new TenantAdministrationException(
                "FIRST_OWNER_INVITATION_EMAIL_MISMATCH",
                "FIRST_OWNER invitation email does not match actor identity");
        }
    }

    private void sendInvitation(
            Long tenantId,
            String inviteeEmail,
            String inviteBaseUrl,
            String rawToken,
            String locale) {
        NotificationCapability notification = notificationCapability.orElseThrow(
            () -> new TenantAdministrationException(
                "NOTIFICATION_PROVIDER_MISSING",
                "NotificationCapability is required for FIRST_OWNER invitations"));
        String inviteUrl = UriComponentsBuilder.fromUriString(inviteBaseUrl)
            .queryParam("token", rawToken)
            .build(true)
            .toUriString();
        notification.send(new NotificationRequest(
            tenantId,
            inviteeEmail,
            NotificationTemplateKeys.TENANT_OWNER_INVITATION_INITIAL,
            locale,
            Map.of("inviteUrl", inviteUrl)));
    }

    private void publishFirstOwnerAccepted(
            TenantInvitation invitation,
            TenantMember ownerMember,
            BizUserProfile profile) {
        eventBusCapability.publishIntegration(new TenantFirstOwnerAcceptedEvent(
            invitation.getTenantId(),
            ownerMember.getId(),
            profile.getId(),
            invitation.getId()));
    }

    private void writeAudit(
            TenantInvitation invitation,
            TenantMember ownerMember,
            BizUserProfile profile,
            OffsetDateTime now) {
        TenantAuditLog audit = new TenantAuditLog();
        audit.setId(idGenerator.nextId());
        audit.setTenantId(invitation.getTenantId());
        audit.setActorRefId(ownerMember.getIdentityId());
        audit.setAction("FIRST_OWNER_ACCEPTED");
        audit.setResourceType("TENANT_INVITATION");
        audit.setResourceId(String.valueOf(invitation.getId()));
        audit.setDescription("FIRST_OWNER invitation accepted and tenant activated");
        audit.setContext(toJson(Map.of(
            "memberId", ownerMember.getId(),
            "profileId", profile.getId(),
            "tenantStatus", TenantStatus.ACTIVE.name())));
        audit.setSuccess(true);
        audit.setCreatedAt(now);
        auditLogRepository.save(audit);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new TenantAdministrationException(
                "TENANT_EVENT_SERIALIZATION_FAILED",
                "Failed to serialize platform-tenant event payload");
        }
    }

    private static FirstOwnerInvitationView view(TenantInvitation invitation) {
        return new FirstOwnerInvitationView(
            invitation.getId(),
            invitation.getTenantId(),
            invitation.getInviteeEmail(),
            invitation.getStatus().name(),
            invitation.getExpiresAt());
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static TokenPair newTokenPair() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new TokenPair(raw, SecretHashing.sha256Base64Url(raw));
    }

    private record TokenPair(String raw, String hash) {
    }
}
