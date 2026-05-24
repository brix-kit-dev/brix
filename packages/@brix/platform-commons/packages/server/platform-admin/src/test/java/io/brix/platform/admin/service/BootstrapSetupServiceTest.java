package io.brix.platform.admin.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;

import io.brix.platform.admin.config.PlatformAdminSetupProperties;
import io.brix.platform.admin.dto.CreateFirstAdminRequest;
import io.brix.platform.tenant.core.IdGenerator;
import io.brix.platform.tenant.dto.AuditEvent;
import io.brix.platform.tenant.entity.BootstrapState;
import io.brix.platform.tenant.entity.Identity;
import io.brix.platform.tenant.entity.PlatformAdmin;
import io.brix.platform.tenant.enums.IdentityStatus;
import io.brix.platform.tenant.enums.PlatformAdminRole;
import io.brix.platform.tenant.enums.PlatformAdminStatus;
import io.brix.platform.tenant.repository.BootstrapStateRepository;
import io.brix.platform.tenant.repository.IdentityRepository;
import io.brix.platform.tenant.repository.PlatformAdminRepository;
import io.brix.platform.tenant.service.AuditService;
import io.runtime.sdk.capability.NotificationCapability;

@SuppressWarnings("unchecked")
class BootstrapSetupServiceTest {

    private final BootstrapTokenService bootstrapTokenService = mock(BootstrapTokenService.class);
    private final BootstrapStateRepository bootstrapStateRepository = mock(BootstrapStateRepository.class);
    private final IdentityRepository identityRepository = mock(IdentityRepository.class);
    private final PlatformAdminRepository platformAdminRepository = mock(PlatformAdminRepository.class);
    private final SetupTokenService setupTokenService = mock(SetupTokenService.class);
    private final ObjectProvider<NotificationCapability> notificationProvider = mock(ObjectProvider.class);
    private final NotificationCapability notificationCapability = mock(NotificationCapability.class);
    private final PlatformAdminSetupProperties setupProperties = mock(PlatformAdminSetupProperties.class);
    private final AuditService auditService = mock(AuditService.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);

    private final BootstrapSetupService service = new BootstrapSetupService(
            bootstrapTokenService,
            bootstrapStateRepository,
            identityRepository,
            platformAdminRepository,
            setupTokenService,
            notificationProvider,
            setupProperties,
            auditService,
            idGenerator);

    @Test
    void createFirstAdminFlushesPrincipalBeforeIssuingSetupToken() {
        Long bootstrapIdentityId = 970000000000000001L;
        Long identityId = 974180454301175808L;
        Long adminId = 976369206184382464L;
        BootstrapState state = new BootstrapState();
        state.setBootstrapIdentityId(bootstrapIdentityId);

        when(bootstrapTokenService.requireCurrentSessionForUpdate())
                .thenReturn(new BootstrapTokenService.ValidBootstrapSession(state, null));
        when(platformAdminRepository.findActiveSuperAdmins()).thenReturn(List.of());
        when(identityRepository.existsByEmail("ops-admin@example.invalid")).thenReturn(false);
        when(notificationProvider.getIfAvailable()).thenReturn(notificationCapability);
        when(idGenerator.nextId()).thenReturn(identityId, adminId);
        doReturn(new SetupTokenService.IssuedSetupToken(
                "raw-setup-token", SetupTokenService.PURPOSE_INITIAL_SETUP, OffsetDateTime.now().plusMinutes(15)))
                .when(setupTokenService).issuePlatformAdminSetupToken(identityId, bootstrapIdentityId);
        when(setupProperties.buildSetupUrl("raw-setup-token")).thenReturn("https://setup.example.invalid?token=redacted");

        assertTrue(service.createFirstAdmin(new CreateFirstAdminRequest(
                "Ops Admin", "ops-admin@example.invalid", "test")).setupLinkSent());

        InOrder order = inOrder(identityRepository, platformAdminRepository, setupTokenService, notificationCapability);
        order.verify(identityRepository).saveAndFlush(any(Identity.class));
        order.verify(platformAdminRepository).saveAndFlush(any(PlatformAdmin.class));
        order.verify(setupTokenService).issuePlatformAdminSetupToken(identityId, bootstrapIdentityId);
        order.verify(notificationCapability).sendSetupLink(
                "ops-admin@example.invalid", "https://setup.example.invalid?token=redacted", SetupTokenService.PURPOSE_INITIAL_SETUP);
        verify(bootstrapStateRepository).save(state);
        verify(auditService).log(any(AuditEvent.class));
    }

