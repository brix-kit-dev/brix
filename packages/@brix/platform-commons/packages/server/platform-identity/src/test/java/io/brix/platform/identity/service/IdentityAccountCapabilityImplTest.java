package io.brix.platform.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;

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

class IdentityAccountCapabilityImplTest {

    private IdentityRepository identityRepository;
    private PlatformAdminRepository platformAdminRepository;
    private IdentityAccountCapabilityImpl capability;

    @BeforeEach
    void setUp() {
        identityRepository = mock(IdentityRepository.class);
        platformAdminRepository = mock(PlatformAdminRepository.class);
        capability = new IdentityAccountCapabilityImpl(
                identityRepository,
                platformAdminRepository,
                Optional.empty());
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
}
