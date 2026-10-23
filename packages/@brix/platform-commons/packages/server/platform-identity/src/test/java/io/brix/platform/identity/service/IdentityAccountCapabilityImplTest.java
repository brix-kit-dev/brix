package io.brix.platform.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.brix.platform.identity.entity.Identity;
import io.brix.platform.identity.entity.PlatformAdmin;
import io.brix.platform.identity.enums.IdentityStatus;
import io.brix.platform.identity.enums.PlatformAdminRole;
import io.brix.platform.identity.enums.PlatformAdminStatus;
import io.brix.platform.identity.repository.IdentityRepository;
import io.brix.platform.identity.repository.PlatformAdminRepository;
import io.runtime.sdk.capability.IdentityAccountCapability.PlatformAdminRecord;
import io.runtime.sdk.capability.SecretEncryptionCapability;
import io.runtime.sdk.capability.TotpCapability;

class IdentityAccountCapabilityImplTest {

    private IdentityRepository identityRepository;
    private PlatformAdminRepository platformAdminRepository;
    private RecordingUnitOfWork unitOfWork;
    private IdentityAccountCapabilityImpl capability;

    @BeforeEach
    void setUp() {
        identityRepository = mock(IdentityRepository.class);
        platformAdminRepository = mock(PlatformAdminRepository.class);
        unitOfWork = new RecordingUnitOfWork();
        capability = new IdentityAccountCapabilityImpl(
                identityRepository,
                platformAdminRepository,
                Optional.empty(),
                unitOfWork);
    }

    @Test
    void updatesPasswordHashThroughIdentityRepositoryOnly() {
        when(identityRepository.updatePasswordHash(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("new-hash"),
                org.mockito.ArgumentMatchers.any(OffsetDateTime.class)))
                .thenReturn(1);

        capability.updatePasswordHash(1L, "new-hash");

        verify(identityRepository).updatePasswordHash(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("new-hash"),
                org.mockito.ArgumentMatchers.any(OffsetDateTime.class));
    }

    @Test
    void rejectsPasswordHashUpdateWhenIdentityIsMissing() {
        when(identityRepository.updatePasswordHash(
                org.mockito.ArgumentMatchers.eq(404L),
                org.mockito.ArgumentMatchers.eq("new-hash"),
                org.mockito.ArgumentMatchers.any(OffsetDateTime.class)))
                .thenReturn(0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> capability.updatePasswordHash(404L, "new-hash"));

        assertEquals("Identity not found: id=404", ex.getMessage());
    }

    @Test
    void locksIdentityAndIncrementsTokenVersionOnFailedLoginThreshold() {
        Identity identity = new Identity("owner@example.invalid", "Owner");
        identity.setId(1L);
        identity.setStatus(IdentityStatus.ACTIVE);
        identity.setTokenVersion(4L);
        identity.setFailedLoginCount(4);

        when(identityRepository.findById(1L)).thenReturn(Optional.of(identity));

        var result = capability.recordFailedLogin(1L, 5, 15, "127.0.0.1");

        assertEquals(true, result.locked());
        assertEquals(5, result.failedLoginCount());
        assertEquals(5L, identity.getTokenVersion());
        verify(identityRepository).save(identity);
    }

    @Test
    void findsOnlyActivePlatformAdmin() {
        PlatformAdmin admin = new PlatformAdmin(1L, PlatformAdminRole.PLATFORM_SUPER_ADMIN);
        admin.setId(2L);
        admin.setStatus(PlatformAdminStatus.ACTIVE);
        admin.setMfaEnabled(true);
        when(platformAdminRepository.findByIdentityId(1L)).thenReturn(Optional.of(admin));

        Optional<PlatformAdminRecord> record = capability.findActivePlatformAdmin(1L);

        assertEquals(true, record.isPresent());
        assertEquals(2L, record.orElseThrow().adminId());
    }