    @Test
    void createFirstAdminReissuesSetupLinkForExistingPendingFirstAdminWithSameEmail() {
        Long bootstrapIdentityId = 970000000000000001L;
        Long identityId = 974180454301175808L;
        Long adminId = 976369206184382464L;
        BootstrapState state = new BootstrapState();
        state.setBootstrapIdentityId(bootstrapIdentityId);

        Identity pendingIdentity = new Identity("ops-admin@example.invalid", "ops-admin");
        pendingIdentity.setId(identityId);
        pendingIdentity.setStatus(IdentityStatus.PENDING_SETUP);
        pendingIdentity.setPasswordHash(null);
        pendingIdentity.setMfaEnabled(false);
        PlatformAdmin existingAdmin = new PlatformAdmin(identityId, PlatformAdminRole.PLATFORM_SUPER_ADMIN);
        existingAdmin.setId(adminId);
        existingAdmin.setStatus(PlatformAdminStatus.ACTIVE);

        when(bootstrapTokenService.requireCurrentSessionForUpdate())
                .thenReturn(new BootstrapTokenService.ValidBootstrapSession(state, null));
        when(platformAdminRepository.findActiveSuperAdmins()).thenReturn(List.of(existingAdmin));
        when(identityRepository.findById(identityId)).thenReturn(Optional.of(pendingIdentity));
        when(notificationProvider.getIfAvailable()).thenReturn(notificationCapability);
        doReturn(new SetupTokenService.IssuedSetupToken(
                "raw-setup-token", SetupTokenService.PURPOSE_INITIAL_SETUP, OffsetDateTime.now().plusMinutes(15)))
                .when(setupTokenService).issuePlatformAdminSetupToken(identityId, bootstrapIdentityId);
        when(setupProperties.buildSetupUrl("raw-setup-token")).thenReturn("https://setup.example.invalid?token=redacted");

        assertTrue(service.createFirstAdmin(new CreateFirstAdminRequest(
                "Ops Admin", "OPS-ADMIN@example.invalid", "retry")).setupLinkSent());

        verify(identityRepository, never()).saveAndFlush(any(Identity.class));
        verify(platformAdminRepository, never()).saveAndFlush(any(PlatformAdmin.class));
        verify(setupTokenService).issuePlatformAdminSetupToken(identityId, bootstrapIdentityId);
        verify(notificationCapability).sendSetupLink(
                "ops-admin@example.invalid", "https://setup.example.invalid?token=redacted", SetupTokenService.PURPOSE_INITIAL_SETUP);
        verify(bootstrapStateRepository).save(state);
        verify(auditService).log(any(AuditEvent.class));
    }

    @Test
    void createFirstAdminFailsClosedWhenSetupLinkDeliveryFails() {
        Long bootstrapIdentityId = 970000000000000001L;
        Long identityId = 974180454301175808L;
        Long adminId = 976369206184382464L;
        BootstrapState state = new BootstrapState();
        state.setBootstrapIdentityId(bootstrapIdentityId);

        when(bootstrapTokenService.requireCurrentSessionForUpdate())
                .thenReturn(new BootstrapTokenService.ValidBootstrapSession(state, null));
        when(platformAdminRepository.findActiveSuperAdmins()).thenReturn(List.of());
        when(identityRepository.existsByEmail("ops-admin@example.invalid")).thenReturn(false);
        when(notificationProvider.getIfAvailable()).thenReturn(notificationCapability);
        when(idGenerator.nextId()).thenReturn(identityId, adminId);
        doReturn(new SetupTokenService.IssuedSetupToken(
                "raw-setup-token", SetupTokenService.PURPOSE_INITIAL_SETUP, OffsetDateTime.now().plusMinutes(15)))
                .when(setupTokenService).issuePlatformAdminSetupToken(identityId, bootstrapIdentityId);
        when(setupProperties.buildSetupUrl("raw-setup-token")).thenReturn("https://setup.example.invalid?token=redacted");
        doThrow(new IllegalStateException("smtp unavailable")).when(notificationCapability).sendSetupLink(
                "ops-admin@example.invalid", "https://setup.example.invalid?token=redacted", SetupTokenService.PURPOSE_INITIAL_SETUP);

        assertThrows(SetupLinkDeliveryException.class, () -> service.createFirstAdmin(new CreateFirstAdminRequest(
                "Ops Admin", "ops-admin@example.invalid", "test")));

        verify(bootstrapStateRepository, never()).save(state);
        verify(auditService, never()).log(any(AuditEvent.class));
    }
}
