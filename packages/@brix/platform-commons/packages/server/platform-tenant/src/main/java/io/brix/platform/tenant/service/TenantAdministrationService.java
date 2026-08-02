/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import io.brix.platform.tenant.dto.CreateTenantRequest;
import io.brix.platform.tenant.entity.Tenant;
import io.brix.platform.tenant.enums.TenantStatus;
import io.brix.platform.tenant.internal.AcceptFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.CreateFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.CreatePendingTenantCommand;
import io.brix.platform.tenant.internal.FirstOwnerAcceptanceResult;
import io.brix.platform.tenant.internal.FirstOwnerInvitationView;
import io.brix.platform.tenant.internal.InstallationQuotaView;
import io.brix.platform.tenant.internal.PlatformPageRequest;
import io.brix.platform.tenant.internal.PlatformPageView;
import io.brix.platform.tenant.internal.PlatformTenantView;
import io.brix.platform.tenant.internal.ResendFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.RevokeFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.TenantAdministration;
import io.brix.platform.tenant.internal.TenantAdministrationTenant;
import io.brix.platform.tenant.repository.TenantRepository;
import io.runtime.sdk.capability.TenantQuotaCapability.InstallationQuotaSnapshot;

/**
 * Data Owner implementation of the TenantAdministration internal contract.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public class TenantAdministrationService implements TenantAdministration {

    private static final int MAX_PAGE_SIZE = 200;

    private final TenantProvisioningService tenantProvisioningService;
    private final FirstOwnerInvitationService firstOwnerInvitationService;
    private final TenantRepository tenantRepository;
    private final TenantQuotaService tenantQuotaService;

    /**
     * Creates the administration service.
     *
     * @param tenantProvisioningService tenant provisioning application service
     * @param firstOwnerInvitationService FIRST_OWNER workflow service
     */
    public TenantAdministrationService(
            TenantProvisioningService tenantProvisioningService,
            FirstOwnerInvitationService firstOwnerInvitationService,
            TenantRepository tenantRepository,
            TenantQuotaService tenantQuotaService) {
        this.tenantProvisioningService = tenantProvisioningService;
        this.firstOwnerInvitationService = firstOwnerInvitationService;
        this.tenantRepository = tenantRepository;
        this.tenantQuotaService = tenantQuotaService;
    }

    @Override
    @Transactional
    public TenantAdministrationTenant createPendingTenant(CreatePendingTenantCommand command) {
        Tenant tenant = tenantProvisioningService.createTenant(
            CreateTenantRequest.builder()
                .code(command.code())
                .name(command.name())
                .build());
        return new TenantAdministrationTenant(
            tenant.getId(),
            tenant.getCode(),
            tenant.getName(),
            tenant.getStatus().name());
    }

    @Override
    @Transactional(readOnly = true)
    public PlatformPageView<PlatformTenantView> listTenants(PlatformPageRequest request) {
        InstallationQuotaSnapshot quota = tenantQuotaService.getInstallationQuota();
        Page<Tenant> page = findPlatformAdminTenantPage(
            tenantStatus(request.status()),
            searchTerm(request.query()),
            pageable(request, tenantSortProperty(request.sortBy())));
        return new PlatformPageView<>(
            page.getContent().stream()
                .map(tenant -> toView(tenant, quota))
                .toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isFirst(),
            page.isLast());
    }

    @Override
    @Transactional(readOnly = true)
    public InstallationQuotaView installationQuota() {
        InstallationQuotaSnapshot quota = tenantQuotaService.getInstallationQuota();
        return new InstallationQuotaView(
            quota.installationId(),
            quota.quota(),
            quota.used(),
            quota.licenseStatus(),
            quota.expiresAt(),
            quota.canCreateTenant(),
            quota.refusalReason(),
            quota.updatedAt());
    }

    private Page<Tenant> findPlatformAdminTenantPage(
            TenantStatus status,
            String term,
            Pageable pageable) {
        if (term == null) {
            return status == null
                    ? tenantRepository.findAll(pageable)
                    : tenantRepository.findPlatformAdminPageByStatus(status, pageable);
        }
        return status == null
                ? tenantRepository.findPlatformAdminPageByTerm(term, pageable)
                : tenantRepository.findPlatformAdminPageByStatusAndTerm(status, term, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FirstOwnerInvitationView> latestFirstOwnerInvitation(Long tenantId) {
        return firstOwnerInvitationService.latest(tenantId);
    }

    @Override
    public FirstOwnerInvitationView createFirstOwnerInvitation(CreateFirstOwnerInvitationCommand command) {
        return firstOwnerInvitationService.create(command);
    }

    @Override
    public FirstOwnerInvitationView resendFirstOwnerInvitation(ResendFirstOwnerInvitationCommand command) {
        return firstOwnerInvitationService.resend(command);
    }

    @Override
    public void revokeFirstOwnerInvitation(RevokeFirstOwnerInvitationCommand command) {
        firstOwnerInvitationService.revoke(command);
    }

    @Override
    public FirstOwnerAcceptanceResult acceptFirstOwnerInvitation(AcceptFirstOwnerInvitationCommand command) {
        return firstOwnerInvitationService.accept(command);
    }

    private static PlatformTenantView toView(Tenant tenant, InstallationQuotaSnapshot quota) {
        String status = tenant.getStatus() == null ? null : tenant.getStatus().name();
        String theme = tenant.getDefaultTheme() == null ? null : tenant.getDefaultTheme().name();
        return new PlatformTenantView(
            tenant.getId(),
            tenant.getCode(),
            tenant.getName(),
            status,
            tenant.getCreatedAt(),
            tenant.getUpdatedAt(),
            quota.used(),
            quota.quota(),
            quota.licenseStatus(),
            tenant.getDefaultLocale(),
            tenant.getDefaultTimezone(),
            theme);
    }

    private static Pageable pageable(PlatformPageRequest request, String sortProperty) {
        int page = Math.max(0, request.page());
        int size = Math.max(1, Math.min(MAX_PAGE_SIZE, request.size()));
        Sort.Direction direction = request.descending() ? Sort.Direction.DESC : Sort.Direction.ASC;
        return org.springframework.data.domain.PageRequest.of(page, size, Sort.by(direction, sortProperty));
    }

    private static String tenantSortProperty(String requested) {
        if (requested == null || requested.isBlank()) {
            return "createdAt";
        }
        return switch (requested) {
            case "tenantId", "id" -> "id";
            case "code" -> "code";
            case "name" -> "name";
            case "status" -> "status";
            case "updatedAt" -> "updatedAt";
            case "createdAt" -> "createdAt";
            default -> "createdAt";
        };
    }

    private static String searchTerm(String query) {
        return query == null || query.isBlank() ? null : query.trim();
    }

    private static TenantStatus tenantStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return TenantStatus.valueOf(value);
    }
}