    @Test
    void successfulLoginClearsFailureStateThroughIdentityOwner() {
        Identity identity = mock(Identity.class);
        when(identityRepository.findById(1L)).thenReturn(Optional.of(identity));

        capability.recordSuccessfulLogin(1L, "127.0.0.1");

        verify(identity).recordSuccessfulLogin("127.0.0.1");
        verify(identityRepository).save(identity);
    }

    @Test
    void readsRunInAReadOnlyUnitOfWorkAndWritesInAReadWriteOne() {
        when(identityRepository.findByEmail("owner@example.com")).thenReturn(Optional.empty());
        when(identityRepository.incrementTokenVersion(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(OffsetDateTime.class)))
                .thenReturn(1);

        capability.findIdentityByEmail("owner@example.com");
        capability.incrementTokenVersion(1L);

        assertEquals(List.of("readOnly", "required"), unitOfWork.modes());
    }

    @Test
    void argumentValidationHappensBeforeAnyUnitOfWorkIsOpened() {
        assertThrows(IllegalArgumentException.class, () -> capability.updatePasswordHash(null, "hash"));

        assertEquals(List.of(), unitOfWork.modes());
    }

    @Test
    void acceptsATotpCodeOnceAndRejectsItsReuse() {
        TotpCapability totp = mock(TotpCapability.class);
        SecretEncryptionCapability encryption = mock(SecretEncryptionCapability.class);
        IdentityAccountCapabilityImpl withTotp = new IdentityAccountCapabilityImpl(
                identityRepository, platformAdminRepository, Optional.empty(), unitOfWork,
                Optional.of(totp), Optional.of(encryption));
        Identity identity = new Identity("staff@example.invalid", "Staff");
        identity.setId(7L);
        identity.setStatus(IdentityStatus.ACTIVE);
        identity.setMfaEnabled(true);
        identity.setMfaSecretEncrypted("v1.iv.ciphertext");
        when(identityRepository.findById(7L)).thenReturn(Optional.of(identity));
        when(encryption.decryptSecret("v1.iv.ciphertext")).thenReturn("SECRET");
        when(totp.matchTimeStep("SECRET", "123456")).thenReturn(OptionalLong.of(42L));
        when(identityRepository.acceptTotpStep(eq(7L), eq(42L), any())).thenReturn(1, 0);

        assertTrue(withTotp.isTotpEnabled(7L));
        assertTrue(withTotp.verifyTotp(7L, "123456"));
        assertFalse(withTotp.verifyTotp(7L, "123456"), "the same time step must not be accepted twice");
    }

    @Test
    void totpVerificationFailsClosed() {
        TotpCapability totp = mock(TotpCapability.class);
        SecretEncryptionCapability encryption = mock(SecretEncryptionCapability.class);
        IdentityAccountCapabilityImpl withTotp = new IdentityAccountCapabilityImpl(
                identityRepository, platformAdminRepository, Optional.empty(), unitOfWork,
                Optional.of(totp), Optional.of(encryption));
        Identity unbound = new Identity("new@example.invalid", "New");
        unbound.setId(8L);
        unbound.setStatus(IdentityStatus.ACTIVE);
        Identity disabled = new Identity("gone@example.invalid", "Gone");
        disabled.setId(9L);
        disabled.setStatus(IdentityStatus.DISABLED);
        disabled.setMfaEnabled(true);
        disabled.setMfaSecretEncrypted("v1.iv.ciphertext");
        when(identityRepository.findById(8L)).thenReturn(Optional.of(unbound));
        when(identityRepository.findById(9L)).thenReturn(Optional.of(disabled));

        assertFalse(withTotp.isTotpEnabled(8L));
        assertFalse(withTotp.verifyTotp(8L, "123456"));
        assertFalse(withTotp.verifyTotp(9L, "123456"));
        assertFalse(withTotp.verifyTotp(404L, "123456"));
        assertFalse(capability.verifyTotp(8L, "123456"), "no TOTP capability means no second factor");
        verify(identityRepository, never()).acceptTotpStep(any(), anyLong(), any());
    }
}
