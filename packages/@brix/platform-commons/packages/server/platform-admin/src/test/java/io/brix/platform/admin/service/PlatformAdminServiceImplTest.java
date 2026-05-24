package io.brix.platform.admin.service;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.brix.platform.admin.config.PlatformAdminSetupProperties;
import io.brix.platform.admin.dto.ChangeOwnPasswordRequest;
import io.brix.platform.admin.dto.CreatePlatformAdminRequest;
import io.brix.platform.tenant.dto.AuditEvent;
import io.brix.platform.tenant.entity.Identity;
import io.brix.platform.tenant.entity.PlatformAdmin;
import io.brix.platform.tenant.core.IdGenerator;
import io.brix.platform.tenant.enums.IdentityStatus;
import io.brix.platform.tenant.enums.PlatformAdminRole;
import io.brix.platform.tenant.repository.IdentityRepository;
import io.brix.platform.tenant.repository.PlatformAdminRepository;
import io.brix.platform.tenant.service.AuditService;
import io.runtime.sdk.capability.IdentityTenantCapability;
import io.runtime.sdk.capability.NotificationCapability;
import io.runtime.sdk.capability.PasswordCapability;
import io.runtime.sdk.capability.SecretEncryptionCapability;
import io.runtime.sdk.capability.TotpCapability;

class PlatformAdminServiceImplTest {

