/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.service;

import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import io.brix.platform.tenant.dto.CreateTenantRequest;
import io.brix.platform.tenant.entity.Tenant;
import io.brix.platform.tenant.internal.AcceptFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.CreateFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.CreatePendingTenantCommand;
import io.brix.platform.tenant.internal.FirstOwnerAcceptanceResult;
import io.brix.platform.tenant.internal.FirstOwnerInvitationView;
import io.brix.platform.tenant.internal.ResendFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.RevokeFirstOwnerInvitationCommand;
import io.brix.platform.tenant.internal.TenantAdministration;
import io.brix.platform.tenant.internal.TenantAdministrationTenant;

/**
 * Data Owner implementation of the TenantAdministration internal contract.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public class TenantAdministrationService implements TenantAdministration {

    private final TenantProvisioningService tenantProvisioningService;
    private final FirstOwnerInvitationService firstOwnerInvitationService;

    /**
     * Creates the administration service.
     *
     * @param tenantProvisioningService tenant provisioning application service
     * @param firstOwnerInvitationService FIRST_OWNER workflow service
     */
    public TenantAdministrationService(
            TenantProvisioningService tenantProvisioningService,
            FirstOwnerInvitationService firstOwnerInvitationService) {
        this.tenantProvisioningService = tenantProvisioningService;
        this.firstOwnerInvitationService = firstOwnerInvitationService;
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
}
