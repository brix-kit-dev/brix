package io.brix.platform.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.brix.platform.admin.config.PlatformAdminSetupProperties;
import io.brix.platform.tenant.core.IdGenerator;
import io.brix.platform.tenant.dto.AuditEvent;
import io.brix.platform.tenant.entity.SetupToken;
import io.brix.platform.tenant.repository.SetupTokenRepository;
import io.brix.platform.tenant.security.SecretHashing;
import io.brix.platform.tenant.service.AuditService;

@SuppressWarnings("null")
class SetupTokenServiceTest {

    private final SetupTokenRepository setupTokenRepository = mock(SetupTokenRepository.class);
    private final PlatformAdminSetupProperties setupProperties = mock(PlatformAdminSetupProperties.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);
        private final AuditService auditService = mock(AuditService.class);
    private final SetupTokenService setupTokenService = new SetupTokenService(
            setupTokenRepository, setupProperties, idGenerator, auditService);

    @Test
    void issueStoresOnlyTokenHashAndPurpose() {
        when(setupProperties.getTokenTtlSeconds()).thenReturn(900L);
        when(idGenerator.nextId()).thenReturn(100L);

        SetupTokenService.IssuedSetupToken issued = setupTokenService.issue(
                10L, SetupTokenService.PURPOSE_INITIAL_SETUP, 20L);

        ArgumentCaptor<SetupToken> captor = ArgumentCaptor.forClass(SetupToken.class);
        verify(setupTokenRepository).saveAndFlush(captor.capture());
        SetupToken saved = captor.getValue();

        assertNotNull(issued.token());
        assertEquals(SetupTokenService.PURPOSE_INITIAL_SETUP, issued.purpose());
        assertEquals(100L, saved.getId());
        assertEquals(10L, saved.getIdentityId());
        assertEquals(20L, saved.getCreatedBy());
        assertNotEquals(issued.token(), saved.getTokenHash());
        assertEquals(SecretHashing.sha256Base64Url(issued.token()), saved.getTokenHash());
        verify(auditService).log(any(AuditEvent.class));
    }

    @Test
    void consumeMarksValidatedTokenUsed() {
        String rawToken = "setup-token";
        SetupToken token = new SetupToken();
        token.setId(1L);
        token.setIdentityId(10L);
        token.setPurpose(SetupTokenService.PURPOSE_PASSWORD_RESET);
        token.setTokenHash(SecretHashing.sha256Base64Url(rawToken));
        token.setExpiresAt(OffsetDateTime.now().plusMinutes(5));
        when(setupTokenRepository.findByTokenHash(token.getTokenHash())).thenReturn(Optional.of(token));
        when(setupTokenRepository.save(any(SetupToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SetupToken consumed = setupTokenService.consume(rawToken);

        assertNotNull(consumed.getUsedAt());
        verify(setupTokenRepository).save(token);
        verify(auditService).log(any(AuditEvent.class));
    }
}