    private final PlatformAdminRepository adminRepository = mock(PlatformAdminRepository.class);
    private final IdentityRepository identityRepository = mock(IdentityRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final PasswordCapability passwordCapability = mock(PasswordCapability.class);
    private final IdentityTenantCapability identityTenantCapability = mock(IdentityTenantCapability.class);
    private final SetupTokenService setupTokenService = mock(SetupTokenService.class);
    private final NotificationCapability notificationCapability = mock(NotificationCapability.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);
    private final PlatformAdminSetupProperties setupProperties = mock(PlatformAdminSetupProperties.class);
    private final TotpCapability totpCapability = mock(TotpCapability.class);
    private final SecretEncryptionCapability secretEncryptionCapability = mock(SecretEncryptionCapability.class);

    private final PlatformAdminServiceImpl service = new PlatformAdminServiceImpl(
            adminRepository,
            identityRepository,
            auditService,
            passwordCapability,
            identityTenantCapability,
            setupTokenService,
            notificationCapability,
            idGenerator,
            setupProperties,
            totpCapability,
            secretEncryptionCapability);

    @Test
    void createAdminFlushesPrincipalBeforeIssuingSetupToken() {
        Long identityId = 974180454301175808L;
        Long adminId = 976369206184382464L;
        Long operatorIdentityId = 970000000000000001L;

        when(identityRepository.existsByEmail("ops-admin@example.invalid")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(identityId, adminId);
        doReturn(new SetupTokenService.IssuedSetupToken(
                "raw-setup-token", SetupTokenService.PURPOSE_INITIAL_SETUP, java.time.OffsetDateTime.now().plusMinutes(15)))
                .when(setupTokenService).issue(identityId, SetupTokenService.PURPOSE_INITIAL_SETUP, operatorIdentityId);
        when(setupProperties.buildSetupUrl("raw-setup-token")).thenReturn("https://setup.example.invalid?token=redacted");

        assertTrue(service.createAdmin(new CreatePlatformAdminRequest(
                "Ops Admin",
                "ops-admin@example.invalid",
                "PLATFORM_SUPER_ADMIN",
                "test"), operatorIdentityId).setupLinkSent());

        InOrder order = inOrder(identityRepository, adminRepository, setupTokenService, notificationCapability);
        order.verify(identityRepository).saveAndFlush(any(Identity.class));
        order.verify(adminRepository).saveAndFlush(any(PlatformAdmin.class));
        order.verify(setupTokenService).issue(identityId, SetupTokenService.PURPOSE_INITIAL_SETUP, operatorIdentityId);
        order.verify(notificationCapability).sendSetupLink(
                "ops-admin@example.invalid", "https://setup.example.invalid?token=redacted", SetupTokenService.PURPOSE_INITIAL_SETUP);
        verify(auditService).log(any(AuditEvent.class));
    }

    @Test
    void resetPasswordIssuesSetupLinkWithoutReturningSecret() {
        Long adminId = 976369206184382464L;
        Long identityId = 974180454301175808L;
        Long operatorIdentityId = 970000000000000001L;

        PlatformAdmin admin = new PlatformAdmin(identityId, PlatformAdminRole.PLATFORM_SUPER_ADMIN);
        admin.setId(adminId);
        Identity identity = new Identity("admin@example.invalid", "Admin");
        identity.setId(identityId);

        when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(identityRepository.findById(identityId)).thenReturn(Optional.of(identity));
        doReturn(new SetupTokenService.IssuedSetupToken(
                "raw-setup-token", SetupTokenService.PURPOSE_PASSWORD_RESET, java.time.OffsetDateTime.now().plusMinutes(15)))
                .when(setupTokenService).issue(identityId, SetupTokenService.PURPOSE_PASSWORD_RESET, operatorIdentityId);
        when(setupProperties.buildSetupUrl("raw-setup-token")).thenReturn("https://setup.example.invalid?token=redacted");

        assertTrue(service.resetPassword(adminId, operatorIdentityId).setupLinkSent());

        verify(setupTokenService).invalidatePreviousFor(identityId);
        verify(notificationCapability).sendSetupLink(
                "admin@example.invalid", "https://setup.example.invalid?token=redacted", SetupTokenService.PURPOSE_PASSWORD_RESET);
        verify(identityTenantCapability).incrementTokenVersion(identityId);
        verify(auditService).log(any(AuditEvent.class));
    }

    @Test
    void createAdminFailsClosedWhenSetupLinkDeliveryFails() {
        Long identityId = 974180454301175808L;
        Long adminId = 976369206184382464L;
        Long operatorIdentityId = 970000000000000001L;

        when(identityRepository.existsByEmail("ops-admin@example.invalid")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(identityId, adminId);
        doReturn(new SetupTokenService.IssuedSetupToken(
                "raw-setup-token", SetupTokenService.PURPOSE_INITIAL_SETUP, java.time.OffsetDateTime.now().plusMinutes(15)))
                .when(setupTokenService).issue(identityId, SetupTokenService.PURPOSE_INITIAL_SETUP, operatorIdentityId);
        when(setupProperties.buildSetupUrl("raw-setup-token")).thenReturn("https://setup.example.invalid?token=redacted");
        doThrow(new IllegalStateException("smtp unavailable")).when(notificationCapability).sendSetupLink(
                "ops-admin@example.invalid", "https://setup.example.invalid?token=redacted", SetupTokenService.PURPOSE_INITIAL_SETUP);

        assertThrows(SetupLinkDeliveryException.class, () -> service.createAdmin(new CreatePlatformAdminRequest(
                "Ops Admin",
                "ops-admin@example.invalid",
                "PLATFORM_SUPER_ADMIN",
                "test"), operatorIdentityId));

        verify(auditService, never()).log(any(AuditEvent.class));
    }

    @Test
    void changeOwnPasswordFlushesNewHashBeforeReturningSuccess() {
        Long identityId = 974180454301175808L;
        Identity identity = new Identity("admin@example.invalid", "Admin");
        identity.setId(identityId);
        identity.setStatus(IdentityStatus.ACTIVE);
        identity.setPasswordHash("old-hash");
        identity.setMfaEnabled(true);
        identity.setMfaSecretEncrypted("encrypted-secret");

        when(identityRepository.findById(identityId)).thenReturn(Optional.of(identity));
        when(passwordCapability.verify("OldPassword!1", "old-hash")).thenReturn(true);
        when(secretEncryptionCapability.decryptSecret("encrypted-secret")).thenReturn("TOTPSECRET");
        when(totpCapability.validateCode("TOTPSECRET", "123456")).thenReturn(true);
        when(passwordCapability.hash("NewPassword!1")).thenReturn("new-hash");
        when(identityRepository.saveAndFlush(any(Identity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.changeOwnPassword(identityId, new ChangeOwnPasswordRequest(
                "OldPassword!1", "NewPassword!1", "123456"));

        ArgumentCaptor<Identity> captor = ArgumentCaptor.forClass(Identity.class);
        verify(identityRepository).saveAndFlush(captor.capture());
        assertEquals("new-hash", captor.getValue().getPasswordHash());
        verify(identityTenantCapability).incrementTokenVersion(identityId);
        verify(auditService).log(any(AuditEvent.class));
    }

    @Test
    void changeOwnPasswordRejectsWeakPasswordBeforeHashWrite() {
        Long identityId = 974180454301175808L;
        Identity identity = new Identity("admin@example.invalid", "Admin");
        identity.setId(identityId);
        identity.setStatus(IdentityStatus.ACTIVE);
        identity.setPasswordHash("old-hash");
        identity.setMfaEnabled(true);
        identity.setMfaSecretEncrypted("encrypted-secret");

        when(identityRepository.findById(identityId)).thenReturn(Optional.of(identity));
        when(passwordCapability.verify("OldPassword!1", "old-hash")).thenReturn(true);
        when(secretEncryptionCapability.decryptSecret("encrypted-secret")).thenReturn("TOTPSECRET");
        when(totpCapability.validateCode("TOTPSECRET", "123456")).thenReturn(true);

        assertThrows(RuntimeException.class, () -> service.changeOwnPassword(
                identityId, new ChangeOwnPasswordRequest("OldPassword!1", "weak-password", "123456")));

        verify(passwordCapability, never()).hash(any());
        verify(identityRepository, never()).saveAndFlush(any(Identity.class));
        verify(identityTenantCapability, never()).incrementTokenVersion(identityId);
    }
}
