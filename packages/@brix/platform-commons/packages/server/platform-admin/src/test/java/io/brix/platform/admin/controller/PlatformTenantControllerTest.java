package io.brix.platform.admin.controller;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import io.brix.platform.admin.dto.CreatePlatformTenantRequest;
import io.brix.platform.admin.dto.PlatformTenantDto;
import io.brix.platform.auth.context.SecurityContextHolder;
import io.brix.platform.tenant.dto.AuditEvent;
import io.brix.platform.tenant.entity.Tenant;
import io.brix.platform.tenant.enums.TenantStatus;
import io.brix.platform.tenant.repository.TenantRepository;
import io.brix.platform.tenant.service.AuditService;
import io.brix.platform.tenant.service.TenantProvisioningService;
import io.runtime.sdk.capability.TenantProvisioningCapability;
import io.runtime.sdk.capability.TenantProvisioningCapability.CreateTenantCommand;
import io.runtime.sdk.capability.TenantProvisioningCapability.TenantRecord;
import io.runtime.sdk.capability.TenantQuotaCapability;

class PlatformTenantControllerTest {

    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final TenantProvisioningService tenantProvisioningService = mock(TenantProvisioningService.class);
    private final SecurityContextHolder securityContextHolder = mock(SecurityContextHolder.class);
    private final TenantQuotaCapability tenantQuotaCapability = mock(TenantQuotaCapability.class);
    private final TenantProvisioningCapability tenantProvisioningCapability = mock(TenantProvisioningCapability.class);

    private final PlatformTenantController controller = new PlatformTenantController(
            tenantRepository,
            auditService,
            tenantProvisioningService,
            securityContextHolder,
            tenantQuotaCapability,
            tenantProvisioningCapability);

    @Test
    void createTenantDelegatesToProvisioningCapabilityAndKeepsControllerAtomic() {
        when(securityContextHolder.getCurrentUserId()).thenReturn(java.util.Optional.of("42"));
        when(tenantRepository.existsByCode("acme-corp")).thenReturn(false);
        when(tenantProvisioningCapability.createTenant(any(CreateTenantCommand.class)))
                .thenReturn(new TenantRecord(1001L, "acme-corp", "Acme Corp", TenantStatus.PENDING_ACTIVATION.name()));
        Tenant createdTenant = new Tenant("acme-corp", "Acme Corp");
        createdTenant.setId(1001L);
        createdTenant.setStatus(TenantStatus.PENDING_ACTIVATION);
        when(tenantRepository.findById(1001L)).thenReturn(Optional.of(createdTenant));
        when(tenantQuotaCapability.getInstallationQuota())
                .thenReturn(new TenantQuotaCapability.InstallationQuotaSnapshot(
                        "default", 3, 0, "VALID", null, true, null, null));

        ResponseEntity<PlatformTenantDto> response = controller.createTenant(
                new CreatePlatformTenantRequest("acme-corp", "Acme Corp"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        PlatformTenantDto body = response.getBody();
        assertNotNull(body);
        assertEquals(1001L, body.tenantId());
        assertEquals("acme-corp", body.code());

        ArgumentCaptor<CreateTenantCommand> commandCaptor = ArgumentCaptor.forClass(CreateTenantCommand.class);
        verify(tenantProvisioningCapability).createTenant(commandCaptor.capture());
        assertEquals("acme-corp", commandCaptor.getValue().code());
        assertEquals("Acme Corp", commandCaptor.getValue().name());
        assertEquals(42L, commandCaptor.getValue().ownerIdentityId());

        verify(tenantRepository).existsByCode("acme-corp");
        verify(tenantRepository).findById(1001L);
        verifyNoMoreInteractions(tenantRepository);
        verify(tenantProvisioningService, never()).createTenant(any());

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).log(auditCaptor.capture());
        assertEquals("Tenant created by platform admin", auditCaptor.getValue().getDescription());
    }
}