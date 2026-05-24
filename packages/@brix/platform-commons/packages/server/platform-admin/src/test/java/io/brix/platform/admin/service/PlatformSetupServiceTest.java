package io.brix.platform.admin.service;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.context.ApplicationEventPublisher;

import io.brix.platform.admin.dto.PlatformSetupCompleteRequest;
import io.brix.platform.tenant.dto.AuditEvent;
import io.brix.platform.tenant.entity.Identity;
import io.brix.platform.tenant.entity.PlatformAdmin;
import io.brix.platform.tenant.entity.SetupToken;
import io.brix.platform.tenant.enums.IdentityStatus;
import io.brix.platform.tenant.enums.PlatformAdminRole;
import io.brix.platform.tenant.enums.PlatformAdminStatus;
import io.brix.platform.tenant.repository.IdentityRepository;
import io.brix.platform.tenant.repository.PlatformAdminRepository;
import io.brix.platform.tenant.service.AuditService;
import io.runtime.sdk.capability.PasswordCapability;
import io.runtime.sdk.capability.SecretEncryptionCapability;
import io.runtime.sdk.capability.StateStoreCapability;
import io.runtime.sdk.capability.TotpCapability;

class PlatformSetupServiceTest {

    private final SetupTokenService setupTokenService = mock(SetupTokenService.class);
    private final IdentityRepository identityRepository = mock(IdentityRepository.class);
    private final PlatformAdminRepository platformAdminRepository = mock(PlatformAdminRepository.class);
    private final PasswordCapability passwordCapability = mock(PasswordCapability.class);
    private final TotpCapability totpCapability = mock(TotpCapability.class);
    private final SecretEncryptionCapability secretEncryptionCapability = mock(SecretEncryptionCapability.class);
    private final StateStoreCapability stateStoreCapability = mock(StateStoreCapability.class);
    private final AuditService auditService = mock(AuditService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final PlatformSetupService service = new PlatformSetupService(
            setupTokenService,
            identityRepository,
            platformAdminRepository,
            passwordCapability,
            totpCapability,
            secretEncryptionCapability,
            stateStoreCapability,
            auditService,
            eventPublisher);

    @Test
    void completeFlushesPasswordHashBeforeReturningSuccess() {
        Long identityId = 974180454301175808L;
        SetupToken token = setupToken(identityId);
        Identity identity = new Identity("admin@example.invalid", "Admin");
        identity.setId(identityId);
        PlatformAdmin admin = new PlatformAdmin(identityId, PlatformAdminRole.PLATFORM_SUPER_ADMIN);
        admin.setId(976369206184382464L);
        admin.setStatus(PlatformAdminStatus.ACTIVE);
        PlatformSetupService.TotpSetupChallenge challenge =
                new PlatformSetupService.TotpSetupChallenge(identityId, SetupTokenService.PURPOSE_PASSWORD_RESET, "encrypted-secret");

        when(setupTokenService.validate("raw-token")).thenReturn(token);
        when(identityRepository.findById(identityId)).thenReturn(Optional.of(identity));
        when(platformAdminRepository.findByIdentityId(identityId)).thenReturn(Optional.of(admin));
        when(stateStoreCapability.get("platform-admin:setup-totp:challenge-id", PlatformSetupService.TotpSetupChallenge.class))
                .thenReturn(Optional.of(challenge));
        when(secretEncryptionCapability.decryptSecret("encrypted-secret")).thenReturn("TOTPSECRET");
        when(totpCapability.validateCode("TOTPSECRET", "123456")).thenReturn(true);
        when(passwordCapability.hash("NewPassword!1")).thenReturn("new-hash");
        when(identityRepository.saveAndFlush(any(Identity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(platformAdminRepository.saveAndFlush(any(PlatformAdmin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.complete(new PlatformSetupCompleteRequest("raw-token", "challenge-id", "NewPassword!1", "123456"));

        ArgumentCaptor<Identity> identityCaptor = ArgumentCaptor.forClass(Identity.class);
        verify(identityRepository).saveAndFlush(identityCaptor.capture());
        assertEquals("new-hash", identityCaptor.getValue().getPasswordHash());
        assertEquals(IdentityStatus.ACTIVE, identityCaptor.getValue().getStatus());
        verify(setupTokenService).consume("raw-token");
        verify(stateStoreCapability).remove("platform-admin:setup-totp:challenge-id");
        verify(auditService, atLeastOnce()).log(any(AuditEvent.class));
    }

    @Test
    void completeRejectsWeakPasswordBeforeConsumingSetupToken() {
        Long identityId = 974180454301175808L;
        SetupToken token = setupToken(identityId);
        Identity identity = new Identity("admin@example.invalid", "Admin");
        identity.setId(identityId);
        PlatformAdmin admin = new PlatformAdmin(identityId, PlatformAdminRole.PLATFORM_SUPER_ADMIN);
        PlatformSetupService.TotpSetupChallenge challenge =
                new PlatformSetupService.TotpSetupChallenge(identityId, SetupTokenService.PURPOSE_PASSWORD_RESET, "encrypted-secret");

        when(setupTokenService.validate("raw-token")).thenReturn(token);
        when(identityRepository.findById(identityId)).thenReturn(Optional.of(identity));
        when(platformAdminRepository.findByIdentityId(identityId)).thenReturn(Optional.of(admin));
        when(stateStoreCapability.get("platform-admin:setup-totp:challenge-id", PlatformSetupService.TotpSetupChallenge.class))
                .thenReturn(Optional.of(challenge));
        when(secretEncryptionCapability.decryptSecret("encrypted-secret")).thenReturn("TOTPSECRET");
        when(totpCapability.validateCode("TOTPSECRET", "123456")).thenReturn(true);

        assertThrows(RuntimeException.class, () -> service.complete(
                new PlatformSetupCompleteRequest("raw-token", "challenge-id", "weak-password", "123456")));

        verify(passwordCapability, never()).hash(any());
        verify(identityRepository, never()).saveAndFlush(any(Identity.class));
        verify(setupTokenService, never()).consume("raw-token");
    }

    private static SetupToken setupToken(Long identityId) {
        SetupToken token = new SetupToken();
        token.setId(1L);
        token.setIdentityId(identityId);
        token.setPurpose(SetupTokenService.PURPOSE_PASSWORD_RESET);
        token.setExpiresAt(OffsetDateTime.now().plusMinutes(15));
        return token;
    }
}
